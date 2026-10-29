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
import androidx.compose.ui.graphics.lerp
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
import com.palmnote.data.datastore.PreferencesManager
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
import kotlin.math.roundToInt

/**
 * 预览默认印几笔：固定 5 笔。
 *
 * 折叠态必须整张纸（含合计、本月累计、条码、落款）一屏可见、不用滑动——
 * 印太多就得滚动才能看到底部，打印动画的意义也没了。多出的笔数折成
 * 「另 N 笔」，点一下可以展开看全部；保存/分享始终是完整明细。
 */
private const val DEFAULT_PRINTED_ITEMS = 5

// 纸面配色：暖白纯色（用户定稿：不要渐变）；与黑遮罩拉开明度差，齿形靠明度自然浮现
internal val PaperColor = Color(0xFFFDFBF5)
internal val Ink = Color(0xFF3D3A33)

// 打印机壳面：浅银灰磨砂（黑遮罩上靠明度差立得住）；顶边中段下沉 ShellNotchDepth 形成出纸缺口
private val ShellTop = Color(0xFFEAE8EC)
private val ShellBottom = Color(0xFFD9D7DB)
private val ShellNotchDepth = 10.dp
private val ExitStripHeight = 3.dp
private val ShellHeight = 34.dp

// 中段壳面比整机矮 ShellNotchDepth：渐变按它在整机里的真实纵向位置对齐，
// 中段与两肩的壳面颜色才连成一体、看不出拼接
private val ShellMouthBrush = Brush.verticalGradient(
    0f to lerp(ShellTop, ShellBottom, ShellNotchDepth.value / ShellHeight.value),
    1f to ShellBottom
)
internal val InkLight = Ink.copy(alpha = 0.55f)
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
    style: String,
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
                    ReceiptPaperFor(
                        style = style,
                        dayKey = dayKey,
                        entries = entries,
                        wallets = wallets,
                        bookName = bookName,
                        monthExpense = monthExpense,
                        monthIncome = monthIncome,
                        forceFull = true
                    )
                }
            }
            ReceiptDialogContent(
                style = style,
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
    style: String,
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
            // 40dp：纸的锯齿不顶状态栏，顶部留一口气
            .padding(top = 40.dp)
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
                ReceiptPaperFor(
                    style = style,
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
        // 固定底部：打印机机身 + 按钮（不随小票滚动）。
        // 机身上移 9dp：小票尾部收进凹陷缺口、缩在灰色出口条后 2dp，出口条在小票下方露出 3dp；
        // 上移量小于纸 14dp 的底部内边距，落款不受影响。Spacer 相应补偿，视觉间距不变。
        PrinterBody(
            printing = !printed,
            modifier = Modifier.offset(y = (-9).dp)
        )
        Spacer(Modifier.height(7.dp))
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

/**
 * 打印机机身：浅银灰磨砂壳（黑遮罩上靠明度差立得住）。
 * 顶边不是一条平线：左右凸肩高出中间 10dp，中间下沉的部分就是出纸缺口，
 * 小票末端没进口内、从两肩之间钻出来（两端突出、与出口不平齐）。
 */
@Composable
private fun PrinterBody(printing: Boolean, modifier: Modifier = Modifier) {
    // 机身比纸（300dp）略宽，左右各多出 15dp：两肩在纸两侧，纸从中间的缺口出
    Box(modifier = modifier.width(330.dp).height(ShellHeight)) {
        // 缺口底板：只垫缺口底部 3dp——灰色出口条在小票末端的下方露出，
        // 纸尾缩在它后面 2dp（防发丝缝）；缺口上半段透出纸面与背景
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = ShellNotchDepth - ExitStripHeight)
                .width(300.dp)
                .height(ExitStripHeight)
                .background(Color(0xFFC9C7CC))
        )
        // 中段壳面：顶边下沉形成出纸缺口，与纸同宽，纸的末端没进口内
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxHeight()
                .padding(top = ShellNotchDepth)
                .width(300.dp)
                .background(ShellMouthBrush)
        )
        PrinterShoulder(isLeft = true, modifier = Modifier.align(Alignment.TopStart))
        PrinterShoulder(isLeft = false, modifier = Modifier.align(Alignment.TopEnd))
        PrinterMouthShading(modifier = Modifier.align(Alignment.TopCenter))
        // 底边环境光遮蔽：机身落地的一圈微暗，底部不至于轻飘飘
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f))
                    ),
                    RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                )
        )
        PrinterLamp(printing = printing, modifier = Modifier.align(Alignment.BottomStart))
        // 底部装饰横条（机身"铭牌"）
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .width(72.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFC2C0C5))
        )
    }
}

