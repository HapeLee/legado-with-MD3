package io.legado.app.core.ui.player

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState

/** 最多等待 60 个布局帧；宿主缺失或封面无法布局时也必须允许播放页打开。 */
internal const val PLAYER_ANCHOR_WAIT_FRAMES = 60

/** 返回 true 才使用胶囊形变；关闭胶囊立即淡入，不等待任何布局帧。 */
internal suspend fun awaitPlayerMorphAnchors(
    morph: ReadAloudMorphState,
    awaitCapsuleAnchor: Boolean,
): Boolean {
    if (!awaitCapsuleAnchor) return false
    var previousPanel = Rect.Zero
    var previousCover = Rect.Zero
    var stableFrames = 0
    repeat(PLAYER_ANCHOR_WAIT_FRAMES) {
        withFrameNanos { }
        val panel = morph.panelStartBounds
        val cover = morph.coverStartBounds
        val ready = !morph.screenBounds.isEmpty &&
                (!morph.coverPageVisible || !morph.capsuleCoverLinked || !morph.coverEndBounds.isEmpty) &&
                morph.hasCapsuleAnchors
        stableFrames =
            if (ready && panel == previousPanel && cover == previousCover) stableFrames + 1 else 0
        if (stableFrames >= 2) return true
        previousPanel = panel
        previousCover = cover
    }
    return false
}
