package io.legado.app.model.localBook

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream

class MarkdownFileTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newBook(file: File): Book = Book(
        type = BookType.text or BookType.local,
        bookUrl = file.absolutePath,
        name = "测试书",
        author = "",
        originName = file.name,
        charset = "UTF-8",
    ).apply { latestChapterTime = file.lastModified() }

    private fun scan(file: File) = MarkdownFile(newBook(file)).scan { FileInputStream(file) }

    private fun bodyOf(file: File, start: Long, end: Long, stripHeading: Boolean) =
        MarkdownFile(newBook(file)).readChapterBody({ FileInputStream(file) }, start, end, stripHeading)

    private fun write(content: String): File =
        tmp.newFile("book.md").apply { writeText(content) }

    @Test
    fun splitsChaptersOnH1AndH2Only() {
        val file = write(
            """
            开头的前言内容。

            # 第一章

            第一章正文。

            ## 第一节

            第一节正文。

            ### 三级标题不拆章

            三级标题后的正文。

            # 第二章

            第二章正文。
            """.trimIndent() + "\n"
        )
        val chapters = scan(file)
        assertEquals(listOf("前言", "第一章", "第一节", "第二章"), chapters.map { it.title })
        assertTrue(chapters.all { it.start < it.end || it.startsWithHeading })
        val first = chapters[1]
        assertEquals(true, first.startsWithHeading)
        assertTrue(first.end <= chapters[2].start)
        val body = bodyOf(file, first.start, first.end, true)
        assertTrue(body.contains("第一章正文。"))
        assertFalse(body.contains("#"))
    }

    @Test
    fun fencedHashLinesAreNotChapterMarks() {
        val file = write(
            """
            # A

            ```kotlin
            # 不是标题的注释
            val x = 1
            ```

            围栏后的正文。

            # B
            """.trimIndent() + "\n"
        )
        val chapters = scan(file)
        assertEquals(listOf("A", "B"), chapters.map { it.title })
        val bodyA = bodyOf(file, chapters[0].start, chapters[0].end, true)
        assertTrue(bodyA.contains("# 不是标题的注释"))
        assertTrue(bodyA.contains("围栏后的正文。"))
    }

    @Test
    fun closingHashesAndBomDoNotLeakIntoTitle() {
        val file = write("\uFEFF## 序章 ##\n\n正文内容。\n")
        val chapters = scan(file)
        assertEquals(listOf("序章"), chapters.map { it.title })
        val body = bodyOf(file, chapters[0].start, chapters[0].end, true)
        assertTrue(body.contains("正文内容。"))
        assertFalse(body.contains("##"))
    }

    @Test
    fun fileWithoutHeadingsBecomesSingleChapter() {
        val file = write("第一行内容。\n\n第二行内容。\n")
        val chapters = scan(file)
        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
        assertEquals(0L, chapters[0].start)
        val body = bodyOf(file, chapters[0].start, chapters[0].end, false)
        assertTrue(body.contains("第一行内容。"))
        assertTrue(body.contains("第二行内容。"))
    }

    @Test
    fun oversizeChapterSplitsAtBlankLines() {
        val big = "段落内容。".repeat(20) + "\n\n"
        val content = buildString {
            append("# 长章\n\n")
            repeat(400) { append(big) }
        }
        assertTrue(content.toByteArray(Charsets.UTF_8).size > 102400)
        val file = write(content)
        val chapters = scan(file)
        assertTrue(chapters.size >= 2)
        assertEquals("长章(1)", chapters[0].title)
        assertTrue(chapters[1].title.startsWith("长章("))
    }

    @Test
    fun chapterBodyRendersThroughRichChannel() {
        val file = write("# 标题章\n\n正文**重点**与<img src=\"pic/x.png\"/>。\n")
        val chapters = scan(file)
        val body = bodyOf(file, chapters[0].start, chapters[0].end, true)
        val html = MarkdownToHtml.convert(body)
        val plain = LocalBookSemanticHtml.plainText(html)
        assertTrue(plain.contains("正文重点与"))
        assertTrue(plain.contains("<img src=\"pic/x.png\">"))
    }

    @Test
    fun localRelativeImageResolvesAgainstBookDirectory() {
        val imageDir = tmp.newFolder("images")
        val image = File(imageDir, "pic 1.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val md = tmp.newFile("nested.md").apply { writeText("# t\n") }
        val opened = MarkdownFile.getImage(newBook(md), "images/pic%201.png")
        assertNotNull(opened)
        opened!!.use { assertEquals(1, it.read()) }
        assertTrue(image.isFile)
    }

    @Test
    fun imageReturnsNullWhenBookFileCannotBeLocated() {
        val book = newBook(File("book.md")).copy(bookUrl = "content://authority/document/1")
        assertEquals(null, MarkdownFile.getImage(book, "pic.png"))
    }

    @Test
    fun isMarkdownMatchesLocalMdAndMarkdownExtensions() {
        fun bookNamed(name: String, local: Boolean = true) = Book(
            type = if (local) BookType.text or BookType.local else BookType.text,
            originName = name,
        )
        assertTrue(bookNamed("a.md").isMarkdown)
        assertTrue(bookNamed("a.MD").isMarkdown)
        assertTrue(bookNamed("a.markdown").isMarkdown)
        assertFalse(bookNamed("a.txt").isMarkdown)
        assertFalse(bookNamed("a.md", local = false).isMarkdown)
    }

    @Test
    fun fileWithoutHeadingsSplitsOversizeContent() {
        val big = "段落内容。".repeat(20) + "\n\n"
        val content = buildString { repeat(400) { append(big) } }
        val size = content.toByteArray(Charsets.UTF_8).size.toLong()
        assertTrue(size > 102400)
        val file = write(content)
        val chapters = scan(file)
        assertTrue("无标题大文件必须切块，实际 ${chapters.size} 章", chapters.size >= 2)
        assertEquals(0L, chapters.first().start)
        assertTrue(chapters.last().end <= size)
        assertTrue(chapters.all { it.end - it.start <= 102400L * 3 })
        assertEquals("全文(1)", chapters[0].title)
    }

    @Test
    fun trailingHeadingHasValidRange() {
        val file = write("# A\n\n正文。\n\n# B\n")
        val chapters = scan(file)
        assertEquals(listOf("A", "B"), chapters.map { it.title })
        val last = chapters.last()
        assertTrue("末尾标题章不得是零长度范围", last.end > last.start)
        val body = bodyOf(file, last.start, last.end, true)
        assertTrue(body.isEmpty())
    }

    @Test
    fun titleEndingWithHashIsNotTrimmed() {
        val file = write("# 使用 C#\n\n正文。\n")
        val chapters = scan(file)
        assertEquals(listOf("使用 C#"), chapters.map { it.title })
    }

    @Test
    fun absoluteImagePathIsRejected() {
        val outside = tmp.newFile("secret.png").apply { writeBytes(byteArrayOf(9)) }
        val md = tmp.newFile("book.md").apply { writeText("# t\n") }
        assertEquals(null, MarkdownFile.getImage(newBook(md), outside.absolutePath))
        assertTrue(outside.isFile)
    }

    @Test
    fun parentTraversalImagePathIsRejected() {
        val bookDir = tmp.newFolder("books")
        val outside = tmp.newFile("secret.png").apply { writeBytes(byteArrayOf(9)) }
        val md = File(bookDir, "book.md").apply { writeText("# t\n") }
        assertEquals(null, MarkdownFile.getImage(newBook(md), "../secret.png"))
        assertTrue(outside.isFile)
    }

    @Test
    fun plusSignInImageNameIsPreserved() {
        val md = tmp.newFile("book.md").apply { writeText("# t\n") }
        val image = tmp.newFile("a+b.png").apply { writeBytes(byteArrayOf(7)) }
        val opened = MarkdownFile.getImage(newBook(md), "a+b.png")
        assertNotNull(opened)
        opened!!.use { assertEquals(7, it.read()) }
        assertTrue(image.isFile)
    }
}
