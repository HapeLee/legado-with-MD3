package io.legado.app.debug

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import io.legado.app.constant.BookType
import io.legado.app.constant.PageAnim
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookHelp
import io.legado.app.ui.main.MainIntent
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugScenarioActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixtureId = intent.getStringExtra(EXTRA_FIXTURE)
        val sessionId = intent.getStringExtra(EXTRA_SESSION).orEmpty()
        if (fixtureId == null || !FIXTURE_ID.matches(fixtureId)) {
            fail(sessionId, "invalid fixture ID")
            return
        }

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { installFixture(fixtureId) }
            }.onSuccess { bookUrl ->
                Log.i(LOG_TAG, "[session=$sessionId] FIXTURE_READY fixture=$fixtureId")
                startActivity(
                    MainIntent.createReadBookIntent(this@DebugScenarioActivity, bookUrl)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
            val content = buildString {
                appendLine(definition.readyMarker)
                repeat(definition.repeat) { paragraphIndex ->
                    append(paragraphIndex + 1)
                    append(". ")
                    appendLine(definition.paragraph)
                }
            }
            BookHelp.saveText(book, chapter, content)
        }
        return book.bookUrl
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
    )

    private companion object {
        const val EXTRA_FIXTURE = "fixture"
        const val EXTRA_SESSION = "session"
        const val LOG_TAG = "LegadoDebug"
        const val DEBUG_ORIGIN = "legado-debug://fixture-source"
        val FIXTURE_ID = Regex("[a-z0-9][a-z0-9-]*")
    }
}
