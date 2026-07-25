package com.palmnote.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.PalmNoteApp
import com.palmnote.ui.components.simpleViewModel
import androidx.compose.ui.res.stringResource
import com.palmnote.R
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*

private data class AcquisitionTypeOption(
    val type: String,
    val labelRes: Int,
    val icon: ImageVector,
    val color: Color
)

private val acquisitionTypes = listOf(
    AcquisitionTypeOption("PURCHASE", R.string.acquisition_purchase, Icons.Outlined.ShoppingCart, AccentOrange),
    AcquisitionTypeOption("GIFT", R.string.acquisition_gift, Icons.Outlined.CardGiftcard, Purple),
    AcquisitionTypeOption("LOTTERY", R.string.acquisition_lottery, Icons.Outlined.Casino, DeepOrange),
    AcquisitionTypeOption("PRIZE", R.string.acquisition_prize, Icons.Outlined.EmojiEvents, Amber),
    AcquisitionTypeOption("INHERITANCE", R.string.acquisition_inheritance, Icons.Outlined.FamilyRestroom, Brown),
    AcquisitionTypeOption("OTHER", R.string.acquisition_other, Icons.Outlined.MoreHoriz, ModuleSettings),
    AcquisitionTypeOption("CUSTOM", R.string.acquisition_custom, Icons.Outlined.Edit, ModuleSettings)
)

