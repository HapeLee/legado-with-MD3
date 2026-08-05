package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import io.legado.app.domain.model.readaloud.SpeechEmotion
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudTtsEmotionMapperTest {
    @Test
    fun `maps canonical emotion to provider vocabulary`() {
        assertEquals(
            "开心",
            CloudTtsEmotionMapper.map(
                CloudTtsProviderType.Mimo,
                SpeechEmotion.Cheerful.storageValue,
            ).style,
        )
        assertEquals(
            "cheerful",
            CloudTtsEmotionMapper.map(
                CloudTtsProviderType.AzureSpeech,
                SpeechEmotion.Cheerful.storageValue,
            ).style,
        )
        assertEquals(
            "happy",
            CloudTtsEmotionMapper.map(
                CloudTtsProviderType.Volcengine,
                SpeechEmotion.Cheerful.storageValue,
            ).style,
        )
    }

    @Test
    fun `uses instructions for prompt controlled providers`() {
        val openAi = CloudTtsEmotionMapper.map(
            CloudTtsProviderType.OpenAiSpeech,
            SpeechEmotion.Angry.storageValue,
        )
        val alibaba = CloudTtsEmotionMapper.map(
            CloudTtsProviderType.AlibabaCloud,
            SpeechEmotion.Sad.storageValue,
        )

        assertEquals("", openAi.style)
        assertEquals("Speak with controlled anger and intensity.", openAi.instruction)
        assertEquals("", alibaba.style)
        assertEquals("用悲伤、低落的情绪朗读", alibaba.instruction)
    }

    @Test
    fun `falls back when provider has no matching emotion control`() {
        assertEquals(
            CloudTtsEmotionControl(),
            CloudTtsEmotionMapper.map(
                CloudTtsProviderType.AwsPolly,
                SpeechEmotion.Angry.storageValue,
            ),
        )
    }
}
