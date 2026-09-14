package io.legado.app.ui.book.read

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin

/**
 * 文本选择浮层共用的进出动画规格。
 *
 * [TextActionSelectionMenu] 与 [MarkingSelectionMenu] 会在同一次选区里互相切换（快捷菜单点“标记”
 * 后换成标记卡片），两边必须共用同一套时长和缩放比例，否则切换时会出现两套节奏叠加的割裂感。
 * 各菜单只传自己的缩放原点：居中的小菜单用 [TransformOrigin.Center]，贴着选区展开的标记卡片
 * 用朝向选区的那条边。
 */
internal object SelectionMenuMotion {

    /** 淡入时长。 */
    private const val FADE_IN_DURATION_MILLIS = 360

    /** 缩放入场时长。 */
    private const val SCALE_IN_DURATION_MILLIS = 400

    /**
     * 出场总时长，取淡出与缩放中较长的一个。
     *
     * 主动关闭浮层时（例如 [MarkingSelectionMenu] 的 `requestDismiss`）要按它来决定延迟多久才真正
     * 摘掉浮层：早于该时长会把出场动画截断。
     */
    const val EXIT_DURATION_MILLIS = 320

    private const val SCALE_OUT_DURATION_MILLIS = 280
    private const val ENTER_SCALE = 0.94f
    private const val EXIT_SCALE = 0.96f

    fun enter(transformOrigin: TransformOrigin = TransformOrigin.Center): EnterTransition =
        fadeIn(animationSpec = tween(FADE_IN_DURATION_MILLIS)) +
                scaleIn(
                    animationSpec = tween(SCALE_IN_DURATION_MILLIS),
                    initialScale = ENTER_SCALE,
                    transformOrigin = transformOrigin,
                )

    fun exit(transformOrigin: TransformOrigin = TransformOrigin.Center): ExitTransition =
        fadeOut(animationSpec = tween(EXIT_DURATION_MILLIS)) +
                scaleOut(
                    animationSpec = tween(SCALE_OUT_DURATION_MILLIS),
                    targetScale = EXIT_SCALE,
                    transformOrigin = transformOrigin,
                )
}
