package io.legado.app.core.ui.player

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.legado.app.ui.book.readaloud.morph.LocalReadAloudMorph
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs

/** 在动画两端更新封面页可见性，避免每帧重组页面；过渡期间保留起始页面状态。 */
@Composable
fun TrackPlayerMorphCoverPage(pagerState: PagerState) {
    val morph = LocalReadAloudMorph.current
    LaunchedEffect(morph, pagerState) {
        if (morph == null) return@LaunchedEffect
        snapshotFlow {
            if (morph.expanded || morph.progress.value <= 0f) {
                pagerState.currentPage == 1 && abs(pagerState.currentPageOffsetFraction) < 0.001f
            } else null
        }.filterNotNull().collect(morph::reportCoverPageVisible)
    }
}

/** 播放页封面：保持目标布局，过渡期间让位给宿主唯一的飞行封面。 */
@Composable
fun PlayerMorphCover(
    appearance: PlayerMorphAppearance,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
) {
    val morph = LocalReadAloudMorph.current
    val radiusPx = with(LocalDensity.current) { 8.dp.toPx() }
    BookCoverImage(
        name = appearance.bookName,
        author = appearance.author,
        path = appearance.coverPath,
        sourceOrigin = appearance.sourceOrigin,
        bookUrl = null,
        preferCache = true,
        showLoadingPlaceholder = morph == null,
        requestBuilder = { if (morph != null) size(coil3.size.Size(1024, 1024)) },
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                morph?.reportCoverEnd(
                    bounds = coordinates.boundsInRoot(),
                    cornerRadiusPx = if (circular) {
                        minOf(coordinates.size.width, coordinates.size.height) / 2f
                    } else radiusPx,
                )
            }
            .clip(if (circular) CircleShape else RoundedCornerShape(8.dp))
            .graphicsLayer {
                alpha =
                    if (morph == null || morph.expanded || !morph.hasCapsuleAnchors || !morph.capsuleCoverLinked || morph.coverEndBounds.isEmpty) 1f else 0f
            },
    )
}
