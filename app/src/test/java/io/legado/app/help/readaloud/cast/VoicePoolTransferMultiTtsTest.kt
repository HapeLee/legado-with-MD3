package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * multitts「导出角色」的读取与解析。
 *
 * 样例逐行照抄真机导出的 `voice_pool.yaml`（讯飞配音 + 火山翻译两组、一个停用项、一个音效组），
 * 解析器是逐行手写的，所以断言要钉在真实文件上而不是构造一个理想化的 YAML。
 */
class VoicePoolTransferMultiTtsTest {

    @Test
    fun `reads groups display names and engine voice keys`() {
        val preview = VoicePoolTransfer.parseMultiTts(YAML)

        assertEquals(listOf("男童", "男老年"), preview.pools.map { it.name })
        assertEquals(2, preview.voiceCount)
        val boy = preview.pools.first()
        assertEquals("讯飞配音•聆万万(男小聪8-12)", boy.voices.first().displayName)
        // speakerId 用引擎自己的音色键：显示名会重复，value 才是它表里的唯一标识
        assertEquals("xfpeiyin_lingwanwan_1", boy.voices.first().voiceName)
    }

    @Test
    fun `skips disabled entries and sound effect groups`() {
        val preview = VoicePoolTransfer.parseMultiTts(YAML)

        assertEquals(1, preview.skippedDisabled)
        assertEquals(1, preview.skippedEffects)
        assertEquals(
            false,
            preview.pools.any { it.name.contains("音效") } ||
                preview.pools.any { it.voices.any { v -> v.voiceName.contains("yinxiao") } }
        )
    }

    @Test
    fun `unwraps the zip that multitts exports`() {
        val zip = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { out ->
                out.putNextEntry(ZipEntry("voice_pool.yaml"))
                out.write(YAML.toByteArray())
                out.closeEntry()
            }
            bytes.toByteArray()
        }

        assertEquals(YAML, VoicePoolTransfer.readMultiTtsYaml(ByteArrayInputStream(zip)))
    }

    @Test
    fun `passes a plain yaml file through`() {
        val text = VoicePoolTransfer.readMultiTtsYaml(ByteArrayInputStream(YAML.toByteArray()))

        assertEquals(2, VoicePoolTransfer.parseMultiTts(text).pools.size)
    }

    private companion object {
        val YAML = """
            男童:
            - !!org.nobody.multitts.tts.role.VoiceItem
              activate: true
              group: 男童
              name: 讯飞配音•聆万万(男小聪8-12)
              value: xfpeiyin_lingwanwan_1
            - !!org.nobody.multitts.tts.role.VoiceItem
              activate: false
              group: 男童
              name: 火山翻译•男童-林林
              value: huoshan_tts.other.BV415_streaming_1
            男老年:
            - !!org.nobody.multitts.tts.role.VoiceItem
              activate: true
              group: 男老年
              name: 讯飞配音•王大爷
              value: xfpeiyin_wangdaye_1
            音效:
            - !!org.nobody.multitts.tts.role.VoiceItem
              activate: true
              group: 音效
              name: 鼓掌声
              value: bendiyinxiao_gusheng_1
        """.trimIndent()
    }
}
