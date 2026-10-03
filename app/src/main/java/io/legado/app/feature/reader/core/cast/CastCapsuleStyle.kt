package io.legado.app.feature.reader.core.cast

import com.google.gson.annotations.SerializedName
import kotlin.math.max

/**
 * 一颗胶囊的显示样式。数值全部用百分比表达，正文字号怎么变，比例关系都不变。
 *
 * 圆角/头像圆角：100 = 现行那颗胶囊与圆形头像，0 = 矩形/直角。
 * 头像大小：100 = 现行那格（胶囊高 × 0.66），拉大撑满胶囊高，拉小只剩一个点。
 * 位移以「胶囊高」为单位，所以字号变大时头像位移同步变大，不会在小字号下跑出胶囊。
 */
data class CastCapsuleStyle(
    @SerializedName("cornerRadius") val cornerRadius: Int = FULL,
    /** 底色，0 = 跟随主题（沿用正文反色派生的那层淡底）。 */
    @SerializedName("bgColor") val bgColor: Int = 0,
    @SerializedName("bgColorNight") val bgColorNight: Int = 0,
    @SerializedName("bgImage") val bgImage: String = "",
    @SerializedName("bgImageNight") val bgImageNight: String = "",
    @SerializedName("avatarRadius") val avatarRadius: Int = FULL,
    /** 头像直径相对现行那格（胶囊高 × avatarRatio）的百分比。 */
    @SerializedName("avatarScale") val avatarScale: Int = FULL,
    @SerializedName("avatarDx") val avatarDx: Int = 0,
    @SerializedName("avatarDy") val avatarDy: Int = 0,
    /** 以下三个开关只作用于角色胶囊：关掉的那一栏连宽度一起省掉，不是画了再藏。 */
    @SerializedName("showAvatar") val showAvatar: Boolean = true,
    @SerializedName("showName") val showName: Boolean = true,
    @SerializedName("showPool") val showPool: Boolean = true,
) {

    /** 胶囊底板的圆角半径：100 时正好是高的一半（现在的样子）。 */
    fun cornerPx(height: Float): Float =
        height / 2f * (cornerRadius.coerceIn(0, FULL) / FULL.toFloat())

    /** 头像的圆角半径：100 = 圆，0 = 直角方块。 */
    fun avatarCornerPx(diameter: Float): Float =
        diameter / 2f * (avatarRadius.coerceIn(0, FULL) / FULL.toFloat())

    /** 头像直径：唯一取法，测量侧与绘制侧都从这里拿，否则宽度会量得和画的不一样。 */
    fun avatarDiameter(height: Float): Float =
        height * CastCapsuleGeometry.avatarRatio *
            (avatarScale.coerceIn(AVATAR_SCALE_MIN, AVATAR_SCALE_MAX) / FULL.toFloat())

    /**
     * 头像左沿相对胶囊左沿的偏移，夹到「头像右沿不出胶囊」。
     *
     * 角色那颗**只有一种落点**：按整格垂直居中的那条线，与 showName / showPool 无关——
     * 胶囊是行内元素、左沿不动，头像必须一直在同一个位置。
     * 共用这一口径的三处：测量侧 [CastCapsuleGeometry.widthOf]（经 textLeftPx）、
     * 绘制侧 ReaderCanvasSurface.drawRoleCast、预览侧 CastCapsuleStyleScreen.PreviewBoard。
     */
    fun avatarLeft(height: Float): Float {
        val diameter = avatarDiameter(height)
        return ((height - diameter) / 2f + height * avatarDx / SHIFT_FULL.toFloat())
            .coerceIn(0f, max(0f, height - diameter))
    }

    /**
     * 头像中心相对垂直中心的偏移。
     *
     * 角色那颗与未分配占位那颗共用 [avatarLeft]：两颗都是正方形底板（见
     * [CastCapsuleGeometry.widthOf] 的只显头像分支与 `placeholderWidthPx`），
     * 图标必须落在同一格中心，各走一套落点就会一高一低。
     */
    fun avatarCenterOffset(height: Float): Float {
        val room = (height - avatarDiameter(height)) / 2f
        return (height * avatarDy / SHIFT_FULL.toFloat()).coerceIn(-room, room)
    }

    /** 深浅模式各自的底色与底图（0 / 空串 = 跟随主题、无底图）。 */
    fun backgroundColor(isNight: Boolean): Int =
        if (isNight) bgColorNight.takeIf { it != 0 } ?: bgColor else bgColor

    fun backgroundImage(isNight: Boolean): String =
        if (isNight) bgImageNight.takeIf { it.isNotEmpty() } ?: bgImage else bgImage

    /** 这一份跟「从没设过」完全一致：走默认绘制路径。 */
    fun isDefault(): Boolean = this == Default

    companion object {
        const val FULL = 100
        const val SHIFT_FULL = 50

        /** 头像大小可调区间：拉满是刚好填满胶囊高，拉小只剩一个点。 */
        const val AVATAR_SCALE_MIN = 30
        const val AVATAR_SCALE_MAX = 150

        val Default = CastCapsuleStyle()

        fun safe(style: CastCapsuleStyle?): CastCapsuleStyle = style ?: Default
    }
}
