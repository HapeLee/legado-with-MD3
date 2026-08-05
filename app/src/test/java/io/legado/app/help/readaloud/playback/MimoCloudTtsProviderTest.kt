package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.CloudTtsEngine
import io.legado.app.domain.model.readaloud.CloudTtsProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MimoCloudTtsProviderTest {
    @Test
    fun `exposes the official built in voice directory`() {
        val voices = MimoCloudTtsProvider().fetchVoices(
            CloudTtsEngine(
                id = "mimo",
                name = "MiMo",
                provider = CloudTtsProviderType.Mimo,
            ),
        )

        assertEquals(
            listOf("mimo_default", "冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean"),
            voices.map { it.id },
        )
        assertTrue(voices.all { it.styles.isNotEmpty() })
    }
}
