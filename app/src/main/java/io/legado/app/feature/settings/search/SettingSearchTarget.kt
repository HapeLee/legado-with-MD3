package io.legado.app.feature.settings.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import kotlinx.coroutines.flow.first

/** Wraps both UI engines without changing the original control's actions or toggle semantics. */
@Composable
fun SettingSearchTarget(
    settingId: String,
    locate: Boolean,
    topInset: Dp,
    onLocated: () -> Unit,
    content: @Composable () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var highlighted by remember { mutableStateOf(false) }
    val highlightAlpha = remember { Animatable(0f) }
    val topInsetPx = with(LocalDensity.current) { topInset.toPx() }
    val highlightColor = LegadoTheme.colorScheme.primary
    val locatedDescription = stringResource(R.string.settings_search_located)

    LaunchedEffect(locate) {
        if (locate) {
            snapshotFlow { size }.first { it.height > 0 }
            requester.bringIntoView(Rect(0f, -topInsetPx, size.width.toFloat(), size.height.toFloat()))
            highlighted = true
            onLocated()
        }
    }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            highlightAlpha.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 1200
                    0f at 0
                    0.12f at 180
                    0f at 500
                    0f at 620
                    0.12f at 800
                    0f at 1120
                },
            )
            highlighted = false
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .testTag("setting:$settingId")
            .bringIntoViewRequester(requester)
            .onSizeChanged { size = it }
            .semantics { if (highlighted) stateDescription = locatedDescription }
            .drawWithContent {
                drawContent()
                if (highlightAlpha.value > 0f) drawRoundRect(
                    color = highlightColor.copy(alpha = highlightAlpha.value),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            },
    ) { content() }
}
