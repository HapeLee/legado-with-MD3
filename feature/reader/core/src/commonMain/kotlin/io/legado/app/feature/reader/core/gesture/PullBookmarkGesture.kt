package io.legado.app.feature.reader.core.gesture

import kotlin.math.abs

data class PullBookmarkConfig(
    val activationDistancePx: Float,
    val verticalDominanceRatio: Float = PullBookmarkDefaults.VERTICAL_DOMINANCE_RATIO,
    val maxPageOffsetRatio: Float = PullBookmarkDefaults.MAX_PAGE_OFFSET_RATIO,
)

/** One compatibility source for the legacy View host and the Compose reader surface. */
object PullBookmarkDefaults {
    const val ACTIVATION_DISTANCE_DP = 80f
    const val VERTICAL_DOMINANCE_RATIO = 1.5f
    const val MAX_PAGE_OFFSET_RATIO = 0.6f
    const val HINT_CONTENT_TOP_MARGIN_DP = 8
    const val HINT_FADE_MILLIS = 120
    const val HINT_CORNER_RADIUS_DP = 24f
    const val RETURN_DURATION_MILLIS = 180

    fun config(activationDistancePx: Float) = PullBookmarkConfig(
        activationDistancePx = activationDistancePx,
        verticalDominanceRatio = VERTICAL_DOMINANCE_RATIO,
        maxPageOffsetRatio = MAX_PAGE_OFFSET_RATIO,
    )
}

data class PullBookmarkDrag(
    val isCandidate: Boolean,
    val pageOffsetPx: Float,
    val isArmed: Boolean,
)

data class PullBookmarkClaim(
    val isDragging: Boolean,
    val isReleased: Boolean,
)

/** Pure gesture policy shared by the temporary View host and the new Compose pointer input. */
object PullBookmarkGesture {
    /** Release must use the pointer's final displacement, not the last MOVE sample. */
    fun shouldToggleOnRelease(wasDragging: Boolean, finalDrag: PullBookmarkDrag): Boolean =
        wasDragging && finalDrag.isCandidate && finalDrag.isArmed

    /**
     * Locks gesture ownership after a pull candidate has been rejected.
     *
     * The page-turn delegate may start animating as soon as ownership is released, so a later
     * diagonal movement cannot safely reclaim the same pointer stream for bookmarking.
     */
    fun claim(previouslyReleased: Boolean, drag: PullBookmarkDrag): PullBookmarkClaim =
        if (previouslyReleased || !drag.isCandidate) {
            PullBookmarkClaim(isDragging = false, isReleased = true)
        } else {
            PullBookmarkClaim(isDragging = true, isReleased = false)
        }

    fun drag(
        deltaX: Float,
        deltaY: Float,
        pageHeightPx: Float,
        enabled: Boolean,
        pagedMode: Boolean,
        selectionActive: Boolean,
        config: PullBookmarkConfig,
    ): PullBookmarkDrag {
        val candidate = enabled && pagedMode && !selectionActive && deltaY > 0f &&
            abs(deltaY) > abs(deltaX) * config.verticalDominanceRatio
        return PullBookmarkDrag(
            isCandidate = candidate,
            pageOffsetPx = if (candidate) {
                deltaY.coerceIn(0f, pageHeightPx * config.maxPageOffsetRatio)
            } else {
                0f
            },
            isArmed = candidate && deltaY >= config.activationDistancePx,
        )
    }
}
