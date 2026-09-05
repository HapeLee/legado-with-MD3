package io.legado.app.feature.reader.core.transition

import kotlin.math.abs

enum class ReaderTransitionMode {
    COVER,
    SLIDE,
    SIMULATION,
    SCROLL,
    FADE,
    NONE;

    companion object {
        fun fromPageAnim(value: Int): ReaderTransitionMode = when (value) {
            0 -> COVER
            1 -> SLIDE
            2 -> SIMULATION
            3 -> SCROLL
            4 -> FADE
            else -> NONE
        }
    }
}

enum class ReaderTurnDirection { PREVIOUS, NEXT }

object ReaderCoverShadowPolicy {
    const val colorArgb: Int = 0x30111111
    const val widthDp: Float = 36f

    /** The cover shadow fades rightward from the moving page's trailing edge. */
    fun edgePx(direction: ReaderTurnDirection, displayOffsetPx: Float, pageWidthPx: Float): Float =
        when (direction) {
            ReaderTurnDirection.PREVIOUS -> displayOffsetPx
            ReaderTurnDirection.NEXT -> pageWidthPx + displayOffsetPx
        }
}

object ReaderProgrammaticTurnPolicy {
    /** Legacy PageDelegate.keyTurnPage ignores keys while a page animation is running. */
    fun shouldAccept(animationRunning: Boolean): Boolean = !animationRunning
}

data class ReaderPageTransition(
    val direction: ReaderTurnDirection? = null,
    val offsetPx: Float = 0f,
    val pageExtentPx: Float = 0f,
    val dragging: Boolean = false,
) {
    val progress: Float
        get() = if (pageExtentPx <= 0f) 0f else (abs(offsetPx) / pageExtentPx).coerceIn(0f, 1f)
}

data class ReaderTransitionDecision(
    val targetOffsetPx: Float,
    val commit: Boolean,
)

/** Direction is captured at touch-slop, while visible displacement starts at that capture point. */
data class ReaderHorizontalDrag(
    val originPx: Float,
    val direction: ReaderTurnDirection,
) {
    fun transition(
        totalDeltaPx: Float,
        pageExtentPx: Float,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ): ReaderPageTransition = ReaderPageTransitionPolicy.drag(
        deltaPx = totalDeltaPx - originPx,
        pageExtentPx = pageExtentPx,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        lockedDirection = direction,
    )

    companion object {
        fun capture(totalDeltaPx: Float): ReaderHorizontalDrag? = when {
            totalDeltaPx > 0f -> ReaderHorizontalDrag(totalDeltaPx, ReaderTurnDirection.PREVIOUS)
            totalDeltaPx < 0f -> ReaderHorizontalDrag(totalDeltaPx, ReaderTurnDirection.NEXT)
            else -> null
        }

    }
}

object ReaderPageTransitionPolicy {
    fun settleDurationMillis(
        mode: ReaderTransitionMode,
        currentOffsetPx: Float,
        targetOffsetPx: Float,
        pageExtentPx: Float,
        baseDurationMillis: Int = 360,
    ): Int {
        if (pageExtentPx <= 0f || baseDurationMillis <= 0 || currentOffsetPx == targetOffsetPx) return 0
        // FadePageDelegate animates its normalized alpha for the full configured duration.
        // Horizontal/curl delegates scale duration by their remaining scroll distance.
        return if (mode == ReaderTransitionMode.FADE) {
            baseDurationMillis
        } else {
            settleDurationMillis(currentOffsetPx, targetOffsetPx, pageExtentPx, baseDurationMillis)
        }
    }

    fun settleDurationMillis(
        currentOffsetPx: Float,
        targetOffsetPx: Float,
        pageExtentPx: Float,
        baseDurationMillis: Int = 300,
    ): Int {
        if (pageExtentPx <= 0f || baseDurationMillis <= 0) return 0
        return (baseDurationMillis * abs(targetOffsetPx - currentOffsetPx) / pageExtentPx)
            .toInt().coerceIn(0, baseDurationMillis)
    }

