package io.legado.app.ui.main

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.widget.components.image.cover.sharedCoverSourceRadius

/** Connects a book cover to a full-screen reading destination, including the overlay corners. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.readerSharedBounds(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedCoverKey: String?,
    displayCornerRadiusPx: Float,
    density: Float,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null || sharedCoverKey == null) {
        return this
    }
    val targetRadius = displayCornerRadiusPx / density
    val startRadius = sharedCoverSourceRadius(sharedCoverKey)?.value ?: targetRadius
    val radius by animatedVisibilityScope.transition.animateFloat(
        label = "reader-clip-corner-radius",
    ) { state ->
        if (state == EnterExitState.Visible) targetRadius else startRadius
    }
    return this.then(with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(sharedCoverKey),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = fadeIn(animationSpec = tween(600)),
            exit = fadeOut(animationSpec = tween(600)),
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(radius.dp)),
        )
    })
}
