package com.palmnote.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.palmnote.app.R
import com.palmnote.ui.theme.LocalIsDarkTheme
import com.palmnote.ui.theme.ModuleBill
import com.palmnote.ui.theme.ModuleItem
import com.palmnote.ui.theme.ModuleLife
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * 模块介绍页的一个功能点：文案 + 专属图标。
 */
private data class OnboardingFeature(@StringRes val textRes: Int, val icon: ImageVector)

/**
 * 模块介绍页：一个功能模块的图标、主题色、名称、简介与功能点卡片。
 * 统一左对齐版式：裸大图标在左、文案在右（用户指定的页面骨架）。
 */
private data class OnboardingModulePage(
    val icon: ImageVector,
    val tint: Color,
    /** 深色模式下的替代色（仅密码本需要：与库内 vaultTint 的深色值一致）。 */
    val tintDark: Color? = null,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val features: List<OnboardingFeature>
)

/** 模块页着色：密码本在深色模式下用库内 vaultTint 的浅紫，与真实界面一致。 */
@Composable
private fun pageTint(page: OnboardingModulePage): Color =
    if (LocalIsDarkTheme.current && page.tintDark != null) page.tintDark else page.tint

/** 安全页专属绿：数据安全语义色，与主题主色（青）区分。 */
private val SafetyGreen = Color(0xFF2E7D32)

private val modulePages = listOf(
    OnboardingModulePage(
        icon = Icons.Outlined.AccountBalanceWallet,
        tint = ModuleBill,
        titleRes = R.string.onboarding_mod_bill_title,
        bodyRes = R.string.onboarding_mod_bill_body,
        features = listOf(
            OnboardingFeature(R.string.onboarding_bill_f1, Icons.Outlined.AccountBalance),
            OnboardingFeature(R.string.onboarding_bill_f2, Icons.Outlined.CalendarMonth),
            OnboardingFeature(R.string.onboarding_bill_f3, Icons.Outlined.PhotoCamera)
        )
    ),
    OnboardingModulePage(
        icon = Icons.Outlined.Inventory2,
        tint = ModuleItem,
        titleRes = R.string.onboarding_mod_item_title,
        bodyRes = R.string.onboarding_mod_item_body,
        features = listOf(
            OnboardingFeature(R.string.onboarding_item_f1, Icons.Outlined.Inventory2),
            OnboardingFeature(R.string.onboarding_item_f2, Icons.Outlined.Timeline),
            OnboardingFeature(R.string.onboarding_item_f3, Icons.Outlined.Notifications)
        )
    ),
    OnboardingModulePage(
        icon = Icons.Outlined.Favorite,
        tint = ModuleLife,
        titleRes = R.string.onboarding_mod_life_title,
        bodyRes = R.string.onboarding_mod_life_body,
        features = listOf(
            OnboardingFeature(R.string.onboarding_life_f1, Icons.Outlined.Flight),
            OnboardingFeature(R.string.onboarding_life_f2, Icons.Outlined.FavoriteBorder),
            OnboardingFeature(R.string.onboarding_life_f3, Icons.Outlined.Timer)
        )
    ),
    OnboardingModulePage(
        icon = Icons.Outlined.Lock,
        tint = Color(0xFF6750A4),
        tintDark = Color(0xFFD0BCFF),
        titleRes = R.string.onboarding_mod_vault_title,
        bodyRes = R.string.onboarding_mod_vault_body,
        features = listOf(
            OnboardingFeature(R.string.onboarding_vault_f1, Icons.Outlined.AutoAwesome),
            OnboardingFeature(R.string.onboarding_vault_f2, Icons.Outlined.Fingerprint),
            OnboardingFeature(R.string.onboarding_vault_f3, Icons.Outlined.EnhancedEncryption)
        )
    )
)

/**
 * 数据安全须知页的一条：图标 + 标题 + 正文。
 */
private data class OnboardingSafetyPoint(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int
)

private val safetyPoints = listOf(
    OnboardingSafetyPoint(
        icon = Icons.Outlined.Security,
        titleRes = R.string.onboarding_local_title,
        bodyRes = R.string.onboarding_local_body
    ),
    OnboardingSafetyPoint(
        icon = Icons.Outlined.Key,
        titleRes = R.string.onboarding_pin_title,
        bodyRes = R.string.onboarding_pin_body
    ),
    OnboardingSafetyPoint(
        icon = Icons.Outlined.DeleteForever,
        titleRes = R.string.onboarding_uninstall_title,
        bodyRes = R.string.onboarding_uninstall_body
    ),
    OnboardingSafetyPoint(
        icon = Icons.Outlined.Backup,
        titleRes = R.string.onboarding_backup_title,
        bodyRes = R.string.onboarding_backup_body
    )
)

