package io.legado.app.ui.book.read.sheet

import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent

/**
 * 标识页眉/页脚的 6 个位置之一，用于自定义模板编辑。
 *
 * 每个目标集中管理：
 *  - [tipValue]：该位置在 [ReadBookConfig] 中保存的 tip 类型常量
 *  - [customTemplate]：自定义模板字符串的只读 getter（读取 [ReadBookConfig]）
 *  - [configUpdate]：持久化时使用的 [ConfigUpdate] 工厂
 *  - [applyTemplate]：把模板保存到 [ReadBookConfig] 并派发 [ReadBookIntent.UpdateConfig]
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

    /** 该位置当前选中的 tip 类型（如 [ReadBookConfig.tipCustom] / [ReadBookConfig.tipBookName] 等）。 */
    val tipValue: Int
        get() = when (this) {
            HEADER_LEFT -> ReadBookConfig.tipHeaderLeft
            HEADER_MIDDLE -> ReadBookConfig.tipHeaderMiddle
            HEADER_RIGHT -> ReadBookConfig.tipHeaderRight
            FOOTER_LEFT -> ReadBookConfig.tipFooterLeft
            FOOTER_MIDDLE -> ReadBookConfig.tipFooterMiddle
            FOOTER_RIGHT -> ReadBookConfig.tipFooterRight
        }

    val customTemplate: String
        get() = when (this) {
            HEADER_LEFT -> ReadBookConfig.customTipHeaderLeft
            HEADER_MIDDLE -> ReadBookConfig.customTipHeaderMiddle
            HEADER_RIGHT -> ReadBookConfig.customTipHeaderRight
            FOOTER_LEFT -> ReadBookConfig.customTipFooterLeft
            FOOTER_MIDDLE -> ReadBookConfig.customTipFooterMiddle
            FOOTER_RIGHT -> ReadBookConfig.customTipFooterRight
        }
}