/** 左右凸肩：比出纸缺口高 ShellNotchDepth，出口两端「突出、不平齐」。宽 16dp = 15dp + 1dp 叠压在中段上——15dp 在屏上非整数像素，两块矩形平铺会漏出一条发丝缝（背后暗色透出来像一根线），叠压后缝不可能存在。 */
@Composable
private fun PrinterShoulder(isLeft: Boolean, modifier: Modifier = Modifier) {
    val shape = if (isLeft) {
        RoundedCornerShape(topStart = 5.dp, bottomStart = 10.dp)
    } else {
        RoundedCornerShape(topEnd = 5.dp, bottomEnd = 10.dp)
    }
    Box(
        modifier
            .width(16.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ShellTop, ShellBottom)))
    ) {
        // 凸肩顶棱高光：塑料壳斜面的一道反光。画在肩体内部、随肩体轮廓裁切——
        // 独立画小条会脱离机身圆角
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.55f))
        )
    }
}

/** 口内的两层阴影：槽内暗部贴着口沿 6dp、不随缺口加深拉高；沿口下的落影让纸与壳的交接是凹口、不是一根硬线。 */
@Composable
private fun PrinterMouthShading(modifier: Modifier = Modifier) {
    Box(modifier.width(300.dp).height(ShellNotchDepth + 5.dp)) {
        // 槽内暗部：出纸口内侧是黑的，纸刚钻出来的那截被「口」罩住变暗
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(ShellNotchDepth)
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f))
                        )
                    )
            )
        }
        // 口内落影
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(5.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFBEBCC1).copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
    }
}

/** 指示灯 + 光晕：整体挂在机身左下角，光晕与 LED 同心（圆心距底 12dp）。打印中呼吸闪烁（光晕随动），打完常亮。 */
@Composable
private fun PrinterLamp(printing: Boolean, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "printerLamp")
    val breathing by pulse.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "printerLampAlpha"
    )
    Box(modifier.width(23.dp).height(18.dp)) {
        // 光晕：LED 读作光源，打印中随呼吸明暗
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 11.dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    StatusActive.copy(alpha = if (printing) breathing * 0.22f else 0.22f)
                )
        )
        // LED 本体
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 3.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (printing) StatusActive.copy(alpha = breathing) else StatusActive)
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

/** 纸面：锯齿顶边 + 暖白渐变纸身 + 内容，底边平直没进机身出纸缺口。宽度固定 300dp，保证截图尺寸稳定。 */
@Composable
internal fun DailyReceiptPaper(
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
    // 条码数字 = 打印时间的年月日时分（yyyyMMddHHmm，恰好 12 位 EAN-13 数据位），扫码读到的就是这一刻
    val printCode = remember { SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault()).format(Date()) }
    val dayExpense = entries.filter { it.bill.type == BillType.EXPENSE }.sumOf { it.bill.amount }
    val dayIncome = entries.filter { it.bill.type == BillType.INCOME }.sumOf { it.bill.amount }
    val sorted = remember(entries) { entries.sortedBy { it.bill.createdAt } }

    Column(modifier = Modifier.width(300.dp)) {
        ScallopedEdge()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PaperColor)
                .thermalPaperTexture(seed = dayKey.hashCode())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            ReceiptHeader(dayKey, entries, bookName)
            DoubleRule()
            ReceiptTableHeader()
            // 纸质小票印不下几十笔：超过 9 笔截断到前 8 笔 + 省略行（合计仍按全日算）
            val shown = sorted.take(maxItems)
            shown.forEachIndexed { index, entry ->
                if (index > 0) DashedDivider()
                ReceiptItem(entry, wallets)
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
            Barcode(ean13Digits(printCode), printCode.chunked(4).joinToString(" "))
            Spacer(Modifier.height(10.dp))
            ReceiptFooter()
        }
    }
}

/** 按设置的小票样式渲染纸面：明细流水（默认）/ 分类汇总。 */
@Composable
private fun ReceiptPaperFor(
    style: String,
    dayKey: String,
    entries: List<DailyReceiptEntry>,
    wallets: Map<Long, String>,
    bookName: String,
    monthExpense: Long,
    monthIncome: Long,
    onShowAll: (() -> Unit)? = null,
    forceFull: Boolean = false
) {
    if (style == PreferencesManager.RECEIPT_STYLE_SUMMARY) {
        DailySummaryPaper(dayKey = dayKey, entries = entries)
    } else {
        DailyReceiptPaper(
            dayKey = dayKey,
            entries = entries,
            wallets = wallets,
            bookName = bookName,
            monthExpense = monthExpense,
            monthIncome = monthIncome,
            onShowAll = onShowAll,
            forceFull = forceFull
        )
    }
}

