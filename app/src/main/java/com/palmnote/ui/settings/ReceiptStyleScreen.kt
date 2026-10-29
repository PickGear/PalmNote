package com.palmnote.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.model.BillType
import com.palmnote.ui.bills.DailyReceiptEntry
import com.palmnote.ui.bills.DailyReceiptPaper
import com.palmnote.ui.bills.DailySummaryPaper
import com.palmnote.ui.components.CompactTopAppBar

// 小票样式预览的样例流水：金额单位为分，日期固定同一天（两种样式共用，方便对比）。
// 分类/钱包名是演示数据（与预设分类同风格），固定中文——预览不随界面语言变。
private val receiptSampleEntries = listOf(
    DailyReceiptEntry(
        Bill(
            amount = 12900,
            type = BillType.EXPENSE,
            category = "shopping",
            date = 1767609600000,
            yearMonth = "2026-01",
            walletId = 1L,
            merchant = "淘宝"
        ),
        "购物"
    ),
    DailyReceiptEntry(
        Bill(
            amount = 2500,
            type = BillType.EXPENSE,
            category = "drinking",
            date = 1767613200000,
            yearMonth = "2026-01",
            walletId = 1L,
            merchant = "茶颜悦色"
        ),
        "饮品"
    ),
    DailyReceiptEntry(
        Bill(
            amount = 1000,
            type = BillType.INCOME,
            category = "management",
            date = 1767616800000,
            yearMonth = "2026-01",
            walletId = 2L
        ),
        "理财"
    ),
    DailyReceiptEntry(
        Bill(
            amount = 18600,
            type = BillType.TRANSFER,
            category = "transfer",
            date = 1767620400000,
            yearMonth = "2026-01",
            walletId = 1L,
            toWalletId = 2L
        ),
        "转账"
    )
)
private val receiptSampleWallets = mapOf(1L to "支付宝", 2L to "微信")

/** 小票样式选择页：两种纸样按真实 300dp 尺寸完整展示，点选即切换。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptStyleScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings_receipt_style),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        ReceiptStyleChoices(
            currentStyle = state.receiptStyle,
            onSelect = viewModel::setReceiptStyle,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/** 两种纸样的完整展示与点选切换。 */
@Composable
private fun ReceiptStyleChoices(currentStyle: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                stringResource(R.string.settings_receipt_style_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            ReceiptStyleBlock(
                title = stringResource(R.string.settings_receipt_style_detail),
                description = stringResource(R.string.settings_receipt_style_detail_desc),
                selected = currentStyle == PreferencesManager.RECEIPT_STYLE_DETAIL,
                onSelect = { onSelect(PreferencesManager.RECEIPT_STYLE_DETAIL) }
            ) {
                DailyReceiptPaper(
                    dayKey = "2026-01-05",
                    entries = receiptSampleEntries,
                    wallets = receiptSampleWallets,
                    bookName = "日常",
                    monthExpense = 1493000,
                    monthIncome = 1199300
                )
            }
        }
        item {
            ReceiptStyleBlock(
                title = stringResource(R.string.settings_receipt_style_summary),
                description = stringResource(R.string.settings_receipt_style_summary_desc),
                selected = currentStyle == PreferencesManager.RECEIPT_STYLE_SUMMARY,
                onSelect = { onSelect(PreferencesManager.RECEIPT_STYLE_SUMMARY) }
            ) {
                DailySummaryPaper(dayKey = "2026-01-05", entries = receiptSampleEntries)
            }
        }
    }
}

/** 单个样式的完整纸样（真实 300dp 尺寸、不缩放）+ 纸样投影 + 选中态动画描边与说明。 */
@Composable
private fun ReceiptStyleBlock(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    paper: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    // 描边动画化：1dp↔2dp 的跳变会让卡内内容位移，动画过渡后不可感
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, label = "receiptStyleBorder")
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "receiptStyleBorderColor"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .clickable(onClick = onSelect)
    ) {
        ReceiptStyleStage(selected = selected, paper = paper)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 纸样的展示舞台：模拟小票弹窗的黑遮罩底 + 纸样投影 + 选中「使用中」徽标。 */
@Composable
private fun ReceiptStyleStage(selected: Boolean, paper: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.shadow(elevation = 6.dp)) { paper() }
        if (selected) {
            // 使用中徽标：贴舞台右上角，像票据上的印章位
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.settings_receipt_style_in_use),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
