package io.legado.app.ui.book.read

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.help.config.AppConfigStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 阅读页 AI 悬浮小窗的状态宿主：只读取已启用的技能列表供快速选择。
 * 不持有会话、不访问网络；真正的对话交给 AiChat 页，避免与阅读页 ViewModel 的职责耦合。
 *
 * 菜单里显示哪些技能由 [PreferKey.aiQuickSkillIds] 决定：
 * - 偏好不存在（从未设置过）→ 显示全部启用技能（兼容老行为）；
 * - 偏好存在（含空串，表示用户故意一个都不选）→ 严格只显示 id 在集合里的技能。
 * 无论哪种情况都排除书源编写这类重型流程技能。
 */
class AiQuickAccessViewModel(
    private val aiProfileGateway: AiProfileGateway
) : ViewModel() {

    companion object {
        /** 书源编写是重型流程技能，放在悬浮球二级菜单里不合适，只在 AI 设置/工作区使用。 */
        private const val BOOK_SOURCE_SKILL_ID = "builtin.book_source"

        /** 解析偏好里的勾选集合；null / 空白 / 空集合都视为「未自定义」。 */
        fun parseSelected(raw: String?): Set<String> =
            raw?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
    }

    private val _skills = MutableStateFlow<ImmutableList<AiQuickSkillUi>>(persistentListOf())
    val skills = _skills.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeSkills(),
                AppConfigStore.observeString(PreferKey.aiQuickSkillIds),
            ) { list, raw -> list to raw }
                .collect { (list, raw) ->
                    // raw == null 表示用户从未配置过 → 保持老行为显示全部；
                    // raw != null（哪怕是空串，表示用户故意一个都不选）→ 严格按勾选集合过滤。
                    val selected = raw?.let { parseSelected(it) }
                    _skills.update {
                        list.filter { skill ->
                            skill.enabled &&
                                skill.id != BOOK_SOURCE_SKILL_ID &&
                                (selected == null || skill.id in selected)
                        }
                            .sortedBy { it.sortNumber }
                            .map { AiQuickSkillUi(id = it.id, name = it.name, description = it.description) }
                            .toImmutableList()
                    }
                }
        }
    }
}

data class AiQuickSkillUi(
    val id: String,
    val name: String,
    val description: String
)