val assetCategoryItems = listOf(
    CategoryItem("DIGITAL", Icons.Outlined.Devices, CatSkyBlue),
    CategoryItem("APPLIANCE", Icons.Outlined.Kitchen, CatMint),
    CategoryItem("FURNITURE", Icons.Outlined.Chair, Amber),
    CategoryItem("CLOTHING", Icons.Outlined.Checkroom, LifeMoodHappy),
    CategoryItem("SPORTS", Icons.Outlined.SportsBasketball, CatLime),
    CategoryItem("BOOKS", Icons.AutoMirrored.Outlined.MenuBook, CatBrightPurple),
    CategoryItem("COSMETICS", Icons.Outlined.Face, CatLightPurple),
    CategoryItem("FOOD", Icons.Outlined.Restaurant, AccentOrange),
    CategoryItem("TOOLS", Icons.Outlined.Build, CatBrightBlue),
    CategoryItem("BABY", Icons.Outlined.ChildCare, CatPink),
    CategoryItem("PET", Icons.Outlined.Pets, CatPeach),
    CategoryItem("TRANSPORT", Icons.Outlined.DirectionsCar, StatusAway),
    CategoryItem("MEDICAL", Icons.Outlined.LocalHospital, CatBrightRed),
    CategoryItem("STATIONERY", Icons.Outlined.Edit, CatBrightTeal),
    CategoryItem("MUSICAL", Icons.Outlined.MusicNote, CatIndigo),
    CategoryItem("PHOTOGRAPHY", Icons.Outlined.CameraAlt, Warning),
    CategoryItem("COLLECTION", Icons.Outlined.Star, CatGold),
    CategoryItem("JEWELRY", Icons.Outlined.Watch, CatWarmRose),
    CategoryItem("PLANTS", Icons.Outlined.Eco, CatFreshGreen),
    CategoryItem("OTHER", Icons.Outlined.Inventory2, StatusRetired)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssetScreen(
    assetId: Long? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {},
    viewModel: AssetViewModel = simpleViewModel { PalmNoteApp.container.assetViewModel() }
) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val isEditing = assetId != null

    LaunchedEffect(assetId) {
        if (assetId != null) viewModel.initFormForEdit(assetId) else viewModel.resetForm()
    }
    LaunchedEffect(formState.isSaved) { if (formState.isSaved) onNavigateBack() }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = if (isEditing) stringResource(R.string.asset_edit_title) else stringResource(R.string.asset_add_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.saveAsset() }, enabled = !formState.isSaving) {
                        if (formState.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                }
            )
        }
    ) { padding ->
        val customAssetCategories by viewModel.customCategories.collectAsStateWithLifecycle()
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ═══════════════════════════════════════════
            // Section 1: 图片
            // ═══════════════════════════════════════════
            item { ImageSection(formState, viewModel) }

            // ═══════════════════════════════════════════
            // Section 2: 基本信息（名称 + 分类 + 数量）
            // ═══════════════════════════════════════════
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    SectionHeader(Icons.Outlined.Info, stringResource(R.string.asset_basic_info))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 名称
                    OutlinedTextField(
                        value = formState.name,
                        onValueChange = { viewModel.updateFormField { copy(name = it, nameError = null) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.asset_name)) },
                        placeholder = { Text(stringResource(R.string.asset_input_name_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        isError = formState.nameError != null,
                        supportingText = formState.nameError?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 分类
                    Text(stringResource(R.string.asset_category), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (formState.categoryError != null) {
                        Text(formState.categoryError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val presetOverrides by PalmNoteApp.container.preferencesManager.presetCategoryOverrides
                        .collectAsStateWithLifecycle(initialValue = emptyMap())
                    val enrichedPresets = remember(presetOverrides) {
                        assetCategoryItems.filter { item ->
                            val key = "preset_${item.name}"
                            val json = presetOverrides[key]
                            if (json != null) try { org.json.JSONObject(json).optBoolean("enabled", true) } catch (_: Exception) { true } else true
                        }.map { item ->
                            val resolvedColor = ColorResolver.resolve(item.name, item.color)
                            if (resolvedColor != item.color) item.copy(color = resolvedColor) else item
                        }
                    }
                    val allAssetCategories = remember(customAssetCategories, enrichedPresets) { enrichedPresets + customAssetCategories }
                    fun presetDisplayName(key: String): String {
                        val overrideKey = "preset_$key"
                        val json = presetOverrides[overrideKey]
                        if (json != null) try { val obj = org.json.JSONObject(json); if (obj.has("name")) return obj.getString("name") } catch (_: Exception) {}
                        return if (assetCategoryItems.any { it.name == key }) com.palmnote.ui.components.getCategoryName(key, context) else key
                    }
                    CategoryPicker(
                        selected = formState.category,
                        onSelected = { viewModel.updateFormField { copy(category = it, categoryError = null) } },
                        categories = allAssetCategories,
                        onManageCategories = { onNavigateToCategory("ASSET") },
                        getDisplayName = { presetDisplayName(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 数量（紧凑行）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.asset_quantity_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { val q = ((formState.quantity.toIntOrNull() ?: 1) - 1).coerceAtLeast(1); viewModel.updateFormField { copy(quantity = q.toString()) } }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Remove, stringResource(R.string.field_decrease), modifier = Modifier.size(18.dp))
                            }
                            Text(formState.quantity, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                            IconButton(onClick = { val q = (formState.quantity.toIntOrNull() ?: 1) + 1; viewModel.updateFormField { copy(quantity = q.toString()) } }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Add, stringResource(R.string.field_increase), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 3: 获取方式 + 价格日期
            // ═══════════════════════════════════════════
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    SectionHeader(Icons.Outlined.ShoppingCart, stringResource(R.string.asset_acquisition))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 获取方式
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(acquisitionTypes, key = { it.type }) { option ->
                            val isSelected = formState.acquisitionType == option.type
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateFormField { copy(acquisitionType = option.type) } },
                                label = { Text(stringResource(option.labelRes), style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = { Icon(option.icon, null, Modifier.size(14.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = option.color.copy(alpha = 0.15f),
                                    selectedLabelColor = option.color,
                                    selectedLeadingIconColor = option.color
                                )
                            )
                        }
                    }
                    if (formState.acquisitionType == "CUSTOM") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = formState.customAcquisitionLabel,
                            onValueChange = { viewModel.updateFormField { copy(customAcquisitionLabel = it) } },
                            label = { Text(stringResource(R.string.asset_custom_acquisition)) },
                            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 价格 + 日期（同行排列）
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 价格
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (formState.acquisitionType == "PURCHASE") stringResource(R.string.asset_price) else stringResource(R.string.asset_valuation_optional),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = formState.purchasePrice,
                                onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) viewModel.updateFormField { copy(purchasePrice = it) } },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                                placeholder = { Text("0.00", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                prefix = { Text("¥") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = MaterialTheme.shapes.medium,
                                singleLine = true
                            )
                        }
                        // 日期
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (formState.acquisitionType == "PURCHASE") stringResource(R.string.asset_purchase_date) else stringResource(R.string.asset_acquisition_date),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            DatePickerField(
                                selectedDate = formState.acquisitionDate,
                                onDateSelected = { viewModel.updateFormField { copy(acquisitionDate = it, dateError = null) } }
                            )
                        }
                    }
                    if (formState.dateError != null) {
                        Text(formState.dateError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }

                    // 购买渠道（仅购买时显示）
                    if (formState.acquisitionType == "PURCHASE") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.asset_purchase_channel), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = formState.purchaseChannel,
                            onValueChange = { viewModel.updateFormField { copy(purchaseChannel = it) } },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.asset_purchase_channel_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 4: 位置 + 保修 + 折旧模式
            // ═══════════════════════════════════════════
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    SectionHeader(Icons.Outlined.LocationOn, stringResource(R.string.asset_location_warranty))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 位置
                    Text(stringResource(R.string.asset_location), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    val commonLocations = listOf(stringResource(R.string.location_bedroom), stringResource(R.string.location_living_room), stringResource(R.string.location_study), stringResource(R.string.location_kitchen), stringResource(R.string.location_desk), stringResource(R.string.location_in_car))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(commonLocations, key = { it }) { loc ->
                            FilterChip(
                                selected = formState.location == loc,
                                onClick = { viewModel.updateFormField { copy(location = loc) } },
                                label = { Text(loc, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = formState.location,
                        onValueChange = { viewModel.updateFormField { copy(location = it) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.asset_custom_location_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 保修 + 折旧模式（同行）
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.asset_warranty_date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            DatePickerField(
                                selectedDate = formState.warrantyExpireDate,
                                onDateSelected = { viewModel.updateFormField { copy(warrantyExpireDate = it) } },
                                placeholder = stringResource(R.string.asset_select_warranty_date)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.asset_cost_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            var costModeExpanded by remember { mutableStateOf(false) }
                            var costModeBoxHeight by remember { mutableIntStateOf(0) }
                            var costModeBoxWidth by remember { mutableIntStateOf(0) }
                            val density = LocalDensity.current
                            Box(modifier = Modifier.onSizeChanged { costModeBoxHeight = it.height; costModeBoxWidth = it.width }) {
                                OutlinedTextField(
                                    value = when (formState.costMode) {
                                        "DAILY" -> stringResource(R.string.asset_cost_daily)
                                        "PER_USE" -> stringResource(R.string.asset_cost_per_use)
                                        else -> ""
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                                    placeholder = { Text(stringResource(R.string.asset_cost_mode), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    trailingIcon = {
                                        IconButton(onClick = { costModeExpanded = true }) {
                                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                        }
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        cursorColor = Color.Transparent
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { costModeExpanded = true }
                                )
                                if (costModeExpanded) {
                                    Popup(
                                        onDismissRequest = { costModeExpanded = false },
                                        alignment = Alignment.TopEnd,
                                        offset = IntOffset(x = 0, y = costModeBoxHeight)
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.background,
                                            tonalElevation = 2.dp,
                                            shadowElevation = 4.dp,
                                            modifier = Modifier.width(with(density) { costModeBoxWidth.toDp() })
                                        ) {
                                            Column {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.asset_cost_daily)) },
                                                    onClick = { viewModel.updateFormField { copy(costMode = "DAILY") }; costModeExpanded = false }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.asset_cost_per_use)) },
                                                    onClick = { viewModel.updateFormField { copy(costMode = "PER_USE") }; costModeExpanded = false }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 5: 备注
            // ═══════════════════════════════════════════
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                    SectionHeader(Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.asset_description))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = formState.description,
                        onValueChange = { viewModel.updateFormField { copy(description = it) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        placeholder = { Text(stringResource(R.string.asset_add_remark_hint)) },
                        shape = MaterialTheme.shapes.medium,
                        maxLines = 4
                    )
                }
            }

            // ═══════════════════════════════════════════
            // Section 6: 高级选项（折叠）
            // ═══════════════════════════════════════════
            item {
                var expanded by remember { mutableStateOf(false) }
                LaunchedEffect(expanded) { if (expanded) listState.animateScrollToItem(5) }
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { expanded = !expanded },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.asset_advanced_options), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (expanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // 品牌 + 型号（同行）
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.asset_brand), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = formState.brand,
                                        onValueChange = { viewModel.updateFormField { copy(brand = it) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(stringResource(R.string.asset_brand_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        shape = MaterialTheme.shapes.medium, singleLine = true
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.asset_model_name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = formState.model,
                                        onValueChange = { viewModel.updateFormField { copy(model = it) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(stringResource(R.string.asset_model_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        shape = MaterialTheme.shapes.medium, singleLine = true
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 序列号
                            Text(stringResource(R.string.asset_serial_number), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = formState.serialNumber,
                                onValueChange = { viewModel.updateFormField { copy(serialNumber = it) } },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.asset_serial_number_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                shape = MaterialTheme.shapes.medium, singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 状况
                            Text(stringResource(R.string.asset_condition), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("NEW" to stringResource(R.string.asset_condition_new), "GOOD" to stringResource(R.string.asset_condition_good), "FAIR" to stringResource(R.string.asset_condition_fair), "POOR" to stringResource(R.string.asset_condition_poor)).forEach { (condition, label) ->
                                    FilterChip(selected = formState.condition == condition, onClick = { viewModel.updateFormField { copy(condition = condition) } }, label = { Text(label, style = MaterialTheme.typography.labelMedium) })
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 保险
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.asset_insurance_company), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(value = formState.insuranceCompany, onValueChange = { viewModel.updateFormField { copy(insuranceCompany = it) } }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.asset_insurance_company_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) }, shape = MaterialTheme.shapes.medium, singleLine = true)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.asset_insurance_policy), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(value = formState.insurancePolicyNo, onValueChange = { viewModel.updateFormField { copy(insurancePolicyNo = it) } }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.asset_insurance_policy_no_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) }, shape = MaterialTheme.shapes.medium, singleLine = true)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 保险到期 + 维护间隔（同行）
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.asset_insurance_expire_date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    DatePickerField(selectedDate = formState.insuranceExpireDate, onDateSelected = { viewModel.updateFormField { copy(insuranceExpireDate = it) } }, placeholder = stringResource(R.string.asset_select_insurance_date))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.asset_maintenance_interval), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(value = formState.maintenanceIntervalDays, onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) viewModel.updateFormField { copy(maintenanceIntervalDays = it) } }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp), placeholder = { Text(stringResource(R.string.asset_maintenance_interval_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium, singleLine = true)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 折旧率 + 当前估值（同行）
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.asset_depreciation_rate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(value = formState.depreciationRate, onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) viewModel.updateFormField { copy(depreciationRate = it) } }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.asset_depreciation_rate_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium, singleLine = true, suffix = { Text("%") })
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.asset_current_value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(value = formState.currentValue, onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d{0,10}(\\.\\d{0,2})?$"))) viewModel.updateFormField { copy(currentValue = it) } }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.asset_current_value_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) }, prefix = { Text("¥") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium, singleLine = true)
                                }
                            }

                        }
                    }
                }
            }

            // ═══════════════════════════════════════════
            // 保存按钮
            // ═══════════════════════════════════════════
            item {
                Button(
                    onClick = { viewModel.saveAsset() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !formState.isSaving,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    if (formState.isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEditing) stringResource(R.string.save_changes) else stringResource(R.string.asset_add_button), fontWeight = FontWeight.Bold)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ── 图片区域 ──────────────────────────────────────

@Composable
private fun ImageSection(formState: AddAssetFormState, viewModel: AssetViewModel) {
    val images = formState.images.toImageList()
    val customAssetCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val catInfo = if (formState.category.isNotEmpty()) {
        val fromPreset = assetCategoryItems.find { it.name == formState.category }
        if (fromPreset != null) {
            val resolved = ColorResolver.resolve(formState.category, fromPreset.color)
            if (resolved != fromPreset.color) fromPreset.copy(color = resolved) else fromPreset
        } else customAssetCategories.find { it.name == formState.category }
    } else null

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.addImage(it) } }

    ModuleCard(tint = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.asset_image_section), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${images.size}/4", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))

        var draggedIndex by remember { mutableIntStateOf(-1) }
        var dragTotal by remember { mutableFloatStateOf(0f) }
        var slotWidthPx by remember { mutableFloatStateOf(80f) }
        val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }
        val currentSlotWidth by rememberUpdatedState(slotWidthPx)
        val currentDragIndex by rememberUpdatedState(draggedIndex)

        Row(
            modifier = Modifier.fillMaxWidth()
                .onSizeChanged { slotWidthPx = (it.width - spacingPx * 3) / 4f }
                .pointerInput(images.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset -> val idx = (offset.x / (currentSlotWidth + spacingPx)).toInt().coerceIn(0, images.size - 1); draggedIndex = idx; dragTotal = 0f },
                        onDrag = { change, amount -> change.consume(); dragTotal += amount.x; val step = currentSlotWidth + spacingPx; if (abs(dragTotal) > step * 0.5f) { val target = (currentDragIndex + if (dragTotal > 0) 1 else -1).coerceIn(0, images.size - 1); if (target != currentDragIndex) { viewModel.reorderImages(currentDragIndex, target); draggedIndex = target; dragTotal -= step * if (dragTotal > 0) 1f else -1f } } },
                        onDragEnd = { draggedIndex = -1; dragTotal = 0f },
                        onDragCancel = { draggedIndex = -1; dragTotal = 0f }
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until 4) {
                Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                    if (i < images.size) {
                        val isDragging = i == draggedIndex
                        Box(
                            modifier = Modifier.fillMaxSize().zIndex(if (isDragging) 2f else 0f)
                                .then(if (isDragging) Modifier.graphicsLayer { translationX = dragTotal; scaleX = 1.05f; scaleY = 1.05f } else Modifier)
                                .clip(MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(model = images[i], contentDescription = null, modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                            IconButton(onClick = { viewModel.removeImage(i) }, modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.medium)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.asset_remove_image), tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            if (images.size > 1) {
                                Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.asset_drag_to_reorder), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp))
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background((catInfo?.color ?: AccentOrange).copy(alpha = 0.1f)).clickable { if (images.size < 4) imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.AddPhotoAlternate, null, tint = (catInfo?.color ?: AccentOrange).copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                                Text(stringResource(R.string.asset_upload), style = MaterialTheme.typography.labelSmall, color = (catInfo?.color ?: AccentOrange).copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
        if (images.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.asset_image_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 通用组件 ──────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

