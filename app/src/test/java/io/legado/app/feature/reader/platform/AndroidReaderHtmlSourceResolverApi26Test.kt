package io.legado.app.feature.reader.platform

import android.app.Application
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * API 26/27 回归：`QuoteSpan.getColor()` / `BulletSpan.getColor()` 是 API 28 才有的方法，
 * 直接读 `span.color` 会在 minSdk 26 的设备上抛 `NoSuchMethodError`——
 * 带 `<blockquote>` 或 `<ul><li>` 的章节会整章崩掉。颜色在低版本上回落 `null`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidReaderHtmlSourceResolverApi26Test {

    private val html = "<blockquote>引用</blockquote><ul><li>条目</li></ul>"

    @Test
    fun quoteAndBulletDecorationsResolveOnApi26() {
        val paragraphs = AndroidReaderHtmlSourceResolver(20f, 2f).resolve(html, 0)
        val decorations = paragraphs.flatMap { it.decorations }
        val kinds = decorations.map { it.kind }.toSet()

        assertTrue("缺少 QUOTE 装饰：$kinds", ReaderParagraphDecorationKind.QUOTE in kinds)
        assertTrue("缺少 BULLET 装饰：$kinds", ReaderParagraphDecorationKind.BULLET in kinds)
        decorations.forEach {
            assertTrue(
                "API 26 上颜色应回落 null（读 span.color 会 NoSuchMethodError），实际 ${it.colorArgb}",
                it.colorArgb == null,
            )
        }
    }
}
