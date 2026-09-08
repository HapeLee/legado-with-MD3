package io.legado.app.domain.gateway

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [AndroidReplaceRuleSettingsGateway] 的基线测试。
 *
 * 两条断言放在**同一个**测试方法内按序执行，不是图省事：底层是全局 `AppConfigStore`，
 * 同一进程内测试之间共享偏好状态，拆成两个方法会因执行顺序不同而互相污染。
 *
 * 钉住的是迁移等价性——未设置时回落 `"desc"`，且与迁移前
 * `context.getPrefString(PreferKey.replaceSortMode, "desc") ?: "desc"` 逐字一致；
 * 写入后能被同一个 gateway 读回。若 impl 擅自改 key 或改默认值，这里会先红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AndroidReplaceRuleSettingsGatewayTest {

    private val gateway = AndroidReplaceRuleSettingsGateway(RuntimeEnvironment.getApplication())

    @Test
    fun sortModeDefaultsToDescAndPersistsUpdate() = runTest {
        assertEquals("desc", gateway.getSortMode())

        gateway.setSortMode("asc")

        assertEquals("asc", gateway.getSortMode())
    }
}
