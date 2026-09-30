package io.legado.app.feature.reader.core.cast

import kotlin.math.max

/**
 * 角色胶囊的行内几何（测量侧与绘制侧共用同一组比例，保证宽度不溢出）。
 * 全部以「正文字号 fontSizePx」为基准 → 胶囊随字号设置自动缩放。
 */
object CastCapsuleGeometry {
    /** 胶囊高 = 字号 × 1.35（略高于字身，行内垂直居中）。 */
    const val heightRatio = 1.35f

    /** 标签字号 = 字号 × 0.8。 */
    const val textScale = 0.8f

    /** 头像直径 = 胶囊高 × 0.66（缩小避免顶出圆角带）；样式里的 avatarScale 再乘一次。 */
    const val avatarRatio = 0.66f

    /** 头像与标签间距 = 胶囊高 × 0.14。 */
    const val gapRatio = 0.14f

    /** 左右内边距 = 胶囊高 × 0.24。 */
    const val padRatio = 0.24f

    /** 池标签（名字后小字，无括号）字号 = 标签字号 × 0.75。 */
    const val poolScale = 0.75f

    /** 名字与池小字间距 = 胶囊高 × 0.08。 */
    const val poolGapRatio = 0.08f

    /** 占位胶囊尾部呼吸 = 胶囊高 × 0.16。 */
    const val tailRatio = 0.16f

    /** 变声器标记（均衡器小竖条）宽 = 胶囊高 × 0.34。 */
    const val effectRatio = 0.34f

    /** 池小字/名字与变声器标记的间隙 = 胶囊高 × 0.1。 */
    const val effectGapRatio = 0.1f

    fun heightPx(fontSizePx: Float): Float = fontSizePx * heightRatio

    /** 头像块之后文字该从哪儿起（头像大小与那个横向位移都会把文字顶过去；头像关掉时直接是内边距）。 */
    fun textLeftPx(heightPx: Float, style: CastCapsuleStyle): Float =
        if (!style.showAvatar) {
            heightPx * padRatio
        } else {
            max(
                heightPx * padRatio,
                style.avatarLeft(heightPx) + style.avatarDiameter(heightPx) + heightPx * gapRatio,
            )
        }

    /** 名字之后池小字该从哪儿起（名字关掉时小字就顶到名字那个位置）。 */
    fun poolLeftPx(
        heightPx: Float,
        style: CastCapsuleStyle,
        nameWidthPx: Float,
    ): Float = textLeftPx(heightPx, style) +
        (if (style.showName) nameWidthPx + heightPx * poolGapRatio else 0f)

    /**
     * 配乐胶囊文案：`♪ 声音池名`。
     *
     * 池名就是场景（用户口径：本来声音池就是场景，不再另设场景预设），所以胶囊上必须
     * 看得见当前段用的是哪个池，而不是只有一个孤零零的 ♪。池名为空时只留 ♪。
     */
    fun bgmLabel(poolName: String): String =
        if (poolName.isBlank()) BGM_NOTE else "$BGM_NOTE $poolName"

    /** 配乐胶囊宽：与角色胶囊同款（无头像、无池小字），只是文案带 ♪ 前缀。 */
    fun bgmWidthPx(fontSizePx: Float, labelWidthPx: Float): Float =
        widthOf(fontSizePx, labelWidthPx, 0f, withAvatar = false)

    private const val BGM_NOTE = "♪"

    /**
     * 只剩头像：名字与池小字都不显示（池小字还要真的有内容），也没有变声器标记。
     *
     * 测量侧与绘制侧共用这一条判定，否则宽度按正方形量、头像按有文字排，两边就错开了。
     */
    fun isAvatarOnly(
        style: CastCapsuleStyle,
        hasPoolText: Boolean,
        withEffect: Boolean,
    ): Boolean = style.showAvatar && !style.showName &&
        !(style.showPool && hasPoolText) && !withEffect

    /**
     * 胶囊总宽 = 2×内边距 +（可选头像+间隙）+ 名字宽 +（可选池小字宽）+（可选变声器标记）+ 占位尾部。
     * [labelWidthPx] 现在只是名字文本宽（池小字单独传），不再有括号留白。
     * 样式里关掉的那一栏按 0 计入，量出来的宽度就是画出来的宽度。
     */
    fun widthOf(
        fontSizePx: Float,
        labelWidthPx: Float,
        poolWidthPx: Float,
        withAvatar: Boolean,
        withEffect: Boolean = false,
        style: CastCapsuleStyle = CastCapsuleStyle.Default,
    ): Float {
        val h = heightPx(fontSizePx)
        val hasAvatar = withAvatar && style.showAvatar
        // 全部关掉只剩头像时，这颗胶囊就是一块正方形：圆角 0 是正方形，拉满是圆，中间是圆角矩形。
        if (hasAvatar && isAvatarOnly(style, poolWidthPx > 0f, withEffect)) return h
        val lead = if (hasAvatar) textLeftPx(h, style) - h * padRatio else 0f
        val name = if (style.showName) labelWidthPx else 0f
        val pool = if (style.showPool && poolWidthPx > 0f) {
            h * poolGapRatio + poolWidthPx
        } else {
            0f
        }
        val tail = if (withAvatar) 0f else h * tailRatio
        val badge = if (withEffect) h * (effectRatio + effectGapRatio) else 0f
        return h * padRatio * 2f + lead + name + pool + badge + tail
    }

    /**
     * 未分配占位胶囊宽：里面只有一个人形图标，没有任何文字（点它就是给这句分配角色）。
     *
     * 图标占的是头像那一格，所以头像位移同样把它顶出去，右边留白跟着加宽。
     */
    fun placeholderWidthPx(
        fontSizePx: Float,
        style: CastCapsuleStyle = CastCapsuleStyle.Default,
    ): Float {
        val h = heightPx(fontSizePx)
        val iconRight = style.avatarLeft(h) + style.avatarDiameter(h)
        return h * padRatio + max(h * padRatio, iconRight - h * padRatio) + h * padRatio
    }
}
