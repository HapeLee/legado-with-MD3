package io.legado.app.domain.gateway

import android.app.Application
import io.legado.app.domain.model.settings.OtherSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [AndroidReadBookReplaceSessionGateway] 的基线测试。
 *
 * 只覆盖「当前无书」这一条：`ReadBook.book` 是 `private set`，单测无法注入书籍；
 * 而 `saveRead()` / `loadContent(false)` 会真实落库并启动加载协程，不适合放进单测。
 *
 * 钉住的是契约最关键的约定——无书时 [ReadBookReplaceSessionGateway.snapshot] 返回 null、
 * 两个 setter 静默跳过。这与迁移前 `ReplaceRuleViewModel` 里的
 * `ReadBook.book?.setUseReplaceRule(...)` / `?.setReSegment(...)` 逐条一致；
 * 一旦实现退化成直接解引用，这个测试会先红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AndroidReadBookReplaceSessionGatewayTest {

    private val gateway = AndroidReadBookReplaceSessionGateway(FakeOtherSettingsGateway())

    @Test
    fun snapshotReturnsNullWhenNoBookOpen() {
        assertNull(gateway.snapshot())
    }

    @Test
    fun settersAreSilentNoOpWhenNoBookOpen() {
        gateway.setUseReplaceRule(true)
        gateway.setReSegment(false)

        assertNull(gateway.snapshot())
    }

    private class FakeOtherSettingsGateway : OtherSettingsGateway {
        private val state = MutableStateFlow(OtherSettings())

        override val currentSettings: OtherSettings get() = state.value

        override val settings: Flow<OtherSettings> get() = state

        override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
            state.value = transform(state.value)
        }
    }
}
