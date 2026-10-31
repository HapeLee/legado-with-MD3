package io.legado.app.model.localBook

import android.app.Application
import io.legado.app.feature.reader.core.semantic.ReaderSemanticContract
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import me.ag2s.epublib.epub.EpubReader
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Small self-authored ZIP publications, exercised through the actual EPUB resource reader. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReaderEpubFileContractTest {
    @Test
    fun epub2And3ExtractSharedXhtmlCssImagesAndFragments() {
        for (version in listOf(2, 3)) {
            val book = javaClass.getResourceAsStream("/reader/semantic/epub$version.epub")!!.use { EpubReader().readEpub(it) }
            assertEquals(1, book.spine.spineReferences.size)
            assertEquals(2, book.tableOfContents.tocReferences.size)
            val resource = book.spine.spineReferences.single().resource
            assertTrue(resource.href.endsWith("Text/book.xhtml"))
            assertTrue(book.resources.getByHref("OEBPS/Images/p.png")!!.data.isNotEmpty())
            val document = Jsoup.parse(String(resource.data, Charsets.UTF_8))
            LocalBookSemanticHtml.inlineDocumentCssSemantics(document) { href ->
                book.resources.getByHref(LocalBookSemanticHtml.resolveRelativeHref(resource.href, href))?.let { String(it.data, Charsets.UTF_8) }
            }
            LocalBookSemanticHtml.cutFragment(document.body(), "one", "two")
            val channel = LocalBookSemanticHtml.emitMixedChannel(document.body(), "第一章")
            val source = ReaderChapterSourceParser.parse(0, "第一章", channel.lines().filter { it.isNotBlank() }, true, true, AndroidReaderHtmlSemanticTextResolver)
            assertTrue(source.semanticContent.contains("A&😀B\uFFFC"))
            assertTrue(source.semanticContent.contains("脚注"))
            assertFalse(source.semanticContent.contains("Hidden"))
            assertFalse(source.semanticContent.contains("Tail"))
            assertTrue(ReaderSemanticContract.validate(source).isEmpty())
        }
    }
}
