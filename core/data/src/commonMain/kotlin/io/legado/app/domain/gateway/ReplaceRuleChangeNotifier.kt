package io.legado.app.domain.gateway

/**
 * 替换规则变更的广播出口。
 *
 * 替换规则的增删改会让已加载的正文内容失效，阅读页需要重新应用规则。
 * 这是 `:feature:replacerules` 与阅读器之间唯一的横向通信点——通过契约协作，
 * 避免 feature 直接依赖 app 侧的事件总线工具（`postEvent` 底层是 Android 的
 * LiveEventBus，无法进 `commonMain`）。
 *
 * 实现侧**必须**继续投递到 `EventBus.REPLACE_RULE_CHANGED`：订阅方的
 * `ReadBookViewModel` 走的是同一套总线的 `eventFlow`，换成别的总线会收发不匹配。
 */
interface ReplaceRuleChangeNotifier {

    fun notifyChanged()
}
