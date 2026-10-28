package com.palmnote.ui.bills

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.palmnote.app.R
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.toMoney
import com.palmnote.domain.util.CurrencyUtils
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.NoDialogWindowAnimation
import com.palmnote.ui.life.lifeShareFileName
import com.palmnote.ui.life.recordInto
import com.palmnote.ui.life.saveSharePng
import com.palmnote.ui.life.sharePng
import com.palmnote.ui.theme.AccentOrange
import com.palmnote.ui.theme.ExpenseRed
import com.palmnote.ui.theme.InfoBlue
import com.palmnote.ui.theme.StatusActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 预览默认印几笔：固定 5 笔。
 *
 * 折叠态必须整张纸（含合计、本月累计、条码、落款）一屏可见、不用滑动——
 * 印太多就得滚动才能看到底部，打印动画的意义也没了。多出的笔数折成
 * 「另 N 笔」，点一下可以展开看全部；保存/分享始终是完整明细。
 */
private const val DEFAULT_PRINTED_ITEMS = 5

// 纸面配色：纯白纸 + 暖调收尾（与弹层米色底拉开明度差，齿形才靠明度自然浮现；
// 旧值 #FFFEFA 与米色底只差 3% 明度，齿完全看不出来）
private val PaperTop = Color(0xFFFFFFFF)
private val PaperBottom = Color(0xFFFBF6EC)
private val Ink = Color(0xFF3D3A33)
private val InkLight = Ink.copy(alpha = 0.55f)
private val PaperRule = Ink.copy(alpha = 0.30f)

/**
 * 每日小票（账单页点日期行）：热敏小票样式的当日流水，可保存到相册 / 分享。
 *
 * **居中对话框而不是底部弹窗**：小票（机身+纸+按钮）整体偏高，底部弹窗撑满到顶
 * 后机身与标题会被挤出可视区、纸也贴着屏幕边缘；改为居中 Dialog，超高时内容
 * 内部滚动，打印预览的整体感才成立。
 *
 * 结构与 LifeShareSheet 同款：弹出的就是将要保存的那张纸的真实预览，
 * 录制走 [recordInto]（GraphicsLayer），保存/分享复用 life 包的通用实现。
 */
@Composable
internal fun DailyReceiptSheet(
    dayKey: String,
    entries: List<DailyReceiptEntry>,
    wallets: Map<Long, String>,
    bookName: String,
    monthExpense: Long,
    monthIncome: Long,
    onDismiss: () -> Unit
) {
    // 打印动画：纸从顶部逐帧「吐出」（可视高度随进度增长），到位后按钮才淡入
    val printProgress = remember { Animatable(0f) }
    var printed by remember { mutableStateOf(false) }
    // 完整纸的录制层：保存/分享永远导出**全部明细**，与预览截断与否无关
    val fullLayer = rememberGraphicsLayer()
    // 「另 N 笔」点开后预览切到完整纸（可滚动查看），不影响打印动画
    var showFull by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 热敏打印机匀速出纸：2s 线性，看得出新内容一行行从缝口冒出来
        printProgress.animateTo(1f, tween(2000, easing = LinearEasing))
        printed = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        NoDialogWindowAnimation()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // 隐形完整纸：只为录制（保存/分享永远导出全部明细）。
            // 要点：① forceFull 必须为 true（否则按预览笔数截断）；
            // ② 节点保持完整尺寸并移出屏幕——不能压成 0×0（graphicsLayer 的 alpha 层
            //    尺寸等于节点尺寸，0×0 会把内容裁没）；
            // ③ wrapContentHeight(unbounded) ：Dialog 的屏高约束会截掉纸的下半段
            //    （条码/落款录不到），这里放开高度上限按内容真实高度测量。
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = 0.01f }
                    .offset(x = (-600).dp, y = 0.dp)
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
            ) {
                Box(modifier = Modifier.recordInto(fullLayer)) {
                    DailyReceiptPaper(
                        dayKey = dayKey,
                        entries = entries,
                        wallets = wallets,
                        bookName = bookName,
                        monthExpense = monthExpense,
                        monthIncome = monthIncome,
                        onShowAll = null,
                        forceFull = true
                    )
                }
            }
            ReceiptDialogContent(
                args = ReceiptArgs(dayKey, entries, wallets, bookName, monthExpense, monthIncome),
                layer = fullLayer,
                printProgress = printProgress.value,
                printed = printed,
                showFull = showFull,
                onShowFull = { showFull = true }
            )
        }
    }
}

