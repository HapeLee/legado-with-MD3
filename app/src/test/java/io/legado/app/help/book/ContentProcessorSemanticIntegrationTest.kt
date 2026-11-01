package io.legado.app.help.book

import android.app.Application
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookMarking
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.*
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.domain.usecase.BookmarkTargetVerdict
import io.legado.app.domain.usecase.RelocateMarkingTargetUseCase
import io.legado.app.domain.usecase.VerifyBookmarkTargetUseCase
import io.legado.app.feature.reader.core.layout.*
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.platform.ReaderLocalPresentationResolver
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.support.InMemoryAppDatabaseFixture
import io.legado.app.ui.book.read.PendingBookmarkTarget
import io.legado.app.ui.book.read.ReadBookmarkNavigateDelegate
import io.legado.app.utils.GSON
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ContentProcessorSemanticIntegrationTest {
    private class ReadGateway : ReadSettingsGateway {
        override val settings = MutableStateFlow(ReadSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (ReadSettings) -> ReadSettings) { settings.value = transform(settings.value) }
    }
    private class OtherGateway : OtherSettingsGateway {
        override val settings = MutableStateFlow(OtherSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (OtherSettings) -> OtherSettings) { settings.value = transform(settings.value) }
    }
    private class AloudGateway : ReadAloudSettingsGateway {
        override val settings = MutableStateFlow(ReadAloudSettings())
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) { settings.value = transform(settings.value) }
    }
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private val read = ReadGateway()
    private val other = OtherGateway()
    private lateinit var book: Book
    private lateinit var chapter: BookChapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var previousBook: Book? = null
    private val bookField get() = ReadBook::class.java.getDeclaredField("book").apply { isAccessible = true }

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        AppConfigStore.init(context)
        stopKoin()
        startKoin { modules(module {
            single<ReadSettingsGateway> { read }
            single<OtherSettingsGateway> { other }
            single<ReadAloudSettingsGateway> { AloudGateway() }
        }) }
        fixture = InMemoryAppDatabaseFixture(context)
        val db = fixture.database
        val styleRepository = ReadStyleRepository(HighlightRuleRepository(db.highlightRuleDao, context))
        val config = ReadBookConfig.Config(paragraphIndent = "")
        File(styleRepository.configFilePath).writeText(GSON.toJson(List(6) { config }))
        File(styleRepository.shareConfigFilePath).writeText(GSON.toJson(config))
        ReadBookConfig.initialize(ReadStyleConfigStore(styleRepository), read)
        book = Book(name = "semantic-${UUID.randomUUID()}", author = "fixture", origin = "source", bookUrl = "https://fixture/${UUID.randomUUID()}")
        chapter = BookChapter(bookUrl = book.bookUrl, index = 0, title = "chapter", url = "chapter-0")
        db.bookDao.insert(book)
        db.bookChapterDao.insert(chapter)
        previousBook = ReadBook.book
        bookField.set(null, book)
    }

    @After fun teardown() {
        scope.cancel()
        BookHelp.delContent(book, chapter)
        bookField.set(null, previousBook)
        fixture.close()
        stopKoin()
    }

    private fun source(content: BookContent) = ReaderChapterSourceParser.parse(0, "", content.textList, false,
        read.currentSettings.adaptSpecialStyle, AndroidReaderHtmlSemanticTextResolver, localPresentation = content.presentation)

    private fun textItems(source: ReaderChapterSource) = source.blocks.filterIsInstance<ReaderChapterSourceBlock.Paragraph>()
        .flatMap { it.items }.filterIsInstance<ReaderChapterInlineSource.Text>()

    @Test fun oldBgmRecordsKeepTheirTargetAcrossStyleSwitchesAndAllConsumers() = runBlocking {
        val sample = io.legado.app.support.ReaderSemanticFixtures
        val raw = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(Jsoup.parse(sample.html).body().outerHtml())
        book.origin = io.legado.app.constant.BookType.localTag
        book.originName = "fixture.epub"
        book.type = 0
        val old = sample.expected.getAsJsonObject("bgm")
        val saved = io.legado.app.data.entities.BgmSceneMark(book.bookUrl, 0,
            old.get("paragraphOrdinal").asInt, old.get("poolName").asString, old.get("trackName").asString,
            old.get("enabled").asBoolean, old.get("volume").asFloat, createdAt = 1000, updatedAt = 2000)
        fixture.database.bgmSceneDao.put(saved)
        BookHelp.saveText(book, chapter, raw)
        for (enabled in listOf(true, false, true)) {
            read.update { it.copy(adaptSpecialStyle = enabled) }
            val processed = ContentProcessor.get(book).getContent(book, chapter, raw, includeTitle = false,
                paragraphIndent = "", includePresentation = true,
                localPresentationProvider = {
                    val document = Jsoup.parse(sample.html)
                    io.legado.app.model.localBook.LocalBookSemanticHtml.inlineDocumentCssSemantics(document) { sample.css }
                    ReaderLocalPresentationResolver.resolve(document.body().outerHtml())
                })
            val current = ReaderChapterSourceParser.parse(0, "", processed.textList, false, enabled,
                AndroidReaderHtmlSemanticTextResolver, bgmScenes = io.legado.app.help.readaloud.cast.BgmSceneStore
                    .labelsForChapter(book.bookUrl, 0), localPresentation = processed.presentation)
            if (enabled) {
                assertTrue(textItems(current).any { it.style.link == "#chapter" })
                assertTrue(textItems(current).any { it.style.fontWeight == 700 })
            }
            assertEquals(sample.body, current.semanticContent)
            val ranges = io.legado.app.help.readaloud.cast.BgmSceneStore.positionsToOrdinals(current)
            val target = current.paragraphs.single { it.ordinal == saved.paragraphOrdinal }
            assertEquals(old.get("text").asString + "\n", current.semanticContent.substring(target.start, target.endExclusive))
            assertEquals(saved.paragraphOrdinal, ranges.last { it.first <= target.start }.second)
            assertEquals(saved, io.legado.app.help.readaloud.cast.BgmSceneStore.activeMark(book.bookUrl, 0, saved.paragraphOrdinal))
            assertEquals(old.get("text").asString, io.legado.app.help.readaloud.cast.BgmSceneStore.overview(book, 0).single().preview.trim())
            val web = io.legado.app.api.ReaderWebChapter.fromSource(current)
            assertEquals(target.start, web.paragraphs[saved.paragraphOrdinal].start)
            val speech = io.legado.app.feature.reader.core.readaloud.ReaderReadAloudChapter.create(0, "", current.semanticContent, listOf(0))
            assertTrue(speech.paragraphs.any { it.chapterPosition == target.start && it.text == old.get("text").asString })
            val found = SearchContentRepository(readSettingsGateway = read, otherSettingsGateway = other)
                .search(book, old.get("text").asString, false, false).toList().last().single()
            assertEquals("chapter\n".length + target.start, found.queryIndexInChapter)
            assertEquals(listOf(saved), fixture.database.bgmSceneDao.getChapter(book.bookUrl, 0))
        }
        // Legacy online HTML is one stored paragraph even when it contains several visual lines.
        book.origin = "source"
        book.originName = ""
        val html = "plain\n<usehtml><p>乙&amp;😀<br>尾</p></usehtml>\nafter"
        BookHelp.saveText(book, chapter, html)
        val processed = ContentProcessor.get(book).getContent(book, chapter, html, includeTitle = false, paragraphIndent = "")
        val rows = io.legado.app.help.readaloud.cast.BgmSceneStore.paragraphTexts(processed.textList, true)
        assertEquals(mapOf(0 to "plain", 1 to "乙&😀\n尾", 2 to "after"), rows)
        assertEquals(rows[1], io.legado.app.help.readaloud.cast.BgmSceneStore.overview(book, 0).single().preview)
        assertEquals(listOf(saved), fixture.database.bgmSceneDao.getChapter(book.bookUrl, 0))
    }

    @Test fun localCrossNodeRulesAndJsExecuteOnceAndOnlyDropAmbiguousStyles() {
        book.origin = io.legado.app.constant.BookType.localTag
        book.originName = "fixture.epub"
        book.type = 0
        book.setUseReplaceRule(true)
        fixture.database.replaceRuleDao.insert(ReplaceRule(id = 101, pattern = "今天天气", replacement = "昨天", isRegex = false, order = 0))
        fixture.database.replaceRuleDao.insert(ReplaceRule(id = 102, pattern = "昨天", replacement =
            "@js:chapter.putVariable('runs', String(parseInt(chapter.getVariable('runs') || '0') + 1)); '昨天';", isRegex = true, order = 1))
        chapter.putVariable("runs", "0")
        val html = "<p>今天<strong>天气</strong><img src='Images/x.png'></p><p><a id='note' href='#note'>after</a></p>"
        val raw = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(Jsoup.parse(html).body().outerHtml())
        val processed = ContentProcessor.get(book).getContent(book, chapter, raw, includeTitle = false,
            paragraphIndent = "", includePresentation = true, localPresentationProvider = { ReaderLocalPresentationResolver.resolve(html) })
        val current = source(processed)
        assertEquals("昨天\uFFFC\nafter\n", current.semanticContent)
        assertEquals("1", chapter.getVariable("runs"))
        assertEquals(current.semanticContent.indexOf("after"), current.anchorPositions["note"])
        assertEquals("#note", textItems(current).single { it.value == "after" }.style.link)
        assertTrue(textItems(current).filter { it.value.contains("昨天") }.all { it.style.fontWeight == null })
        assertTrue(processed.textList.first().contains("Images/x.png"))
    }

    @Test fun localSegmentationConversionAndIndentKeepTheirOldMeaningWithEitherAppearanceSetting() = runBlocking {
        book.origin = io.legado.app.constant.BookType.localTag
        book.originName = "fixture.epub"
        book.type = 0
        val html = "<p>繁體 <strong>內容</strong></p><p>接續內容。</p>"
        val raw = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(Jsoup.parse(html).body().outerHtml())
        for (segmented in listOf(false, true)) for (converter in listOf(0, 1)) for (indent in listOf("", "　　")) {
            book.setReSegment(segmented)
            val expected = if (segmented) listOf(if (converter == 1) "繁体内容接续内容。" else "繁體內容接續內容。")
                else if (converter == 1) listOf("繁体 内容", "接续内容。") else listOf("繁體 內容", "接續內容。")
            for (enabled in listOf(true, false)) {
                read.update { it.copy(adaptSpecialStyle = enabled, chineseConverterType = converter) }
                val processed = ContentProcessor.get(book).getContent(book, chapter, raw, includeTitle = false,
                    paragraphIndent = indent, includePresentation = true,
                    localPresentationProvider = { ReaderLocalPresentationResolver.resolve(html) })
                assertEquals(expected.map { indent + it }, processed.textList)
                val current = source(processed)
                if (enabled) assertTrue(textItems(current).any { it.style.fontWeight == 700 && it.value.contains(if (converter == 1) "内容" else "內容") })
                assertEquals(expected.joinToString("\n", postfix = "\n") { indent + it }, current.semanticContent)
                assertEquals(expected.indices.toList(), current.paragraphs.map { it.ordinal })
            }
        }
    }

    @Test fun anUnmatchedLegacyHtmlEditDoesNotChangeTheBodyOrItsAttributes() = runBlocking {
        val original = "<usehtml><p><a href='target'>body</a><img src='x.png'></p></usehtml>"
        fixture.database.bookContentProcessDao.upsert(io.legado.app.data.entities.BookContentProcess(
            id = "no-match", bookUrl = book.bookUrl, chapterIndex = chapter.index,
            kind = io.legado.app.data.entities.BookContentProcess.KIND_AI_REWRITE,
            anchorJson = GSON.toJson(TextProcessAnchor(0, 0, "target", normalizedTextHash = "")),
            actionJson = GSON.toJson(io.legado.app.domain.model.TextProcessAction.replace("broken"))))
        val result = ContentProcessor.get(book).getContent(book, chapter, original, includeTitle = false, paragraphIndent = "")
        assertEquals(listOf(original), result.textList)
        assertTrue(result.effectiveContentProcesses.isEmpty())
    }

    @Test fun semanticWebResponseRoundTripsToReaderWhileLegacyResponseStaysAString() = runBlocking {
        BookHelp.saveText(book, chapter, "<usehtml><p>A&amp;<b>😀B</b></p></usehtml>\ntail")
        val parameters = mapOf("url" to listOf(book.bookUrl), "index" to listOf("0"))
        val legacy = io.legado.app.api.controller.BookController.getBookContent(parameters)
        assertTrue(legacy.data is String)
        assertTrue((legacy.data as String).contains("<usehtml>"))
        val response = io.legado.app.api.controller.BookController.getBookContent(parameters + ("format" to listOf("semantic")))
        assertTrue(response.errorMsg, response.isSuccess)
        val web = response.data as io.legado.app.api.ReaderWebChapter
        val storedPosition = web.paragraphs.last().start
        val host = Host()
        delegate(host).navigateToBookmark(bookmark("tail", storedPosition))
        withTimeout(5_000) { host.done.await() }
        assertEquals(0 to storedPosition, host.jumped)
        assertNull(host.pendingTarget)
    }

    @Test fun localRulesAndEditsUseTheSameOldPlainBodyWithStylesOnOrOff() = runBlocking {
        read.update { it.copy(adaptSpecialStyle = false) }
        book.origin = io.legado.app.constant.BookType.localTag
        book.originName = "fixture.epub"
        book.type = 0
        book.setUseReplaceRule(true)
        fixture.database.replaceRuleDao.insert(ReplaceRule(pattern = "天气", replacement = "气候", isRegex = false))
        fixture.database.bookContentProcessDao.upsert(io.legado.app.data.entities.BookContentProcess(
            id = "disabled-style-edit", bookUrl = book.bookUrl, chapterIndex = chapter.index,
            kind = io.legado.app.data.entities.BookContentProcess.KIND_AI_REWRITE,
            anchorJson = GSON.toJson(TextProcessAnchor(0, 0, "after", normalizedTextHash = "")),
            actionJson = GSON.toJson(io.legado.app.domain.model.TextProcessAction(
                io.legado.app.domain.model.TextProcessAction.TYPE_REPLACE, replacement = "edited"))))
        val html = "<p><b>天气</b>&amp;<img src='Images/x.png'></p><p><a id='note' href='#note'>after</a></p>"
        val raw = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(Jsoup.parse(html).body().outerHtml())
        for (enabled in listOf(false, true)) {
            read.update { it.copy(adaptSpecialStyle = enabled) }
            val result = ContentProcessor.get(book).getContent(book, chapter,
                raw, includeTitle = false, paragraphIndent = "", includePresentation = true,
                localPresentationProvider = { ReaderLocalPresentationResolver.resolve(html) })
            assertEquals(listOf("气候&<img src=\"Images/x.png\">", "edited"), result.textList)
            assertEquals("气候&\uFFFC\nedited\n", source(result).semanticContent)
            if (enabled) {
                val items = textItems(source(result))
                assertEquals(700, items.single { it.value == "气候" }.style.fontWeight)
                assertEquals("#note", items.single { it.value == "edited" }.style.link)
            }
        }
    }

    @Test fun plainTitleAndIndentationKeepLegacyLineSemantics() = runBlocking {
        val processor = ContentProcessor.get(book)
        for (title in listOf("  title　", "", "A\nB")) {
            chapter.title = title
            val body = "  甲\n　乙 \n<img src=\"x\">"
            val displayTitle = chapter.getDisplayTitle(useReplace = false, chineseConvert = false, chineseConverterType = 0)
            val legacy = (displayTitle + "\n" + body).split("\n")
                .map { it.trim { c -> c.code <= 0x20 || c == '　' } }.filter { it.isNotEmpty() }
                .mapIndexed { index, line -> if (index == 0) line else "　　$line" }
            assertEquals(legacy, processor.getContent(book, chapter, body, includeTitle = true,
                useReplace = false, chineseConvert = false, reSegment = false, paragraphIndent = "　　").textList)
        }
        book.origin = io.legado.app.constant.BookType.localTag
        book.originName = "fixture.epub"
        book.type = 0
        chapter.title = "chapter"
        val html = "<h2 id='heading'>chapter</h2><p>正文“<a href='#note'>链接</a>”尾</p><p id='note'>注释</p>"
        val canonical = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(Jsoup.parse(html).body().outerHtml())
        val shaper = ReaderTextShaper { GlyphClusters(it.map(Char::toString), FloatArray(it.length) { 10f }) }
        val measureStyle = ReaderChapterMeasureStyle(ReaderTextStyle(1, 10f), ReaderTextStyle(2, 20f), 0,
            ReaderTextAlignment.START, ReaderTextAlignment.START)
        for (raw in listOf(canonical.trim(), canonical)) {
            for (useReplace in listOf(false, true)) for (indent in listOf("", "　　")) {
                book.setUseReplaceRule(useReplace)
                val processed = ContentProcessor.get(book).getContent(book, chapter, raw, includeTitle = false,
                    paragraphIndent = indent, includePresentation = true,
                    localPresentationProvider = { ReaderLocalPresentationResolver.resolve(html) })
                assertEquals(raw == canonical.trim(), processed.sameTitleRemoved)
                for (labels in listOf<Map<Int, String>?>(null, mapOf(0 to "<<甲（池）>>"))) {
                    val current = ReaderChapterSourceParser.parse(0, "", processed.textList, false, true,
                        castLabels = labels, localPresentation = processed.presentation)
                    val label = if (labels == null) "" else "<<甲（池）>>"
                    val heading = if (processed.sameTitleRemoved) "" else "${indent}chapter\n"
                    assertEquals("${heading}${indent}正文“${label}链接”尾\n${indent}注释\n", current.semanticContent)
                    assertEquals(if (processed.sameTitleRemoved) 0 else current.semanticContent.indexOf("chapter"),
                        current.anchorPositions["heading"])
                    assertEquals(current.semanticContent.indexOf("注释"), current.anchorPositions["note"])
                    val measured = ReaderChapterBlockMeasurer(shaper, shaper, { null },
                        castTracker = if (labels == null) null else io.legado.app.feature.reader.core.cast.CastMarkers.CastQuoteTracker(),
                        castCapsuleWidthPx = { _, _, _, _ -> 10f }, castCapsuleHeightPx = 10f)
                        .measure(current, measureStyle.copy(bodyIndentText = indent))
                        as ReaderChapterMeasureResult.Success
                    val page = ReaderPaginator.paginateBlocks(measured.blocks,
                        ReaderPaginationConfig(0, "", 500, 1000, 0f, 0f, 0f, 0f, 10f, 8f)).single()
                    if (labels != null) assertEquals(1, page.elements.filterIsInstance<ReaderElement.RoleCast>().size)
                    val linked = page.elements.filterIsInstance<ReaderElement.Text>().filter { it.link == "#note" }
                    assertEquals("链接", linked.joinToString("") { it.value })
                    assertTrue(linked.all { it.drawsLinkUnderline && it.resolvedColorArgb(99) == 99 })
                    assertEquals(linked.first(), page.elementAt((linked.first().bounds.left + linked.first().bounds.right) / 2f,
                        (linked.first().bounds.top + linked.first().bounds.bottom) / 2f))
                }
            }
        }
    }

    @Test fun existingOnlineHtmlUsesTheLegacyReplacementBoundaryForEitherStyleSetting() = runBlocking {
        fixture.database.replaceRuleDao.insert(ReplaceRule(id = 201, pattern = "天气", replacement = "气候", isRegex = false, order = 0))
        fixture.database.replaceRuleDao.insert(ReplaceRule(id = 202, pattern = "气候", replacement =
            "@js:chapter.putVariable('runs', String(parseInt(chapter.getVariable('runs') || '0') + 1)); '气候';", order = 1))
        val processor = ContentProcessor.get(book)
        for (enabled in listOf(true, false)) {
            read.update { it.copy(adaptSpecialStyle = enabled) }
            chapter.putVariable("runs", "0")
            val result = processor.getContent(book, chapter,
                "plain天气\n<usehtml><p><a href='target.xhtml'>今天天气很好</a></p></usehtml>",
                includeTitle = false, paragraphIndent = "")
            assertTrue(source(result).semanticContent.contains("plain气候"))
            assertEquals(if (enabled) "1" else "2", chapter.getVariable("runs"))
            assertTrue(result.toString().contains(if (enabled) "今天天气很好" else "今天气候很好"))
            assertTrue(result.toString().contains("target.xhtml"))
        }
    }

    @Test fun chineseConversionChangesBodyButPreservesBothChannelResourcePaths() = runBlocking {
        read.update { it.copy(chineseConverterType = 1) }
        val result = ContentProcessor.get(book).getContent(book, chapter,
            "繁體內容\n<usehtml><p><a id='繁體' href='繁體.xhtml'>繁體內容</a></p></usehtml>\n<img src=\"繁體圖片.png\">",
            includeTitle = false, useReplace = false, paragraphIndent = "")
        assertEquals("繁体内容", result.textList.first())
        val rich = result.textList.first { it.startsWith("<usehtml>") }.removePrefix("<usehtml>").removeSuffix("</usehtml>")
        val link = Jsoup.parseBodyFragment(rich).selectFirst("a")!!
        assertEquals("繁体内容", link.text())
        assertEquals("繁體.xhtml", link.attr("href"))
        assertEquals("繁體", link.id())
        assertTrue(result.textList.last().contains("繁體圖片.png"))
    }

    @Test fun unmatchedRuleDoesNotCountHtmlSerializationAsAChange() {
        fixture.database.replaceRuleDao.insert(ReplaceRule(pattern = "absent", replacement = "X", isRegex = false))
        val raw = "<usehtml><p title='a>b'>A&amp;B</p></usehtml>"
        val result = ContentProcessor.get(book).getContent(book, chapter, raw, includeTitle = false, paragraphIndent = "")
        assertEquals(listOf(raw), result.textList)
        assertTrue(result.effectiveReplaceRules!!.isEmpty())
    }

    @Test fun resegmentationUsesTheLegacyStyleBoundary() = runBlocking {
        book.setReSegment(true)
        val before = "甲 乙\n丙 丁"
        val after = "戊 己\n庚 辛"
        val raw = "$before\n<usehtml><pre>A  B\n C</pre></usehtml>\n$after"
        for (enabled in listOf(true, false)) {
            read.update { it.copy(adaptSpecialStyle = enabled) }
            val output = ContentProcessor.get(book).getContent(book, chapter, raw,
                includeTitle = false, useReplace = false, chineseConvert = false, paragraphIndent = "")
            val expected = if (enabled) listOf(
                *MixedContentSegments.paragraphs(ContentHelp.reSegment(before + "\n", chapter.title), "", true).toTypedArray(),
                "<usehtml><pre>A  B\n C</pre></usehtml>",
                *MixedContentSegments.paragraphs(ContentHelp.reSegment("\n" + after, chapter.title), "", true).toTypedArray(),
            ) else MixedContentSegments.paragraphs(ContentHelp.reSegment(raw, chapter.title), "", false)
            assertEquals(expected, output.textList)
        }
        book.origin = io.legado.app.constant.BookType.localTag
        book.originName = "fixture.epub"
        book.type = 0
        read.update { it.copy(adaptSpecialStyle = true) }
        for (body in listOf("甲说：\"<b>你好</b>。\"", "“<b>第一句</b>”“第二句”", "甲说。“<b>回答</b>”乙又说。“结尾”")) {
            val html = "<p>$body</p><p><a id='n' href='#n'>引用</a></p>"
            val raw = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(Jsoup.parse(html).body().outerHtml())
            val processed = ContentProcessor.get(book).getContent(book, chapter, raw,
                includeTitle = false, useReplace = false, chineseConvert = false, paragraphIndent = "", includePresentation = true,
                localPresentationProvider = { ReaderLocalPresentationResolver.resolve(html) })
            val current = source(processed)
            assertEquals("#n", textItems(current).single { it.value == "引用" }.style.link)
            assertEquals(current.semanticContent.indexOf("引用"), current.anchorPositions["n"])
            assertTrue(textItems(current).any { it.style.fontWeight == 700 })
        }
    }

    @Test fun searchUsesCurrentSemanticStyleAndInvalidatesRuleRevision() = runBlocking {
        BookHelp.saveText(book, chapter, "<usehtml><p>A&amp;<strong>B</strong></p></usehtml>")
        val repository = SearchContentRepository(readSettingsGateway = read, otherSettingsGateway = other)
        val results = repository.search(book, "A&B", false, false).toList().last()
        assertEquals(1, results.size)
        assertEquals("chapter\n".length, results.single().queryIndexInChapter)
        read.update { it.copy(adaptSpecialStyle = false) }
        assertNull(repository.getCache(book, "A&B", false, false))
        assertNull(repository.getLastSession(book.bookUrl))
        assertTrue(repository.search(book, "A&B", false, false).toList().last().isEmpty())
        read.update { it.copy(adaptSpecialStyle = true) }
        repository.search(book, "A&B", false, false).toList()
        ContentProcessor.get(book).upReplaceRules()
        assertNull(repository.getLastSession(book.bookUrl))
    }

    private class Host : ReadBookmarkNavigateDelegate.Host {
        private var pending: PendingBookmarkTarget? = null
        override val pendingTarget: PendingBookmarkTarget? get() = pending
        var jumped: Pair<Int, Int>? = null
        val done = CompletableDeferred<Unit>()
        override fun jumpToChapter(chapterIndex: Int, chapterPos: Int) { jumped = chapterIndex to chapterPos; done.complete(Unit) }
        override fun setPendingTarget(pending: PendingBookmarkTarget?) { this.pending = pending; done.complete(Unit) }
    }
    private fun delegate(host: Host, labels: Map<Int, String> = emptyMap()): ReadBookmarkNavigateDelegate {
        val settings = object : io.legado.app.domain.gateway.ReadAloudSettingsGateway {
            override val settings = MutableStateFlow(io.legado.app.domain.model.settings.ReadAloudSettings(multiRoleCast = labels.isNotEmpty()))
            override val currentSettings get() = settings.value
            override suspend fun update(transform: (io.legado.app.domain.model.settings.ReadAloudSettings) -> io.legado.app.domain.model.settings.ReadAloudSettings) {
                settings.value = transform(settings.value)
            }
        }
        return ReadBookmarkNavigateDelegate(scope,
            BookRepository(fixture.database.bookDao, fixture.database.bookChapterDao, fixture.database),
            VerifyBookmarkTargetUseCase(), RelocateMarkingTargetUseCase(), host, settings, { _, _ -> labels })
    }
    private fun bookmark(text: String, pos: Int) = Bookmark(bookName = book.name, bookAuthor = book.author,
        bookUrl = book.bookUrl, chapterIndex = 0, chapterName = chapter.title, chapterPos = pos, bookText = text)

    @Test fun cachePopulationPreservesSearchIdentityWhileBodyEditsInvalidateIt() = runBlocking {
        val epub = javaClass.getResourceAsStream("/reader/semantic/epub2.epub")!!.use {
            me.ag2s.epublib.epub.EpubReader().readEpub(it)
        }
        val document = Jsoup.parse(String(epub.spine.spineReferences.single().resource.data, Charsets.UTF_8))
        val channel = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(document.body().outerHtml())
        ContentProcessor.get(book)
        val repository = SearchContentRepository(readSettingsGateway = read, otherSettingsGateway = other)
        repository.beginSearch(book, "A&😀B", false, false)
        BookHelp.saveText(book, chapter, channel, notifyContentChange = false)
        assertNotNull(repository.getCache(book, "A&😀B", false, false))
        val results = repository.search(book, "A&😀B", false, false).toList().last()
        assertEquals(1, results.size)
        assertNotNull(repository.getLastSession(book.bookUrl))
        BookHelp.saveText(book, chapter, "changed body")
        assertNull(repository.getLastSession(book.bookUrl))
    }

    @Test fun searchOnlyInvalidatesWhenItsInputsChange() = runBlocking {
        val second = chapter.copy(index = 1, url = "chapter-1")
        val background = chapter.copy(index = 99, url = "background")
        fixture.database.bookChapterDao.insert(second)
        try {
            for (change in listOf("cache", "processor", "body", "rules", "settings")) {
                read.update { it.copy(adaptSpecialStyle = true) }
                BookHelp.saveText(book, chapter, "needle")
                BookHelp.saveText(book, second, "needle")
                var checks = 0
                val repository = SearchContentRepository(readSettingsGateway = read, otherSettingsGateway = other,
                    rulesRevisionProvider = {
                        // The fifth identity read is between chapters, after publishing the first hit.
                        if (++checks == 5) when (change) {
                            "cache" -> BookHelp.saveText(book, background, "downloaded")
                            "processor" -> ContentProcessor.get("other-${book.name}", book.origin)
                            "body" -> BookHelp.saveText(book, chapter, "changed")
                            "rules" -> ContentProcessor.get(book).upReplaceRules()
                            "settings" -> read.settings.value = read.currentSettings.copy(adaptSpecialStyle = false)
                        }
                        ContentProcessor.rulesRevision
                    })
                val result = runCatching { repository.search(book, "needle", false, false).toList().last() }
                if (change in listOf("cache", "processor")) assertEquals(change, 2, result.getOrThrow().size)
                else {
                    assertTrue(change, result.exceptionOrNull() is SearchContentChangedException)
                    assertNull(repository.getLastSession(book.bookUrl))
                }
            }
        } finally {
            BookHelp.delContent(book, second)
            BookHelp.delContent(book, background)
        }
    }

    @Test fun matchingMarkingTextStillValidatesStoredContext() = runBlocking {
        BookHelp.saveText(book, chapter, "<usehtml><p>A&amp;B? A&amp;B!</p></usehtml>")
        val anchor = TextProcessAnchor(0, 0, "A&B", contextAfter = "!", normalizedTextHash = "")
        val marking = BookMarking(id = "context", bookUrl = book.bookUrl, chapterIndex = 0,
            chapterName = chapter.title, anchorJson = GSON.toJson(anchor))
        val host = Host()
        delegate(host).navigateToMarking(marking)
        withTimeout(5_000) { host.done.await() }
        assertEquals(0 to 5, host.jumped)
    }

    @Test fun bookmarkCoordinatesIncludeEnabledReaderCastMarkers() = runBlocking {
        val body = "前文“正文”"
        val labels = mapOf(0 to "<<甲（池）>>")
        BookHelp.saveText(book, chapter, body)
        val host = Host()
        delegate(host, labels).navigateToBookmark(bookmark("正文", 0))
        withTimeout(5_000) { host.done.await() }
        val readerSource = ReaderChapterSourceParser.parse(0, "", listOf(body), false, true,
            AndroidReaderHtmlSemanticTextResolver, castLabels = labels)
        assertTrue(readerSource.semanticContent.indexOf("正文") > body.indexOf("正文"))
        assertEquals(0 to readerSource.semanticContent.indexOf("正文"), host.jumped)
    }

    @Test fun unanchoredPositionOnlyGetsSafeRangeCorrection() = runBlocking {
        BookHelp.saveText(book, chapter, "A😀B")
        val host = Host()
        delegate(host).navigateToBookmark(bookmark("", 2))
        withTimeout(5_000) { host.done.await() }
        assertEquals(0 to 1, host.jumped)
    }

    @Test fun unchangedSourceStillRelocatesStaleBookmarkAgainstCurrentSemanticBody() = runBlocking {
        BookHelp.saveText(book, chapter, "<usehtml><p>prefix <b>A&amp;B</b></p></usehtml>")
        val host = Host()
        delegate(host).navigateToBookmark(bookmark("A&B", 0))
        withTimeout(5_000) { host.done.await() }
        assertEquals(0 to 7, host.jumped)
        assertNull(host.pendingTarget)
    }

    @Test fun uncertainBookmarkKeepsConfirmationAndOriginalStoredPosition() = runBlocking {
        BookHelp.saveText(book, chapter, "<usehtml><p>A&amp;B A&amp;B</p></usehtml>")
        val stored = bookmark("A&B", 99)
        val host = Host()
        delegate(host).navigateToBookmark(stored)
        withTimeout(5_000) { host.done.await() }
        assertNull(host.jumped)
        assertEquals(BookmarkTargetVerdict.ContentMismatch, host.pendingTarget?.verdict)
        assertEquals(99, stored.chapterPos)
    }

    @Test fun navigationRespectsDisabledHtmlInterpretation() = runBlocking {
        read.update { it.copy(adaptSpecialStyle = false) }
        BookHelp.saveText(book, chapter, "<usehtml><p>A&amp;B</p></usehtml>")
        val host = Host()
        delegate(host).navigateToBookmark(bookmark("A&B", 0))
        withTimeout(5_000) { host.done.await() }
        assertNull(host.jumped)
        assertEquals(BookmarkTargetVerdict.ContentMismatch, host.pendingTarget?.verdict)
    }
}
