package io.legado.app.help.readaloud.cast

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正则角色 0 命中时那行日志的内容。
 *
 * 用户报「开正则什么都不生效」，光看正则分不出是书里用的括号跟他写的不是一种，
 * 还是正则本身没对上，所以这行归因必须自己把证据摆出来。
 */
class CastSpeechOverlayNoHitReasonTest {

    @Test
    fun `says which bracket the chapter actually uses when the probe character is absent`() {
        val reason = CastSpeechOverlay.explainNoHit(
            "［([^］]*)］",
            "【系统】少年看起来心里很平静啊。「走吧。」"
        )

        assertTrue(reason, "［" in reason && "一次都没出现" in reason)
        assertTrue(reason, "【" in reason && "】" in reason)
    }

    @Test
    fun `counts the probe character when it is present`() {
        val reason = CastSpeechOverlay.explainNoHit("［([^］]*)］", "［系统］平静。［系统］走了。")

        assertTrue(reason, "出现 2 次" in reason)
    }

    @Test
    fun `refuses to guess when the pattern uses escapes`() {
        val reason = CastSpeechOverlay.explainNoHit("\\d+岁", "他12岁。")

        assertTrue(reason, "转义" in reason)
    }
}
