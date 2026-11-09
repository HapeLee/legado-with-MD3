package io.legado.app.model.localBook

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.EmptyFileException
import io.legado.app.feature.reader.core.source.ReaderLocalPresentation
import io.legado.app.feature.reader.platform.ReaderLocalPresentationResolver
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.upKind
import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.StringUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.Charset

/**
 * 本地 markdown 书籍（.md/.markdown）。
 *
 * 与 TextFile 同构：流式扫描原文件记录章节字节偏移；内容按章节范围读取后经
 * [MarkdownToHtml] 转成语义 HTML，纯文本走 [LocalBookSemanticHtml.plainText]、
 * 富文本走 [ReaderLocalPresentationResolver.resolve]，与 EPUB 共用同一套呈现通道。
 *
 * 目录策略：`#`/`##` 标题行是章节边界；围栏代码块内的 `#` 行不算；
 * 全文没有任何一二级标题时作为「全文」章，超长时同样按空行切块，避免整章读入。
 */
class MarkdownFile(private var book: Book) {

    companion object {
        //换行单元随编码变化：UTF-16 的换行是双字节，单字节 0x0A 切分会把尾字节留给下一行
        private val singleByteLineBreak = byteArrayOf(0x0a)
        private val utf16LeLineBreak = byteArrayOf(0x0a, 0x00)
        private val utf16BeLineBreak = byteArrayOf(0x00, 0x0a)

        private const val scanBufferSize = 512000
        private const val readChunkSize = 512 * 1024

        //与 TextFile.maxLengthWithToc 同量级：超长章节按空行（退化为按行）切块
        private const val maxLengthWithToc = 102400

        private val headingRegex = Regex("^ {0,3}(#{1,6})[ \t]+(.*)$")
        //CommonMark：闭合 # 序列前必须有空白，否则属于标题内容（如「使用 C#」）
        private val closingHashesRegex = Regex("[ \t]+#+[ \t]*$")
        private val fenceRegex = Regex("^(`{3,}|~{3,})")

        private var markdownFile: MarkdownFile? = null

        @Synchronized
        private fun getMarkdownFile(book: Book): MarkdownFile {
            if (markdownFile == null || markdownFile?.book?.bookUrl != book.bookUrl || book.isLocalModified()) {
                markdownFile = MarkdownFile(book)
                return markdownFile!!
            }
            markdownFile?.book = book
            return markdownFile!!
        }

        @Synchronized
        @Throws(FileNotFoundException::class)
        fun getChapterList(book: Book): ArrayList<BookChapter> {
            return getMarkdownFile(book).getChapterList()
        }

        @Synchronized
        @Throws(FileNotFoundException::class)
        fun getContent(book: Book, bookChapter: BookChapter): String {
            return getMarkdownFile(book).getContent(bookChapter)
        }

        @Synchronized
        fun getPresentation(book: Book, bookChapter: BookChapter): ReaderLocalPresentation? {
            return getMarkdownFile(book).getPresentation(bookChapter)
        }

        /**
         * 相对路径图片按 markdown 文件所在目录解析；书籍无法定位到真实文件
         * （SAF content URI 等拿不到兄弟文件）时返回 null，由调用方降级为占位图。
         * 网络图与 data URI 不走这里（ImageProvider 分流到下载路径）。
         */
        fun getImage(book: Book, src: String): InputStream? {
            val base = bookFileParent(book.bookUrl) ?: return null
            val raw = src.trim()
            //URLDecoder 会把字面 '+' 当空格，先转义以保留文件名里的 '+'
            val cleaned = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }
                .getOrDefault(raw)
            if (cleaned.isEmpty()) return null
            //图片只能来自书籍所在目录：拒绝绝对路径，并用 canonical 路径阻断 ../ 穿越
            if (File(cleaned).isAbsolute) return null
            val baseDir = File(base).canonicalFile
            val basePath = baseDir.path.removeSuffix(File.separator)
            val file = File(baseDir, cleaned).canonicalFile
            if (!file.path.startsWith(basePath + File.separator)) return null
            if (!file.isFile) return null
            return runCatching { file.inputStream() }.getOrNull()
        }

        private fun bookFileParent(bookUrl: String): String? {
            val path = when {
                bookUrl.startsWith("file://", true) ->
                    runCatching { URLDecoder.decode(bookUrl.removePrefix("file://"), "UTF-8") }
                        .getOrDefault(bookUrl.removePrefix("file://"))
                File(bookUrl).isAbsolute -> bookUrl
                //content:// 等单文档 URI 无法访问兄弟文件，显式降级
                else -> return null
            }
            return File(path).parent
        }

