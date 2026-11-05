package io.legado.app.model

import android.app.Application
import android.util.Base64
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.ReadSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「强制软件气泡」分支：书源把段评入口画成一张图时，打开开关后才有的那半套判定。
 *
 * 这一分支要读全局开关（走 Koin），所以这里起一个 Koin 实例；Koin 只在类级别起停：
 * [ParagraphBubbleSource] 的开关读法是「首次解析一次 gateway 后一直持有」（生产环境
 * 同样如此），逐方法重启容器会让它握着上一个测试留下的旧实例。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ParagraphBubbleForcedSourceTest {

    @Before
    fun setUp() {
        fakeGateway.setForceSoftware(true)
    }

    @Test
    fun readsCountPaintedInsideInlineSvg() {
        val icon = dataSvgIcon(count = "7")

        val spec = ParagraphBubbleSource.parse("$icon,{\"style\":\"text\"}")

        assertEquals("7", spec?.displayText)
        assertEquals("normal", spec?.status)
    }

    @Test
    fun readsCountFromOptionsWhenIconItselfHasNoText() {
        val source = "https://example.com/comment.png,{\"click\":\"showCmt(1)\",\"num\":\"12\"}"

        val spec = ParagraphBubbleSource.parse(source)

        assertEquals("12", spec?.displayText)
    }

    @Test
    fun cacheIsKeyedOnForceSwitch() {
        val source = "https://example.com/comment.png,{\"click\":\"showCmt(1)\",\"num\":\"12\"}"
        assertEquals("12", ParagraphBubbleSource.parse(source)?.displayText)

        // 关掉开关：同一条 src 必须立刻变回普通图片（缓存不能跨开关态沿用）
        fakeGateway.setForceSoftware(false)
        assertNull(ParagraphBubbleSource.parse(source))

        fakeGateway.setForceSoftware(true)
        assertEquals("12", ParagraphBubbleSource.parse(source)?.displayText)
    }

    @Test
    fun ordinaryImageStaysOrdinary() {
        // 没有任何段评语义（style/type/click 都不沾边）→ 不能因为开了开关就把配图吃掉
        val source = "https://example.com/cover.png,{\"width\":\"50%\"}"

        assertNull(ParagraphBubbleSource.parse(source))
    }

    private fun dataSvgIcon(count: String): String {
        val markup = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"48\" height=\"48\">" +
            "<path d=\"M4 4h40v40H4z\"/>" +
            "<text x=\"24\" y=\"28\" text-anchor=\"middle\">$count</text>" +
            "</svg>"
        val encoded = Base64.encodeToString(markup.toByteArray(), Base64.NO_WRAP)
        return "data:image/svg+xml;base64,$encoded"
    }

    companion object {
        private val fakeGateway = FakeReadSettingsGateway()

        @JvmStatic
        @BeforeClass
        fun startKoinOnce() {
            stopKoin()
            startKoin {
                modules(
                    module {
                        single<ReadSettingsGateway> { fakeGateway }
                    }
                )
            }
        }

        @JvmStatic
        @AfterClass
        fun stopKoinOnce() {
            stopKoin()
        }
    }

    private class FakeReadSettingsGateway : ReadSettingsGateway {
        override val settings = MutableStateFlow(
            ReadSettings(forceSoftwareParagraphBubble = true)
        )
        override val currentSettings get() = settings.value
        override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
            settings.value = transform(settings.value)
        }

        fun setForceSoftware(enabled: Boolean) = runBlocking {
            update { it.copy(forceSoftwareParagraphBubble = enabled) }
        }
    }
}
