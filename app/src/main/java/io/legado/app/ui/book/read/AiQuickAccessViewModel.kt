package io.legado.app.ui.book.read

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.AiProfileGateway
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 阅读页 AI 悬浮小窗的状态宿主：只读取已启用的技能列表供快速选择。
 * 不持有会话、不访问网络；真正的对话交给 AiChat 页，避免与阅读页 ViewModel 的职责耦合。
 */
class AiQuickAccessViewModel(
    private val aiProfileGateway: AiProfileGateway
) : ViewModel() {

    companion object {
        /** 书源编写是重型流程技能，放在悬浮球二级菜单里不合适，只在 AI 设置/工作区使用。 */
        private const val BOOK_SOURCE_SKILL_ID = "builtin.book_source"
    }

    private val _skills = MutableStateFlow<ImmutableList<AiQuickSkillUi>>(persistentListOf())
    val skills = _skills.asStateFlow()

    init {
        viewModelScope.launch {
            aiProfileGateway.observeSkills().collect { list ->
                _skills.update {
                    list.filter { it.enabled && it.id != BOOK_SOURCE_SKILL_ID }
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
