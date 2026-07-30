package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.SpeechRoleType

object CloudTtsRoleInstructionMapper {
    const val VERSION = "speech-role-v1"

    fun map(provider: CloudTtsProviderType, roleType: SpeechRoleType): String {
        if (roleType != SpeechRoleType.Thought) return ""
        return when (provider) {
            CloudTtsProviderType.OpenAiSpeech,
            CloudTtsProviderType.GeminiTts,
            CloudTtsProviderType.Mimo,
            CloudTtsProviderType.AlibabaCloud ->
                "Use an intimate internal-monologue delivery, restrained and slightly quieter than spoken dialogue."
            else -> ""
        }
    }
}
