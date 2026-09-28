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
    // 返回书架时把正文压在封面之上：封面那一侧的 sharedBounds 用的是库默认 fadeIn()（spring），
    // 几百毫秒就满了，而正文是自己的 600ms 淡出。overlay 里谁在上面谁决定观感——封面在上面
    // 就是「先闪出封面、再看着它缩小」，正文在上面才是打开动画的倒放（边缩小边让正文化掉、露出封面）。
    // 进入方向上正文本来就是进入方、本来就画在上面，所以这条对打开的观感没有影响。
    return this.then(with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(sharedCoverKey),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = fadeIn(animationSpec = tween(600)),
            exit = fadeOut(animationSpec = tween(600)),
            zIndexInOverlay = 1f,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(radius.dp)),
        )
    })
}
