package io.legado.app.model

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.TTSReadAloudService
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadAloudStopServiceTest {
    private lateinit var previousServiceClass: Class<*>

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        stopKoin()
        startKoin {
            modules(module {
                single<ReadAloudSettingsGateway> { TestReadAloudSettingsGateway() }
            })
        }
        val serviceClassField = ReadAloud::class.java.getDeclaredField("aloudClass").apply {
            isAccessible = true
        }
        previousServiceClass = serviceClassField.get(ReadAloud) as Class<*>
        serviceClassField.set(ReadAloud, TTSReadAloudService::class.java)
        setServiceFlag("isRun", true)
        setServiceFlag("stopRequested", false)
    }

    @After
    fun tearDown() {
        ReadAloud::class.java.getDeclaredField("aloudClass").apply {
            isAccessible = true
            set(ReadAloud, previousServiceClass)
        }
        setServiceFlag("isRun", false)
        setServiceFlag("stopRequested", false)
        stopKoin()
    }

    @Test
    fun stopFromExternalCapsuleDoesNotStartAnotherForegroundService() {
        val context = RecordingContext(RuntimeEnvironment.getApplication())

        ReadAloud.stop(context)

        assertEquals(
            TTSReadAloudService::class.java.name,
            context.stoppedService?.component?.className
        )
        assertEquals(0, context.foregroundStartCount)
        assertFalse(BaseReadAloudService.isRun)
        assertFalse(BaseReadAloudService.requestStop())
    }

    private fun setServiceFlag(name: String, value: Boolean) {
        BaseReadAloudService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            setBoolean(null, value)
        }
    }

    private class RecordingContext(base: Application) : ContextWrapper(base) {
        var stoppedService: Intent? = null
        var foregroundStartCount = 0

        override fun stopService(name: Intent): Boolean {
            stoppedService = name
            return true
        }

        override fun startForegroundService(service: Intent): ComponentName? {
            foregroundStartCount++
            return service.component
        }
    }

    private class TestReadAloudSettingsGateway : ReadAloudSettingsGateway {
        override val settings = MutableStateFlow(ReadAloudSettings())
        override val currentSettings get() = settings.value

        override suspend fun update(transform: (ReadAloudSettings) -> ReadAloudSettings) {
            settings.value = transform(settings.value)
        }
    }
}
