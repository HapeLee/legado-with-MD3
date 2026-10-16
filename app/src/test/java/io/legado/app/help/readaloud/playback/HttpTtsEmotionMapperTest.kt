package io.legado.app.help.readaloud.playback

import io.legado.app.domain.model.readaloud.HttpTtsEmotionDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpTtsEmotionMapperTest {
    private val catalog = listOf(
        HttpTtsEmotionDraft("默认", "neutral"),
        HttpTtsEmotionDraft("开心", "happy"),
        HttpTtsEmotionDraft("悲伤", "sad"),
    )

    @Test fun `internal cheerful selects source happy and its name`() {
        assertEquals(catalog[1], HttpTtsEmotionMapper.map("cheerful", catalog))
    }

    @Test fun `unknown and unsupported emotions use declared neutral`() {
        assertEquals(catalog[0], HttpTtsEmotionMapper.map("calm", catalog))
        assertEquals(catalog[0], HttpTtsEmotionMapper.map("unrecognized", catalog))
    }

    @Test fun `source labels retain their exact spelling`() {
        val chinese = listOf(HttpTtsEmotionDraft("开心", "高兴"))
        assertEquals(chinese[0], HttpTtsEmotionMapper.map("cheerful", chinese))
    }

    @Test fun `unmapped catalog without neutral uses empty source default`() {
        assertEquals(HttpTtsEmotionDraft("", ""), HttpTtsEmotionMapper.map("fearful", catalog.drop(1)))
    }

    @Test fun `legacy sources without catalog retain raw tags`() {
        assertEquals(HttpTtsEmotionDraft("", "cheerful"), HttpTtsEmotionMapper.map("cheerful", emptyList()))
    }
}