/**
 * 热敏纸质感：3dp 一档的横向纤维线（热敏纸卷材留下的细纹）+ 稀疏暖灰噪点。
 * alpha 压在 0.04 以下——纹理可见但不抢内容；确定性序列，同一天永远同一张纸。
 */
internal fun Modifier.thermalPaperTexture(seed: Int): Modifier = drawBehind {
    val fiber = Ink.copy(alpha = 0.035f)
    var y = 0f
    while (y < size.height) {
        drawLine(fiber, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += 3.dp.toPx()
    }
    var state = seed * 31 + 17
    repeat(220) {
        state = state * 1103515245 + 12345
        val px = ((state ushr 8) and 0xFFFF) % size.width.toInt().coerceAtLeast(1)
        state = state * 1103515245 + 12345
        val py = ((state ushr 8) and 0xFFFF) % size.height.toInt().coerceAtLeast(1)
        drawCircle(Ink.copy(alpha = 0.032f), radius = 0.8f, center = Offset(px.toFloat(), py.toFloat()))
    }
}

/**
 * 撕边（参考主流记账 App 的小票纸）：打孔撕边——纸沿打孔线撕下，顶边留下一排
 * 半圆缺口。缺口与平面相间、等距铺满整行，缺口内衬弧形
 * 软投影。只用于纸的**顶边**（底边平直，收进机身缺口）；旧版扇贝/三角已废弃。
 */
@Composable
internal fun ScallopedEdge() {
    Canvas(modifier = Modifier.fillMaxWidth().height(5.dp)) {
        val pitch = 15.dp.toPx()
        val r = 5.dp.toPx()
        val count = (size.width / pitch).toInt().coerceAtLeast(8)
        // 缺口中心：整组居中，两端各留半个平面段
        val start = (size.width - count * pitch) / 2f + pitch / 2f
        val path = Path()
        path.moveTo(0f, size.height)
        path.lineTo(0f, 0f)
        var cx = start
        repeat(count) {
            // 半圆缺口：从缺口左缘沿弧底绕到右缘（y 向下，-180 扫角经过 90°=弧底）
            path.arcTo(
                androidx.compose.ui.geometry.Rect(cx - r, -r, cx + r, r),
                180f, -180f, false
            )
            cx += pitch
        }
        path.lineTo(size.width, 0f)
        path.lineTo(size.width, size.height)
        path.lineTo(0f, size.height)
        path.close()
        // 缺口内衬软投影（整体下移 2dp 的 7% 墨层）：每个缺口的下半出现弧形暗影
        val shadow = Path().apply {
            addPath(path, Offset(0f, 2.dp.toPx()))
        }
        drawPath(shadow, Ink.copy(alpha = 0.07f))
        drawPath(path, PaperColor, style = Fill)
    }
}

@Composable
private fun ReceiptHeader(dayKey: String, entries: List<DailyReceiptEntry>, bookName: String) {
    val context = LocalContext.current
    // 空流水防御：唯一调用口按「有流水的日期」分组，理论到不了这里；日期退回从 dayKey 解析
    val date = entries.firstOrNull()?.bill?.date
        ?: runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayKey)?.time
        }.getOrNull()
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
        if (date != null) {
            Text(
                DateUtils.formatDisplayDateWithWeekday(context, date),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Ink
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            stringResource(
                R.string.bill_receipt_book_line,
                bookName,
                pluralStringResource(R.plurals.bill_count, entries.size, entries.size)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = InkLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Spacer(Modifier.height(10.dp))
}

/** 小票一行：时间 + 分类 + 金额；第二行「－ 钱包 · 备注/商家」（转账显示钱包对）。 */
@Composable
private fun ReceiptItem(entry: DailyReceiptEntry, wallets: Map<Long, String>) {
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
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(bill.createdAt)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Ink
            )
            Spacer(Modifier.width(8.dp))
            Text(
                entry.categoryName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 占满剩余宽度把金额推到右缘，与表头「金 额」同一条线；中间空隙即弹性的
                modifier = Modifier.weight(1f)
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

/** 表头：时 间 / 项 目 / 金 额。透明 "00:00" 占位把「项 目」顶到与数据行的分类同一 x。 */
@Composable
private fun ReceiptTableHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box {
            Text(
                "00:00",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.Transparent
            )
            Text(
                stringResource(R.string.bill_receipt_col_time),
                style = MaterialTheme.typography.labelMedium,
                color = InkLight,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.bill_receipt_col_category),
            style = MaterialTheme.typography.labelMedium,
            color = InkLight,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.bill_receipt_col_amount),
            style = MaterialTheme.typography.labelMedium,
            color = InkLight
        )
    }
}

