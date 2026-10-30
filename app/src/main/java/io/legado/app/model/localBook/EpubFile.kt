package io.legado.app.model.localBook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.FileUtils
import io.legado.app.utils.encodeURI
import io.legado.app.utils.isXml
import io.legado.app.utils.printOnDebug
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.domain.TOCReference
import me.ag2s.epublib.epub.EpubReader
import me.ag2s.epublib.util.zip.AndroidZipFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset

class EpubFile(var book: Book) {

    companion object : BaseLocalBookParse {
        private var eFile: EpubFile? = null

        @Synchronized
        private fun getEFile(book: Book): EpubFile {
            if (eFile == null || eFile?.book?.bookUrl != book.bookUrl) {
                eFile = EpubFile(book)
                //对于Epub文件默认不启用替换
                //io.legado.app.data.entities.Book getUseReplaceRule
                return eFile!!
            }
            eFile?.book = book
            return eFile!!
        }

        @Synchronized
        override fun getChapterList(book: Book): ArrayList<BookChapter> {
            return getEFile(book).getChapterList()
        }

        @Synchronized
        override fun getContent(book: Book, chapter: BookChapter): String? {
            return getEFile(book).getContent(chapter)
        }

        @Synchronized
        override fun getImage(
            book: Book,
            href: String
        ): InputStream? {
            return getEFile(book).getImage(href)
        }

        @Synchronized
        override fun upBookInfo(book: Book) {
            return getEFile(book).upBookInfo()
        }

        fun clear() {
            eFile = null
        }
    }

    private var mCharset: Charset = Charset.defaultCharset()

    /**
     *持有引用，避免被回收
     */
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var epubBook: EpubBook? = null
        get() {
            if (field == null || fileDescriptor == null) {
                field = readEpub()
            }
            return field
        }
    private var epubBookContents: List<Resource>? = null
        get() {
            if (field == null || fileDescriptor == null) {
                field = epubBook?.contents
            }
            return field
        }

    init {
        upBookCover(true)
    }

    /**
     * 重写epub文件解析代码，直接读出压缩包文件生成Resources给epublib，这样的好处是可以逐一修改某些文件的格式错误
     */
    private fun readEpub(): EpubBook? {
        return kotlin.runCatching {
            //ContentScheme拷贝到私有文件夹采用懒加载防止OOM
            //val zipFile = BookHelp.getEpubFile(book)
            BookHelp.getBookPFD(book)?.let {
                fileDescriptor = it
                val zipFile = AndroidZipFile(it, book.originName)
                EpubReader().readEpubLazy(zipFile, "utf-8")
            }


        }.onFailure {
            AppLog.put("读取Epub文件失败\n${it.localizedMessage}", it)
            it.printOnDebug()
        }.getOrThrow()
    }

