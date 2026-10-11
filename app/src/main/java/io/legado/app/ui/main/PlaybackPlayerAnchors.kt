package io.legado.app.ui.main

import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.domain.model.PlaybackCapsuleState

/** 只有同一播放会话的胶囊才能作为封面飞行起点。不同会话仍使用真实胶囊位置，但封面只淡出交接。 */
internal fun capsuleMatchesPlayer(
    capsule: PlaybackCapsuleState,
    source: PlaybackCapsuleSource,
    bookUrl: String,
): Boolean = capsule.source == source &&
        (source == PlaybackCapsuleSource.ReadAloud || bookUrl.isBlank() || capsule.bookUrl == bookUrl)

/** 与主页底栏的实际组合条件一致，隐藏底栏时由全局胶囊提供锚点。 */
internal fun shouldUseHomePlaybackCapsule(
    onMainRoute: Boolean,
    showBottomView: Boolean,
    useFloatingBottomBar: Boolean,
    useRail: Boolean,
): Boolean = onMainRoute && showBottomView && useFloatingBottomBar && !useRail

/**
 * 关闭预测性返回时，宿主必须在所有层级接管返回，不能只兜底根页面的退出。
 *
 * NavDisplay 只有进入预测性手势（`inPredictiveBack`）才会用 `predictivePopTransitionSpec`
 * 预览，而手势提交后它会在同一段可 seek 转场上把进度补到 1，所以这里不能靠把该 transform
 * 换成零时长来“关掉预览”——那样连正常的返回动画一起丢掉。改为宿主先消费返回事件：手势
 * 过程不接收进度（页面不跟手），松手后由 `navigateBack` 出栈，NavDisplay 走
 * `popTransitionSpec` 播放普通返回动画。
 *
 * `PredictiveBackHandler`、`BackHandler` 与 NavDisplay 的返回处理器都注册在同一个
 * `NavigationEventDispatcher` 上（后注册者优先）：宿主的处理器在 NavDisplay 之后注册，
 * 因此只要启用就抢在它前面；播放浮层、底部弹层与书内子页在更晚的时机注册，仍然优先。
 * 播放浮层存在时让位给它自己的返回处理。
 */
internal fun shouldHandleActivityBack(
    predictiveBackEnabled: Boolean,
    playerPresent: Boolean,
): Boolean =
    !predictiveBackEnabled && !playerPresent
