package io.legado.app.help.config

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.AiToolGroup

/**
 * AI 工具分组的开关。
 *
 * 只记录「被用户关掉」的分组，默认空 = 全部开启。这样以后新增分组时
 * 老用户不需要重新设置一次，也不会因为升级突然少了工具。
 */
object AiToolGroupSettings {

    fun disabledGroups(): Set<AiToolGroup> =
        AiToolGroup.parse(AppConfigStore.getString(PreferKey.aiDisabledToolGroups))

    fun isEnabled(group: AiToolGroup): Boolean = group !in disabledGroups()

    fun setEnabled(group: AiToolGroup, enabled: Boolean) {
        val disabled = disabledGroups().toMutableSet()
        if (enabled) disabled.remove(group) else disabled.add(group)
        AppConfigStore.putString(
            PreferKey.aiDisabledToolGroups,
            disabled.map { it.key }.sorted().joinToString(",")
        )
    }
}
