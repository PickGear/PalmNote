package com.palmnote.ui.asset

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.app.Activity
import android.os.Build
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.data.db.entity.UsageRecord
import com.palmnote.data.db.entity.getWarrantyStatusText
import com.palmnote.data.db.entity.getInsuranceStatusText
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import androidx.compose.ui.res.stringResource
import com.palmnote.R
import com.palmnote.ui.components.*
import com.palmnote.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AssetDetailScreen(
    assetId: Long,
    onNavigateBack: () -> Unit = {},
    onNavigateToEdit: (Long) -> Unit = {},
    viewModel: AssetViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val showAwayDialog by viewModel.showAwayDialog.collectAsStateWithLifecycle()
    val showClearDialog by viewModel.showClearDialog.collectAsStateWithLifecycle()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsStateWithLifecycle()

    LaunchedEffect(assetId) {
        viewModel.loadAssetDetail(assetId)
    }

    val asset = detailState.asset

    if (asset == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (detailState.isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.asset_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.loadAssetDetail(assetId) }) {
                        Text(stringResource(R.string.life_screen_retry))
                    }
                }
            }
        }
        return
    }

    val statusColor = getStatusColor(asset.status)
    val statusText = getStatusText(asset.status)

    val catInfo = getCategoryIcon(asset.category)

    val imageList = remember(asset.images) { asset.images.toImageList() }
    val tags = remember(asset.tags) { parseTags(asset.tags) }
    var previewIndex by remember { mutableIntStateOf(-1) }
    val showPreview = previewIndex in imageList.indices
    var showAllRecords by remember { mutableStateOf(false) }

    DarkSystemBarsForPreview(showPreview)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.asset_detail_page_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(assetId) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { viewModel.showDeleteDialog() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ModuleCard(tint = assetTint(), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(72.dp).background(if (asset.images.isEmpty()) catInfo.color.copy(alpha = 0.12f) else Color.Transparent, MaterialTheme.shapes.large),
                            contentAlignment = Alignment.Center
                        ) {
                            val firstImage = imageList.firstOrNull()
                            if (firstImage != null) {
                                AsyncImage(
                                    model = File(firstImage),
                                    contentDescription = asset.name,
                                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(imageVector = catInfo.icon, contentDescription = null, tint = catInfo.color, modifier = Modifier.size(36.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = asset.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (asset.quantity > 1) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(shape = MaterialTheme.shapes.small, color = AccentOrange.copy(alpha = 0.12f)) {
                                        Text(text = "×${asset.quantity}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.titleSmall, color = AccentOrange, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusChip(text = statusText, color = statusColor)
                                if (asset.warrantyExpireDate != null) StatusChip(text = asset.getWarrantyStatusText(context), color = if (asset.isWarrantyValid) StatusActive else StatusRetired)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(label = when {
                            asset.status == "REMOVED" -> stringResource(R.string.asset_sold_price_label)
                            asset.acquisitionType == "PURCHASE" -> stringResource(R.string.asset_price)
                            else -> stringResource(R.string.asset_valuation_price)
                        }, value = CurrencyUtils.formatCurrency(asset.displayPrice), color = AccentOrange)
                        StatItem(label = stringResource(R.string.asset_days_owned), value = "${detailState.daysOwned}", color = MaterialTheme.colorScheme.primary)
                        StatItem(label = if (asset.costMode == "PER_USE") stringResource(R.string.asset_cost_single) else stringResource(R.string.asset_cost_daily_avg), value = CurrencyUtils.formatCurrency(if (asset.costMode == "PER_USE") detailState.costPerUse else detailState.costPerDay), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            // Image Gallery
            if (imageList.isNotEmpty()) {
                item {
                    ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.asset_images_count, imageList.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            imageList.forEachIndexed { index, imagePath ->
                                Box(Modifier.size(120.dp).clip(MaterialTheme.shapes.medium).clickable { previewIndex = index }) {
                                    AsyncImage(model = File(imagePath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                    }
                }
            }
            // Basic Info Section
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.asset_basic_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow(stringResource(R.string.asset_category), com.palmnote.ui.components.getCategoryName(asset.category, context))
                    if (asset.subCategory.isNotEmpty()) DetailRow(stringResource(R.string.asset_subcategory), asset.subCategory)
                    if (asset.brand.isNotEmpty()) DetailRow(stringResource(R.string.asset_brand), asset.brand)
                    if (asset.model.isNotEmpty()) DetailRow(stringResource(R.string.asset_model_name), asset.model)
                    if (asset.serialNumber.isNotEmpty()) DetailRow(stringResource(R.string.asset_serial_number), asset.serialNumber)
                    DetailRow(stringResource(R.string.asset_condition), when (asset.condition) { "NEW" -> stringResource(R.string.asset_condition_new); "GOOD" -> stringResource(R.string.asset_condition_good); "FAIR" -> stringResource(R.string.asset_condition_fair); "POOR" -> stringResource(R.string.asset_condition_poor); else -> asset.condition })
                    if (asset.quantity > 1) DetailRow(stringResource(R.string.asset_quantity_label), "×${asset.quantity}")
                    if (tags.isNotEmpty()) DetailRow(stringResource(R.string.asset_tags_label), tags.joinToString("、"))
                    if (asset.isFavorite) DetailRow(stringResource(R.string.asset_favorite), stringResource(R.string.asset_yes))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow(stringResource(R.string.asset_acquisition_type), getAcquisitionText(asset.acquisitionType))
                    asset.acquisitionDate?.let { DetailRow(if (asset.acquisitionType == "PURCHASE") stringResource(R.string.asset_purchase_date) else stringResource(R.string.asset_acquisition_date), DateUtils.formatDisplayYearDate(context, it)) }
                    if (asset.acquisitionType == "PURCHASE" && asset.purchaseChannel.isNotEmpty()) DetailRow(stringResource(R.string.asset_purchase_channel), asset.purchaseChannel)
                    if (asset.location.isNotEmpty()) DetailRow(stringResource(R.string.asset_location), asset.location)
                    if (asset.room.isNotEmpty()) DetailRow(stringResource(R.string.asset_room), asset.room)
                    if (asset.description.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(text = stringResource(R.string.asset_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(text = asset.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            // Valuation Info
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.asset_value_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (asset.purchasePrice > 0) DetailRow(stringResource(R.string.asset_price), CurrencyUtils.formatCurrency(asset.purchasePrice))
                    if (asset.currentValue > 0) DetailRow(stringResource(R.string.asset_current_value), CurrencyUtils.formatCurrency(asset.currentValue))
                    if (asset.depreciationRate > 0) DetailRow(stringResource(R.string.asset_depreciation_rate), "${"%.1f".format(asset.depreciationRate)}%")
                    DetailRow(stringResource(R.string.asset_cost_calculation), when (asset.costMode) { "DAILY" -> stringResource(R.string.asset_cost_daily); "PER_USE" -> stringResource(R.string.asset_cost_per_use); "DEPRECIATION" -> stringResource(R.string.asset_depreciation); else -> asset.costMode })
                }
            }

            // Warranty Info
            if (asset.warrantyExpireDate != null) {
                item {
                    ModuleCard(tint = if (asset.isWarrantyValid) goalTint() else anniversaryTint(), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = if (asset.isWarrantyValid) Icons.Filled.VerifiedUser else Icons.Outlined.GppBad, contentDescription = null, tint = if (asset.isWarrantyValid) StatusActive else StatusLost, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(text = stringResource(R.string.asset_warranty_info), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(text = stringResource(R.string.asset_warranty_expiry_format, DateUtils.formatDisplayYearDate(context, asset.warrantyExpireDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val daysLeft = DateUtils.getDaysUntil(asset.warrantyExpireDate)
                            if (daysLeft >= 0) StatusChip(text = stringResource(R.string.asset_warranty_remaining_days, daysLeft), color = StatusActive)
                            else StatusChip(text = stringResource(R.string.asset_warranty_expired_days, -daysLeft), color = StatusRetired)
                        }
                    }
                }
            }
            // Maintenance Info
            if (asset.maintenanceIntervalDays > 0 || asset.lastMaintenanceDate != null) {
                item {
                    val isDue = asset.isMaintenanceDue
                    ModuleCard(tint = if (isDue) anniversaryTint() else goalTint(), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Build, contentDescription = null, tint = if (isDue) AccentOrange else MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(text = stringResource(R.string.asset_maintenance_info), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (asset.lastMaintenanceDate != null) Text(text = stringResource(R.string.asset_last_maintenance_format, DateUtils.formatDisplayYearDate(context, asset.lastMaintenanceDate)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (asset.maintenanceIntervalDays > 0) Text(text = stringResource(R.string.asset_maintenance_cycle_format, asset.maintenanceIntervalDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isDue) StatusChip(text = stringResource(R.string.asset_maintenance_needed), color = AccentOrange)
                            else if (asset.nextMaintenanceDate != null) {
                                val daysUntil = DateUtils.getDaysUntil(asset.nextMaintenanceDate)
                                StatusChip(text = if (daysUntil >= 0) stringResource(R.string.asset_days_later_format, daysUntil) else stringResource(R.string.asset_maintenance_expired), color = if (daysUntil < 7) AccentOrange else StatusActive)
                            }
                        }
                        if (isDue) {
                            Spacer(Modifier.height(8.dp))
                            var showMaintConfirm by remember { mutableStateOf(false) }
                            Button(onClick = { showMaintConfirm = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.asset_complete_maintenance), fontWeight = FontWeight.Bold)
                            }
                            if (showMaintConfirm) {
                                AppDialog(
                                    onDismissRequest = { showMaintConfirm = false },
                                    title = { Text(stringResource(R.string.asset_maintenance_info), fontWeight = FontWeight.Bold) },
                                    text = { Text(stringResource(R.string.asset_maintenance_complete_confirm)) },
                                    confirmButton = { TextButton(onClick = { viewModel.completeMaintenance(assetId); showMaintConfirm = false }) { Text(stringResource(R.string.confirm)) } },
                                    dismissButton = { TextButton(onClick = { showMaintConfirm = false }) { Text(stringResource(R.string.cancel)) } }
                                )
                            }
                        }
                    }
                }
            }
            // Insurance Info
            if (asset.insuranceExpireDate != null || asset.insuranceCompany.isNotEmpty()) {
                item {
                    ModuleCard(tint = if (asset.isInsuranceValid) goalTint() else anniversaryTint(), modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.asset_insurance_info), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (asset.insuranceCompany.isNotEmpty()) DetailRow(stringResource(R.string.asset_insurance_company), asset.insuranceCompany)
                        if (asset.insurancePolicyNo.isNotEmpty()) DetailRow(stringResource(R.string.asset_insurance_policy), asset.insurancePolicyNo)
                        if (asset.insuranceExpireDate != null) DetailRow(stringResource(R.string.asset_insurance_expiry), DateUtils.formatDisplayYearDate(context, asset.insuranceExpireDate))
                        if (asset.getInsuranceStatusText(context).isNotEmpty()) DetailRow(stringResource(R.string.asset_status), asset.getInsuranceStatusText(context))
                    }
                }
            }
            // Status Info (for non-held)
            if (asset.status != "HELD") {
                item {
                    val statusBg = when (asset.status) {
                        "AWAY" -> billTint()
                        "REMOVED" -> anniversaryTint()
                        else -> MaterialTheme.colorScheme.surface
                    }

                    ModuleCard(tint = statusBg, modifier = Modifier.fillMaxWidth()) {
                        val statusTitle = when (asset.status) { "AWAY" -> stringResource(R.string.asset_away_info); "REMOVED" -> stringResource(R.string.asset_removed_info); else -> stringResource(R.string.asset_status_info_label) }
                        Text(text = statusTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        when (asset.status) {
                            "AWAY" -> {
                                tags.takeIf { it.isNotEmpty() }?.let { DetailRow(stringResource(R.string.asset_away_reason), it.joinToString("、")) }
                                asset.retireDate?.let { DetailRow(stringResource(R.string.asset_away_date), DateUtils.formatDisplayYearDate(context, it)) }
                                if (asset.retireReason.isNotEmpty()) DetailRow(stringResource(R.string.asset_description), asset.retireReason)
                            }
                            "REMOVED" -> {
                                tags.takeIf { it.isNotEmpty() }?.let { DetailRow(stringResource(R.string.asset_clear_reason), it.joinToString("、")) }
                                asset.retireDate?.let { DetailRow(stringResource(R.string.asset_retire_date), DateUtils.formatDisplayYearDate(context, it)) }
                                if (asset.retireReason.isNotEmpty()) DetailRow(stringResource(R.string.asset_retire_reason), asset.retireReason)
                                asset.lostDate?.let { DetailRow(stringResource(R.string.asset_lost_date), DateUtils.formatDisplayYearDate(context, it)) }
                                if (asset.lostReason.isNotEmpty()) DetailRow(stringResource(R.string.asset_lost_reason), asset.lostReason)
                                asset.soldDate?.let { DetailRow(stringResource(R.string.asset_sold_date), DateUtils.formatDisplayYearDate(context, it)) }
                                asset.soldPrice?.let { DetailRow(stringResource(R.string.asset_sold_price_label), CurrencyUtils.formatCurrency(it)) }
                                asset.soldChannel?.let { DetailRow(stringResource(R.string.asset_sold_channel), it) }
                                asset.soldToWhom?.takeIf { it.isNotEmpty() }?.let { DetailRow(stringResource(R.string.asset_sold_to), it) }
                                if (asset.purchasePrice > 0 && asset.soldPrice != null) {
                                    DetailRow(stringResource(R.string.asset_profit_loss), (if (asset.soldPrice >= asset.purchasePrice) "+" else "") + CurrencyUtils.formatCurrency(asset.soldPrice - asset.purchasePrice))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        var showReactConfirm by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { showReactConfirm = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.asset_reactivate))
                        }
                        if (showReactConfirm) {
                            AppDialog(
                                onDismissRequest = { showReactConfirm = false },
                                title = { Text(stringResource(R.string.asset_status_held), fontWeight = FontWeight.Bold) },
                                text = { Text(stringResource(R.string.asset_restore_confirm)) },
                                confirmButton = { TextButton(onClick = { viewModel.reactivateAsset(assetId); showReactConfirm = false }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.primary) } },
                                dismissButton = { TextButton(onClick = { showReactConfirm = false }) { Text(stringResource(R.string.cancel)) } }
                            )
                        }
                    }
                }
            }

            // Other Info
            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.asset_other_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailRow(stringResource(R.string.asset_created_at), DateUtils.formatDisplayYearDate(context, asset.createdAt))
                    DetailRow(stringResource(R.string.asset_updated_at), DateUtils.formatDisplayYearDate(context, asset.updatedAt))
                    if (asset.linkedBillId != null) DetailRow(stringResource(R.string.asset_linked_bill), "#${asset.linkedBillId}")
                    if (asset.linkedMomentId != null) DetailRow(stringResource(R.string.asset_linked_moment), "#${asset.linkedMomentId}")
                }
            }

            // Usage Records
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(text = stringResource(R.string.asset_usage_records), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = stringResource(R.string.asset_usage_count_format, asset.useCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(
                        onClick = { viewModel.addUsageRecord(assetId) },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.asset_record_usage))
                    }
                }
            }
            if (detailState.usageRecords.isEmpty()) {
                item { EmptyState(icon = Icons.Outlined.History, title = stringResource(R.string.asset_no_usage_records), subtitle = stringResource(R.string.asset_click_to_record_usage)) }
            } else {
                val visibleRecords = if (showAllRecords) detailState.usageRecords else detailState.usageRecords.take(3)
                items(visibleRecords, key = { it.id }) { record ->
                    val density = LocalDensity.current
                    val actionWidthPx = with(density) { 140.dp.toPx() }
                    var editRecord by remember { mutableStateOf<UsageRecord?>(null) }
                    UsageRecordItem(record, actionWidthPx, { editRecord = it }, { viewModel.deleteUsageRecord(it) })
                    UsageRecordEditDialog(editRecord, viewModel, { editRecord = null })
                }
                if (detailState.usageRecords.size > 3) {
                    item {
                        TextButton(onClick = { showAllRecords = !showAllRecords }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAllRecords) stringResource(R.string.asset_collapse) else stringResource(R.string.asset_expand_all_format, detailState.usageRecords.size), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                ModuleCard(tint = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.asset_status_change), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusActionButton(
                            onClick = { viewModel.reactivateAsset(assetId) },
                            label = stringResource(R.string.asset_status_held), icon = Icons.Outlined.CheckCircle,
                            color = StatusHeld, enabled = asset.status != "HELD"
                        )
                        StatusActionButton(
                            onClick = { viewModel.showAwayDialog() },
                            label = stringResource(R.string.asset_status_away), icon = Icons.Outlined.Schedule,
                            color = StatusAway, enabled = asset.status == "HELD"
                        )
                        StatusActionButton(
                            onClick = { viewModel.showClearDialog() },
                            label = stringResource(R.string.asset_status_removed_label), icon = Icons.Outlined.Archive,
                            color = StatusRemoved, enabled = asset.status == "HELD",
                            filled = true
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    ImagePreview(showPreview, imageList, previewIndex, { previewIndex = -1 }, viewModel)

    if (showAwayDialog) {
        var reason by remember { mutableStateOf("") }
        SurfaceAlertDialog(
            onDismissRequest = { viewModel.hideAwayDialog() },
            title = { Text(stringResource(R.string.asset_dialog_away_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.asset_dialog_away_confirm, asset.name))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text(stringResource(R.string.asset_dialog_away_reason_hint)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.awayAsset(assetId, asset.tags, reason) }) { Text(stringResource(R.string.asset_dialog_away_action), color = StatusAway) } }
        )
    }
    if (showClearDialog) {
        var clearType by remember { mutableIntStateOf(0) } // 0=退役, 1=丢失, 2=售出
        var reason by remember { mutableStateOf("") }
        var selectedPreset by remember { mutableIntStateOf(-1) }
        var price by remember { mutableStateOf("") }
        var channel by remember { mutableStateOf("") }
        val typeColors = listOf(StatusRemoved, StatusLost, AccentOrange)
        val typeIcons = listOf(Icons.Outlined.Archive, Icons.AutoMirrored.Outlined.HelpOutline, Icons.Outlined.Sell)
        val retirePresets = listOf(stringResource(R.string.asset_preset_scrapped), stringResource(R.string.asset_preset_expired), stringResource(R.string.asset_preset_damaged), stringResource(R.string.asset_preset_upgrade), stringResource(R.string.asset_preset_no_use))
        val lostPresets = listOf(stringResource(R.string.asset_preset_lost), stringResource(R.string.asset_preset_stolen), stringResource(R.string.asset_preset_forgotten), stringResource(R.string.asset_preset_missing))
        val sellPresets = listOf(stringResource(R.string.asset_preset_xianyu), stringResource(R.string.asset_preset_secondhand), stringResource(R.string.asset_preset_gift_others))
        SurfaceAlertDialog(
            onDismissRequest = { viewModel.hideClearDialog() },
            title = { Text(stringResource(R.string.asset_dialog_clear_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(stringResource(R.string.asset_clear_type_retire), stringResource(R.string.asset_clear_type_lost), stringResource(R.string.asset_clear_type_sell)).forEachIndexed { i, label ->
                            FilterChip(
                                selected = clearType == i,
                                onClick = { clearType = i; reason = ""; selectedPreset = -1 },
                                label = { Text(label) },
                                leadingIcon = { Icon(typeIcons[i], null, Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = typeColors[i].copy(alpha = 0.15f),
                                    selectedLabelColor = typeColors[i],
                                    selectedLeadingIconColor = typeColors[i]
                                )
                            )
                        }
                    }
                    HorizontalDivider()
                    val presets = when (clearType) { 0 -> retirePresets; 1 -> lostPresets; else -> sellPresets }
                    Text(stringResource(R.string.asset_quick_select), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEachIndexed { i, label ->
                            SuggestionChip(
                                onClick = { selectedPreset = i; reason = label },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                border = BorderStroke(if (selectedPreset == i) 1.dp else 0.dp, if (selectedPreset == i) typeColors[clearType] else MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                    HorizontalDivider()
                    val reasonLabel = listOf(stringResource(R.string.asset_retire_reason_hint), stringResource(R.string.asset_lost_reason_hint), stringResource(R.string.asset_remark_hint))
                    if (clearType < 2) {
                        OutlinedTextField(value = reason, onValueChange = { reason = it; selectedPreset = -1 }, label = { Text(reasonLabel[clearType]) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    } else {
                        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text(stringResource(R.string.asset_sell_price)) }, prefix = { Text("¥") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                        OutlinedTextField(value = channel, onValueChange = { channel = it }, label = { Text(stringResource(R.string.asset_sell_channel_hint)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                        OutlinedTextField(value = reason, onValueChange = { reason = it; selectedPreset = -1 }, label = { Text(reasonLabel[2]) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (clearType) {
                        0 -> viewModel.retireAsset(assetId, reason)
                        1 -> viewModel.markAssetLost(assetId, reason)
                        2 -> viewModel.sellAsset(assetId, price.toDoubleOrNull() ?: 0.0, channel)
                    }
                }) { Text(stringResource(R.string.confirm), color = typeColors[clearType]) }
            }
        )
    }
    if (showDeleteDialog) {
        SurfaceAlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text(stringResource(R.string.asset_dialog_delete_title)) },
            text = { Text(stringResource(R.string.asset_dialog_delete_confirm, asset.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.softDeleteAsset(assetId)
                    viewModel.hideDeleteDialog()
                    onNavigateBack()
                }) { Text(stringResource(R.string.delete), color = ErrorLight) }
            }
        )
    }
    } // closes Box wrapper
}

@Composable
private fun UsageRecordEditDialog(record: UsageRecord?, viewModel: AssetViewModel, onDismiss: () -> Unit) {
    record?.let { r ->
        val context = LocalContext.current
        var editNote by remember(r.id) { mutableStateOf(r.note) }
        var editDate by remember(r.id) { mutableStateOf(r.usedAt) }
        var showDatePicker by remember(r.id) { mutableStateOf(false) }
        AppDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.asset_edit_usage_record)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = DateUtils.formatDisplayYearDate(context, editDate), style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedTextField(value = editNote, onValueChange = { editNote = it }, label = { Text(stringResource(R.string.asset_description)) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.updateUsageRecord(r.id, editDate, editNote); onDismiss() }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        )
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = editDate)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                tonalElevation = 0.dp,
                colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { editDate = it }; showDatePicker = false }) { Text(stringResource(R.string.confirm), color = AccentOrange) } },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
            ) { DatePicker(state = datePickerState, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.background)) }
        }
    }
}

@Composable
private fun SurfaceAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) } }
    )
}

@Suppress("DEPRECATION")
@Composable
private fun DarkSystemBarsForPreview(showPreview: Boolean) {
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(showPreview) {
        if (showPreview && window != null) {
            val w = window
            val origStatusBar = w.statusBarColor
            val origNavBar = w.navigationBarColor
            val decorView = w.decorView
            val origVis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) decorView.systemUiVisibility else 0
            val origInsetsAppearance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) w.insetsController?.systemBarsAppearance ?: 0 else 0

            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            w.statusBarColor = android.graphics.Color.BLACK
            w.navigationBarColor = android.graphics.Color.BLACK

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                w.insetsController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            } else if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = 0
            }

            onDispose {
                w.statusBarColor = origStatusBar
                w.navigationBarColor = origNavBar
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    w.insetsController?.setSystemBarsAppearance(origInsetsAppearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                } else if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    decorView.systemUiVisibility = origVis
                }
            }
        } else {
            onDispose { }
        }
    }
}

@Composable
private fun ImagePreview(showPreview: Boolean, imageList: List<String>, previewIndex: Int, onClose: () -> Unit, viewModel: AssetViewModel) {
    if (!showPreview) return
    BackHandler { onClose() }
    val pagerState = rememberPagerState(pageCount = { imageList.size }, initialPage = previewIndex)
    val context = LocalContext.current
    val snackbarPreviewHost = remember { SnackbarHostState() }
    val previewScope = rememberCoroutineScope()
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }
    ) {
        SnackbarHost(snackbarPreviewHost, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 48.dp))
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.asset_close), tint = Color.White) }
                if (imageList.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        imageList.forEachIndexed { i, _ ->
                            Box(Modifier.size(if (i == pagerState.currentPage) 8.dp else 6.dp).clip(MaterialTheme.shapes.extraSmall).background(if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.4f)))
                        }
                    }
                }
                Spacer(Modifier.width(48.dp))
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                var zoomScale by remember { mutableFloatStateOf(1f) }
                Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                    }
                }.graphicsLayer { scaleX = zoomScale; scaleY = zoomScale }, contentAlignment = Alignment.Center) {
                    AsyncImage(model = File(imageList[page]), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Button(
                    onClick = {
                        viewModel.downloadImageToGallery(imageList[pagerState.currentPage])
                        previewScope.launch { snackbarPreviewHost.showSnackbar(context.getString(R.string.asset_saved_to_album)) }
                    },
                    modifier = Modifier.height(44.dp).wrapContentWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.asset_save_to_phone), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatusActionButton(
    onClick: () -> Unit, label: String, icon: ImageVector,
    color: Color, enabled: Boolean, filled: Boolean = false
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            enabled = enabled
        ) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            enabled = enabled
        ) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label)
        }
    }
}

@Composable
private fun UsageRecordItem(record: UsageRecord, actionsWidthPx: Float, onEdit: (UsageRecord) -> Unit, onDelete: (Long) -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier.fillMaxWidth().pointerInput(record.id) {
            detectHorizontalDragGestures(
                onDragEnd = { offsetX = if (offsetX < -actionsWidthPx / 2) -actionsWidthPx else 0f },
                onHorizontalDrag = { _, amount -> offsetX = (offsetX + amount).coerceIn(-actionsWidthPx, 0f) }
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).align(Alignment.CenterEnd).padding(end = 4.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onEdit(record) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit), tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { onDelete(record.id) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = ErrorLight) }
        }
        Box(Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) {
            ModuleCard(tint = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(text = DateUtils.formatDateTime(record.usedAt), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (record.note.isNotEmpty()) {
                        Text(text = record.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
