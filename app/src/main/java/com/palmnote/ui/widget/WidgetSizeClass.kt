package com.palmnote.ui.widget

/**
 * 组件的**尺寸档**：目录里只做这四档（4×2 / 2×2 / 4×1 / 4×4）。
 *
 * 2026-10-21 定「只做几档、其余靠桌面编辑调整」；v37 收敛后去掉 2×1，每族固定四档
 * （见 `WidgetPin` 的 40 个形态）。这四档是我们**保证好看**的档位，其余尺寸由桌面自由拖拽，
 * 组件归到最近的一档重排（不做逐像素的自适应，那在 RemoteViews 里既做不到也不值得）。
 *
 * 归并使用**宽高两个阈值**而不是「离哪个设计尺寸最近」：桌面在 Android 12 以下给的
 * `OPTION_APPWIDGET_MIN/MAX_*` 常常是「横屏最宽 × 竖屏最高」这种组合值，按最近邻会跳到对角档位；
 * 阈值法对这类脏数据更稳（只有一处不达标就只降一档）。
 */
internal enum class WidgetSizeClass(
    /** 目录里展示的格子标签，如 `4×2`。 */
    val cells: String,
    /** 设计宽（dp），取自 `docs/widgets/spec.html` 的网格：1 格 82dp。 */
    val designWidthDp: Int,
    /** 设计高（dp），1 格 74dp。 */
    val designHeightDp: Int
) {
    /** 4×2：横长卡，内容可以左右分两栏。 */
    WIDE("4×2", 340, 148),

    /** 2×2：方卡，只能纵向堆叠。 */
    SQUARE("2×2", 160, 160),

    /** 4×1：一整条，纵向只有一行。 */
    BAR("4×1", 340, 74),

    /** 2×1：小矮条。 */
    CHIP("2×1", 160, 74),

    /** 4×4：大卡，可以放主图 + 明细。 */
    TALL("4×4", 340, 360);

    /** 只有一行高：布局必须换成横排，纵向堆叠一定溢出。 */
    val isRow: Boolean get() = this == BAR || this == CHIP

    companion object {
        /** 3 格宽（245dp）以上算「宽」，以下算「窄」（2 格 160dp）。 */
        private const val WIDE_MIN_WIDTH_DP = 210

        /** 1 格高（74dp）；留到 100dp 都归单行档，2 格（148dp）才开始堆叠。 */
        private const val ROW_MAX_HEIGHT_DP = 100

        /** 3 格高（220dp）以上算大卡；4×3（268dp）也落这一档。 */
        private const val TALL_MIN_HEIGHT_DP = 220

        /**
         * 按摆放尺寸归档。宽高都可能出现 0 或异常小值（个别桌面 resize 过程中会给），
         * 由调用方用各自的设计尺寸兜底后再进来。
         */
        fun of(widthDp: Int, heightDp: Int): WidgetSizeClass {
            val wide = widthDp >= WIDE_MIN_WIDTH_DP
            return when {
                heightDp < ROW_MAX_HEIGHT_DP -> if (wide) BAR else CHIP
                heightDp >= TALL_MIN_HEIGHT_DP -> TALL
                wide -> WIDE
                else -> SQUARE
            }
        }
    }
}
