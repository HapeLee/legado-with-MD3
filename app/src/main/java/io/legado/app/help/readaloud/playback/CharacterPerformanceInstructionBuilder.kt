package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CharacterPerformanceProfile
import io.legado.app.domain.model.readaloud.CloudTtsProviderType

object CharacterPerformanceInstructionBuilder {
    const val VERSION = "character-performance-v1"

    private val supportedProviders = setOf(
        CloudTtsProviderType.OpenAiSpeech,
        CloudTtsProviderType.GeminiTts,
        CloudTtsProviderType.Mimo,
        CloudTtsProviderType.AlibabaCloud,
    )

    fun build(
        provider: CloudTtsProviderType,
        profile: CharacterPerformanceProfile?,
    ): String {
        if (provider !in supportedProviders || profile == null) return ""
        val role = profile.role.toRoleLabel()
        val voiceAge = profile.voiceAgeBand.toAgeLabel()
        val personality = profile.personality.toSafeTraitText()
        if (role.isBlank() && voiceAge.isBlank() && personality.isBlank()) return ""
        return buildString {
            append("保持该角色的声音表演一致。")
            if (role.isNotBlank()) append("角色定位：$role。")
            if (voiceAge.isNotBlank()) append("声音年龄：$voiceAge。")
            if (personality.isNotBlank()) append("性格特征：$personality。")
            append("这些内容仅用于控制表演，不要朗读，也不要修改台词。")
        }
    }

    private fun String.toRoleLabel(): String = when (trim()) {
        "male_lead" -> "男主角"
        "female_lead" -> "女主角"
        "male_supporting" -> "男性配角"
        "female_supporting" -> "女性配角"
        else -> ""
    }

    private fun String.toSafeTraitText(): String =
        replace(CONTROL_CHARACTERS, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .take(MAX_PERSONALITY_LENGTH)

    private fun String.toAgeLabel(): String = when (trim()) {
        "child" -> "儿童"
        "teen" -> "少年或少女"
        "young_adult" -> "青年"
        "adult" -> "成年人"
        "elderly" -> "老人"
        else -> ""
    }

    private const val MAX_PERSONALITY_LENGTH = 240
    private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]")
    private val WHITESPACE = Regex("\\s+")
}