/** 小票行的说明行：商家/备注/地点在左（分类名下），钱包名在右（金额下）。转账显示「A → B」、右侧不重复。 */
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
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "00:00",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.Transparent
        )
        Spacer(Modifier.width(8.dp))
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
        if (bill.type != BillType.TRANSFER) {
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
}

/** 虚线分隔（油墨虚线，非实线——热敏小票的典型分栏）。 */
@Composable
internal fun DashedDivider() {
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
internal fun TotalRow(label: String, amount: Long, color: Color, bold: Boolean = false) {
    val context = LocalContext.current
    // 负号在货币符前（-¥1.00），与明细行的 prefix 口径一致；formatCurrency 会输出 ¥-1.00
    val prefix = if (amount < 0) "-" else ""
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
        Text(
            prefix + CurrencyUtils.formatCurrency(context, (if (amount < 0) -amount else amount).toMoney()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

/** 本月累计（内容丰富度：当日之外再给当月视角）。对齐口径与合计行一致：标签左、数字右。 */
@Composable
private fun ReceiptMonthSummary(monthExpense: Long, monthIncome: Long) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MonthSummaryRow(stringResource(R.string.bill_receipt_month_expense), monthExpense)
        MonthSummaryRow(stringResource(R.string.bill_receipt_month_income), monthIncome)
    }
}

@Composable
private fun MonthSummaryRow(label: String, amount: Long) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = InkLight,
            modifier = Modifier.weight(1f)
        )
        Text(
            CurrencyUtils.formatCurrency(context, amount.toMoney()),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = InkLight
        )
    }
}

/**
 * 条码：真·EAN-13。digits13 = 13 位编号（含校验位，内容是打印时间的年月日时分），
 * 用标准编码表逐模块绘制（101 + 左 6 位 + 01010 + 右 6 位 + 101，共 95 模块）——
 * 扫码器真的能扫出来。全部条形等高（起始/中间/结尾的定位条不另加长，排版更整洁）。
 * 装饰用途、非加密场景。
 */
@Composable
internal fun Barcode(digits13: String, display: String? = null) {
    val ean = remember(digits13) { ean13Bits(digits13) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
            val quiet = 9.dp.toPx()
            val moduleW = (size.width - 2 * quiet) / 95f
            for (i in ean.indices) {
                if (ean[i] != '1') continue
                drawRect(
                    Ink,
                    Offset(quiet + i * moduleW, 0f),
                    androidx.compose.ui.geometry.Size(moduleW.coerceAtLeast(1f), size.height)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            // 默认按 EAN-13 的印刷习惯分组：首位 + 6 位 + 6 位；汇总样式用 * 号框（Code39 风）
            display ?: "${digits13[0]} ${digits13.substring(1, 7)} ${digits13.substring(7)}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = InkLight,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** EAN-13 人读编号：内容 12 位由调用方给（打印时间的 yyyyMMddHHmm），校验位按标准公式计算，共 13 位。 */
internal fun ean13Digits(data12: String): String {
    val data = data12.take(12).padEnd(12, '0')
    val digits = data.map { it - '0' }
    // 校验位：第 2/4/…/12 位 ×3 + 其余 ×1，(10 − 和 mod 10) mod 10
    val check = (10 - (digits.withIndex().sumOf { (i, d) -> if (i % 2 == 1) d * 3 else d } % 10)) % 10
    return data + check.toString()
}

/** EAN-13 位串（95 模块，'1'=条）：由 13 位编号按标准表绘制。 */
internal fun ean13Bits(digits13: String): String {
    val all = digits13
    // 左侧 6 位按首位套 L/G 奇偶模板，右侧 6 位全 R（标准表：
    // G = L 反转再取反，R = L 只取反——两者不同，混用就扫不出来）
    val parity = listOf(
        "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
        "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"
    )
    val l = listOf(
        "0001101", "0011001", "0010011", "0111101", "0100011",
        "0110001", "0101111", "0111011", "0110111", "0001011"
    )
    val g = l.map { p -> p.reversed().map { if (it == '1') '0' else '1' }.joinToString("") }
    val r = l.map { p -> p.map { if (it == '1') '0' else '1' }.joinToString("") }
    val left = (1..6).joinToString("") { i ->
        val d = all[i] - '0'
        if (parity[all[0] - '0'][i - 1] == 'L') l[d] else g[d]
    }
    val right = (7..12).joinToString("") { i -> r[all[i] - '0'] }
    return "101" + left + "01010" + right + "101"
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
