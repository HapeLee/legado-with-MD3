package io.legado.app.data.entities.rule

/**
 * 子项弹性布局样式。
 *
 * 数据字段下沉 commonMain；`alignSelf()` 是纯 Int 常量映射（无 Android 依赖），一并下沉。
 * 依赖 `android.view.View` + FlexboxLayout 的 `apply(view)` 留在 app 侧扩展
 * （见 `FlexChildStyleAndroid.kt`）。
 */
data class FlexChildStyle(
    val layout_flexGrow: Float = 0F,
    val layout_flexShrink: Float = 1F,
    val layout_alignSelf: String = "auto",
    val layout_flexBasisPercent: Float = -1F,
    val layout_wrapBefore: Boolean = false,
    /** 自定义的内部水平对齐属性 **/
    val layout_justifySelf: String = "auto"
) {

    fun alignSelf(): Int {
        return when (layout_alignSelf) {
            "auto" -> -1
            "flex_start" -> 0
            "flex_end" -> 1
            "center" -> 2
            "baseline" -> 3
            "stretch" -> 4
            else -> -1
        }
    }

    companion object {
        val defaultStyle = FlexChildStyle()
    }

}
