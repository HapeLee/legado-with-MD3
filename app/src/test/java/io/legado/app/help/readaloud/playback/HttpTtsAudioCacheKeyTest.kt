package io.legado.app.help.readaloud.playback

import io.legado.app.data.entities.HttpTTS
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import org.junit.Assert.*
import org.junit.Test

class HttpTtsAudioCacheKeyTest {
    private val source = HttpTTS(id = 1, url = "http://localhost/tts", speed = 5)
    private val voice = ReadAloudVoice("voice", "http", "1", "tone", "Speaker")

    @Test fun `different tones emotions engines and synthesis speeds never share key`() {
        val key = httpTtsAudioCacheSourceKey(source, voice, "cheerful")
        assertNotEquals(key, httpTtsAudioCacheSourceKey(source, voice.copy(speakerId = "other"), "cheerful"))
        assertNotEquals(key, httpTtsAudioCacheSourceKey(source, voice, "sad"))
        assertNotEquals(key, httpTtsAudioCacheSourceKey(source.copy(id = 2), voice, "cheerful"))
        assertNotEquals(key, httpTtsAudioCacheSourceKey(source.copy(speed = 10), voice, "cheerful"))
        assertNotEquals(key, httpTtsAudioCacheSourceKey(source, voice.copy(revision = 1), "cheerful"))
        assertNotEquals(key, httpTtsAudioCacheSourceKey(source, voice.copy(emotionCatalogJson = """[{"emotionName":"开心","emotionTag":"happy"}]"""), "cheerful"))
    }

    @Test fun `legacy default tone at default speed retains downloaded audio identity`() {
        val defaultVoice = voice.copy(speakerId = "")
        assertEquals(source.url, httpTtsAudioCacheSourceKey(source, defaultVoice, ""))
        assertNotEquals(source.url, httpTtsAudioCacheSourceKey(source.copy(speed = 10), defaultVoice, ""))
    }
}