/** 小票数据的打包（避免对话框参数列表过长）。 */
private data class ReceiptArgs(
    val dayKey: String,
    val entries: List<DailyReceiptEntry>,
    val wallets: Map<Long, String>,
    val bookName: String,
    val monthExpense: Long,
    val monthIncome: Long
)

/** 对话框主体：打印机 + 纸（按进度长出）+ 按钮槽（固定高，打完淡入）。 */
@Composable
private fun ReceiptDialogContent(
    args: ReceiptArgs,
    layer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    printProgress: Float,
    printed: Boolean,
    showFull: Boolean,
    onShowFull: () -> Unit
) {
    val actionsAlpha by animateFloatAsState(
        targetValue = if (printed) 1f else 0f,
        animationSpec = tween(200),
        label = "receiptActions"
    )
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = 24.dp)
            // 吞掉点击，避免点内容区误关
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 滚动区只装小票：机身与按钮在下面固定，滑动时只有纸在动
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            // 纸贴在机身上（内容不足一屏时也贴底）
            verticalArrangement = Arrangement.Bottom
        ) {
            ReceiptPrinter(printProgress = printProgress) {
                DailyReceiptPaper(
                    dayKey = args.dayKey,
                    entries = args.entries,
                    wallets = args.wallets,
                    bookName = args.bookName,
                    monthExpense = args.monthExpense,
                    monthIncome = args.monthIncome,
                    onShowAll = if (showFull) null else onShowFull,
                    forceFull = showFull
                )
            }
        }
        // 固定底部：打印机机身 + 按钮（不随小票滚动）
        PrinterBody(printing = !printed)
        Spacer(Modifier.height(16.dp))
        // 按钮槽固定 48dp：显示/隐藏不改变整组高度，打印期间画面不跳动
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 32.dp)
                .alpha(actionsAlpha)
        ) {
            if (printed) ReceiptActions(layer, args.dayKey)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 打印机出纸：机身固定在滚动区外、纸在滚动区内按进度从缝口长出。
 *
 * - 纸槽高度恒定（占位撑开），纸在其中整体向上平移、超出部分裁掉：
 *   先出头（店头先出现），落款最后从缝口冒出；
 * - 位移走 graphicsLayer 图层属性，逐帧必然生效（布局/重组路径曾导致真机不更新）。
 */
@Composable
private fun ReceiptPrinter(
    printProgress: Float,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 出票口在下方、纸向上出。物理关系：
        //   · 缝口这一端 = 内容新增处（日期→流水→合计→落款依次冒出来）
        //   · 最先印的店头最先出现，并且离缝口越来越远（往上走）
        // 实现要点：
        //   1. 纸槽高度**恒定**（透明占位撑成完整纸高）——整组高度不变，弹窗居中稳定、
        //      机身位置在打印全程纹丝不动；
        //   2. 纸在其中整体向上平移，由 clipToBounds 裁掉尚未出来的下半段；
        //   3. translationY 在 graphicsLayer 里读进度（图层属性更新），逐帧必然生效。
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.fillMaxWidth().alpha(0f)) { content() }
            Box(modifier = Modifier.fillMaxWidth().clipToBounds(), contentAlignment = Alignment.TopCenter) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            // 纸整体向下偏移、超出容器底部的部分被裁掉 →
                            // 可见的是纸的**头部一段**（店头最先出现），随进度整体上移，
                            // 落款最后从缝口冒出：先出头、机器在下、纸向上长。
                            translationY = size.height * (1f - printProgress)
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    content()
                }
            }
        }
    }
}

