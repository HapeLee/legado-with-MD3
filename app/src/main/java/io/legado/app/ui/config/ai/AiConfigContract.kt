package io.legado.app.ui.config.ai

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.AiSkillProfile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiConfigUiState(
    val providers: ImmutableList<AiProviderListItemUi> = persistentListOf(),
    val models: ImmutableList<AiModelListItemUi> = persistentListOf(),
    val skills: ImmutableList<AiSkillListItemUi> = persistentListOf(),
    val currentModelProfileId: String? = null,
    val currentModelName: String = "",
    val providerCount: Int = 0,
    val modelCount: Int = 0,
    val presetCount: Int = 0,
    val skillCount: Int = 0,
    /** 被用户关闭的工具分组 key；空集合表示全部开启。 */
    val disabledToolGroups: Set<String> = emptySet()
)

@Stable
data class AiProviderListItemUi(
    val providerId: String,
    val providerName: String,
    val protocol: String,
    val baseUrl: String,
    val modelCount: Int,
    val enabled: Boolean,
    val models: ImmutableList<AiModelListItemUi> = persistentListOf()
)

@Stable
data class AiModelListItemUi(
    val providerId: String,
    val modelProfileId: String,
    val providerName: String,
    val protocol: String,
    val baseUrl: String,
    val modelName: String,
    val modelId: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val enabled: Boolean,
    val isCurrent: Boolean,
    val text: Boolean = true,
)

/** Skill 列表项；保留 Room 实体的全部字段，编辑保存时不丢排序与时间戳。 */
@Stable
data class AiSkillListItemUi(
    val id: String,
    val name: String,
    val description: String,
    val content: String,
    val sourceUrl: String,
    val enabled: Boolean,
    val sortNumber: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toProfile(): AiSkillProfile = AiSkillProfile(
        id = id,
        name = name,
        description = description,
        content = content,
        sourceUrl = sourceUrl,
        enabled = enabled,
        sortNumber = sortNumber,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

sealed interface AiConfigIntent {
    data class SetDefaultModel(val modelProfileId: String) : AiConfigIntent
    data class SaveSkill(val skill: AiSkillProfile) : AiConfigIntent
    data class DeleteSkill(val skillId: String) : AiConfigIntent
    data class SetSkillEnabled(val skillId: String, val enabled: Boolean) : AiConfigIntent
    data class SetToolGroupEnabled(val groupKey: String, val enabled: Boolean) : AiConfigIntent
}

sealed interface AiConfigEffect {
    data class ShowMessage(val message: String) : AiConfigEffect
    /** 用资源 id 走本地化，避免 ViewModel 里硬编码文案。 */
    data class ShowMessageRes(val resId: Int) : AiConfigEffect
}