        @Synchronized
        fun clear() {
            markdownFile = null
        }
    }

    private var charset: Charset = book.fileCharset()

    private var cachedChapterUrl: String? = null
    private var cachedCharset: Charset? = null
    private var cachedHtml: String? = null
    private var cachedPlain: String? = null

    internal class ScanChapter(
        val start: Long,
        val baseTitle: String,
        var title: String,
        val startsWithHeading: Boolean,
    ) {
        var end: Long = start
        var charCount: Int = 0
        var subNumber: Int = 0
    }

    @Throws(FileNotFoundException::class, EmptyFileException::class)
    fun getChapterList(): ArrayList<BookChapter> {
        if (book.charset.isNullOrBlank() || book.isLocalModified()) {
            LocalBook.getBookInputStream(book).use { bis ->
                val buffer = ByteArray(scanBufferSize)
                val length = bis.read(buffer)
                if (length == -1) throw EmptyFileException("Unexpected Empty Markdown File")
                book.charset = EncodingDetect.getEncode(buffer.copyOf(length))
            }
        }
        charset = book.fileCharset()
        //重新生成目录（含切换编码后重扫）时丢弃上一次的转换缓存
        cachedChapterUrl = null
        cachedCharset = null
        cachedHtml = null
        cachedPlain = null
        val ranges = scan { LocalBook.getBookInputStream(book) }
        val toc = ArrayList<BookChapter>(ranges.size)
        ranges.forEachIndexed { index, range ->
            val chapter = BookChapter()
            chapter.title = range.title
            chapter.start = range.start
            chapter.end = range.end
            chapter.wordCount = StringUtils.wordCountFormat(range.charCount)
            chapter.bookUrl = book.bookUrl
            chapter.index = index
            chapter.url = MD5Utils.md5Encode16(book.originName + index + chapter.title)
            if (range.startsWithHeading) chapter.putVariable("mdHeading", "1")
            toc.add(chapter)
        }
        //本地目录规则对 md 只是占位；用户改过的值不再被无条件覆盖
        if (book.tocUrl.isBlank()) book.tocUrl = "^#{1,2}\\s+\\S"
        book.wordCount = StringUtils.wordCountFormat(ranges.sumOf { it.charCount })
        book.upKind()
        return toc
    }

    fun getContent(chapter: BookChapter): String {
        charset = book.fileCharset()
        return converted(chapter).second
    }

    fun getPresentation(chapter: BookChapter): ReaderLocalPresentation? {
        charset = book.fileCharset()
        return ReaderLocalPresentationResolver.resolve(converted(chapter).first)
    }

    /** 同一章的 HTML 与纯文本必须出自同一次转换，两条通道才能绑定。 */
    private fun converted(chapter: BookChapter): Pair<String, String> {
        cachedChapterUrl?.let { url ->
            if (url == chapter.url && cachedCharset == charset) return cachedHtml!! to cachedPlain!!
        }
        val body = readChapterBody(
            { LocalBook.getBookInputStream(book) },
            chapter.start!!,
            chapter.end!!,
            chapter.getVariable("mdHeading") == "1",
        )
        val html = MarkdownToHtml.convert(body)
        val plain = LocalBookSemanticHtml.plainText(html)
        cachedChapterUrl = chapter.url
        cachedCharset = charset
        cachedHtml = html
        cachedPlain = plain
        return html to plain
    }

    /** 章节正文 = 字节范围解码；标题章再剥掉首行（标题行包含在章节范围内）。 */
    internal fun readChapterBody(
        openStream: () -> InputStream,
        start: Long,
        end: Long,
        stripHeadingLine: Boolean,
    ): String {
        val raw = readRange(openStream, start, end)
        return if (stripHeadingLine) raw.substringAfter('\n', "") else raw
    }

    private fun readRange(openStream: () -> InputStream, start: Long, end: Long): String {
        val size = end - start
        if (size <= 0) return ""
        //避免 size.toInt() 溢出成负数后抛出含义不明的 NegativeArraySizeException
        if (size > Int.MAX_VALUE) throw FileNotFoundException("markdown 章节过大: $size bytes")
        openStream().use { bis ->
            var skipped = 0L
            while (skipped < start) {
                val step = bis.skip(start - skipped)
                if (step <= 0) break
                skipped += step
            }
            if (skipped < start) throw FileNotFoundException("markdown seek 失败: $start")
            val bytes = ByteArray(size.toInt())
            var read = 0
            while (read < bytes.size) {
                val n = bis.read(bytes, read, bytes.size - read)
                if (n <= 0) break
                read += n
            }
            return String(bytes, 0, read, charset)
        }
    }

    /**
     * 当前编码的换行字节序列。UTF-16 的换行占两个字节（LE `0A 00` / BE `00 0A`），
     * 只按单字节 `0A` 切行会把尾字节留给下一行，后续每行都解码错位、标题随之漏识别。
     * 返回 null 表示 charset 只是 "UTF-16"，字节序还得看 BOM。
     */
    private fun lineBreakBytes(): ByteArray? {
        val name = charset.name().uppercase()
        if (!name.startsWith("UTF-16")) return singleByteLineBreak
        return when {
            name.endsWith("LE") -> utf16LeLineBreak
            name.endsWith("BE") -> utf16BeLineBreak
            else -> null
        }
    }

    /** "UTF-16" 的字节序由 BOM 决定；无 BOM 时 Java 按大端解码。 */
    private fun utf16LineBreak(head: ByteArray, headLength: Int): ByteArray {
        val littleEndian = headLength >= 2 &&
                head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()
        return if (littleEndian) utf16LeLineBreak else utf16BeLineBreak
    }

    private fun isLineBreakAt(buffer: ByteArray, index: Int, lineBreak: ByteArray): Boolean {
        for (offset in lineBreak.indices) {
            if (buffer[index + offset] != lineBreak[offset]) return false
        }
        return true
    }

    /**
     * 流式扫描：按行记录 `#`/`##` 章节边界的字节偏移，围栏代码块内不识别标题；
     * 超长章节在空行处切块（兜底按行切）。任何文件都至少产出一章。
     */
    internal fun scan(openStream: () -> InputStream): ArrayList<ScanChapter> {
        val ranges = ArrayList<ScanChapter>()
        val prefixRanges = ArrayList<ScanChapter>()
        val splitLong = book.getSplitLongChapter()
        var inFence = false
        var fenceChar = '`'
        var cur: ScanChapter? = null
        var pending: ScanChapter? = null
        var prefixCur: ScanChapter? = null
        var prefixAdopted = false
        var prefixStart = -1L
        var prefixChars = 0
        val prefixSample = StringBuilder()

        fun closeCurrent(end: Long) {
            cur?.let {
                it.end = end
                ranges.add(it)
            }
            cur = null
        }

        /** 把「第一个标题前」的切块结果作为前言/全文收录；只有发生切块时才生效。 */
        fun adoptPrefixRanges(label: String): Boolean {
            if (prefixAdopted || prefixRanges.isEmpty()) return false
            prefixAdopted = true
            prefixCur?.takeIf { it.end > it.start }?.let { prefixRanges.add(it) }
            prefixRanges.forEachIndexed { index, block ->
                block.title = if (prefixRanges.size == 1) label else "$label(${index + 1})"
            }
            ranges.addAll(prefixRanges)
            return true
        }

        fun processLine(buf: ByteArray, from: Int, toExclusive: Int, lineStart: Long) {
            val lineEnd = lineStart + (toExclusive - from)
            val line = String(buf, from, toExclusive - from, charset)
                .removePrefix("\uFEFF")
                .trimEnd('\r', '\n')

            //上一行是章节标题：新章从标题行开始，本行起计入正文
            pending?.let {
                cur = it
                pending = null
            }

            val trimmed = line.trimStart(' ', '\t')
            val fenceMatch = fenceRegex.find(trimmed)
            if (fenceMatch != null) {
                val ch = fenceMatch.groupValues[1][0]
                if (!inFence) {
                    inFence = true
                    fenceChar = ch
                } else if (ch == fenceChar && trimmed.substring(fenceMatch.value.length).isBlank()) {
                    inFence = false
                }
            }

            if (!inFence) {
                val m = headingRegex.matchEntire(line)
                if (m != null && m.groupValues[1].length <= 2) {
                    val title = closingHashesRegex.replace(m.groupValues[2], "").trim()
                    if (title.isNotEmpty()) {
                        closeCurrent(lineStart)
                        if (ranges.isEmpty()) {
                            if (!adoptPrefixRanges("前言") && prefixStart >= 0) {
                                //第一个标题前的独立内容作为前言
                                ranges.add(ScanChapter(prefixStart, "前言", "前言", false).apply {
                                    end = lineStart
                                    charCount = prefixChars
                                })
                            }
                            if (prefixStart >= 0 && prefixSample.length <= 500) {
                                book.intro = prefixSample.toString().trim()
                            }
                        }
                        pending = ScanChapter(lineStart, title, title, true)
                        return
                    }
                }
            }

            val current = cur
            if (current == null) {
                //第一个标题前的内容
                if (line.isNotBlank()) {
                    if (prefixStart < 0) prefixStart = lineStart
                    prefixChars += line.length + 1
                    if (prefixSample.length <= 500) prefixSample.append(line).append('\n')
                }
                //无标题文件或超长前言也要切块：用隐式章跟踪空行切点，
                //只有被 adoptPrefixRanges 采用时才进入目录。
                if (splitLong) {
                    val active = prefixCur ?: ScanChapter(0L, "全文", "全文", false)
                        .also { prefixCur = it }
                    active.charCount += line.length + 1
                    active.end = lineEnd
                    val used = lineEnd - active.start
                    //围栏代码块内不切章：切片会被独立解析，切在代码块中间会让后半段代码变成正文。
                    if (!inFence && used > maxLengthWithToc &&
                        (line.isBlank() || used > maxLengthWithToc * 2)
                    ) {
                        val nextSub = if (active.subNumber == 0) 2 else active.subNumber + 1
                        if (active.subNumber == 0) active.title = "${active.baseTitle}(1)"
                        prefixRanges.add(active)
                        prefixCur = ScanChapter(
                            lineEnd, active.baseTitle, "${active.baseTitle}($nextSub)", false
                        ).apply { subNumber = nextSub }
                    }
                }
            } else {
                current.charCount += line.length + 1
                current.end = lineEnd
                val used = lineEnd - current.start
                //围栏代码块内不切章（同上）：闭合围栏行本身已把 inFence 复位，仍是干净的块边界。
                if (splitLong && !inFence && used > maxLengthWithToc &&
                    (line.isBlank() || used > maxLengthWithToc * 2)
                ) {
                    val nextSub = if (current.subNumber == 0) 2 else current.subNumber + 1
                    if (current.subNumber == 0) current.title = "${current.baseTitle}(1)"
                    ranges.add(current)
                    cur = ScanChapter(lineEnd, current.baseTitle, "${current.baseTitle}($nextSub)", false)
                        .apply { subNumber = nextSub }
                }
            }
        }

        var lineStart = 0L
        var carry = ByteArray(0)
        var carryLen = 0
        openStream().use { bis ->
            val chunk = ByteArray(readChunkSize)
            val declaredBreak = lineBreakBytes()
            var breakBytes = singleByteLineBreak
            if (declaredBreak == null) {
                //"UTF-16" 未指明字节序：先读两个字节看 BOM，再决定换行与扫描步长
                val head = ByteArray(2)
                var headLen = 0
                while (headLen < 2) {
                    val n = bis.read(head, headLen, 2 - headLen)
                    if (n <= 0) break
                    headLen += n
                }
                breakBytes = utf16LineBreak(head, headLen)
                carry = head.copyOf(headLen)
                carryLen = headLen
            } else {
                breakBytes = declaredBreak
            }
            //按换行单元步进，保证 UTF-16 下每个切点都落在字符边界上
            val step = breakBytes.size
            while (true) {
                val n = bis.read(chunk)
                if (n <= 0) break
                if (carryLen + n > carry.size) {
                    carry = carry.copyOf(maxOf(carry.size * 2, carryLen + n))
                }
                chunk.copyInto(carry, carryLen, 0, n)
                carryLen += n
                var lineBegin = 0
                var i = 0
                while (i + step <= carryLen) {
                    if (isLineBreakAt(carry, i, breakBytes)) {
                        processLine(carry, lineBegin, i + step, lineStart)
                        lineStart += (i + step - lineBegin)
                        lineBegin = i + step
                    }
                    i += step
                }
                if (lineBegin > 0) {
                    System.arraycopy(carry, lineBegin, carry, 0, carryLen - lineBegin)
                    carryLen -= lineBegin
                }
            }
            if (carryLen > 0) {
                processLine(carry, 0, carryLen, lineStart)
                lineStart += carryLen
            }
        }

        //尾部标题章也必须给出有效范围，避免 start == end 的零长度章节
        pending?.let { ranges.add(it.apply { end = lineStart }) }
        closeCurrent(lineStart)
        if (ranges.isEmpty() && !adoptPrefixRanges("全文")) {
            //全文没有任何一二级标题：整个文件一章
            ranges.add(ScanChapter(0, "全文", "全文", false).apply {
                end = lineStart
                charCount = prefixChars
            })
        }
        return ranges
    }
}
