package io.legado.app.web

import android.app.Application
import com.google.gson.JsonParser
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.ServerSocket
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LegadoReaderCompatibilityTest {

    private lateinit var application: Application
    private lateinit var databaseFixture: InMemoryAppDatabaseFixture

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        databaseFixture = InMemoryAppDatabaseFixture(application)
        stopKoin()
        val fakeGateway = object : BookshelfSettingsGateway {
            override val currentSettings = io.legado.app.domain.model.settings.BookshelfSettings()
            override val settings = kotlinx.coroutines.flow.flowOf(io.legado.app.domain.model.settings.BookshelfSettings())
            override suspend fun update(transform: (io.legado.app.domain.model.settings.BookshelfSettings) -> io.legado.app.domain.model.settings.BookshelfSettings) {}
        }
        startKoin {
            modules(module {
                single<BookshelfSettingsGateway> { fakeGateway }
            })
        }
        val book = Book(
            name = "Test Book",
            author = "Test Author",
            bookUrl = "http://example.com/test_book"
        )
        appDb.bookDao.insert(book)
    }

    @After
    fun tearDown() {
        databaseFixture.close()
        stopKoin()
    }

    private fun request(
        urlStr: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Triple<Int, String, String?> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write(body) }
        }
        conn.connect()
        val code = conn.responseCode
        val contentType = conn.contentType
        val responseBody = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
        return Triple(code, responseBody, contentType)
    }

    @Test
    fun pluginBookshelfSupportsDuplicateBooksAndSavesTheSelectedBook() = runBlocking {
        val original = appDb.bookDao.getBook("http://example.com/test_book")!!
        val recent = original.copy(
            bookUrl = "http://example.com/recent_book",
            durChapterTime = original.durChapterTime + 1,
        )
        appDb.bookDao.insert(recent)
        val port = ServerSocket(0).use { it.localPort }
        val server = KtorServer(port)
        server.start()
        try {
            withTimeout(5000) {
                while (runCatching { Socket("127.0.0.1", port).use {} }.isFailure) delay(25)
            }
            val (webCode, webBody) = request("http://127.0.0.1:$port/getBookshelf")
            assertEquals(200, webCode)
            assertEquals(2, JsonParser.parseString(webBody).asJsonObject.getAsJsonArray("data").size())

            val (pluginCode, pluginBody) = request("http://127.0.0.1:$port/getBookshelf?source=legado-reader")
            assertEquals(200, pluginCode)
            val pluginBooks = JsonParser.parseString(pluginBody).asJsonObject.getAsJsonArray("data")
            assertEquals(1, pluginBooks.size())
            assertEquals(recent.bookUrl, pluginBooks[0].asJsonObject["bookUrl"].asString)
            assertEquals(2, appDb.bookDao.all.size)

            val progress = """{"url":"${recent.bookUrl}","name":"Test Book","author":"Test Author","durChapterIndex":7,"durChapterPos":12,"durChapterTime":123456,"durChapterTitle":"Chapter 7"}"""
            val (saveCode, saveBody) = request(
                "http://127.0.0.1:$port/saveBookProgress", "POST", progress,
                mapOf("Content-Type" to "application/json"),
            )
            assertEquals(200, saveCode)
            assertTrue(JsonParser.parseString(saveBody).asJsonObject["isSuccess"].asBoolean)
            assertEquals(7, appDb.bookDao.getBook(recent.bookUrl)!!.durChapterIndex)
            assertEquals(12, appDb.bookDao.getBook(recent.bookUrl)!!.durChapterPos)
            assertEquals(original.durChapterIndex, appDb.bookDao.getBook(original.bookUrl)!!.durChapterIndex)

            val (_, missingBookBody) = request(
                "http://127.0.0.1:$port/saveBookProgress", "POST",
                progress.replace(recent.bookUrl, "http://example.com/missing_book"),
                mapOf("Content-Type" to "application/json"),
            )
            assertTrue(!JsonParser.parseString(missingBookBody).asJsonObject["isSuccess"].asBoolean)
            assertEquals(original.durChapterIndex, appDb.bookDao.getBook(original.bookUrl)!!.durChapterIndex)
        } finally {
            server.stop()
        }
    }
}