    fun drag(
        deltaPx: Float,
        pageExtentPx: Float,
        hasPrevious: Boolean,
        hasNext: Boolean,
        lockedDirection: ReaderTurnDirection? = null,
    ): ReaderPageTransition {
        if (pageExtentPx <= 0f || (deltaPx == 0f && lockedDirection == null)) return ReaderPageTransition(pageExtentPx = pageExtentPx)
        val direction = lockedDirection ?: if (deltaPx > 0f) ReaderTurnDirection.PREVIOUS else ReaderTurnDirection.NEXT
        val available = if (direction == ReaderTurnDirection.PREVIOUS) hasPrevious else hasNext
        return ReaderPageTransition(
            direction = direction,
            offsetPx = if (!available) 0f else when (direction) {
                ReaderTurnDirection.PREVIOUS -> deltaPx.coerceIn(0f, pageExtentPx)
                ReaderTurnDirection.NEXT -> deltaPx.coerceIn(-pageExtentPx, 0f)
            },
            pageExtentPx = pageExtentPx,
            dragging = available,
        )
    }

    fun release(
        transition: ReaderPageTransition,
        velocityPxPerSecond: Float,
        commitProgress: Float = 0.35f,
        flingVelocityPxPerSecond: Float = 900f,
        cancelled: Boolean = false,
        lastDragDeltaPx: Float? = null,
    ): ReaderTransitionDecision {
        val direction = transition.direction
            ?: return ReaderTransitionDecision(targetOffsetPx = 0f, commit = false)
        val velocityCommits = when (direction) {
            ReaderTurnDirection.PREVIOUS -> velocityPxPerSecond >= flingVelocityPxPerSecond
            ReaderTurnDirection.NEXT -> velocityPxPerSecond <= -flingVelocityPxPerSecond
        }
        // Horizontal View delegates cancel on the final reversal, regardless of distance.
        val movingForward = lastDragDeltaPx?.takeIf { it != 0f }?.let {
            if (direction == ReaderTurnDirection.NEXT) it < 0f else it > 0f
        }
        val commit = !cancelled && transition.dragging &&
            (movingForward ?: (transition.progress >= commitProgress || velocityCommits))
        val target = if (!commit) 0f else when (direction) {
            ReaderTurnDirection.PREVIOUS -> transition.pageExtentPx
            ReaderTurnDirection.NEXT -> -transition.pageExtentPx
        }
        return ReaderTransitionDecision(targetOffsetPx = target, commit = commit)
    }
}

data class ReaderPageTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val alpha: Float = 1f,
)

data class ReaderTransitionTransforms(
    val previous: ReaderPageTransform? = null,
    val current: ReaderPageTransform = ReaderPageTransform(),
    val next: ReaderPageTransform? = null,
    val currentOnTop: Boolean = false,
)

fun ReaderPageTransition.transforms(mode: ReaderTransitionMode): ReaderTransitionTransforms {
    val width = pageExtentPx
    return when (direction) {
        ReaderTurnDirection.PREVIOUS -> when (mode) {
            ReaderTransitionMode.FADE -> ReaderTransitionTransforms(
                previous = ReaderPageTransform(alpha = progress),
            )
            ReaderTransitionMode.COVER -> ReaderTransitionTransforms(
                previous = ReaderPageTransform(translationX = -width + offsetPx),
            )
            ReaderTransitionMode.SLIDE -> ReaderTransitionTransforms(
                previous = ReaderPageTransform(translationX = -width + offsetPx),
                current = ReaderPageTransform(translationX = offsetPx),
            )
            else -> ReaderTransitionTransforms()
        }
        ReaderTurnDirection.NEXT -> when (mode) {
            ReaderTransitionMode.FADE -> ReaderTransitionTransforms(
                next = ReaderPageTransform(alpha = progress),
            )
            ReaderTransitionMode.COVER -> ReaderTransitionTransforms(
                current = ReaderPageTransform(translationX = offsetPx),
                next = ReaderPageTransform(),
                currentOnTop = true,
            )
            ReaderTransitionMode.SLIDE -> ReaderTransitionTransforms(
                current = ReaderPageTransform(translationX = offsetPx),
                next = ReaderPageTransform(translationX = width + offsetPx),
                // SlidePageDelegate draws the destination first, then the current page.
                currentOnTop = true,
            )
            else -> ReaderTransitionTransforms()
        }
        null -> ReaderTransitionTransforms()
    }
}
