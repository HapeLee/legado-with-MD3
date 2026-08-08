package io.legado.app.help.book

import android.os.Build
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.AppPattern.spaceRegex
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.domain.model.BookContentProcessEngine
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.escapeRegex
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.replace
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import splitties.init.appCtx
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

class ContentProcessor private constructor(
    private val bookName: String,
    private val bookOrigin: String
) {

    companion object {
        private val processors = hashMapOf<String, WeakReference<ContentProcessor>>()
        private val isAndroid8 = Build.VERSION.SDK_INT in 26..27

        fun get(book: Book) = get(book.name, book.origin)

        fun get(bookName: String, bookOrigin: String): ContentProcessor {
            val processorWr = processors[bookName + bookOrigin]
            var processor: ContentProcessor? = processorWr?.get()
            if (processor == null) {
                processor = ContentProcessor(bookName, bookOrigin)
                processors[bookName + bookOrigin] = WeakReference(processor)
            }
            return processor
        }

        fun upReplaceRules() {
            processors.forEach {
                it.value.get()?.upReplaceRules()
            }
        }

    }

    private val titleReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    private val contentReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    val removeSameTitleCache = hashSetOf<String>()

    init {
        upReplaceRules()
        upRemoveSameTitle()
    }

    fun upReplaceRules() {
        titleReplaceRules.run {
            clear()
            addAll(appDb.replaceRuleDao.findEnabledByTitleScope(bookName, bookOrigin))
        }
        contentReplaceRules.run {
            clear()
            addAll(appDb.replaceRuleDao.findEnabledByContentScope(bookName, bookOrigin))
        }
    }

    private fun upRemoveSameTitle() {
        val book = appDb.bookDao.getBookByOrigin(bookName, bookOrigin) ?: return
        removeSameTitleCache.clear()
        val files = BookHelp.getChapterFiles(book).filter {
            it.endsWith("nr")
        }
        removeSameTitleCache.addAll(files)
    }

    fun getTitleReplaceRules(): List<ReplaceRule> {
        return titleReplaceRules
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getContentReplaceRules(): List<ReplaceRule> {
        return contentReplaceRules
    }

    fun getContent(
        book: Book,
        chapter: BookChapter,
        content: String,
        includeTitle: Boolean = true,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        reSegment: Boolean = true
    ): BookContent {
        var mContent = content
        var sameTitleRemoved = false
        var effectiveReplaceRules: ArrayList<ReplaceRule>? = null
        var effectiveContentProcesses: List<BookContentProcess> = emptyList()
        if (content != "null") {
            //去除重复标题
            val fileName = chapter.getFileName("nr")
            if (!removeSameTitleCache.contains(fileName)) try {
                val name = Pattern.quote(book.name)
                var title = chapter.title.escapeRegex().replace(spaceRegex, "\\\\s*")
                var matcher = Pattern.compile("^(\\s|\\p{P}|${name})*${title}(\\s)*")
                    .matcher(mContent)
                if (matcher.find()) {
                    mContent = mContent.substring(matcher.end())
                    sameTitleRemoved = true
                } else if (useReplace && book.getUseReplaceRule(AppConfig.replaceEnableDefault)) {
                    title = Pattern.quote(
                        chapter.getDisplayTitle(
                            contentReplaceRules,
                            chineseConvert = false,
                            chineseConverterType = AppConfig.chineseConverterType,
                        )
                    )
                    matcher = Pattern.compile("^(\\s|\\p{P}|${name})*${title}(\\s)*")
                        .matcher(mContent)
                    if (matcher.find()) {
                        mContent = mContent.substring(matcher.end())
                        sameTitleRemoved = true
                    }
                }
            } catch (e: Exception) {
                AppLog.put("去除重复标题出错\n${e.localizedMessage}", e)
            }
            if (reSegment && book.getReSegment()) {
                //重新分段
                mContent = ContentHelp.reSegment(mContent, chapter.title)
            }
            if (chineseConvert) {
                //简繁转换
                try {
                    when (AppConfig.chineseConverterType) {
                        1 -> mContent = ChineseUtils.t2s(mContent)
                        2 -> mContent = ChineseUtils.s2t(mContent)
                    }
                } catch (_: Exception) {
                    appCtx.toastOnUi("简繁转换出错")
                }
            }
            val useHtmlMap = mutableMapOf<String, String>()
            if (AppConfig.adaptSpecialStyle) { //html处理
                mContent = AppPattern.useHtmlRegex.replace(mContent) { matchResult ->
                    val placeholder = "特殊格式的占位不应该被看见${useHtmlMap.size}。"
                    useHtmlMap[placeholder] = "\n${matchResult.value.replace("\n", "")}\n"
                    placeholder
                }
            }
            if (useReplace && book.getUseReplaceRule(AppConfig.replaceEnableDefault)) {
                val replaceBook = book.toSearchBook()
                //替换
                effectiveReplaceRules = arrayListOf()
                mContent = mContent.lines().joinToString("\n") { it.trim() }
                getContentReplaceRules().forEach { item ->
                    if (item.pattern.isEmpty()) {
                        return@forEach
                    }
                    try {
                        val tmp = if (item.isRegex) {
                            mContent.replace(
                                item.regex,
                                item.replacement,
                                item.getValidTimeoutMillisecond(),
                                chapter,
                                replaceBook
                            )
                        } else {
                            mContent.replace(item.pattern, item.replacement)
                        }
                        if (mContent != tmp) {
                            effectiveReplaceRules.add(item)
                            mContent = tmp
                        }
                    } catch (e: RegexTimeoutException) {
                        item.isEnabled = false
                        appDb.replaceRuleDao.update(item)
                        mContent = item.name + e.stackTraceStr
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        AppLog.put("替换净化: 规则 ${item.name}替换出错.\n${mContent}", e)
                        appCtx.toastOnUi("替换净化: 规则 ${item.name}替换出错")
                    }
                }
            }
            useHtmlMap.forEach { (placeholder, originalContent) ->
                mContent = mContent.replace(placeholder, originalContent)
            }
            val contentProcesses = appDb.bookContentProcessDao.getForChapterSync(
                bookUrl = book.bookUrl,
                chapterIndex = chapter.index,
            )
            // 用户划线/高亮笔记独立存于 book_marks，渲染时转成合成 BookContentProcess
            // 混进现有管线（锚点/样式不变，引擎对标记类不改文本）。
            val markings = appDb.bookMarkingDao.getForChapterSync(
                bookUrl = book.bookUrl,
                chapterIndex = chapter.index,
            ).map { it.toRenderProcess() }
            if (contentProcesses.isNotEmpty() || markings.isNotEmpty()) {
                val applyResult =
                    BookContentProcessEngine.apply(mContent, contentProcesses + markings)
                mContent = applyResult.text
                effectiveContentProcesses = applyResult.effectiveProcesses
            }
        }
        if (includeTitle) {
            //重新添加标题
            mContent = chapter.getDisplayTitle(
                getTitleReplaceRules(),
                useReplace = useReplace && book.getUseReplaceRule(AppConfig.replaceEnableDefault),
                chineseConverterType = AppConfig.chineseConverterType,
            ) + "\n" + mContent
        }
        if (isAndroid8) {
            mContent = mContent.replace('\u00A0', ' ')
        }
        val contents = arrayListOf<String>()
        mContent.split("\n").forEach { str ->
            val paragraph = str.trim {
                it.code <= 0x20 || it == '　'
            }
            if (paragraph.isNotEmpty()) {
                if (contents.isEmpty() && includeTitle) {
                    contents.add(paragraph)
                } else {
                    contents.add("${ReadBookConfig.paragraphIndent}$paragraph")
                }
            }
        }
        return BookContent(sameTitleRemoved, contents, effectiveReplaceRules, effectiveContentProcesses)
    }

    /**
     * 把用户划线/高亮笔记转成「仅供渲染」的合成正文处理记录。
     *
     * 引擎对标记类（KIND_USER_UNDERLINE/HIGHLIGHT）不改文本，只按锚点能否解析决定
     * 是否计入 effectiveProcesses；渲染层用 anchorJson + styleJson 画线。id 加
     * `mark:` 前缀避免与真实正文处理记录冲突。
     */
    private fun BookMarking.toRenderProcess(): BookContentProcess {
        // book_marks 无 kind 列（样式即类型）：按 styleJson 推导，供引擎/渲染层识别
        val style = styleJson?.let { GSON.fromJsonObject<TextProcessStyle>(it).getOrNull() }
        val derivedKind = if (style?.underlineMode != 0) {
            BookContentProcess.KIND_USER_UNDERLINE
        } else {
            BookContentProcess.KIND_USER_HIGHLIGHT
        }
        return BookContentProcess(
            id = "mark:$id",
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            kind = derivedKind,
            stage = BookContentProcess.STAGE_CONTENT,
            target = BookContentProcess.TARGET_SELECTION,
            anchorJson = anchorJson,
            actionJson = GSON.toJson(TextProcessAction(TextProcessAction.TYPE_MARK)),
            styleJson = styleJson,
            source = BookContentProcess.SOURCE_USER,
            enabled = enabled,
            status = BookContentProcess.STATUS_ACTIVE,
            sortOrder = 0,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

}
