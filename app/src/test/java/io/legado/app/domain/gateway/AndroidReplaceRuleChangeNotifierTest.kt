package io.legado.app.domain.gateway

import io.legado.app.constant.EventBus
import io.legado.app.utils.eventBus.AppEventBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AndroidReplaceRuleChangeNotifier] 的收发匹配基线。
 *
 * 钉住的是一条**不能靠重构直觉判断**的硬约束：替换规则的变更必须投递到
 * `AppEventBus` 的 `REPLACE_RULE_CHANGED`。订阅方 `ReadBookViewModel` 用的是
 * `AppEventBus.observe`（见其 `eventFlow`），一旦有人把实现换成另一套总线
 * （例如 `FlowEventBus`），阅读页的正文刷新会静默失效——编译照过、单测照绿，
 * 只有真机翻页才发现。这里用一次真实的订阅把这个契约钉死。
 *
 * 投递是异步的（`postEvent` 最终走 `AppEventBus.post` → `callbackFlow.trySend`），
 * 因此沿用 `AppEventBusTest` 的写法：注册后 `delay` 一拍再断言。
 */
class AndroidReplaceRuleChangeNotifierTest {

    @Test
    fun `notifyChanged 经 AppEventBus 投递 REPLACE_RULE_CHANGED`() = runBlocking {
        val received = mutableListOf<Unit>()
        val job = AppEventBus.observe<Unit>(EventBus.REPLACE_RULE_CHANGED)
            .onEach { received += it }
            .launchIn(this)
        delay(20) // 等 callbackFlow 完成监听注册

        AndroidReplaceRuleChangeNotifier().notifyChanged()
        delay(20)

        assertEquals(listOf(Unit), received)
        job.cancel()
    }
}
