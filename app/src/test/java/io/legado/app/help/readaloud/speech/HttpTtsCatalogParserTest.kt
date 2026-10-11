package io.legado.app.help.readaloud.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTtsCatalogParserTest {

    @Test
    fun `平铺发言人数组`() {
        val draft = HttpTtsCatalogParser.parse(
            """
            {
              "name": "测试引擎",
              "url": "https://example.com/tts",
              "speakersJson": "[{\"speakerName\":\"晓晓\",\"toneID\":\"zh-CN-XiaoxiaoNeural\"},{\"speakerName\":\"云希\",\"toneID\":\"zh-CN-YunxiNeural\"}]"
            }
            """.trimIndent()
        )

        assertEquals(2, draft.speakers.size)
        assertEquals("晓晓", draft.speakers[0].speakerName)
        assertEquals("zh-CN-XiaoxiaoNeural", draft.speakers[0].toneID)
        assertEquals("zh-CN-YunxiNeural", draft.speakers[1].toneID)
    }

    @Test
    fun `分组发言人数组`() {
        val draft = HttpTtsCatalogParser.parse(
            """
            {
              "speakersJson": "[{\"groupName\":\"女声\",\"items\":[{\"speakerName\":\"晓晓\",\"toneID\":\"a\"},{\"speakerName\":\"晓伊\",\"toneID\":\"b\"}]},{\"groupName\":\"男声\",\"items\":[{\"speakerName\":\"云希\",\"toneID\":\"c\"}]}]"
            }
            """.trimIndent()
        )

        assertEquals(listOf("a", "b", "c"), draft.speakers.map { it.toneID })
    }

    @Test
    fun `toneID 字段别名`() {
        val draft = HttpTtsCatalogParser.parse(
            """{"speakersJson":"[{\"name\":\"晓晓\",\"toneId\":\"t1\"},{\"name\":\"云希\",\"tone_id\":\"t2\"},{\"name\":\"晓伊\",\"toneID\":\"t3\"}]"}"""
        )

        assertEquals(listOf("t1", "t2", "t3"), draft.speakers.map { it.toneID })
    }

    @Test
    fun `缺 toneID 的发言人被丢弃`() {
        val draft = HttpTtsCatalogParser.parse(
            """{"speakersJson":"[{\"speakerName\":\"只有名字\"},{\"speakerName\":\"晓晓\",\"toneID\":\"a\"}]"}"""
        )

        assertEquals(1, draft.speakers.size)
        assertEquals("a", draft.speakers[0].toneID)
    }

    @Test
    fun `重复 toneID 去重保留首次`() {
        val draft = HttpTtsCatalogParser.parse(
            """{"speakersJson":"[{\"speakerName\":\"晓晓\",\"toneID\":\"a\"},{\"speakerName\":\"晓晓2\",\"toneID\":\"a\"}]"}"""
        )

        assertEquals(1, draft.speakers.size)
        assertEquals("晓晓", draft.speakers[0].speakerName)
    }

    @Test
    fun `情绪字段别名与去重`() {
        val draft = HttpTtsCatalogParser.parse(
            """{"emotionsJson":"[{\"emotionName\":\"开心\",\"emotionTag\":\"happy\"},{\"name\":\"悲伤\",\"tag\":\"sad\"},{\"name\":\"生气\",\"value\":\"angry\"}]"}"""
        )

        assertEquals(
            listOf("happy" to "开心", "sad" to "悲伤", "angry" to "生气"),
            draft.emotions.map { it.emotionTag to it.emotionName }
        )
    }

    @Test
    fun `目录字段是单个对象也能解析`() {
        val draft = HttpTtsCatalogParser.parse(
            """{"speakersJson":"{\"speakerName\":\"晓晓\",\"toneID\":\"a\",\"tags\":[\"女\",\"青年\"]}"}"""
        )

        assertEquals(1, draft.speakers.size)
        assertEquals(listOf("女", "青年"), draft.speakers[0].tags)
    }

    @Test
    fun `空值与非法 JSON 返回空草稿`() {
        assertTrue(HttpTtsCatalogParser.parse("""{"speakersJson":""}""").isEmpty)
        assertTrue(HttpTtsCatalogParser.parse("""{"speakersJson":"not json"}""").isEmpty)
        assertTrue(HttpTtsCatalogParser.parse("not json at all").isEmpty)
        assertTrue(HttpTtsCatalogParser.parse("""{"name":"无目录字段"}""").isEmpty)
    }

    @Test
    fun `目录字段写成对象时不解析`() {
        assertTrue(HttpTtsCatalogParser.parse("""{"speakersJson":{"a":1}}""").isEmpty)
    }

    @Test
    fun `parseList 与源数组同序`() {
        val drafts = HttpTtsCatalogParser.parseList(
            """
            [
              {"name":"A"},
              {"name":"B","speakersJson":"[{\"speakerName\":\"b1\",\"toneID\":\"b\"}]"},
              "not an object",
              {"name":"D","speakersJson":"[{\"speakerName\":\"d1\",\"toneID\":\"d\"}]"}
            ]
            """.trimIndent()
        )

        assertEquals(4, drafts.size)
        assertTrue(drafts[0].isEmpty)
        assertEquals(listOf("b"), drafts[1].speakers.map { it.toneID })
        assertTrue(drafts[2].isEmpty)
        assertEquals(listOf("d"), drafts[3].speakers.map { it.toneID })
    }

    @Test
    fun `parseList 顶层是单对象退化成一个元素`() {
        val drafts = HttpTtsCatalogParser.parseList(
            """{"name":"A","speakersJson":"[{\"speakerName\":\"a1\",\"toneID\":\"a\"}]"}"""
        )

        assertEquals(1, drafts.size)
        assertEquals(listOf("a"), drafts[0].speakers.map { it.toneID })
    }
}