    private fun getContent(chapter: BookChapter): String? {
        /*获取当前章节文本*/
        val contents = epubBookContents ?: return null
        val nextChapterFirstResourceHref = chapter.getVariable("nextUrl").substringBeforeLast("#")
        val currentChapterFirstResourceHref = chapter.url.substringBeforeLast("#")
        val isLastChapter = nextChapterFirstResourceHref.isBlank()
        val startFragmentId = chapter.startFragmentId
        val endFragmentId = chapter.endFragmentId
        val elements = Elements()
        var findChapterFirstSource = false
        val includeNextChapterResource = !endFragmentId.isNullOrBlank()
        /*一些书籍依靠href索引的resource会包含多个章节，需要依靠fragmentId来截取到当前章节的内容*/
        /*注:这里较大增加了内容加载的时间，所以首次获取内容后可存储到本地cache，减少重复加载*/
        for (res in contents) {
            if (!findChapterFirstSource) {
                if (currentChapterFirstResourceHref != res.href) continue
                findChapterFirstSource = true
                // 第一个xhtml文件
                elements.add(
                    getBody(res, startFragmentId, endFragmentId)
                )
                // 不是最后章节 且 已经遍历到下一章节的内容时停止
                if (!isLastChapter && res.href == nextChapterFirstResourceHref) break
                continue
            }
            if (nextChapterFirstResourceHref != res.href) {
                // 其余部分
                elements.add(getBody(res, null, null))
            } else {
                // 下一章节的第一个xhtml
                if (includeNextChapterResource) {
                    //有Fragment 则添加到上一章节
                    elements.add(getBody(res, null, endFragmentId))
                }
                break
            }
        }
        // 如未匹配到任何资源应报告错误，而非返回一个会掩盖问题的空白页面。
        // 目录容器章节跳过资源匹配环节，直接将其渲染为标题页。
        if (!findChapterFirstSource && !chapter.url.startsWith("epub-toc://")) {
            return null
        }
        //title标签中的内容不需要显示在正文中，去除
        elements.select("title").remove()
        LocalBookSemanticHtml.removeHiddenElements(elements)
        elements.select("img[src=\"cover.jpeg\"]").forEachIndexed { i, it ->
            if (i > 0) it.remove()
        }
        LocalBookSemanticHtml.sanitizeImages(elements)
        val tag = Book.rubyTag
        if (book.getDelTag(tag)) {
            elements.select("rp, rt").remove()
        }
        // Inline high-frequency CSS rules (tag/#id/.class) into whitelisted style attributes.
        val cssRules = collectCssRules()
        elements.forEach { body ->
            LocalBookSemanticHtml.inlineCssSemantics(body, cssRules)
        }
        // Mixed channel: plain text/image lines stay replace-friendly,
        // semantic blocks go through single-line <usehtml> wrappers.
        // Title dedup happens at DOM level because ContentProcessor regex cannot see inside <usehtml>.
        // Preserve the old contract: empty volume chapters return "" (not null).
        val channel = elements.flatMap { body ->
            LocalBookSemanticHtml.emitMixedChannel(body, chapter.title)
                .split("\n")
        }.filter { it.isNotBlank() || it.contains("<usehtml>") }
            .joinToString("\n")
        return channel
    }

    private fun collectCssRules(): List<LocalBookSemanticHtml.CssRule> {
        val book = epubBook ?: return emptyList()
        val cssTexts = mutableListOf<Pair<String, String>>()
        runCatching {
            for (res in book.resources.all) {
                val href = res.href ?: continue
                if (!href.endsWith(".css", ignoreCase = true)) continue
                val text = runCatching { String(res.data, Charsets.UTF_8) }.getOrNull()
                if (!text.isNullOrBlank()) cssTexts += href to text
            }
        }
        if (cssTexts.isEmpty()) return emptyList()
        // Deterministic cascade: sort by href so repeated parses agree.
        cssTexts.sortBy { it.first }
        val rules = mutableListOf<LocalBookSemanticHtml.CssRule>()
        var order = 0
        for ((_, css) in cssTexts) {
            val parsed = LocalBookSemanticHtml.parseCssRules(css, order)
            rules += parsed
            order += parsed.size
            // Temporary： high-frequency rules only.
            if (rules.size > 2000) break
        }
        return rules
    }

