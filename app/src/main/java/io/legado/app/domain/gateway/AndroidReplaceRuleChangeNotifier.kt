package io.legado.app.domain.gateway

import io.legado.app.constant.EventBus
import io.legado.app.utils.postEvent

/**
 * [ReplaceRuleChangeNotifier] 的 Android 实现。
 *
 * 继续走 `postEvent`（底层 LiveEventBus），因为订阅方 `ReadBookViewModel` 用的是
 * 同一套总线的 `eventFlow`——换成 `FlowEventBus` 会导致收发不匹配，正文不再刷新。
 */
class AndroidReplaceRuleChangeNotifier : ReplaceRuleChangeNotifier {

    override fun notifyChanged() {
        postEvent(EventBus.REPLACE_RULE_CHANGED, Unit)
    }
}