/** 打印机机身：底座式壳体，**出纸缝在上边缘**（纸从缝里向上出）+ 指示灯 + 装饰横条。 */
@Composable
private fun PrinterBody(printing: Boolean) {
    val pulse = rememberInfiniteTransition(label = "printerLamp")
    val breathing by pulse.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "printerLampAlpha"
    )
    Box(
        modifier = Modifier
            // 机身比纸（300dp）略宽，左右各多出 10dp：像机器比纸口宽、纸从中间吐出
            .width(320.dp)
            .height(34.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 5.dp,
                    topEnd = 5.dp,
                    bottomStart = 10.dp,
                    bottomEnd = 10.dp
                )
            )
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3630), Color(0xFF211F1B))))
    ) {
        // 出纸缝：贴**上边缘**（出票口朝上，纸从这条缝里冒出来）
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(6.dp)
                .background(Color(0xFF0F0D0A))
        )
        // 指示灯：左下角（打印中呼吸，打完常亮弱光）
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 9.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(StatusActive.copy(alpha = if (printing) breathing else 0.35f))
        )
        // 底部装饰横条（机身"铭牌"）
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .width(72.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF4A4640))
        )
    }
}

/** 保存到相册 + 分享两个动作（录制层由调用方持有，两次动作共用同一次录制）。 */
@Composable
private fun ReceiptActions(layer: androidx.compose.ui.graphics.layer.GraphicsLayer, dayKey: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = {
                scope.launch {
                    val ok = runCatching {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        val name = lifeShareFileName("receipt-$dayKey", System.currentTimeMillis())
                        withContext(Dispatchers.IO) { saveBitmapToGallery(context, bitmap, name) }
                    }.isSuccess
                    Toast.makeText(
                        context,
                        context.getString(
                            if (ok) R.string.bill_receipt_saved else R.string.bill_receipt_save_failed
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.weight(1f).height(48.dp),
            // 次要动作给实心底：弹窗背景是半透明遮罩，透明描边按钮在深底上显得空
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(stringResource(R.string.bill_receipt_save))
        }
        Button(
            onClick = {
                scope.launch {
                    val ok = runCatching {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        val name = lifeShareFileName("receipt-$dayKey", System.currentTimeMillis())
                        val file = withContext(Dispatchers.IO) { saveSharePng(context, bitmap, name) }
                        sharePng(context, file)
                    }.isSuccess
                    Toast.makeText(
                        context,
                        context.getString(
                            if (ok) R.string.bill_receipt_shared else R.string.life_share_failed
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
        ) {
            Text(stringResource(R.string.bill_receipt_share))
        }
    }
}

/** 纸面：锯齿顶边 + 暖白渐变纸身 + 内容 + 锯齿底边。宽度固定 300dp，保证截图尺寸稳定。 */
@Composable
private fun DailyReceiptPaper(
    dayKey: String,
    entries: List<DailyReceiptEntry>,
    wallets: Map<Long, String>,
    bookName: String,
    monthExpense: Long,
    monthIncome: Long,
    /** 「另 N 笔」的可点回调；null 表示不显示入口（隐形录制层/已展开时）。 */
    onShowAll: (() -> Unit)? = null,
    /** true = 不分页，印全部明细（预览展开态与隐形录制层用）。 */
    forceFull: Boolean = false
) {
    // forceFull：预览展开「全部」或隐形录制层时不分页，印全部明细
    val maxItems = if (forceFull) Int.MAX_VALUE else DEFAULT_PRINTED_ITEMS
    val paper = Brush.verticalGradient(listOf(PaperTop, PaperBottom))
    val dayExpense = entries.filter { it.bill.type == BillType.EXPENSE }.sumOf { it.bill.amount }
    val dayIncome = entries.filter { it.bill.type == BillType.INCOME }.sumOf { it.bill.amount }
    val sorted = remember(entries) { entries.sortedBy { it.bill.createdAt } }

    Column(modifier = Modifier.width(300.dp)) {
        ZigzagEdge(top = true, seed = dayKey.hashCode())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(paper)
                .thermalPaperTexture(seed = dayKey.hashCode())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            ReceiptHeader(entries, bookName)
            DoubleRule()
            // 纸质小票印不下几十笔：超过 9 笔截断到前 8 笔 + 省略行（合计仍按全日算）
            val shown = sorted.take(maxItems)
            shown.forEachIndexed { index, entry ->
                ReceiptItem(index, entry, wallets)
                if (index != shown.lastIndex) DashedDivider()
            }
            if (sorted.size > shown.size) {
                val moreText = stringResource(R.string.bill_receipt_more_items, sorted.size - shown.size)
                if (onShowAll != null) {
                    // 可点展开：点一下预览切到完整纸（外层可滚动查看全部明细）
                    Text(
                        text = moreText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clickable { onShowAll() },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    Text(
                        text = moreText,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkLight,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            DoubleRule()
            ReceiptTotals(dayExpense, dayIncome, dayIncome - dayExpense)
            Spacer(Modifier.height(8.dp))
            ReceiptMonthSummary(monthExpense, monthIncome)
            Spacer(Modifier.height(12.dp))
            Barcode(dayKey)
            Spacer(Modifier.height(10.dp))
            ReceiptFooter()
        }
        ZigzagEdge(top = false, seed = dayKey.hashCode())
    }
}

/**
 * 热敏纸质感：3dp 一档的极淡横向纤维线（热敏纸卷材留下的细纹）+ 稀疏暖灰噪点。
 * 两者 alpha 都压在 0.02 以下——只在近看时"有纸"，不抢内容；确定性序列，
 * 同一天永远同一张纸。
 */
private fun Modifier.thermalPaperTexture(seed: Int): Modifier = drawBehind {
    val fiber = Ink.copy(alpha = 0.016f)
    var y = 0f
    while (y < size.height) {
        drawLine(fiber, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += 3.dp.toPx()
    }
    var state = seed * 31 + 17
    repeat(140) {
        state = state * 1103515245 + 12345
        val px = ((state ushr 8) and 0xFFFF) % size.width.toInt().coerceAtLeast(1)
        state = state * 1103515245 + 12345
        val py = ((state ushr 8) and 0xFFFF) % size.height.toInt().coerceAtLeast(1)
        drawCircle(Ink.copy(alpha = 0.018f), radius = 0.8f, center = Offset(px.toFloat(), py.toFloat()))
    }
}

/**
 * 锯齿撕边（撕纸风）。
 *
 * 三条让它「像撕的」而不是「画上去的」：
 * 1. 齿不等宽（半齿 6–12dp 确定性随机，种子 = 日期）——等距三角一眼假；
 * 2. 不描边、改**软投影**：齿形整体偏移一层 7% 墨色垫在纸下，
 *    描边是印刷感，投影才是「一张纸放在桌上」；
 * 3. 纸面纯白 + 弹层米色底，靠明度差让齿自然浮现。
 */
@Composable
private fun ZigzagEdge(top: Boolean, seed: Int) {
    Canvas(modifier = Modifier.fillMaxWidth().height(15.dp)) {
        // 齿宽的确定性抖动：线性同余（纯算术，不是随机数生成器），同一天永远同一张纸
        var state = seed * 31 + if (top) 7 else 13
        fun nextHalfDp(): Float {
            state = state * 1103515245 + 12345
            return (6 + ((state ushr 16) and 0x7) % 7).dp.toPx()
        }
        val baseY = if (top) size.height else 0f
        val peakY = if (top) 0f else size.height
        val path = Path()
        path.moveTo(0f, baseY)
        var x = 0f
        var toPeak = true
        while (x < size.width) {
            x = (x + nextHalfDp()).coerceAtMost(size.width)
            path.lineTo(x, if (toPeak) peakY else baseY)
            toPeak = !toPeak
        }
        path.lineTo(size.width, baseY)
        path.close()
        // 软投影：齿形整体下/上偏移 2dp 的低 alpha 墨层，垫在纸面之下
        val shadow = Path().apply {
            addPath(path, Offset(0f, if (top) 2.dp.toPx() else -2.dp.toPx()))
        }
        drawPath(shadow, Ink.copy(alpha = 0.07f))
        drawPath(path, Brush.verticalGradient(listOf(PaperTop, PaperBottom)), style = Fill)
    }
}

@Composable
private fun ReceiptHeader(entries: List<DailyReceiptEntry>, bookName: String) {
    val context = LocalContext.current
    val bill = entries.firstOrNull()?.bill ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "PalmNote",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Ink
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.bill_receipt_title),
            style = MaterialTheme.typography.labelMedium,
            color = InkLight
        )
        Spacer(Modifier.height(10.dp))
        Text(
            DateUtils.formatDisplayDateWithWeekday(context, bill.date),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Ink
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.bill_book) + "：" + bookName + " · " +
                pluralStringResource(R.plurals.bill_count, entries.size, entries.size),
            style = MaterialTheme.typography.bodySmall,
            color = InkLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(Modifier.height(10.dp))
}

/** 小票一行：序号 + 分类（·备注/商家）+ 金额；第二行 钱包（转账显示 A → B）。 */
@Composable
private fun ReceiptItem(index: Int, entry: DailyReceiptEntry, wallets: Map<Long, String>) {
    val context = LocalContext.current
    val bill = entry.bill
    val amountColor = when (bill.type) {
        BillType.EXPENSE -> ExpenseRed
        BillType.TRANSFER -> InfoBlue
        BillType.INCOME -> StatusActive
    }
    val prefix = when (bill.type) {
        BillType.EXPENSE -> "-"
        BillType.INCOME -> "+"
        BillType.TRANSFER -> ""
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(index + 1),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = InkLight
            )
            Spacer(Modifier.width(8.dp))
            Text(
                entry.categoryName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                prefix + CurrencyUtils.formatCurrency(context, bill.amount.toMoney()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = amountColor
            )
        }
        ReceiptItemDetailRow(bill, wallets)
    }
}

/** 小票行的说明行：备注/商家/地点（转账显示钱包对），右侧钱包名。 */
@Composable
private fun ReceiptItemDetailRow(bill: Bill, wallets: Map<Long, String>) {
    val detail = when (bill.type) {
        BillType.TRANSFER -> {
            val from = wallets[bill.walletId].orEmpty()
            val to = wallets[bill.toWalletId].orEmpty()
            "$from → $to"
        }
        else -> listOf(bill.merchant, bill.note, bill.location).firstOrNull { it.isNotBlank() }.orEmpty()
    }
    Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 1.dp)) {
        if (detail.isNotBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        bill.walletId?.let { wid ->
            wallets[wid]?.let { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 虚线分隔（油墨虚线，非实线——热敏小票的典型分栏）。 */
@Composable
private fun DashedDivider() {
    Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
        val dash = 5.dp.toPx()
        val gap = 4.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(PaperRule, Offset(x, size.height / 2), Offset((x + dash).coerceAtMost(size.width), size.height / 2))
            x += dash + gap
        }
    }
}

/** 双实线：小票的表头/合计栏分界。 */
@Composable
private fun DoubleRule() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(PaperRule))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(PaperRule))
    }
}

@Composable
private fun ReceiptTotals(dayExpense: Long, dayIncome: Long, dayBalance: Long) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        TotalRow(stringResource(R.string.bill_receipt_day_expense), dayExpense, Ink)
        TotalRow(stringResource(R.string.bill_receipt_day_income), dayIncome, Ink)
        TotalRow(
            stringResource(R.string.bill_receipt_day_balance),
            dayBalance,
            if (dayBalance >= 0) StatusActive else ExpenseRed,
            bold = true
        )
    }
}

@Composable
private fun TotalRow(label: String, amount: Long, color: Color, bold: Boolean = false) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
        Text(
            CurrencyUtils.formatCurrency(context, amount.toMoney()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

/** 本月累计（内容丰富度：当日之外再给当月视角）。 */
@Composable
private fun ReceiptMonthSummary(monthExpense: Long, monthIncome: Long) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.bill_receipt_month_expense) + " " +
                CurrencyUtils.formatCurrency(context, monthExpense.toMoney()),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = InkLight
        )
        Text(
            stringResource(R.string.bill_receipt_month_income) + " " +
                CurrencyUtils.formatCurrency(context, monthIncome.toMoney()),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = InkLight
        )
    }
}

/** 装饰条码：以日期为种子的确定性假条码（同一天同一张，纯氛围）。 */
@Composable
private fun Barcode(dayKey: String) {
    Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
        val rng = kotlin.random.Random(dayKey.hashCode())
        var x = 0f
        while (x < size.width) {
            val w = listOf(2f, 3f, 5f)[rng.nextInt(3)]
            if (rng.nextBoolean()) {
                drawRect(Ink, Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
            }
            x += w + 2f + rng.nextInt(3)
        }
    }
}

@Composable
private fun ReceiptFooter() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.bill_receipt_thanks),
            style = MaterialTheme.typography.labelMedium,
            color = Ink
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.bill_receipt_printed) + " " +
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = InkLight
        )
    }
}

/** 保存到相册（Pictures/PalmNote）。API 29+ 走 MediaStore 无需权限；更早版本返回 false。 */
private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PalmNote")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    return context.contentResolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    } == true
}
