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

/** 播放浮层（包括进出场）优先处理返回，不能弹掉下层页面。 */
internal fun shouldHandleActivityBack(
    predictiveBackEnabled: Boolean,
    playerPresent: Boolean
): Boolean =
    !predictiveBackEnabled && !playerPresent
