package io.legado.app.ui.widget.dialog

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BottomWebViewDialogTest {
    private lateinit var fixture: InMemoryAppDatabaseFixture

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        AppConfigStore.init(application)
        fixture = InMemoryAppDatabaseFixture(application)
        stopKoin()
        startKoin {
            modules(module {
                single<AppUiConfigurationGateway> {
                    object : AppUiConfigurationGateway {
                        override val configuration = MutableStateFlow(AppUiConfiguration())
                        override val currentConfiguration get() = configuration.value
                        override fun synchronizeSystemDarkTheme(isDarkTheme: Boolean) = Unit
                    }
                }
                single<AppShellSettingsGateway> {
                    object : AppShellSettingsGateway {
                        override val currentSettings = AppShellSettings()
                        override val settings = flowOf(currentSettings)
                        override suspend fun update(transform: (AppShellSettings) -> AppShellSettings) = Unit
                    }
                }
                single<OtherSettingsGateway> {
                    object : OtherSettingsGateway {
                        override val currentSettings = OtherSettings()
                        override val settings = flowOf(currentSettings)
                        override suspend fun update(transform: (OtherSettings) -> OtherSettings) = Unit
                    }
                }
                single<DownloadCacheSettingsGateway> {
                    object : DownloadCacheSettingsGateway {
                        override val currentSettings = DownloadCacheSettings(userAgent = "fallback-UA")
                        override val settings = flowOf(currentSettings)
                        override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) = Unit
                    }
                }
            })
        }
    }

    @After
    fun tearDown() {
        fixture.close()
        stopKoin()
    }

    @Test
    fun inlineHtmlUsesSourceUserAgentAndInjectsPreloadBeforePageScript() {
        val source = source("https://source.test")
        fixture.database.bookSourceDao.insert(source)
        withDialog(source, source.bookSourceUrl, "<html><head></head><body>inline-page</body></html>") { webView ->
            await { shadowOf(webView).lastLoadDataWithBaseURL != null }
            assertEquals("source-UA", webView.settings.userAgentString)
            val html = shadowOf(webView).lastLoadDataWithBaseURL.data
            assertTrue(html.indexOf("preloadedMarker") < html.indexOf("inline-page"))
        }
    }

    @Test
    fun firstUrlRequestIncludesSourceAndLoginHeaders() {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val request = executor.submit<List<String>> {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader()
                        val lines = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                        val body = "<html><head></head><body>remote-page</body></html>".toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                        lines
                    }
                }
                val url = "http://127.0.0.1:${server.localPort}/review"
                val source = source(url)
                fixture.database.bookSourceDao.insert(source)
                source.putLoginHeader("""{"Authorization":"Bearer login-token"}""")
                withDialog(source, url, null) { webView ->
                    await { shadowOf(webView).lastLoadDataWithBaseURL != null }
                    val headers = request.get(5, TimeUnit.SECONDS)
                        .drop(1).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
                    assertEquals("source-UA", headers["user-agent"])
                    assertEquals("https://source.test/", headers["referer"])
                    assertEquals("Bearer login-token", headers["authorization"])
                    assertTrue(shadowOf(webView).lastLoadDataWithBaseURL.data.contains("remote-page"))
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun selectedHeightOverridesInitialAndUpdatedSourceHeight() {
        val source = source("https://source.test")
        fixture.database.bookSourceDao.insert(source)
        val dialog = BottomWebViewDialog(
            source.getKey(),
            BookType.text,
            source.bookSourceUrl,
            html = "<html>comments</html>",
            config = """{"dialogHeight":400,"peekHeight":200,"maxHeight":450,"setFitToContents":false}""",
            heightPercent = 30,
        )
        showDialog(dialog) { webView ->
            await { shadowOf(webView).lastLoadDataWithBaseURL != null }
            val metrics = dialog.requireActivity().windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val expectedHeight = ((metrics.bounds.height() - insets.top - insets.bottom) * 0.3f).roundToInt()
            val behavior = sheetBehavior(dialog)
            assertEquals(expectedHeight, behavior.maxHeight)
            assertEquals(expectedHeight, behavior.peekHeight)
            assertTrue(behavior.isFitToContents)

            dialog.upConfig("""{"heightPercentage":0.9,"peekHeight":700,"maxHeight":800,"maxWidth":320}""")
            await { behavior.maxWidth == 320 }
            assertEquals(expectedHeight, behavior.maxHeight)
            assertEquals(expectedHeight, behavior.peekHeight)
        }
    }

    @Test
    fun defaultHeightFollowsSource() {
        val source = source("https://source.test")
        fixture.database.bookSourceDao.insert(source)
        val dialog = BottomWebViewDialog(
            source.getKey(),
            BookType.text,
            source.bookSourceUrl,
            html = "<html>comments</html>",
            config = """{"dialogHeight":400,"peekHeight":240,"maxHeight":400}""",
        )
        showDialog(dialog) { webView ->
            await { shadowOf(webView).lastLoadDataWithBaseURL != null }
            val behavior = sheetBehavior(dialog)
            assertEquals(400, behavior.maxHeight)
            assertEquals(240, behavior.peekHeight)
        }
    }

    private fun sheetBehavior(dialog: BottomWebViewDialog): BottomSheetBehavior<View> {
        val sheet = dialog.requireDialog().findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        return BottomSheetBehavior.from(sheet)
    }

    private fun source(url: String) = BookSource(
        bookSourceUrl = url,
        header = """{"User-Agent":"source-UA","Referer":"https://source.test/"}""",
        enabledCookieJar = false,
    )

    private fun withDialog(source: BookSource, url: String, html: String?, verify: (WebView) -> Unit) {
        showDialog(
            BottomWebViewDialog(source.getKey(), BookType.text, url, html, "window.preloadedMarker = true;"),
            verify,
        )
    }

    private fun showDialog(dialog: BottomWebViewDialog, verify: (WebView) -> Unit) {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Base_AppTheme)
        val activity = controller.setup().visible().get()
        try {
            dialog.showNow(activity.supportFragmentManager, "review")
            val webView = dialog.requireView().findViewById<FrameLayout>(R.id.web_view_container)
                .getChildAt(0) as WebView
            verify(webView)
        } finally {
            dialog.dismissNow()
            controller.pause().stop().destroy()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("WebView did not load before timeout", condition())
    }
}
