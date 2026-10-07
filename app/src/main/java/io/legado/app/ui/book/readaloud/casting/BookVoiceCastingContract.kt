package io.legado.app.ui.book.readaloud.casting

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class BookVoiceCastingUiState(
    val bookUrl: String,
    val isLoading: Boolean = true,
    val items: ImmutableList<VoiceCastingItemUi> = persistentListOf(),
    val voices: ImmutableList<VoiceOptionUi> = persistentListOf(),
    /** 排版配置名：角色气泡弹层里「应用排版」那一节的候选，与高亮规则编辑弹层同一份来源。 */
    val configNames: ImmutableList<String> = persistentListOf(),
)

@Stable
data class VoiceCastingItemUi(
    val subjectType: String,
    val subjectId: String,
    val kind: CastingSubjectKind,
    val name: String,
    val description: String = "",
    /**
     * 档案里的角色键原值（`male_lead` 这一类英文常量），配音页按它决定整张卡的档次外观。
     *
     * [description] 是它的中文名，两者一起给是因为页面既要说出来，也要按男女主/男女配分档。
     */
    val role: String = "",
    val avatarUri: String? = null,
    /** 这个角色自己的气泡（只填了气泡那几栏的高亮规则 JSON），空 = 没设。 */
    val bubbleRuleJson: String = "",
    val hasBinding: Boolean = false,
    val voiceId: String = "",
    val voiceName: String = "",
    val voiceAvailable: Boolean = false,
    /** 角色声音池（配音侧的权威值，档案的 voiceAgeBand 列存的就是它）。 */
    val poolLabel: String = "",
    /**
     * 角色的变声器预设名（空 = 不变声）。
     *
     * 这是**角色级**的全局状态：朗读侧每句都按 characterId 查这一份（VoiceEffectStore.ofCharacter），
     * 改一次，这个角色在书里所有分配到的句子都跟着变。
     */
    val voiceEffect: String = "",
    val chapterCount: Int = 0,
    val lineCount: Int = 0,
    /** 拖动排序位（0 = 没排过，按男女主/男女配的档次默认放置）。 */
    val sortOrder: Int = 0,
)

@Stable
data class VoiceOptionUi(
    val id: String,
    val name: String,
    val engineType: String,
    /** 引擎显示名（查不到时退回原始 engineId）。 */
    val engineName: String,
    val selectable: Boolean,
)

enum class CastingSubjectKind {
    Narrator,
    UnknownMale,
    UnknownFemale,
    Unknown,
    Character,
}

sealed interface BookVoiceCastingIntent {
    data object Refresh : BookVoiceCastingIntent

    /** 旁白行展开编辑后保存的音色；空串 = 取消绑定，回到朗读引擎自己的默认发音人。 */
    data class SetNarratorVoice(val voiceId: String) : BookVoiceCastingIntent

    /** 人物拖动排序：松手时把当前显示顺序（角色 id）整表写回。 */
    data class SaveCharacterOrder(val characterIds: List<String>) : BookVoiceCastingIntent

    /** 卡片菜单里直接换头像：写进人物档案，空串 = 不用头像。 */
    data class SetAvatar(val characterId: String, val avatarUri: String) : BookVoiceCastingIntent
}

sealed interface BookVoiceCastingEffect {
    data class ShowToast(val message: String) : BookVoiceCastingEffect
}
