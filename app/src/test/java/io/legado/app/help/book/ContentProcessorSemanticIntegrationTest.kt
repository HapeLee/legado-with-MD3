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
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.domain.usecase.BookmarkTargetVerdict
import io.legado.app.domain.usecase.RelocateMarkingTargetUseCase
import io.legado.app.domain.usecase.VerifyBookmarkTargetUseCase
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
        startKoin { modules(module { single<ReadSettingsGateway> { read }; single<OtherSettingsGateway> { other } }) }
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
        read.currentSettings.adaptSpecialStyle, AndroidReaderHtmlSemanticTextResolver)

    @Test fun plainTitleAndIndentationKeepLegacyLineSemantics() {
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
    }

    @Test fun replacementRulesReachDecodedRichNodesWithoutChangingAttributes() {
        fixture.database.replaceRuleDao.insert(ReplaceRule(pattern = "天气", replacement = "气候", isRegex = false))
        val processor = ContentProcessor.get(book)
        val result = processor.getContent(book, chapter,
            "plain天气\n<usehtml><p><a href='天气.xhtml'>今天<strong>天气很好</strong></a></p></usehtml>",
            includeTitle = false, paragraphIndent = "")
        assertTrue(source(result).semanticContent.contains("plain气候"))
        assertTrue(source(result).semanticContent.contains("今天气候很好"))
        val html = result.textList.first { it.startsWith("<usehtml>") }.removePrefix("<usehtml>").removeSuffix("</usehtml>")
        assertEquals("天气.xhtml", Jsoup.parseBodyFragment(html).selectFirst("a")!!.attr("href"))
        assertEquals(1, result.effectiveReplaceRules!!.size)
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

    @Test fun resegmentationKeepsPlainSegmentsActiveAroundRichContent() {
        book.setReSegment(true)
        val before = "甲 乙\n丙 丁"
        val after = "戊 己\n庚 辛"
        val processor = ContentProcessor.get(book)
        val output = processor.getContent(book, chapter, "$before\n<usehtml><pre>A  B\n C</pre></usehtml>\n$after",
            includeTitle = false, useReplace = false, chineseConvert = false, paragraphIndent = "")
        val expected = listOf(
            *MixedContentSegments.paragraphs(ContentHelp.reSegment(before + "\n", chapter.title), "", true).toTypedArray(),
            "<usehtml><pre>A  B\n C</pre></usehtml>",
            *MixedContentSegments.paragraphs(ContentHelp.reSegment("\n" + after, chapter.title), "", true).toTypedArray(),
        )
        assertEquals(expected, output.textList)
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
        val channel = io.legado.app.model.localBook.LocalBookSemanticHtml.emitMixedChannel(document.body())
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

    @Test fun settingsChangeDuringSearchStopsTheOldSession() = runBlocking {
        val second = chapter.copy(index = 1, url = "chapter-1")
        fixture.database.bookChapterDao.insert(second)
        BookHelp.saveText(book, chapter, "<usehtml><p>A&amp;B</p></usehtml>")
        BookHelp.saveText(book, second, "<usehtml><p>A&amp;B</p></usehtml>")
        try {
            val repository = SearchContentRepository(readSettingsGateway = read, otherSettingsGateway = other)
            var emissions = 0
            repository.search(book, "A&B", false, false).collect {
                emissions++
                read.update { it.copy(adaptSpecialStyle = false) }
            }
            assertEquals(1, emissions)
            assertNull(repository.getLastSession(book.bookUrl))
        } finally { BookHelp.delContent(book, second) }
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
