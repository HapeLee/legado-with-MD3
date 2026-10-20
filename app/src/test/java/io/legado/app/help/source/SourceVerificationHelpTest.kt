package io.legado.app.help.source

import android.app.Application
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.exception.NoStackTraceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SourceVerificationHelpTest {

    private lateinit var gateway: FakeOtherSettingsGateway

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        gateway = FakeOtherSettingsGateway()
        startKoin {
            modules(module {
                single<OtherSettingsGateway> { gateway }
            })
        }
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `blocked startBrowser does not start the browser activity`() {
        gateway.state = OtherSettings(blockSourceBrowser = true)

        SourceVerificationHelp.startBrowser(
            source = source(),
            url = "https://example.com/verify",
            title = "Verify",
        )

        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun `blocked startBrowserAwait fails instead of waiting for a browser result`() {
        gateway.state = OtherSettings(blockSourceBrowser = true)
        var failure: Throwable? = null

        val worker = Thread {
            failure = runCatching {
                SourceVerificationHelp.getVerificationResult(
                    source = source(),
                    url = "https://example.com/verify",
                    title = "Verify",
                    useBrowser = true,
                )
            }.exceptionOrNull()
        }
        worker.isDaemon = true
        worker.start()
        worker.join(5_000)

        assertTrue(failure is NoStackTraceException)
    }

    private fun source() = BookSource(
        bookSourceUrl = "https://example.com",
        bookSourceName = "Example",
    )

    private class FakeOtherSettingsGateway : OtherSettingsGateway {
        var state = OtherSettings()

        override val currentSettings: OtherSettings
            get() = state

        override val settings: Flow<OtherSettings>
            get() = flowOf(state)

        override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
            state = transform(state)
        }
    }
}