/**
 * 首次启动引导页：6 页横滑（HorizontalPager），以功能介绍为主、数据安全收尾。
 *
 * PalmNote 专属「便签纸」视觉语言：
 * - 整页随页色：当前页的主题色以低透明度铺满全屏（含状态栏与底部按钮区），
 *   横滑时颜色在相邻页之间平滑渐变
 * - 模块页统一左对齐版式：裸大图标在左、标题与简介在右，功能点卡片列表在下
 * - 欢迎页为便签卡品牌 emblem；安全页为实心绿盾徽分隔线列表
 * - 按钮与页点指示器随页强调色，与所在页面的模块色一致
 *
 * 按钮语义各司其职：
 * - 非最后一页：主按钮"下一步"翻到下一页；副按钮"跳过"直接结束引导进主界面。
 * - 最后一页：主按钮"开始使用"结束引导；跳过位置渲染同款空按钮占位保持布局一致。
 * 结束引导统一走 [onFinish]，由调用方持久化"已看过引导"。
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pageCount = modulePages.size + 2
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pageCount - 1
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary

    // 页面强调色：欢迎页用主色，安全页用专属绿，模块页用对应模块主题色。
    // （调用 @Composable 的 pageTint，故自身也须标记 @Composable）
    @Composable
    fun accentFor(page: Int): Color = when {
        page <= 0 -> primary
        page >= pageCount - 1 -> SafetyGreen
        else -> pageTint(modulePages[page - 1])
    }

    // 整页随页色：以强调色的低透明度铺满；翻页时在相邻两页间线性插值，全屏平滑过渡。
    @Composable
    fun washFor(page: Int): Color = when {
        page <= 0 -> background
        else -> lerp(background, accentFor(page), 0.10f)
    }

    val fraction = pagerState.currentPageOffsetFraction
    val neighbor = pagerState.currentPage + if (fraction > 0f) 1 else -1
    val blend = abs(fraction)
    val wash = lerp(
        washFor(pagerState.currentPage),
        washFor(neighbor.coerceIn(0, pageCount - 1)),
        blend
    )
    // 按钮/页点随页强调色，与所在页面的模块色统一
    val accent = lerp(
        accentFor(pagerState.currentPage),
        accentFor(neighbor.coerceIn(0, pageCount - 1)),
        blend
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = wash
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when {
                    page == 0 -> OnboardingWelcomePage()
                    page <= modulePages.size -> OnboardingModulePageContent(modulePages[page - 1])
                    else -> OnboardingSafetyPage()
                }
            }

            OnboardingDots(pageCount = pageCount, currentPage = pagerState.currentPage, activeColor = accent)

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
            ) {
                Text(
                    text = stringResource(
                        if (isLastPage) R.string.onboarding_start else R.string.onboarding_next
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            if (!isLastPage) {
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 占位与「跳过」按钮同高：渲染同款空 TextButton（不可点、无内容），
                // 保证「开始使用」主按钮的垂直位置与其他页完全一致，不受组件度量影响
                TextButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {}
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 欢迎页：便签卡品牌 emblem（白卡 + 便签三线）+ 欢迎标题与副标题；
 * 左上角为随主色的应用图标水印。
 */
@Composable
private fun OnboardingWelcomePage() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PagePaddingH, vertical = PagePaddingV)
    ) {
        OnboardingCornerIcon(tint = primary)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NoteCardEmblem(color = primary)

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 模块介绍页：统一左对齐版式——裸大图标在左，标题与简介在右，功能点卡片列表在下。
 */
@Composable
private fun OnboardingModulePageContent(page: OnboardingModulePage) {
    val tint = pageTint(page)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PagePaddingH, vertical = PagePaddingV)
    ) {
        OnboardingCornerIcon(tint = tint)
        // 整块内容垂直居中；介绍行固定高度（正文均不超过 2 行），四页介绍文字
        // 的高度与位置严格一致，不随正文折行数差异而上下浮动
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(84.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(page.bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                page.features.forEach { feature ->
                    FeatureCard(
                        tint = tint,
                        icon = feature.icon,
                        textRes = feature.textRes,
                    )
                }
            }
        }
    }
}

