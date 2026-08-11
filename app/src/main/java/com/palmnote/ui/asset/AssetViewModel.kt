package com.palmnote.ui.asset
import kotlin.jvm.JvmSuppressWildcards
import com.palmnote.domain.model.AssetStatus
import android.content.Context
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.palmnote.data.db.dao.CategoryCount
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.UsageRecord
import com.palmnote.domain.model.Money
import com.palmnote.domain.model.toYuanString
import com.palmnote.domain.repository.AssetRepository
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.UsageRecordRepository
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.toComposeColor
import com.palmnote.ui.components.toImageJson
import com.palmnote.ui.components.toImageList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


enum class AssetFilter { ALL, HELD, AWAY, REMOVED }
enum class SortOption { NAME, PRICE, DAILY_COST, DATE, RECENT }

private data class AssetValues(
    val assets: List<Asset>,
    val totalValue: Long,
    val heldValue: Long,
    val removedValue: Long,
    val distribution: List<CategoryCount>
)

private data class StatusCounts(
    val held: Int,
    val away: Int,
    val removed: Int
)

@Stable
data class AssetState(
    val assets: List<Asset> = emptyList(),
    val filteredAssets: List<Asset> = emptyList(),
    val selectedCategory: String? = null,
    val selectedStatus: AssetFilter = AssetFilter.ALL,
    val selectedSort: SortOption = SortOption.RECENT,
    val sortAscending: Boolean = false,
    val searchQuery: String = "",
    val isGridView: Boolean = false,
    val categoryDistribution: List<CategoryCount> = emptyList(),
    val totalValue: Long = 0,
    val heldValue: Long = 0,
    val removedValue: Long = 0,
    val heldCount: Int = 0,
    val awayCount: Int = 0,
    val removedCount: Int = 0
)

@Stable
data class AssetDetailState(
    val asset: Asset? = null,
    val usageRecords: List<UsageRecord> = emptyList(),
    val linkedBill: Bill? = null,
    val costPerDay: Double = 0.0,
    val costPerUse: Double = 0.0,
    val daysOwned: Int = 0,
    val isLoading: Boolean = true
)

@Stable
@kotlinx.serialization.Serializable
data class AddAssetFormState(
    val id: Long? = null,
    val name: String = "",
    val category: String = "",
    val subCategory: String = "",
    val brand: String = "",
    val model: String = "",
    val acquisitionType: String = "PURCHASE",
    val customAcquisitionLabel: String = "",
    val quantity: String = "1",
    val purchasePrice: String = "",
    val acquisitionDate: Long? = null,
    val purchaseChannel: String = "",
    val location: String = "",
    val room: String = "",
    val warrantyExpireDate: Long? = null,
    val costMode: String = "DAILY",
    val description: String = "",
    val images: String = "",
    val status: AssetStatus = AssetStatus.HELD,
    val condition: String = "GOOD",
    val serialNumber: String = "",
    val isFavorite: Boolean = false,
    val depreciationRate: String = "",
    val currentValue: String = "",
    val maintenanceIntervalDays: String = "",
    val lastMaintenanceDate: Long? = null,
    val nextMaintenanceDate: Long? = null,
    val maintenanceNotes: String = "",
    val insuranceExpireDate: Long? = null,
    val insuranceCompany: String = "",
    val insurancePolicyNo: String = "",
    val receiptPath: String = "",
    val totalUsageHours: String = "",
    val linkedBillId: Long? = null,
    val linkedMomentId: Long? = null,
    val sortOrder: String = "0",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val nameError: String? = null,
    val categoryError: String? = null,
    val dateError: String? = null
)

