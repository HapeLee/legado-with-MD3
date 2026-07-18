package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CharacterPerformanceProfile
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterPerformanceInstructionBuilderTest {
    @Test
    fun `builds bounded performance direction for prompt controlled provider`() {
        val instruction = CharacterPerformanceInstructionBuilder.build(
            CloudTtsProviderType.OpenAiSpeech,
            CharacterPerformanceProfile(
                characterId = "character-1",
                role = "female_lead",
                personality = "冷静\n果断，外冷内热",
            ),
        )

        assertTrue(instruction.contains("角色定位：女主角"))
        assertTrue(instruction.contains("性格特征：冷静 果断，外冷内热"))
        assertTrue(instruction.contains("不要朗读"))
        assertFalse(instruction.contains('\n'))
    }

    @Test
    fun `does not send character profile to native style provider`() {
        assertEquals(
            "",
            CharacterPerformanceInstructionBuilder.build(
                CloudTtsProviderType.AzureSpeech,
                CharacterPerformanceProfile(
                    characterId = "character-1",
                    personality = "冷静",
                ),
            ),
        )
    }

    @Test
    fun `supports MiMo natural language performance direction`() {
        val instruction = CharacterPerformanceInstructionBuilder.build(
            CloudTtsProviderType.Mimo,
            CharacterPerformanceProfile(
                characterId = "character-1",
                role = "male_lead",
                personality = "沉稳",
            ),
        )

        assertTrue(instruction.contains("角色定位：男主角"))
        assertTrue(instruction.contains("性格特征：沉稳"))
    }
}