/**
 * 数据安全须知页：标题 + 实心绿盾徽行（分隔线列表），与模块页的卡片语言区分；
 * 左上角为随页色的应用图标水印。
 */
@Composable
private fun OnboardingSafetyPage() {
    val green = SafetyGreen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PagePaddingH, vertical = PagePaddingV)
    ) {
        OnboardingCornerIcon(tint = green)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                tint = green,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.onboarding_safety_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            safetyPoints.forEachIndexed { index, point ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(green),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = point.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(point.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(point.bodyRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index != safetyPoints.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * 欢迎页便签卡 emblem：一张立起的便签卡（白卡 + 圆点 + 便签三线），PalmNote 的品牌记号。
 */
@Composable
private fun NoteCardEmblem(color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.25f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "PalmNote",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
            Spacer(Modifier.height(14.dp))
            listOf(88.dp, 64.dp, 44.dp).forEach { lineWidth ->
                Box(
                    modifier = Modifier
                        .padding(vertical = 3.dp)
                        .size(width = lineWidth, height = 5.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(color.copy(alpha = 0.35f))
                )
            }
        }
    }
}

/**
 * 功能点卡片：模块色底圆角卡（12% 透明度模块色，非白底）+ 实心模块色图标芯片 + 文案；
 * 四个模块页统一此样式，卡片底色随各自模块主题色。
 */
@Composable
private fun FeatureCard(tint: Color, icon: ImageVector, @StringRes textRes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 引导页内容区自身的内边距：欢迎 / 模块 / 安全三种页面统一，水印定位据此换算。 */
private val PagePaddingH = 24.dp
private val PagePaddingV = 16.dp

/**
 * 左上角品牌水印：与隐私页品牌图标**同一位置、同一高度**。
 *
 * 可见图形 22dp，其左缘 / 上缘距屏幕左边与状态栏底边各 27dp —— 与隐私页品牌行逐像素一致
 * （隐私页内容左内边距 20dp + 36dp 方形底框内 22dp 图形的 7dp 内缩 = 27dp；
 * 状态栏下留白 20dp + 7dp 内缩 = 27dp）。故隐私页 → 引导页切换时品牌记号不跳位。
 */
private val CornerIconGlyph = 22.dp

/**
 * 水印图形所在的容器尺寸。`ic_launcher_foreground_bw` 的 viewport 是 108dp，图形只占
 * 居中的 x/y 35~71（1/3，36dp），故容器须取图形的 3 倍，图形才会以 22dp 渲染。
 *
 * 容器比图形大且必须突破父级约束：普通 `size` 会被父级钳到图形尺寸，图形只剩 1/3，
 * 因此 Icon 上必须用 `requiredSize`。
 */
private val CornerIconBox = CornerIconGlyph * 3

/** 水印可见图形的目标位置，相对内容区左上角（即屏幕左边 / 状态栏底边）。 */
private val CornerIconLeft = 27.dp
private val CornerIconTop = 27.dp

/**
 * 引导页左上角的应用图标水印：仅图标本体（启动前景图形），无背景容器，
 * 颜色随所在页面（欢迎页主色、模块页模块色、安全页专属绿）。
 *
 * 外层 Box 就是「可见图形」的 22dp 方框，故定位即图形定位；图形自带的 1/3 空白由
 * 撑到 66dp 的 Icon 容器吃掉，Box 不裁剪（无 clip），不会截断图形。
 */
@Composable
private fun BoxScope.OnboardingCornerIcon(tint: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = CornerIconLeft - PagePaddingH, y = CornerIconTop - PagePaddingV)
            .size(CornerIconGlyph),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_launcher_foreground_bw),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.requiredSize(CornerIconBox)
        )
    }
}

/**
 * 页点指示器：当前页为加宽的胶囊点（随页强调色），其余为小圆点（surfaceVariant），宽度带动画过渡。
 */
@Composable
private fun OnboardingDots(pageCount: Int, currentPage: Int, activeColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            val width by animateDpAsState(
                targetValue = if (active) 20.dp else 8.dp,
                label = "onboardingDotWidth"
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (active) {
                            activeColor
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}
