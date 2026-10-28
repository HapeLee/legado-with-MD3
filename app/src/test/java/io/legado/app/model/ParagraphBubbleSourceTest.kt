package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 段评图片来源解析。
 *
 * 这里钉住的是**与上游气泡包的兼容约定**：`dp:<count>,{...}` 的 count 就是模板里的 `${num}`，
 * `status` 决定用常规色还是强调色，`displayColor` 可以覆盖主题色。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class ParagraphBubbleSourceTest {

    @Test
    fun readsCountAndOptionsFromNativeDpSource() {
        val spec = ParagraphBubbleSource.parse("""dp:12,{"status":"emphasis","displayColor":"#123456"}""")

        assertEquals("12", spec?.displayText)
        assertEquals("emphasis", spec?.status)
        assertEquals("#123456", spec?.displayColor)
    }

    @Test
    fun defaultsToNormalStatusWithoutOptions() {
        val spec = ParagraphBubbleSource.parse("dp:8")

        assertEquals("8", spec?.displayText)
        assertEquals("normal", spec?.status)
        assertNull(spec?.displayColor)
    }

    @Test
    fun prefersDisplayTextOverCount() {
        val spec = ParagraphBubbleSource.parse("""dp:99,{"displayText":"热"}""")

        assertEquals("热", spec?.displayText)
    }

    @Test
    fun readsCanonicalBubbleScheme() {
        val spec = ParagraphBubbleSource.parse(
            "bubble://paragraph?displayText=7&num=7&status=emphasis&displayColor=%23123456"
        )

        assertEquals("7", spec?.displayText)
        assertEquals("emphasis", spec?.status)
        assertEquals("#123456", spec?.displayColor)
    }

    @Test
    fun ignoresOrdinaryImageWithoutForceSoftwareBubble() {
        // 未开启「强制软件气泡」时，普通图片一律不动（含书源 click 选项的段评图标）。
        assertNull(ParagraphBubbleSource.parse("""https://example.com/comment.png,{"click":"showCmt(1)"}"""))
        assertNull(ParagraphBubbleSource.parse("https://example.com/plain.png"))
    }
}
