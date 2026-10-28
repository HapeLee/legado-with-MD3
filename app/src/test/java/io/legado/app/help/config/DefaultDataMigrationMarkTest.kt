package io.legado.app.help.config

import android.app.Application
import androidx.core.content.edit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 内置默认数据迁移标记的语义：读取不写值，只有对应导入成功后显式 mark 才算完成。
 *
 * 这条不变量是「升级后默认数据迁移被跳过」的防线：一旦 needUp* 退回「读取即消费」
 * 的写法，导入失败（DB 未就绪、进程被杀）就再也不会重试，用户只能等下一次
 * needUp 版本号提升。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DefaultDataMigrationMarkTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        LocalConfig.edit { clear() }
    }

    @Test
    fun readingFlagsDoesNotConsumeThem() {
        repeat(3) {
            assertTrue(LocalConfig.needUpHttpTTS)
            assertTrue(LocalConfig.needUpTxtTocRule)
            assertTrue(LocalConfig.needUpRssSources)
            assertTrue(LocalConfig.needUpDictRule)
        }
    }

    @Test
    fun flagsClearOnlyAfterMarking() {
        LocalConfig.markHttpTTSUpToDate()
        LocalConfig.markTxtTocRuleUpToDate()
        LocalConfig.markRssSourcesUpToDate()
        LocalConfig.markDictRuleUpToDate()

        assertFalse(LocalConfig.needUpHttpTTS)
        assertFalse(LocalConfig.needUpTxtTocRule)
        assertFalse(LocalConfig.needUpRssSources)
        assertFalse(LocalConfig.needUpDictRule)
    }

    @Test
    fun partialFailureKeepsRemainingFlagsPending() {
        // 只提交成功的那一项，失败项必须保持待迁移，下次冷启动才会重试
        LocalConfig.markDictRuleUpToDate()

        assertFalse(LocalConfig.needUpDictRule)
        assertTrue(LocalConfig.needUpHttpTTS)
        assertTrue(LocalConfig.needUpTxtTocRule)
        assertTrue(LocalConfig.needUpRssSources)
    }
}