@HiltViewModel
class AssetViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val assetRepository: AssetRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val billRepository: BillRepository,
    private val cachedCategoryConfigs: @JvmSuppressWildcards StateFlow<List<CategoryConfig>>,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(AssetState())
    val state: StateFlow<AssetState> = _state.asStateFlow()

    private val _detailState = MutableStateFlow(AssetDetailState())
    val detailState: StateFlow<AssetDetailState> = _detailState.asStateFlow()

    private val _formState = MutableStateFlow(AddAssetFormState())
    val formState: StateFlow<AddAssetFormState> = _formState.asStateFlow()

    val customCategories: StateFlow<List<com.palmnote.ui.components.CategoryItem>> = cachedCategoryConfigs
        .map { configs -> configs.filter { it.type == "ASSET" && it.isEnabled }
            .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presetCategoryOverrides: StateFlow<Map<String, String>> =
        preferencesManager.presetCategoryOverrides
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val categoryConfigs: StateFlow<List<CategoryConfig>> = cachedCategoryConfigs

    enum class DialogType { AWAY, CLEAR, DELETE }

    private val _dialogType = MutableStateFlow<DialogType?>(null)
    val showAwayDialog: StateFlow<Boolean> = _dialogType.map { it == DialogType.AWAY }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showClearDialog: StateFlow<Boolean> = _dialogType.map { it == DialogType.CLEAR }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showDeleteDialog: StateFlow<Boolean> = _dialogType.map { it == DialogType.DELETE }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        savedStateHandle.get<String>("asset_selected_category")?.let { v -> _state.update { it.copy(selectedCategory = v.ifEmpty { null }) } }
        savedStateHandle.get<String>("asset_selected_status")?.let { v -> _state.update { it.copy(selectedStatus = runCatching { AssetFilter.valueOf(v) }.getOrDefault(AssetFilter.ALL)) } }
        savedStateHandle.get<String>("asset_selected_sort")?.let { v -> _state.update { it.copy(selectedSort = runCatching { SortOption.valueOf(v) }.getOrDefault(SortOption.RECENT)) } }
        savedStateHandle.get<Boolean>("asset_sort_ascending")?.let { v -> _state.update { it.copy(sortAscending = v) } }
        savedStateHandle.get<String>("asset_search_query")?.let { v -> _state.update { it.copy(searchQuery = v) } }
        savedStateHandle.get<Boolean>("asset_is_grid_view")?.let { v -> _state.update { it.copy(isGridView = v) } }
        // 恢复上次未提交的资产表单草稿（进程被杀重建后）
        savedStateHandle.get<String>("asset_draft")?.let { json ->
            try {
                val draft = kotlinx.serialization.json.Json.decodeFromString<AddAssetFormState>(json)
                if (draft.id == null && draft.name.isNotBlank()) {
                    _formState.value = draft.copy(
                        isEditing = false, isSaving = false, isSaved = false,
                        nameError = null, categoryError = null, dateError = null
                    )
                }
            } catch (_: Exception) { savedStateHandle.remove<String>("asset_draft") }
        }
        // 自动保存新建资产表单草稿（防抖）
        viewModelScope.launch {
            _formState.debounce(500).collect { form ->
                val hasContent = form.name.isNotBlank() || form.brand.isNotBlank() ||
                    form.model.isNotBlank() || form.purchasePrice.isNotBlank() || form.description.isNotBlank()
                if (form.isSaved || form.isEditing || !hasContent) {
                    savedStateHandle.remove<String>("asset_draft")
                } else {
                    savedStateHandle["asset_draft"] = kotlinx.serialization.json.Json.encodeToString(form)
                }
            }
        }
        loadAssets()
        loadViewMode()
    }

    private fun loadViewMode() {
        viewModelScope.launch {
            preferencesManager.assetViewMode.collect { isGrid ->
                _state.update { it.copy(isGridView = isGrid) }
            }
        }
    }

    private fun loadAssets() {
        viewModelScope.launch {
            val assetsFlow = combine(
                assetRepository.getAllAssetsSortedByStatus(),
                assetRepository.getTotalAssetValue(),
                assetRepository.getHeldAssetValue(),
                assetRepository.getTotalSoldValue(),
                assetRepository.getCategoryDistribution()
            ) { assets, totalValue, heldValue, removedValue, distribution ->
                AssetValues(
                    assets = assets,
                    totalValue = totalValue ?: 0L,
                    heldValue = heldValue ?: 0L,
                    removedValue = removedValue ?: 0L,
                    distribution = distribution
                )
            }

            val countsFlow = combine(
                assetRepository.getAssetCountByStatus(AssetStatus.HELD),
                assetRepository.getAssetCountByStatus(AssetStatus.AWAY),
                assetRepository.getAssetCountByStatus(AssetStatus.REMOVED)
            ) { held, away, removed ->
                StatusCounts(held, away, removed)
            }

            combine(assetsFlow, countsFlow) { assetValues, counts ->
                val s = _state.value
                AssetState(
                    assets = assetValues.assets,
                    filteredAssets = withContext(Dispatchers.Default) {
                        filterAssets(assetValues.assets, s.selectedCategory, s.selectedStatus, s.selectedSort, s.sortAscending, s.searchQuery)
                    },
                    selectedCategory = s.selectedCategory,
                    selectedStatus = s.selectedStatus,
                    selectedSort = s.selectedSort,
                    sortAscending = s.sortAscending,
                    searchQuery = s.searchQuery,
                    isGridView = s.isGridView,
                    categoryDistribution = assetValues.distribution,
                    totalValue = assetValues.totalValue,
                    heldValue = assetValues.heldValue,
                    removedValue = assetValues.removedValue,
                    heldCount = counts.held,
                    awayCount = counts.away,
                    removedCount = counts.removed
                )
            }.collect { state ->
                _state.value = state
                savedStateHandle["asset_selected_category"] = state.selectedCategory ?: ""
                savedStateHandle["asset_selected_status"] = state.selectedStatus.name
                savedStateHandle["asset_selected_sort"] = state.selectedSort.name
                savedStateHandle["asset_sort_ascending"] = state.sortAscending
                savedStateHandle["asset_search_query"] = state.searchQuery
                savedStateHandle["asset_is_grid_view"] = state.isGridView
            }
        }
    }

    private fun filterAssets(
        assets: List<Asset>,
        category: String?,
        statusFilter: AssetFilter,
        sortOption: SortOption,
        ascending: Boolean,
        query: String
    ): List<Asset> {
        val filtered = assets.filter { asset ->
            val matchesCategory = category == null || asset.category == category
            val matchesStatus = when (statusFilter) {
                AssetFilter.ALL -> true
                AssetFilter.HELD -> asset.status == AssetStatus.HELD
                AssetFilter.AWAY -> asset.status == AssetStatus.AWAY
                AssetFilter.REMOVED -> asset.status == AssetStatus.REMOVED
            }
            val matchesSearch = query.isEmpty() ||
                asset.name.contains(query, ignoreCase = true) ||
                asset.category.contains(query, ignoreCase = true) ||
                asset.description.contains(query, ignoreCase = true)
            matchesCategory && matchesStatus && matchesSearch
        }
        val sorted = when (sortOption) {
            SortOption.NAME -> filtered.sortedBy { it.name }
            SortOption.PRICE -> filtered.sortedBy { it.purchasePrice }
            SortOption.DAILY_COST -> filtered.sortedBy { it.dailyCost }
            SortOption.DATE -> filtered.sortedBy { it.effectiveDate }
            SortOption.RECENT -> filtered.sortedBy { it.id }
        }
        return if (ascending) sorted else sorted.reversed()
    }

    private var searchJob: Job? = null
    
    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _state.value = _state.value.copy(
                filteredAssets = filterAssets(_state.value.assets, _state.value.selectedCategory, _state.value.selectedStatus, _state.value.selectedSort, _state.value.sortAscending, query)
            )
        }
    }

    fun toggleViewMode() {
        val newIsGrid = !_state.value.isGridView
        _state.value = _state.value.copy(isGridView = newIsGrid)
        viewModelScope.launch { preferencesManager.setAssetViewMode(newIsGrid) }
    }

    fun setCategoryFilter(category: String?) {
        _state.value = _state.value.copy(
            selectedCategory = category,
            filteredAssets = filterAssets(_state.value.assets, category, _state.value.selectedStatus, _state.value.selectedSort, _state.value.sortAscending, _state.value.searchQuery)
        )
    }

    fun setStatusFilter(filter: AssetFilter) {
        _state.value = _state.value.copy(
            selectedStatus = filter,
            filteredAssets = filterAssets(_state.value.assets, _state.value.selectedCategory, filter, _state.value.selectedSort, _state.value.sortAscending, _state.value.searchQuery)
        )
    }

    fun setSortFilter(sortOption: SortOption) {
        val current = _state.value
        val ascending = if (sortOption == current.selectedSort) !current.sortAscending else false
        _state.value = current.copy(
            selectedSort = sortOption,
            sortAscending = ascending,
            filteredAssets = filterAssets(current.assets, current.selectedCategory, current.selectedStatus, sortOption, ascending, current.searchQuery)
        )
    }

    // Asset Detail
    private var detailJob: Job? = null

    fun loadAssetDetail(assetId: Long) {
        detailJob?.cancel()
        _detailState.update { it.copy(isLoading = true) }
        detailJob = viewModelScope.launch {
            combine(
                assetRepository.getAssetByIdFlow(assetId),
                usageRecordRepository.getUsageRecordsByAsset(assetId)
            ) { asset, records ->
                Pair(asset, records)
            }.collect { (asset, records) ->
                val daysOwned = asset?.let { DateUtils.getDaysSince(it.effectiveDate).coerceAtLeast(1) } ?: 1
                val costPerDay = if (daysOwned > 0) (asset?.purchasePrice ?: 0L) / 100.0 / daysOwned else 0.0
                val costPerUse = if ((asset?.useCount ?: 0) > 0) {
                    (asset?.purchasePrice ?: 0L) / 100.0 / (asset?.useCount ?: 1)
                } else 0.0

                val linkedBill = asset?.linkedBillId?.let { billRepository.getBillById(it) }

                _detailState.update {
                    AssetDetailState(
                        asset = asset,
                        usageRecords = records,
                        linkedBill = linkedBill,
                        costPerDay = costPerDay,
                        costPerUse = costPerUse,
                        daysOwned = daysOwned,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun addUsageRecord(assetId: Long, note: String = "") {
        viewModelScope.launch {
            val record = UsageRecord(
                assetId = assetId,
                usedAt = System.currentTimeMillis(),
                note = note
            )
            usageRecordRepository.insertUsageRecord(record)
            assetRepository.incrementUseCount(assetId)
        }
    }

    fun deleteUsageRecord(id: Long) {
        viewModelScope.launch { usageRecordRepository.deleteUsageRecordById(id) }
    }

    fun updateUsageRecord(id: Long, usedAt: Long, note: String) {
        viewModelScope.launch {
            usageRecordRepository.updateUsageRecord(
                UsageRecord(id = id, assetId = _detailState.value.asset?.id ?: return@launch, usedAt = usedAt, note = note)
            )
        }
    }

    fun showDialog(type: DialogType) { _dialogType.value = type }
    fun hideDialog() { _dialogType.value = null }
    fun showAwayDialog() { showDialog(DialogType.AWAY) }
    fun hideAwayDialog() { hideDialog() }
    fun showClearDialog() { showDialog(DialogType.CLEAR) }
    fun hideClearDialog() { hideDialog() }
    fun showDeleteDialog() { showDialog(DialogType.DELETE) }
    fun hideDeleteDialog() { hideDialog() }

    fun awayAsset(id: Long, tags: String, reason: String) {
        viewModelScope.launch {
            assetRepository.awayAsset(id, tags, reason)
            hideDialog()
        }
    }

    fun retireAsset(id: Long, reason: String) {
        viewModelScope.launch {
            assetRepository.retireAsset(id, reason)
            hideDialog()
        }
    }

    fun markAssetLost(id: Long, reason: String) {
        viewModelScope.launch {
            assetRepository.markAssetLost(id, reason)
            hideDialog()
        }
    }

    fun sellAsset(id: Long, price: Long, channel: String) {
        viewModelScope.launch {
            assetRepository.sellAsset(id, price, channel, "")
            hideDialog()
        }
    }

    fun reactivateAsset(id: Long) {
        viewModelScope.launch {
            assetRepository.reactivateAsset(id)
        }
    }

    fun completeMaintenance(id: Long) {
        viewModelScope.launch {
            val asset = assetRepository.getAssetById(id) ?: return@launch
            val now = System.currentTimeMillis()
            val nextDate = if (asset.maintenanceIntervalDays > 0) {
                now + (asset.maintenanceIntervalDays * DateUtils.MILLIS_PER_DAY)
            } else null
            assetRepository.completeMaintenance(id, now, nextDate, "")
        }
    }

    fun deleteAsset(id: Long) {
        viewModelScope.launch {
            assetRepository.deleteAsset(id)
            hideDialog()
        }
    }

    // Form management
    fun initFormForEdit(assetId: Long) {
        viewModelScope.launch {
            val asset = assetRepository.getAssetById(assetId) ?: return@launch
            _formState.value = AddAssetFormState(
                id = asset.id,
                name = asset.name,
                category = asset.category,
                subCategory = asset.subCategory,
                brand = asset.brand,
                model = asset.model,
                acquisitionType = if (asset.acquisitionType !in listOf("PURCHASE", "GIFT", "LOTTERY", "PRIZE", "INHERITANCE", "OTHER")) "CUSTOM" else asset.acquisitionType,
                customAcquisitionLabel = if (asset.acquisitionType !in listOf("PURCHASE", "GIFT", "LOTTERY", "PRIZE", "INHERITANCE", "OTHER")) asset.acquisitionType else "",
                quantity = if (asset.quantity > 0) asset.quantity.toString() else "1",
                purchasePrice = if (asset.purchasePrice > 0) asset.purchasePrice.toYuanString() else "",
                acquisitionDate = asset.acquisitionDate,
                purchaseChannel = asset.purchaseChannel,
                location = asset.location,
                room = asset.room,
                warrantyExpireDate = asset.warrantyExpireDate,
                costMode = asset.costMode,
                description = asset.description,
                images = asset.images,
                status = asset.status,
                condition = asset.condition,
                serialNumber = asset.serialNumber,
                isFavorite = asset.isFavorite,
                depreciationRate = if (asset.depreciationRate > 0) asset.depreciationRate.toString() else "",
                currentValue = if (asset.currentValue > 0) asset.currentValue.toYuanString() else "",
                maintenanceIntervalDays = if (asset.maintenanceIntervalDays > 0) asset.maintenanceIntervalDays.toString() else "",
                lastMaintenanceDate = asset.lastMaintenanceDate,
                nextMaintenanceDate = asset.nextMaintenanceDate,
                maintenanceNotes = asset.maintenanceNotes,
                insuranceExpireDate = asset.insuranceExpireDate,
                insuranceCompany = asset.insuranceCompany,
                insurancePolicyNo = asset.insurancePolicyNo,
                receiptPath = asset.receiptPath,
                totalUsageHours = if (asset.totalUsageHours > 0) asset.totalUsageHours.toString() else "",
                linkedBillId = asset.linkedBillId,
                linkedMomentId = asset.linkedMomentId,
                sortOrder = asset.sortOrder.toString(),
                tags = asset.tags,
                createdAt = asset.createdAt,
                isEditing = true
            )
        }
    }

    fun resetForm() {
        _formState.value = AddAssetFormState()
    }

    fun updateFormField(update: AddAssetFormState.() -> AddAssetFormState) {
        _formState.value = _formState.value.update()
    }

    fun saveAsset() {
        val form = _formState.value

        var hasError = false
        if (form.name.isBlank()) {
            _formState.value = form.copy(nameError = application.getString(R.string.asset_error_name_required))
            hasError = true
        }
        if (form.category.isBlank()) {
            _formState.value = _formState.value.copy(categoryError = application.getString(R.string.asset_error_category_required))
            hasError = true
        }
        if (form.acquisitionType == "PURCHASE" && form.acquisitionDate == null) {
            _formState.value = _formState.value.copy(dateError = application.getString(R.string.asset_error_date_required))
            hasError = true
        }
        if (hasError) return

        _formState.value = form.copy(isSaving = true)

        viewModelScope.launch {
            val price = Money.parse(form.purchasePrice)?.cents ?: 0L
            val now = System.currentTimeMillis()

            val asset = Asset(
                id = form.id ?: 0L,
                name = form.name.trim(),
                category = form.category,
                subCategory = form.subCategory.trim(),
                brand = form.brand.trim(),
                model = form.model.trim(),
                quantity = form.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                purchasePrice = price,
                acquisitionType = if (form.acquisitionType == "CUSTOM") form.customAcquisitionLabel else form.acquisitionType,
                acquisitionDate = form.acquisitionDate,
                status = form.status,
                condition = form.condition,
                serialNumber = form.serialNumber.trim(),
                isFavorite = form.isFavorite,
                depreciationRate = form.depreciationRate.toDoubleOrNull() ?: 0.0,
                currentValue = Money.parse(form.currentValue)?.cents ?: 0L,
                maintenanceIntervalDays = form.maintenanceIntervalDays.toIntOrNull() ?: 0,
                lastMaintenanceDate = form.lastMaintenanceDate,
                nextMaintenanceDate = form.nextMaintenanceDate,
                maintenanceNotes = form.maintenanceNotes.trim(),
                insuranceExpireDate = form.insuranceExpireDate,
                insuranceCompany = form.insuranceCompany.trim(),
                insurancePolicyNo = form.insurancePolicyNo.trim(),
                receiptPath = form.receiptPath,
                totalUsageHours = form.totalUsageHours.toDoubleOrNull() ?: 0.0,
                linkedBillId = form.linkedBillId,
                linkedMomentId = form.linkedMomentId,
                sortOrder = form.sortOrder.toIntOrNull() ?: 0,
                tags = form.tags,
                costMode = form.costMode,
                purchaseChannel = form.purchaseChannel.trim(),
                location = form.location.trim(),
                room = form.room.trim(),
                warrantyExpireDate = form.warrantyExpireDate,
                description = form.description.trim(),
                images = form.images,
                createdAt = if (form.isEditing) form.createdAt else now,
                updatedAt = now
            )

            if (form.isEditing && form.id != null) {
                assetRepository.updateAsset(asset)
            } else {
                assetRepository.insertAsset(asset)
            }

            _formState.value = form.copy(isSaving = false, isSaved = true)
        }
    }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val path = com.palmnote.ui.components.saveImageToInternalStorage(application, uri, "asset_") ?: return@launch
            updateImages { add(path) }
        }
    }

    private fun updateImages(transform: MutableList<String>.() -> Unit) {
        val list = _formState.value.images.toImageList().toMutableList()
        list.transform()
        _formState.value = _formState.value.copy(images = list.toImageJson())
    }

    fun removeImage(index: Int) {
        updateImages { if (index in indices) removeAt(index) }
    }

    fun reorderImages(from: Int, to: Int) {
        updateImages {
            if (from in indices && to in indices) {
                add(to, removeAt(from))
            }
        }
    }
}
