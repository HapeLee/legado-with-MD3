package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I·TTS Server「音色列表」导出的两种层级。
 *
 * 真机上有两种文件：一种每个 `group` 就是一个声音池（`[{group:{name},list:[...]}]`，条目不带
 * `categoryPath`）；另一种 `group` 是**来源软件/引擎**（微讯火、猫箱豆包），池在它下面，
 * 由每条音色的 `categoryPath`（🤝女青年、🐱男青年）给出。两种都要认，且判定按分组各自算：
 * 同一份文件里可以一半一半。样例字段照抄真机导出（`config` 只留解析器读的键）。
 */
class VoicePoolTransferTtsServerTest {

    @Test
    fun `flat export keeps one pool per source group`() {
        val preview = VoicePoolTransfer.parseTtsServer(FLAT)

        assertEquals(
            listOf("男童" to "", "女童" to ""),
            preview.pools.map { it.name to it.groupName },
        )
        assertEquals(3, preview.voiceCount)
        // voiceName = 显示名_角色 id：引擎在 Android TTS 里暴露的 Voice.name 就是这个形状
        assertEquals(
            "云夏-愉快-男孩 (zh-CN-YunxiaNeural)_1759800479254",
            preview.pools.first().voices.first().voiceName,
        )
    }

    @Test
    fun `categorized export turns the source group into a group and categoryPath into pools`() {
        val preview = VoicePoolTransfer.parseTtsServer(CATEGORIZED)

        assertEquals(
            listOf(
                Triple("", "旁白（任选一）", "阳光甜妹（升级版） 女|青年|台湾口音|温柔"),
                Triple("微讯火", "🤝男青年", "002新闻男"),
                Triple("微讯火", "🤝女青年", "025台湾女"),
                Triple("微讯火", "🤝女青年", "203呆女20"),
                Triple("微讯火", "微讯火", "没归类的"),
            ),
            preview.pools.flatMap { pool ->
                pool.voices.map { Triple(pool.groupName, pool.name, it.displayName) }
            },
        )
    }

    @Test
    fun `category buckets keep first-appearance order and count every member`() {
        val preview = VoicePoolTransfer.parseTtsServer(CATEGORIZED)
        val weibao = preview.pools.filter { it.groupName == "微讯火" }

        assertEquals(listOf("🤝男青年", "🤝女青年", "微讯火"), weibao.map { it.name })
        assertEquals(listOf(1, 2, 1), weibao.map { it.voices.size })
    }

    @Test
    fun `disabled and sound effect entries are still dropped inside a categorized group`() {
        val preview = VoicePoolTransfer.parseTtsServer(CATEGORIZED)

        assertEquals(1, preview.skippedDisabled)
        // 一条混在微讯火里（bendiyinxiao），一条是整组「🎶全部音效」
        assertEquals(2, preview.skippedEffects)
        assertTrue(preview.pools.none { it.name.contains("全部音效") })
        assertTrue(
            preview.pools.flatMap { it.voices }.none { it.displayName.contains("关掉的") },
        )
    }

    private companion object {
        private val VOICE_CONFIG = """
            {"#type":"tts","audioFormat":"mp3","source":{"#type":"plugin","pluginId":"doubao.com",
             "locale":"zh-CN","voice":"zh_female_xiaohe_conversation_wvae_bigtt"},
             "speechRule":{"target":4,"tag":"narration","tagRuleId":"mingwuyan","tagName":"旁白"}}
        """.trimIndent()

        private val EFFECT_CONFIG = """
            {"#type":"tts","audioFormat":"mp3","source":{"#type":"plugin","pluginId":"bendiyinxiao",
             "locale":"zh-CN","voice":"all_voices"},
             "speechRule":{"target":4,"tag":"localSound1","tagName":"本地音效1"}}
        """.trimIndent()

        /** 每个 group 就是一个池：条目不带 `categoryPath`。 */
        private val FLAT = """
            [
              {"group":{"id":1765831743307,"name":"男童","order":2},"list":[
                {"id":1759800479254,"displayName":"云夏-愉快-男孩 (zh-CN-YunxiaNeural)","groupId":1765831743307,"order":1,
                 "config":$VOICE_CONFIG},
                {"id":1763695618383,"displayName":"男童-林林 (可爱)","groupId":1765831743307,"order":5,
                 "config":$VOICE_CONFIG}
              ]},
              {"group":{"id":1765831775522,"name":"女童","order":3},"list":[
                {"id":1759800479240,"displayName":"晓伊","groupId":1765831775522,"order":2,
                 "config":$VOICE_CONFIG}
              ]}
            ]
        """.trimIndent()

        /** 微讯火/猫箱豆包那种：group 是来源，池在 `categoryPath` 上；旁白组没有 categoryPath。 */
        private val CATEGORIZED = """
            [
              {"group":{"id":1791443833606,"name":"旁白（任选一）","order":1},"list":[
                {"id":1791443933607,"displayName":"阳光甜妹（升级版） 女|青年|台湾口音|温柔","groupId":1791443833606,
                 "config":$VOICE_CONFIG}
              ]},
              {"group":{"id":1791443833616,"name":"微讯火","order":2},"list":[
                {"id":1791443933617,"displayName":"002新闻男","groupId":1791443833616,"isEnabled":true,"order":148,
                 "categoryPath":"🤝男青年","config":$VOICE_CONFIG},
                {"id":1791443933618,"displayName":"025台湾女","groupId":1791443833616,"isEnabled":true,"order":68,
                 "categoryPath":"🤝女青年","config":$VOICE_CONFIG},
                {"id":1791443933619,"displayName":"203呆女20","groupId":1791443833616,"isEnabled":true,"order":69,
                 "categoryPath":"🤝女青年","config":$VOICE_CONFIG},
                {"id":1791443933620,"displayName":"没归类的","groupId":1791443833616,"order":70,
                 "config":$VOICE_CONFIG},
                {"id":1791443933621,"displayName":"关掉的","groupId":1791443833616,"isEnabled":false,
                 "categoryPath":"🤝男青年","config":$VOICE_CONFIG},
                {"id":1791443933622,"displayName":"✨全部音效","groupId":1791443833616,
                 "categoryPath":"🤝男青年","config":$EFFECT_CONFIG}
              ]},
              {"group":{"id":1791443834003,"name":"🎶全部音效","order":3},"list":[
                {"id":1791443934004,"displayName":"✨全部音效","groupId":1791443834003,"isEnabled":true,
                 "config":$EFFECT_CONFIG}
              ]}
            ]
        """.trimIndent()
    }

}