    private fun getBody(res: Resource, startFragmentId: String?, endFragmentId: String?): Element {
        /**
         * <image width="1038" height="670" xlink:href="..."/>
         * ...titlepage.xhtml
         * 大多数epub文件的封面页都会带有cover，可以一定程度上解决封面读取问题
         */
        if (res.href.contains("titlepage.xhtml") ||
            res.href.contains("cover")
        ) {
            return Jsoup.parseBodyFragment("<img src=\"cover.jpeg\" />")
        }

        // Jsoup可能会修复不规范的xhtml文件 解析处理后再获取
        val bodyElement = Jsoup.parse(String(res.data, mCharset)).body()
        bodyElement.children().run {
            select("script").remove()
            select("style").remove()
        }
        // DOM-order fragment cut
        // 基于DOM边界的截取，去除不robust的‘\n’换行
        LocalBookSemanticHtml.cutFragment(bodyElement, startFragmentId, endFragmentId)
        /*选择去除正文中的H标签，部分书籍标题与阅读标题重复待优化*/
        val tag = Book.hTag
        if (book.getDelTag(tag)) {
            bodyElement.run {
                select("h1, h2, h3, h4, h5, h6").remove()
                //getElementsMatchingOwnText(chapter.title)?.remove()
            }
        }
        bodyElement.select("image").forEach {
            it.tagName("img", Parser.NamespaceHtml)
            it.attr("src", it.attr("xlink:href"))
        }
        bodyElement.select("img").forEach {
            val src = it.attr("src").trim().encodeURI()
            val href = res.href.encodeURI()
            val resolvedHref = URLDecoder.decode(URI(href).resolve(src).toString(), "UTF-8")
            it.attr("src", resolvedHref)
        }
        bodyElement.select("a[href]").forEach {
            val resolvedHref = LocalBookSemanticHtml.resolveRelativeHref(res.href, it.attr("href"))
            if (resolvedHref != null) {
                it.attr("href", resolvedHref)
            }
        }
        return bodyElement
    }

    private fun getImage(href: String): InputStream? {
        if (href == "cover.jpeg") return epubBook?.coverImage?.inputStream
        val abHref = URLDecoder.decode(href, "UTF-8")
        return epubBook?.resources?.getByHref(abHref)?.inputStream
    }

    private fun upBookCover(fastCheck: Boolean = false) {
        try {
            epubBook?.let {
                if (book.coverUrl.isNullOrEmpty()) {
                    book.coverUrl = LocalBook.getCoverPath(book)
                }
                if (fastCheck && File(book.coverUrl!!).exists()) {
                    return
                }
                /*部分书籍DRM处理后，封面获取异常，待优化*/
                it.coverImage?.inputStream?.use { input ->
                    val cover = BitmapFactory.decodeStream(input)
                    val out = FileOutputStream(FileUtils.createFileIfNotExist(book.coverUrl!!))
                    cover.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()
                } ?: AppLog.putDebug("Epub: 封面获取为空. path: ${book.bookUrl}")
            }
        } catch (e: Exception) {
            AppLog.put("加载书籍封面失败\n${e.localizedMessage}", e)
            e.printOnDebug()
        }
    }

    private fun upBookInfo() {
        if (epubBook == null) {
            eFile = null
            book.intro = "书籍导入异常"
        } else {
            upBookCover()
            val metadata = epubBook!!.metadata
            book.name = metadata.firstTitle
            if (book.name.isEmpty()) {
                book.name = book.originName.replace(".epub", "")
            }

            if (metadata.authors.isNotEmpty()) {
                val author =
                    metadata.authors[0].toString().replace("^, |, $".toRegex(), "")
                book.author = author
            }
            if (metadata.descriptions.isNotEmpty()) {
                val desc = metadata.descriptions[0]
                book.intro = if (desc.isXml()) {
                    Jsoup.parse(metadata.descriptions[0]).text()
                } else {
                    desc
                }
            }
        }
    }

