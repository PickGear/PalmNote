package com.palmnote.ui.asset

import android.content.ContentValues
import android.net.Uri
import android.util.Log
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.palmnote.R
import com.palmnote.data.DataCache
import com.palmnote.data.datastore.PreferencesManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.palmnote.data.db.dao.CategoryCount
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.UsageRecord
import com.palmnote.domain.repository.AssetRepository
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.UsageRecordRepository
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


enum class AssetFilter { ALL, HELD, AWAY, REMOVED }
enum class SortOption { NAME, PRICE, DAILY_COST, DATE, RECENT }

private val imageJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

fun List<String>.toImageJson(): String = imageJson.encodeToString(this)

fun String.toImageList(): List<String> {
    if (isEmpty()) return emptyList()
    return try {
        imageJson.decodeFromString<List<String>>(this)
    } catch (_: Exception) {
        listOf(this)
    }
}

private data class AssetValues(
    val assets: List<Asset>,
    val totalValue: Double,
    val heldValue: Double,
    val removedValue: Double,
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
    val totalValue: Double = 0.0,
    val heldValue: Double = 0.0,
    val removedValue: Double = 0.0,
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
    val status: String = "HELD",
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

class AssetViewModel(
    private val application: Application,
    private val assetRepository: AssetRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val billRepository: BillRepository,
    private val categoryConfigRepository: com.palmnote.data.repository.CategoryConfigRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(AssetState())
    val state: StateFlow<AssetState> = _state.asStateFlow()

    private val _detailState = MutableStateFlow(AssetDetailState())
    val detailState: StateFlow<AssetDetailState> = _detailState.asStateFlow()

    private val _formState = MutableStateFlow(AddAssetFormState())
    val formState: StateFlow<AddAssetFormState> = _formState.asStateFlow()

    private val _customCategories = MutableStateFlow<List<com.palmnote.ui.components.CategoryItem>>(emptyList())
    val customCategories: StateFlow<List<com.palmnote.ui.components.CategoryItem>> = _customCategories.asStateFlow()

    enum class DialogType { AWAY, CLEAR, DELETE }

    private val _dialogType = MutableStateFlow<DialogType?>(null)
    val showAwayDialog: StateFlow<Boolean> = _dialogType.map { it == DialogType.AWAY }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showClearDialog: StateFlow<Boolean> = _dialogType.map { it == DialogType.CLEAR }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showDeleteDialog: StateFlow<Boolean> = _dialogType.map { it == DialogType.DELETE }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        DataCache.get<AssetState>("asset")?.let { _state.value = it }
        loadAssets()
        loadViewMode()
        loadCustomCategories()
    }

    private fun loadCustomCategories() {
        viewModelScope.launch {
            categoryConfigRepository.getAllCategories().collect { configs ->
                val categories = configs.filter { it.type == "ASSET" && it.isEnabled }
                    .map { com.palmnote.ui.components.CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
                _customCategories.value = categories
            }
        }
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
                    totalValue = totalValue ?: 0.0,
                    heldValue = heldValue ?: 0.0,
                    removedValue = removedValue ?: 0.0,
                    distribution = distribution
                )
            }

            val countsFlow = combine(
                assetRepository.getAssetCountByStatus("HELD"),
                assetRepository.getAssetCountByStatus("AWAY"),
                assetRepository.getAssetCountByStatus("REMOVED")
            ) { held, away, removed ->
                StatusCounts(held, away, removed)
            }

            combine(assetsFlow, countsFlow) { assetValues, counts ->
                val s = _state.value
                AssetState(
                    assets = assetValues.assets,
                    filteredAssets = filterAssets(assetValues.assets, s.selectedCategory, s.selectedStatus, s.selectedSort, s.sortAscending, s.searchQuery),
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
                DataCache.set("asset", state)
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
                AssetFilter.HELD -> asset.status == "HELD"
                AssetFilter.AWAY -> asset.status == "AWAY"
                AssetFilter.REMOVED -> asset.status == "REMOVED"
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
                val costPerDay = (asset?.purchasePrice ?: 0.0) / daysOwned
                val costPerUse = if ((asset?.useCount ?: 0) > 0) {
                    (asset?.purchasePrice ?: 0.0) / (asset?.useCount ?: 1)
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

    fun sellAsset(id: Long, price: Double, channel: String) {
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

    fun softDeleteAsset(id: Long) {
        viewModelScope.launch {
            assetRepository.softDeleteAsset(id)
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
                purchasePrice = if (asset.purchasePrice > 0) asset.purchasePrice.toString() else "",
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
                currentValue = if (asset.currentValue > 0) asset.currentValue.toString() else "",
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
            val price = form.purchasePrice.toDoubleOrNull() ?: 0.0
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
                currentValue = form.currentValue.toDoubleOrNull() ?: 0.0,
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

    private suspend fun saveImageToInternalStorage(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val dir = File(application.filesDir, "images")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "asset_${System.currentTimeMillis()}.jpg")
            application.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            uri.toString()
        }
    }

    fun downloadImageToGallery(imagePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(imagePath)
                    if (!file.exists()) return@withContext
                    val mime = when (file.extension.lowercase()) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                        put(MediaStore.Images.Media.MIME_TYPE, mime)
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/${application.getString(R.string.asset_gallery_folder)}")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = application.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    )
                        uri?.let {
                            application.contentResolver.openOutputStream(it)?.use { output ->
                                file.inputStream().use { input -> input.copyTo(output) }
                            }
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            application.contentResolver.update(it, values, null, null)
                        }
                } catch (e: Exception) { Log.e("AssetViewModel", "Download image failed", e) }
            }
        }
    }

    private fun updateImages(transform: MutableList<String>.() -> Unit) {
        val list = _formState.value.images.toImageList().toMutableList()
        list.transform()
        _formState.value = _formState.value.copy(images = list.toImageJson())
    }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val path = saveImageToInternalStorage(uri)
            updateImages { addAll(listOf(path).take(4 - size)) }
        }
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
