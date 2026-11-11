package io.legado.app.ui.widget.components.menu

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/**
 * 入口包的那层唯一可点节点默认是矩形。
 *
 * 触发内容自己画圆角容器（图标按钮、方卡、胶囊）时，M3 的水波纹会从四个角溢出去，
 * 看起来就是「按钮没有圆角」。形状只能由调用方给出——入口拿不到 trigger 内部的 shape。
 */
internal fun Modifier.clipTriggerShape(shape: Shape?): Modifier =
    if (shape == null) this else clip(shape)
