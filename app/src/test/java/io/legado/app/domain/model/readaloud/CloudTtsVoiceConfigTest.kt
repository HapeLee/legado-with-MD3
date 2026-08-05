package io.legado.app.domain.model.readaloud

import io.legado.app.utils.GSON
import org.junit.Assert.assertNull
import org.junit.Test

class CloudTtsVoiceConfigTest {
    @Test
    fun `missing dynamic controls remain nullable for legacy presets`() {
        val config = GSON.fromJson("{\"speed\":1.0}", CloudTtsVoiceConfig::class.java)

        assertNull(config.automaticEmotion)
        assertNull(config.characterPersonality)
        assertNull(config.thoughtPerformance)
    }
}
