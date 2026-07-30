package io.legado.app.ui.book.read.sheet

import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadSheetConfigUiState

/**
 * 标识页眉/页脚的 6 个位置之一，用于自定义模板编辑。
 *
 * 每个目标集中管理：
 *  - [tipValueOf]：该位置当前选中的 tip 类型（从 UiState 快照读，不读可变全局）
 *  - [customTemplateOf]：该位置的自定义模板字符串（同上）
 *  - [configUpdate]：持久化时使用的 [ConfigUpdate] 工厂
 *  - [applyTemplate]：派发 [ReadBookIntent.UpdateConfig] 走 gateway 持久化
 *
 * 这样的设计让 6 个位置的逻辑只在一处声明，避免散落各处的 if 链。
 */
internal enum class CustomTipTarget {
    HEADER_LEFT,
    HEADER_MIDDLE,
    HEADER_RIGHT,
    FOOTER_LEFT,
    FOOTER_MIDDLE,
    FOOTER_RIGHT;

    /** 通过 ViewModel 派发 [ConfigUpdate]，由 gateway 管线持久化到 [ReadBookConfig]。 */
    fun applyTemplate(template: String, onIntent: (ReadBookIntent) -> Unit) {
        onIntent(ReadBookIntent.UpdateConfig(configUpdate(template)))
    }

    private fun configUpdate(template: String): ConfigUpdate = when (this) {
        HEADER_LEFT -> ConfigUpdate.CustomTipHeaderLeft(template)
        HEADER_MIDDLE -> ConfigUpdate.CustomTipHeaderMiddle(template)
        HEADER_RIGHT -> ConfigUpdate.CustomTipHeaderRight(template)
        FOOTER_LEFT -> ConfigUpdate.CustomTipFooterLeft(template)
        FOOTER_MIDDLE -> ConfigUpdate.CustomTipFooterMiddle(template)
        FOOTER_RIGHT -> ConfigUpdate.CustomTipFooterRight(template)
    }

    /** 该位置当前选中的 tip 类型（tipNone / tipCustom / tipBookName 等）。 */
    fun tipValueOf(config: ReadSheetConfigUiState): Int = when (this) {
        HEADER_LEFT -> config.tipHeaderLeft
        HEADER_MIDDLE -> config.tipHeaderMiddle
        HEADER_RIGHT -> config.tipHeaderRight
        FOOTER_LEFT -> config.tipFooterLeft
        FOOTER_MIDDLE -> config.tipFooterMiddle
        FOOTER_RIGHT -> config.tipFooterRight
    }

    fun customTemplateOf(config: ReadSheetConfigUiState): String = when (this) {
        HEADER_LEFT -> config.customTipHeaderLeft
        HEADER_MIDDLE -> config.customTipHeaderMiddle
        HEADER_RIGHT -> config.customTipHeaderRight
        FOOTER_LEFT -> config.customTipFooterLeft
        FOOTER_MIDDLE -> config.customTipFooterMiddle
        FOOTER_RIGHT -> config.customTipFooterRight
    }
}
