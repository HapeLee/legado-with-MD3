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
import io.legado.app.domain.model.TextProjection
import io.legado.app.feature.reader.core.source.ReaderLocalPresentation
import io.legado.app.utils.replaceMapped
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.BookContentProcessEngine
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ContentProcessor private constructor(
    private val bookName: String,
    private val bookOrigin: String
) {

    companion object {
        private val processors = hashMapOf<String, WeakReference<ContentProcessor>>()
        private val isAndroid8 = Build.VERSION.SDK_INT in 26..27
        private val revision = AtomicLong()
        private val ruleRevision = AtomicLong()
        private val bookRevisions = ConcurrentHashMap<String, Long>()
        private val wholeBookRevisions = ConcurrentHashMap<String, Long>()
        private val chapterRevisions = ConcurrentHashMap<Pair<String, Int>, Long>()
        val rulesRevision: Long get() = ruleRevision.get()

        fun semanticRevision(bookUrl: String): Long = bookRevisions[bookUrl] ?: 0

        fun chapterRevision(bookUrl: String, chapterIndex: Int): Long = maxOf(
            wholeBookRevisions[bookUrl] ?: 0,
            chapterRevisions[bookUrl to chapterIndex] ?: 0,
        )

        fun invalidateSemanticContent(bookUrl: String, chapterIndex: Int? = null) {
            val next = revision.incrementAndGet()
            if (chapterIndex == null) {
                wholeBookRevisions.merge(bookUrl, next) { a, b -> maxOf(a, b) }
                // The whole-book revision supersedes per-chapter entries; drop them so the maps do
                // not grow with every chapter the user has ever opened.
                chapterRevisions.keys.removeIf { it.first == bookUrl }
            } else {
                chapterRevisions.merge(bookUrl to chapterIndex, next) { a, b -> maxOf(a, b) }
            }
            bookRevisions.merge(bookUrl, next) { a, b -> maxOf(a, b) }
        }

        fun get(book: Book) = get(book.name, book.origin)

        @Synchronized
        fun get(bookName: String, bookOrigin: String): ContentProcessor {
            val processorWr = processors[bookName + bookOrigin]
            var processor: ContentProcessor? = processorWr?.get()
            if (processor == null) {
                processor = ContentProcessor(bookName, bookOrigin)
                processors[bookName + bookOrigin] = WeakReference(processor)
            }
            return processor
        }

        @Synchronized
        fun upReplaceRules() {
            processors.forEach {
                it.value.get()?.loadReplaceRules()
            }
            ruleRevision.incrementAndGet()
        }

    }

    private val titleReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    private val contentReplaceRules = CopyOnWriteArrayList<ReplaceRule>()
    val removeSameTitleCache = hashSetOf<String>()

    private val otherGateway by lazy { GlobalContext.get().get<OtherSettingsGateway>() }
    private val readGateway by lazy { GlobalContext.get().get<ReadSettingsGateway>() }

    init {
        loadReplaceRules()
        upRemoveSameTitle()
    }

    fun upReplaceRules() {
        loadReplaceRules()
        ruleRevision.incrementAndGet()
    }

    private fun loadReplaceRules() {
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
        includePresentation: Boolean = false,
    ): BookContent = getContent(book, chapter, content, includeTitle, useReplace, chineseConvert,
        reSegment, paragraphIndent, settings, includePresentation, localPresentationProvider = null)


    internal fun getContent(
        book: Book,
        chapter: BookChapter,
        content: String,
        includeTitle: Boolean = true,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        reSegment: Boolean = true,
        paragraphIndent: String?,
        settings: SettingsSnapshot = captureSettings(),
        includePresentation: Boolean = false,
        localPresentationProvider: (() -> ReaderLocalPresentation?)?,
    ): BookContent {
        val indent = paragraphIndent ?: ReadBookConfig.paragraphIndent
        val canonicalInput = content
        var presentation = if (includePresentation && !includeTitle && settings.adaptSpecialStyle && (book.isEpub || book.isMobi)) {
            try {
                if (localPresentationProvider != null) {
                    localPresentationProvider()
                } else io.legado.app.model.localBook.LocalBook.getPresentation(book, chapter)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.put("本地呈现读取失败: chapter=${chapter.index}, ${error.javaClass.simpleName}")
                null
            }
        } else null
        val originalPresentation = presentation
        presentation = presentation?.bind(canonicalInput)
        if (originalPresentation != null && presentation == null) {
            AppLog.put("本地呈现来源不匹配: chapter=${chapter.index}")
        }
        var projection = presentation?.let { TextProjection(canonicalInput.length) }
        var mContent = canonicalInput
        fun removeTitle(end: Int) {
            projection?.replace(0, end, 0)
            mContent = mContent.substring(end)
            val map = projection
            if (map != null) presentation = presentation?.let { original ->
                val starts = original.chapterStartAnchors + original.anchors.filterValues { it < end }.keys
                original.copy(chapterStartAnchors = starts).remapOrNull(map, mContent, keepDeletedAnchors = true)
            }
            projection = presentation?.let { TextProjection(mContent.length) }
        }
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
                    removeTitle(match.range.last + 1)
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
                        removeTitle(match.range.last + 1)
                        sameTitleRemoved = true
                    }
                }
            } catch (e: Exception) {
                AppLog.put("去除重复标题出错\n${e.localizedMessage}", e)
            }
            if (reSegment && book.getReSegment()) {
                mContent = if (projection != null) ContentHelp.reSegmentMapped(mContent, chapter.title, projection)
                else if (settings.adaptSpecialStyle) MixedContentSegments.transform(mContent,
                    plain = { ContentHelp.reSegment(it, chapter.title) }, rich = { it })
                else ContentHelp.reSegment(mContent, chapter.title)
            }
            if (chineseConvert) {
                try {
                    val convert: ((String) -> String)? = when (settings.chineseConverterType) {
                        1 -> ChineseUtils::t2s
                        2 -> ChineseUtils::s2t
                        else -> null
                    }
                    if (convert != null) mContent = if (projection != null) {
                        MixedContentSegments.transformPlainTextMapped(mContent, projection) { text, map ->
                            ChineseUtils.convertMapped(text, settings.chineseConverterType, map)
                        }
                    } else MixedContentSegments.transform(mContent,
                        plain = { MixedContentSegments.transformPlainText(it, convert) }, rich = { MixedContentSegments.transformHtmlText(it, convert) })
                } catch (_: Exception) {
                    appCtx.toastOnUi("简繁转换出错")
                }
            }
            if (useReplace && book.getUseReplaceRule(settings.replaceEnableDefault)) {
                val replaceBook = book.toSearchBook()
                //替换
                effectiveReplaceRules = arrayListOf()
                fun trimLines(text: String) = text.lines().joinToString("\n") { it.trim() }
                val beforeTrim = mContent
                mContent = if (settings.adaptSpecialStyle) MixedContentSegments.transform(mContent,
                    plain = ::trimLines, rich = { it }) else trimLines(mContent)
                projection?.let { check(it.bindWhitespace(beforeTrim, mContent)) }
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
                            val edits = mutableListOf<TextProjection.Edit>()
                            val result = text.replace(item.regex, item.replacement, remaining, chapter, replaceBook,
                                onReplace = if (projection == null) null else { start, end, count ->
                                    edits += TextProjection.Edit(start, end, count)
                                })
                            projection?.replace(edits)
                            result
                        } else text.replaceMapped(item.pattern, item.replacement, projection)
                        val tmp = if (settings.adaptSpecialStyle) MixedContentSegments.transform(mContent,
                            plain = ::replaceText,
                            rich = { it }) else replaceText(mContent)
                        if (mContent != tmp) {
                            effectiveReplaceRules.add(item)
                            mContent = tmp
                        }
                    } catch (e: RegexTimeoutException) {
                        item.isEnabled = false
                        appDb.replaceRuleDao.update(item)
                        mContent = item.name + e.stackTraceStr
                        projection = null
                        presentation = null
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
                // Editing text must not depend on whether its presentation can be written to a DOM.
                val original = mContent
                val editInput = if (contentProcesses.any { it.enabled && it.status == BookContentProcess.STATUS_ACTIVE &&
                        it.stage == BookContentProcess.STAGE_CONTENT && it.kind != BookContentProcess.KIND_USER_HIGHLIGHT &&
                        it.kind != BookContentProcess.KIND_USER_UNDERLINE }) {
                    MixedContentSegments.toPlainText(mContent)
                } else mContent
                if (editInput != original) {
                    projection = null
                    presentation = null
                }
                val applyResult = BookContentProcessEngine.apply(editInput, contentProcesses + markings,
                    onReplace = if (projection == null) null else { start, end, replacement ->
                        projection?.replace(start, end, replacement.length)
                    })
                val edited = applyResult.effectiveProcesses.any {
                    it.kind != BookContentProcess.KIND_USER_HIGHLIGHT && it.kind != BookContentProcess.KIND_USER_UNDERLINE }
                mContent = if (editInput != original && !edited) original else applyResult.text
                effectiveContentProcesses = applyResult.effectiveProcesses
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
        projection?.let { map ->
            val transport = if (contents.isEmpty()) "" else contents.joinToString("\n", postfix = "\n")
            if (map.bindWhitespace(mContent, transport)) {
                val remapped = presentation?.remapOrNull(map, transport)
                if (remapped == null && presentation != null) {
                    AppLog.put("本地呈现分段重映射失败: chapter=${chapter.index}")
                }
                presentation = remapped
            } else {
                AppLog.put("本地呈现分段映射失败: chapter=${chapter.index}")
                presentation = null
            }
        }
        return BookContent(sameTitleRemoved, contents, effectiveReplaceRules, effectiveContentProcesses, presentation)
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