    private fun getChapterList(): ArrayList<BookChapter> {
        val chapterList = ArrayList<BookChapter>()
        epubBook?.let { eBook ->
            val refs = eBook.tableOfContents.tocReferences
            if (refs == null || refs.isEmpty()) {
                AppLog.putDebug("Epub: NCX file parse error, check the file: ${book.bookUrl}")
                val spineReferences = eBook.spine.spineReferences
                var i = 0
                val size = spineReferences.size
                while (i < size) {
                    val resource = spineReferences[i].resource
                    var title = resource.title
                    if (TextUtils.isEmpty(title)) {
                        try {
                            val doc =
                                Jsoup.parse(String(resource.data, mCharset))
                            val elements = doc.getElementsByTag("title")
                            if (elements.isNotEmpty()) {
                                title = elements[0].text()
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    val chapter = BookChapter()
                    chapter.index = i
                    chapter.bookUrl = book.bookUrl
                    chapter.url = resource.href
                    if (i == 0 && title.isEmpty()) {
                        chapter.title = "封面"
                    } else {
                        chapter.title = title
                    }
                    chapterList.lastOrNull()?.putVariable("nextUrl", chapter.url)
                    chapterList.add(chapter)
                    i++
                }
            } else {
                parseFirstPage(chapterList, refs)
                parseMenu(chapterList, refs, 0, emptyList())
                for (i in chapterList.indices) {
                    chapterList[i].index = i
                }
            }
        }
        return chapterList
    }

    /*获取书籍起始页内容。部分书籍第一章之前存在封面，引言，扉页等内容*/
    /*tile获取不同书籍风格杂乱，格式化处理待优化*/
    private var durIndex = 0
    private fun parseFirstPage(
        chapterList: ArrayList<BookChapter>,
        refs: List<TOCReference>?
    ) {
        val contents = epubBook?.contents
        if (epubBook == null || contents == null || refs == null) return
        val firstRef = refs.firstOrNull { it.resource != null } ?: return
        var i = 0
        durIndex = 0
        while (i < contents.size) {
            val content = contents[i]
            if (!content.mediaType.toString().contains("htm")) {
                i++
                continue
            }
            /**
             * 检索到第一章href停止
             * completeHref可能有fragment(#id) 必须去除
             * fix https://github.com/gedoor/legado/issues/1932
             */
            if (firstRef.completeHref.substringBeforeLast("#") == content.href) break
            val chapter = BookChapter()
            var title = content.title
            if (TextUtils.isEmpty(title)) {
                val elements = Jsoup.parse(
                    String(epubBook!!.resources.getByHref(content.href).data, mCharset)
                ).getElementsByTag("title")
                title =
                    if (elements.isNotEmpty() && elements[0].text().isNotBlank())
                        elements[0].text()
                    else
                        "--卷首--"
            }
            chapter.bookUrl = book.bookUrl
            chapter.title = title
            chapter.url = content.href
            chapter.startFragmentId =
                if (content.href.substringAfter("#") == content.href) null
                else content.href.substringAfter("#")

            chapterList.lastOrNull()?.endFragmentId = chapter.startFragmentId
            chapterList.lastOrNull()?.putVariable("nextUrl", chapter.url)
            chapterList.add(chapter)
            durIndex++
            i++
        }
    }

    private fun parseMenu(
        chapterList: ArrayList<BookChapter>,
        refs: List<TOCReference>?,
        level: Int,
        parentPath: List<Int>,
    ) {
        refs.orEmpty().forEachIndexed { refIndex, ref ->
            val children = ref.children.orEmpty()
            val path = parentPath + refIndex
            val chapter = BookChapter(
                bookUrl = book.bookUrl,
                title = ref.title,
                isVolume = children.isNotEmpty() || ref.resource == null,
                tocLevel = level,
                url = ref.resource?.let { ref.completeHref }
                    ?: "epub-toc://${path.joinToString("/")}",
                startFragmentId = ref.fragmentId,
            )
            if (ref.resource == null) {
                chapter.putVariable("epubTocContainer", "true")
            } else {
                chapterList.lastOrNull { !it.url.startsWith("epub-toc://") }?.apply {
                    endFragmentId = chapter.startFragmentId
                    putVariable("nextUrl", chapter.url)
                }
            }
            chapterList.add(chapter)
            durIndex++

            if (children.isNotEmpty()) {
                parseMenu(chapterList, children, level + 1, path)
            }
        }
    }


    protected fun finalize() {
        fileDescriptor?.close()
    }

}
