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

    private fun newBook(file: File, charsetName: String = "UTF-8"): Book = Book(
        type = BookType.text or BookType.local,
        bookUrl = file.absolutePath,
        name = "测试书",
        author = "",
        originName = file.name,
        charset = charsetName,
    ).apply { latestChapterTime = file.lastModified() }

    private fun scan(file: File, charsetName: String = "UTF-8") =
        MarkdownFile(newBook(file, charsetName)).scan { FileInputStream(file) }

    private fun bodyOf(file: File, start: Long, end: Long, stripHeading: Boolean) =
        MarkdownFile(newBook(file)).readChapterBody({ FileInputStream(file) }, start, end, stripHeading)

    private fun bodyOf(file: File, charsetName: String, start: Long, end: Long, stripHeading: Boolean) =
        MarkdownFile(newBook(file, charsetName)).readChapterBody({ FileInputStream(file) }, start, end, stripHeading)

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

    //UTF-16 的换行是双字节，按单字节 0x0A 切行会让后续行解码错位、标题漏识别
    private var utf16FileSeq = 0

    private fun assertUtf16ChaptersAreAligned(bytes: ByteArray, charsetName: String) {
        val file = tmp.newFile("utf16-${utf16FileSeq++}.md").apply { writeBytes(bytes) }
        val chapters = scan(file, charsetName)
        assertEquals(listOf("第一章", "第一节"), chapters.map { it.title })
        assertEquals(0L, chapters.first().start)
        assertEquals(bytes.size.toLong(), chapters.last().end)
        val body = bodyOf(file, charsetName, chapters[0].start, chapters[0].end, true)
        assertTrue("首章正文不得错位: $body", body.contains("第一段正文。"))
    }

    @Test
    fun utf16LeChaptersStayAligned() {
        val content = "# 第一章\n\n第一段正文。\n\n## 第一节\n\n第二段正文。\n"
        assertUtf16ChaptersAreAligned(content.toByteArray(Charsets.UTF_16LE), "UTF-16LE")
    }

    @Test
    fun utf16BeChaptersStayAligned() {
        val content = "# 第一章\n\n第一段正文。\n\n## 第一节\n\n第二段正文。\n"
        assertUtf16ChaptersAreAligned(content.toByteArray(Charsets.UTF_16BE), "UTF-16BE")
    }

    @Test
    fun utf16WithBomResolvesByteOrder() {
        val content = "# 第一章\n\n第一段正文。\n\n## 第一节\n\n第二段正文。\n"
        //UTF-16 名称本身不带字节序，字节序只能靠 BOM 判断
        val be = content.toByteArray(Charsets.UTF_16)
        assertTrue(be[0] == 0xFE.toByte() && be[1] == 0xFF.toByte())
        assertUtf16ChaptersAreAligned(be, "UTF-16")
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + content.toByteArray(Charsets.UTF_16LE)
        assertUtf16ChaptersAreAligned(le, "UTF-16")
    }

    @Test
    fun oversizeChapterDoesNotSplitInsideFencedCode() {
        val codeLine = "代码行内容。".repeat(10) + "\n"
        val content = buildString {
            append("# 代码章\n\n")
            append("```kotlin\n")
            repeat(1500) { append(codeLine) }
            append("```\n\n")
            append("代码块之后的正文。\n")
        }
        assertTrue(content.toByteArray(Charsets.UTF_8).size > 102400 * 2)
        val file = write(content)
        val chapters = scan(file)
        //围栏整块只能落在一个章节里：切开的一半会被独立解析成正文
        chapters.forEach { chapter ->
            val body = bodyOf(file, chapter.start, chapter.end, chapter.startsWithHeading)
            val fences = body.lines().count { it.trimStart().startsWith("```") }
            assertEquals("章节 ${chapter.title} 的围栏不成对:\n$body", 0, fences % 2)
        }
        assertEquals(1, chapters.count { chapter ->
            bodyOf(file, chapter.start, chapter.end, chapter.startsWithHeading).contains("```")
        })
        val first = bodyOf(file, chapters.first().start, chapters.first().end, true)
        assertTrue(first.contains("代码行内容。"))
        assertTrue(bodyOf(file, chapters.last().start, chapters.last().end, chapters.last().startsWithHeading)
            .contains("代码块之后的正文。"))
    }

    @Test
    fun frontMatterHashLinesAreNotChapters() {
        val file = write(
            """
            ---
            # 这是配置注释
            title: 示例
            ---

            # 第一章

            正文。
            """.trimIndent() + "\n"
        )
        val chapters = scan(file)
        assertEquals(listOf("第一章"), chapters.map { it.title })
        val body = bodyOf(file, chapters[0].start, chapters[0].end, true)
        assertTrue(body.contains("正文。"))
        assertFalse(body.contains("title:"))
    }

    @Test
    fun htmlBlockHashLineIsNotAChapter() {
        val file = write(
            """
            # A

            <div>
            # 不是标题
            </div>

            正文。

            # B
            """.trimIndent() + "\n"
        )
        val chapters = scan(file)
        assertEquals(listOf("A", "B"), chapters.map { it.title })
        val body = bodyOf(file, chapters[0].start, chapters[0].end, true)
        assertTrue(body.contains("# 不是标题"))
    }

    @Test
    fun relativePathSafetyAndSiblingIdArePure() {
        assertTrue(MarkdownFile.isSafeRelativePath("images/a.png"))
        assertTrue(MarkdownFile.isSafeRelativePath("a+b.png"))
        assertFalse(MarkdownFile.isSafeRelativePath("../a.png"))
        assertFalse(MarkdownFile.isSafeRelativePath("images/../a.png"))
        assertFalse(MarkdownFile.isSafeRelativePath("C:\\a.png"))
        assertEquals(
            "primary:Download/images/a.png",
            MarkdownFile.siblingDocumentId("primary:Download/book.md", "images/a.png"),
        )
        assertEquals(null, MarkdownFile.siblingDocumentId("book.md", "a.png"))
    }
}
