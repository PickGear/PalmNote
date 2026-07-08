package com.palmnote.ui.life.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.entity.CrossLink
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.LinkType
import com.palmnote.domain.repository.AssetRepository
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.CrossLinkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinkableBill(val id: Long, val note: String, val amount: String)
data class LinkableAsset(val id: Long, val name: String, val category: String)

data class LinkSelectorState(
    val bills: List<LinkableBill> = emptyList(),
    val assets: List<LinkableAsset> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class LinkSelectorViewModel @Inject constructor(
    private val billRepo: BillRepository,
    private val assetRepo: AssetRepository,
    private val crossLinkRepo: CrossLinkRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LinkSelectorState())
    val state: StateFlow<LinkSelectorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val bills = billRepo.getAllBills().first().map {
                    LinkableBill(id = it.id, note = it.note, amount = "¥${"%.2f".format(it.amount)}")
                }
                val assets = assetRepo.getAllAssets().first().map {
                    LinkableAsset(id = it.id, name = it.name, category = it.category)
                }
                _state.update { it.copy(bills = bills, assets = assets, isLoading = false) }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun createLink(sourceId: Long, targetType: EntityType, targetId: Long, linkType: LinkType) {
        viewModelScope.launch {
            try {
                crossLinkRepo.createLink(CrossLink(
                    sourceType = EntityType.ITEM, sourceId = sourceId,
                    targetType = targetType, targetId = targetId,
                    linkType = linkType, isAutoLinked = false
                ))
            } catch (_: Exception) { }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSelectorSheet(
    sourceItemId: Long,
    onDismiss: () -> Unit,
    viewModel: LinkSelectorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("\u5173\u8054\u6570\u636E", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Text("\u8D26\u5355", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))

            if (state.bills.isEmpty()) {
                Text("\u6682\u65E0\u8D26\u5355", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            } else {
                state.bills.take(5).forEach { bill ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.createLink(sourceItemId, EntityType.BILL, bill.id, LinkType.RELATED_TO)
                            onDismiss()
                        }.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bill.note.ifEmpty { "\u8D26\u5355 #${bill.id}" }, fontSize = 13.sp)
                            Text(bill.amount, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("\u7269\u54C1", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))

            if (state.assets.isEmpty()) {
                Text("\u6682\u65E0\u7269\u54C1", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            } else {
                state.assets.take(5).forEach { asset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.createLink(sourceItemId, EntityType.ASSET, asset.id, LinkType.PART_OF)
                            onDismiss()
                        }.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asset.name, fontSize = 13.sp)
                            Text(asset.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
