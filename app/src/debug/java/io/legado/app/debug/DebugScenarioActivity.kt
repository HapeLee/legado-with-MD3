package io.legado.app.debug

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import io.legado.app.constant.BookType
import io.legado.app.constant.PageAnim
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.main.MainIntent
import io.legado.app.utils.GSON
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugScenarioActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixtureId = intent.getStringExtra(EXTRA_FIXTURE)
        val sessionId = intent.getStringExtra(EXTRA_SESSION).orEmpty()
        val entry = intent.getStringExtra(EXTRA_ENTRY) ?: ENTRY_READER
        val preferencesJson = intent.getStringExtra(EXTRA_PREFERENCES_B64)?.let {
            String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }
        if (fixtureId == null || !FIXTURE_ID.matches(fixtureId)) {
            fail(sessionId, "invalid fixture ID")
            return
        }
        if (entry !in SUPPORTED_ENTRIES) {
            fail(sessionId, "unsupported entry: $entry")
            return
        }

        lifecycleScope.launch {
            runCatching {
                val bookUrl = withContext(Dispatchers.IO) { installFixture(fixtureId) }
                applyPreferences(preferencesJson, sessionId)
                bookUrl
            }.onSuccess { bookUrl ->
                Log.i(LOG_TAG, "[session=$sessionId] FIXTURE_READY fixture=$fixtureId entry=$entry")
                val targetIntent = when (entry) {
                    ENTRY_BOOKSHELF -> {
                        AppConfigStore.putString(PreferKey.defaultHomePage, ENTRY_BOOKSHELF)
                        AppConfigStore.putLong(PreferKey.saveTabPosition, BookGroup.IdAll)
                        MainIntent.createHomeIntent(this@DebugScenarioActivity)
                    }
                    ENTRY_SOURCE_MANAGE -> Intent(
                        this@DebugScenarioActivity,
                        BookSourceActivity::class.java,
                    )
                    ENTRY_THEME_CONFIG -> MainIntent.createIntent(
                        this@DebugScenarioActivity,
                        ConfigTag.THEME_CONFIG,
                    )
                    ENTRY_RSS_READ -> MainIntent.createRssReadIntent(
                        context = this@DebugScenarioActivity,
                        title = "DEBUG RSS 日夜切换",
                        origin = DEBUG_ORIGIN,
                        openUrl = DEBUG_RSS_HTML,
                    )
                    else -> MainIntent.createReadBookIntent(this@DebugScenarioActivity, bookUrl)
                }
                startActivity(
                    targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }.onFailure { error ->
                fail(sessionId, "fixture import failed", error)
            }
        }
    }

    private suspend fun installFixture(fixtureId: String): String {
        val fixture = assets.open("debug-fixtures/$fixtureId.json").bufferedReader().use {
            GSON.fromJson(it, DebugFixture::class.java)
        }
        require(fixture.schemaVersion == 1) { "unsupported fixture schema" }
        require(fixture.id == fixtureId) { "fixture ID mismatch" }
        require(fixture.resetPolicy == "replace") { "unsupported reset policy" }
        require(fixture.chapters.isNotEmpty()) { "fixture must contain chapters" }
        require(fixture.book.startChapter in fixture.chapters.indices) { "invalid start chapter" }

        val pageAnim = when (fixture.book.pageMode) {
            "simulation" -> PageAnim.simulationPageAnim
            else -> error("unsupported page mode: ${fixture.book.pageMode}")
        }
        val chapters = fixture.chapters.mapIndexed { index, chapter ->
            require(chapter.repeat in 1..1000) { "invalid repeat for chapter $index" }
            BookChapter(
                url = "${fixture.book.bookUrl}/chapter/$index",
                title = chapter.title,
                baseUrl = fixture.book.bookUrl,
                bookUrl = fixture.book.bookUrl,
                index = index,
            )
        }
        val book = Book(
            bookUrl = fixture.book.bookUrl,
            tocUrl = "${fixture.book.bookUrl}/toc",
            origin = DEBUG_ORIGIN,
            originName = "Legado Debug Fixture",
            name = fixture.book.name,
            author = fixture.book.author,
            type = BookType.text,
            totalChapterNum = chapters.size,
            durChapterTitle = chapters[fixture.book.startChapter].title,
            durChapterIndex = fixture.book.startChapter,
            durChapterPos = fixture.book.startPosition,
            durChapterTime = System.currentTimeMillis(),
            canUpdate = false,
            readConfig = Book.ReadConfig(pageAnim = pageAnim),
        )

        appDb.withTransaction {
            appDb.bookSourceDao.insert(
                BookSource(
                    bookSourceUrl = DEBUG_ORIGIN,
                    bookSourceName = "Legado Debug Fixture",
                    enabled = false,
                    enabledExplore = false,
                )
            )
            appDb.bookDao.getBook(book.bookUrl)?.let { appDb.bookDao.delete(it) }
            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*chapters.toTypedArray())
        }
        chapters.forEachIndexed { index, chapter ->
            val definition = fixture.chapters[index]
            val paragraphReview = definition.paragraphReviewFixture?.let {
                DebugParagraphReviewFixture.createTag(assets, it)
            }
            if (paragraphReview != null) {
                AppConfigStore.putAll(
                    mapOf(
                        PreferKey.enableReview to false,
                        PreferKey.clickImgWay to "0",
                    )
                )
            }
            val content = buildString {
                if (paragraphReview == null) {
                    appendLine(definition.readyMarker)
                    repeat(definition.repeat) { paragraphIndex ->
                        append(paragraphIndex + 1)
                        append(". ")
                        appendLine(definition.paragraph)
                    }
                } else {
                    append(definition.readyMarker)
                    append(" 1. ")
                    append(definition.paragraph)
                    appendLine(paragraphReview)
                    repeat(definition.repeat - 1) { paragraphIndex ->
                        append(paragraphIndex + 2)
                        append(". ")
                        appendLine(definition.paragraph)
                    }
                }
            }
            BookHelp.saveText(book, chapter, content)
        }
        return book.bookUrl
    }

    /**
     * Apply scenario-declared settings before launching the target UI so bug
     * preconditions (theme engine, reader toggles, ...) are reproduced by
     * parameter instead of manual UI toggling. Only String/Boolean values are
     * accepted; they land in the AppConfigStore overlay and are read
     * synchronously by the relaunched Activity in this process.
     */
    private fun applyPreferences(json: String?, sessionId: String) {
        if (json.isNullOrBlank()) return
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val preferences: Map<String, Any?> = GSON.fromJson(json, type) ?: emptyMap()
        val applied = HashMap<String, Any>()
        preferences.forEach { (key, value) ->
            when (value) {
                is String, is Boolean -> applied[key] = value
                else -> error("unsupported preference type for $key")
            }
        }
        if (applied.isNotEmpty()) {
            AppConfigStore.putAll(applied)
            Log.i(LOG_TAG, "[session=$sessionId] PREFERENCES_APPLIED ${applied.keys}")
        }
    }

    private fun fail(sessionId: String, message: String, error: Throwable? = null) {
        Log.e(LOG_TAG, "[session=$sessionId] FIXTURE_ERROR $message", error)
        finishAffinity()
    }

    private data class DebugFixture(
        val schemaVersion: Int,
        val id: String,
        val resetPolicy: String,
        val book: DebugBook,
        val chapters: List<DebugChapter>,
    )

    private data class DebugBook(
        val bookUrl: String,
        val name: String,
        val author: String,
        val pageMode: String,
        val startChapter: Int,
        val startPosition: Int,
    )

    private data class DebugChapter(
        val title: String,
        val readyMarker: String,
        val paragraph: String,
        val repeat: Int,
        val paragraphReviewFixture: String? = null,
    )

    private companion object {
        const val EXTRA_FIXTURE = "fixture"
        const val EXTRA_SESSION = "session"
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_PREFERENCES_B64 = "preferencesB64"
        const val LOG_TAG = "LegadoDebug"
        const val DEBUG_ORIGIN = "legado-debug://fixture-source"
        const val ENTRY_READER = "reader"
        const val ENTRY_BOOKSHELF = "bookshelf"
        const val ENTRY_SOURCE_MANAGE = "source_manage"
        const val ENTRY_THEME_CONFIG = "theme_config"
        const val ENTRY_RSS_READ = "rss_read"
        const val DEBUG_RSS_HTML = "data:text/html,<html><body style='background:white;color:black'><h1>DEBUG RSS THEME</h1><p>DAY NIGHT WEBVIEW</p></body></html>"
        val SUPPORTED_ENTRIES = setOf(
            ENTRY_READER,
            ENTRY_BOOKSHELF,
            ENTRY_SOURCE_MANAGE,
            ENTRY_THEME_CONFIG,
            ENTRY_RSS_READ,
        )
        val FIXTURE_ID = Regex("[a-z0-9][a-z0-9-]*")
    }
}
