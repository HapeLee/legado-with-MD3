package io.legado.app.ui.book.read

/**
 * 正文/页眉/页脚对系统栏的避让策略，对照原版 PageView 占位 View 语义（配置驱动）：
 *
 * - 状态栏占位 = vwStatusBar：高度恒为状态栏高，`isGone = hideStatusBar || 多窗口`；
 *   导航栏按所在边避让，隐藏时移除避让。两者都不跟随系统栏
 *   瞬时可见性——菜单打开时系统栏只是 overlay，正文不重排（原版 LAYOUT_HIDE_NAVIGATION
 *   的布局稳定语义）。
 * - 刘海避让 = paddingDisplayCutouts（填充刘海区域）：开启时按 displayCutout insets
 *   避让左右/下，顶部只在状态栏占位消失时避让（占位本身已盖住刘海）。
 * - IME 不参与正文避让（原版 vwNavigationBar 的 insets 监听显式跳过 ime）。
 *
 * 高度入参必须是"忽略可见性"的平台采样（getInsetsIgnoringVisibility），隐藏/显隐动画
 * 期间恒定——本策略因此天然事件化：只有配置、多窗口或屏幕形状变化才改写结果，
 * 系统栏显隐动画与菜单开关不再触发整章重排。
 */
object ReaderContentAvoidancePolicy {

    /** 平台侧采样的系统栏与刘海原始尺寸（px）。 */
    data class SystemBarInsets(
        val statusBarTopPx: Int = 0,
        val navigationBarBottomPx: Int = 0,
        val cutoutLeftPx: Int = 0,
        val cutoutTopPx: Int = 0,
        val cutoutRightPx: Int = 0,
        val cutoutBottomPx: Int = 0,
        val navigationBarLeftPx: Int = 0,
        val navigationBarRightPx: Int = 0,
    )

    fun padding(
        insets: SystemBarInsets,
        hideStatusBar: Boolean,
        hideNavigationBar: Boolean,
        paddingDisplayCutouts: Boolean,
        inMultiWindow: Boolean,
    ): ReaderPadding {
        val statusPlaceholderGone = hideStatusBar || inMultiWindow
        val navPlaceholderGone = hideNavigationBar || inMultiWindow
        val cutout = if (paddingDisplayCutouts) insets else SystemBarInsets()
        return ReaderPadding(
            left = maxOf(cutout.cutoutLeftPx, if (navPlaceholderGone) 0 else insets.navigationBarLeftPx),
            top = (if (statusPlaceholderGone) 0 else insets.statusBarTopPx) +
                (if (statusPlaceholderGone) cutout.cutoutTopPx else 0),
            right = maxOf(cutout.cutoutRightPx, if (navPlaceholderGone) 0 else insets.navigationBarRightPx),
            bottom = (if (navPlaceholderGone) 0 else insets.navigationBarBottomPx) +
                cutout.cutoutBottomPx,
        )
    }
}
