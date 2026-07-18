package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.SpeechRoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTtsRoleInstructionMapperTest {
    @Test
    fun `adds internal monologue direction for prompt controlled provider`() {
        val instruction = CloudTtsRoleInstructionMapper.map(
            CloudTtsProviderType.GeminiTts,
            SpeechRoleType.Thought,
        )

        assertTrue(instruction.contains("internal-monologue"))
    }

    @Test
    fun `does not change dialogue or native style provider`() {
        assertEquals(
            "",
            CloudTtsRoleInstructionMapper.map(
                CloudTtsProviderType.OpenAiSpeech,
                SpeechRoleType.Character,
            ),
        )
        assertEquals(
            "",
            CloudTtsRoleInstructionMapper.map(
                CloudTtsProviderType.AzureSpeech,
                SpeechRoleType.Thought,
            ),
        )
    }

    @Test
    fun `supports MiMo thought direction`() {
        val instruction = CloudTtsRoleInstructionMapper.map(
            CloudTtsProviderType.Mimo,
            SpeechRoleType.Thought,
        )

        assertTrue(instruction.contains("internal-monologue"))
    }
}
