package io.legado.app.data.entities.rule

import android.view.View
import com.google.android.flexbox.FlexboxLayout

/**
 * [FlexChildStyle] 的 Android 特有扩展：把样式应用到 View 的 Flexbox LayoutParams。
 *
 * [FlexChildStyle] 本体已下沉 :core:data（数据字段 + `alignSelf()` 纯 Int 映射），
 * 但 `apply(view)` 依赖 `android.view.View` 与 FlexboxLayout，无法进 commonMain，
 * 故作为 Android 侧扩展保留。
 */
fun FlexChildStyle.apply(view: View) {
    val lp = view.layoutParams as FlexboxLayout.LayoutParams
    lp.flexGrow = layout_flexGrow
    lp.flexShrink = layout_flexShrink
    lp.alignSelf = alignSelf()
    lp.flexBasisPercent = layout_flexBasisPercent
    lp.isWrapBefore = layout_wrapBefore
}
