package io.legado.app.help.book

import android.os.Build
import io.legado.app.constant.AppLog
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
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.escapeRegex
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.replace
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class ContentProcessor private constructor(
    private val bookName: String,
    private val bookOrigin: String
) {

    companion object {
        private val processors = hashMapOf<String, WeakReference<ContentProcessor>>()
        private val isAndroid8 = Build.VERSION.SDK_INT in 26..27
        private val revision = AtomicLong()
        val semanticRevision: Long get() = revision.get()
        fun invalidateSemanticContent() { revision.incrementAndGet() }

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
            revision.incrementAndGet()
            processors.forEach {
                it.value.get()?.upReplaceRules()
            }
        }

    }

    private val titleReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    private val contentReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    val removeSameTitleCache = hashSetOf<String>()

    private val otherGateway by lazy { GlobalContext.get().get<OtherSettingsGateway>() }
    private val readGateway by lazy { GlobalContext.get().get<ReadSettingsGateway>() }

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
        revision.incrementAndGet()
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

    data class SettingsSnapshot(
        val chineseConverterType: Int,
        val adaptSpecialStyle: Boolean,
        val replaceEnableDefault: Boolean,
    )

    fun captureSettings(): SettingsSnapshot {
        val read = readGateway.currentSettings
        return SettingsSnapshot(read.chineseConverterType, read.adaptSpecialStyle,
            otherGateway.currentSettings.replaceEnableDefault)
    }

    fun getContent(
        book: Book,
        chapter: BookChapter,
        content: String,
        includeTitle: Boolean = true,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        reSegment: Boolean = true,
    ): BookContent = getContent(book, chapter, content, includeTitle, useReplace, chineseConvert,
        reSegment, paragraphIndent = null)

    fun getContent(
        book: Book,
        chapter: BookChapter,
        content: String,
        includeTitle: Boolean = true,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        reSegment: Boolean = true,
        paragraphIndent: String?,
        settings: SettingsSnapshot = captureSettings(),
    ): BookContent {
        val indent = paragraphIndent ?: ReadBookConfig.paragraphIndent
        var mContent = content
        var sameTitleRemoved = false
        var effectiveReplaceRules: ArrayList<ReplaceRule>? = null
        var effectiveContentProcesses: List<BookContentProcess> = emptyList()
        if (content != "null") {
            //去除重复标题
            val fileName = chapter.getFileName("nr")
            if (!removeSameTitleCache.contains(fileName)) try {
                val name = Regex.escape(book.name)
                var title = chapter.title.escapeRegex().replace(spaceRegex, "\\\\s*")
                var match = Regex("^(\\s|\\p{P}|${name})*${title}(\\s)*")
                    .find(mContent)
                if (match != null) {
                    mContent = mContent.substring(match.range.last + 1)
                    sameTitleRemoved = true
                } else if (useReplace && book.getUseReplaceRule(settings.replaceEnableDefault)) {
                    title = Regex.escape(
                        chapter.getDisplayTitle(
                            contentReplaceRules,
                            chineseConvert = false,
                            chineseConverterType = settings.chineseConverterType,
                        )
                    )
                    match = Regex("^(\\s|\\p{P}|${name})*${title}(\\s)*")
                        .find(mContent)
                    if (match != null) {
                        mContent = mContent.substring(match.range.last + 1)
                        sameTitleRemoved = true
                    }
                }
            } catch (e: Exception) {
                AppLog.put("去除重复标题出错\n${e.localizedMessage}", e)
            }
            if (reSegment && book.getReSegment()) {
                mContent = MixedContentSegments.transform(mContent,
                    plain = { ContentHelp.reSegment(it, chapter.title) }, rich = { it })
            }
            if (chineseConvert) {
                try {
                    val convert: ((String) -> String)? = when (settings.chineseConverterType) {
                        1 -> ChineseUtils::t2s
                        2 -> ChineseUtils::s2t
                        else -> null
                    }
                    if (convert != null) mContent = MixedContentSegments.transform(mContent,
                        plain = { MixedContentSegments.transformPlainText(it, convert) }, rich = { MixedContentSegments.transformHtmlText(it, convert) })
                } catch (_: Exception) {
                    appCtx.toastOnUi("简繁转换出错")
                }
            }
            if (useReplace && book.getUseReplaceRule(settings.replaceEnableDefault)) {
                val replaceBook = book.toSearchBook()
                //替换
                effectiveReplaceRules = arrayListOf()
                mContent = MixedContentSegments.transform(mContent,
                    plain = { it.lines().joinToString("\n") { line -> line.trim() } }, rich = { it })
                getContentReplaceRules().forEach { item ->
                    if (item.pattern.isEmpty()) {
                        return@forEach
                    }
                    try {
                        val started = System.nanoTime()
                        val timeout = item.getValidTimeoutMillisecond()
                        fun replaceText(text: String): String = if (item.isRegex) {
                            val remaining = timeout - (System.nanoTime() - started) / 1_000_000
                            if (remaining <= 0) throw RegexTimeoutException("替换规则超时: ${item.name}")
                            text.replace(item.regex, item.replacement, remaining, chapter, replaceBook)
                        } else text.replace(item.pattern, item.replacement)
                        // Preserve rule order and the existing timeout/error handling. Rich nodes
                        // are decoded by Jsoup; regexes never see attributes or serialized markup.
                        val tmp = MixedContentSegments.transform(mContent,
                            plain = ::replaceText,
                            rich = { MixedContentSegments.transformHtmlText(it, ::replaceText) })
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
                    BookContentProcessEngine.apply(mContent, contentProcesses + markings,
                        if (settings.adaptSpecialStyle) ProcessedContentEditor(indent) else null)
                mContent = applyResult.text
                effectiveContentProcesses = applyResult.effectiveProcesses
                applyResult.unsupportedEdits.forEach { AppLog.put("正文编辑未应用: $it") }
            }
        }
        if (includeTitle) {
            mContent = chapter.getDisplayTitle(
                getTitleReplaceRules(),
                useReplace = useReplace && book.getUseReplaceRule(settings.replaceEnableDefault),
                chineseConverterType = settings.chineseConverterType,
            ) + "\n" + mContent
        }
        if (isAndroid8) mContent = mContent.replace('\u00A0', ' ')
        val contents = MixedContentSegments.paragraphs(mContent, indent, settings.adaptSpecialStyle,
            firstUnindented = includeTitle)
        return BookContent(sameTitleRemoved, contents, effectiveReplaceRules, effectiveContentProcesses)
    }

    /**
     * 把用户划线/高亮笔记转成「仅供渲染」的合成正文处理记录。
     *
     * 引擎对标记类（KIND_USER_UNDERLINE/HIGHLIGHT）不改文本；渲染层在最终语义正文
     * 中解析 anchorJson，再用 styleJson 画线。id 加
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
