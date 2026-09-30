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

/** 宿主只在真正的根页面（如主页）且未开启预测性返回时兜底处理退出，子页面由自身及导航层优先拦截。播放浮层优先处理返回。 */
internal fun shouldHandleActivityBack(
    predictiveBackEnabled: Boolean,
    playerPresent: Boolean,
    isRoot: Boolean = true,
): Boolean =
    !predictiveBackEnabled && !playerPresent && isRoot
