package io.legado.app.feature.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import io.legado.app.R
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.regularHazeEffect
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt

@Stable
internal data class TranslationBubbleLayout(val main: Offset, val actions: ImmutableList<Offset>)

/** Coordinates in dp, within the already inset safe rectangle. */
internal fun translationBubbleLayout(width: Float, height: Float, main: Offset, right: Boolean,
                                     expanded: Boolean = true): TranslationBubbleLayout {
    val clamped = Offset(main.x.coerceIn(0f, (width - 56f).coerceAtLeast(0f)),
        main.y.coerceIn(0f, (height - 56f).coerceAtLeast(0f)))
    val origin = if (expanded && height < 216f) clamped.copy(
        y = if (width < 224f || clamped.y + 28f < height / 2) 0f else (height - 56f).coerceAtLeast(0f)) else clamped
    val points = when {
        height >= 216f -> {
            val center = (origin.y + 28f).coerceIn(108f, height - 108f)
            val x = (if (right) origin.x - 76f else origin.x + 68f)
                .coerceIn(0f, (width - 64f).coerceAtLeast(0f))
            listOf(Offset(x, center - 108f), Offset(x, center - 32f), Offset(x, center + 44f))
        }
        width >= 224f -> {
            val x = (origin.x - 76f).coerceIn(0f, width - 216f)
            val y = if (origin.y + 28f < height / 2) height - 64f else 0f
            List(3) { Offset(x + it * 76f, y.coerceAtLeast(0f)) }
        }
        else -> {
            val x = if (right) 0f else (width - 64f).coerceAtLeast(0f)
            val y = (height - 64f).coerceAtLeast(0f)
            listOf(Offset(x, 0f), Offset(x, y), Offset(if (right) (width - 64f).coerceAtLeast(0f) else 0f, y))
        }
    }
    return TranslationBubbleLayout(origin, points.toImmutableList())
}

@Composable
fun MangaTranslationBubble(
    controls: MangaTranslationControls,
    right: Boolean,
    relativeHeight: Int,
    eInk: Boolean,
    hazeState: HazeState?,
    canHandleBack: Boolean,
    onMove: (Boolean, Int) -> Unit,
    onAutomatic: () -> Unit,
    onDisplay: () -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    BackHandler(enabled = canHandleBack && expanded) { expanded = false }
    val density = LocalDensity.current
    val solid = eInk || !LocalAppUiConfiguration.current.theme.enableBlur || hazeState == null
    val moveLeft = stringResource(R.string.feature_manga_translation_move_left)
    val moveRight = stringResource(R.string.feature_manga_translation_move_right)
    BoxWithConstraints(Modifier.fillMaxSize()
        .then(if (expanded) Modifier.pointerInput(Unit) { detectTapGestures { expanded = false } } else Modifier)
        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.mandatorySystemGestures))
        .padding(8.dp)) {
        val width = maxWidth.value
        val height = maxHeight.value
        var position by remember { mutableStateOf(Offset.Zero) }
        var dragging by remember { mutableStateOf(false) }
        LaunchedEffect(width, height, right, relativeHeight) {
            if (!dragging) position = Offset(if (right) (width - 56f).coerceAtLeast(0f) else 0f,
                (height - 56f).coerceAtLeast(0f) * relativeHeight.coerceIn(0, 1000) / 1000f)
        }
        val layout = translationBubbleLayout(width, height, position, right, expanded)
        if (expanded) {
            val labels = listOf(stringResource(R.string.feature_manga_translation_auto),
                stringResource(R.string.feature_manga_translation_switch), stringResource(R.string.feature_manga_translation_clear))
            val icons = listOf(Icons.Default.Translate, Icons.Default.SwapHoriz, Icons.Default.CleaningServices)
            layout.actions.forEachIndexed { index, point ->
                val active = index == 0 && controls.automatic || index == 1 && controls.showTranslation
                val progress = if (index == 0 && controls.automatic && controls.total > 0)
                    stringResource(R.string.feature_manga_translation_progress, controls.completed, controls.total) else null
                GlassBubble(Modifier.offset { with(density) { IntOffset(point.x.dp.roundToPx(), point.y.dp.roundToPx()) } }
                    .size(64.dp).semantics {
                        selected = active
                        if (progress != null) stateDescription = progress
                    }.clickable {
                        when (index) { 0 -> onAutomatic(); 1 -> onDisplay(); else -> { expanded = false; onClear() } }
                    }, solid, eInk, hazeState, active) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icons[index], null, Modifier.size(if (progress == null) 22.dp else 18.dp))
                        Text(labels[index], fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center)
                        if (progress != null) Text(progress, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        val mainLabel = stringResource(R.string.feature_manga_translation_title)
        GlassBubble(Modifier.offset { with(density) { IntOffset(layout.main.x.dp.roundToPx(), layout.main.y.dp.roundToPx()) } }
            .size(56.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(moveLeft) { onMove(false, relativeHeight); true },
                    CustomAccessibilityAction(moveRight) { onMove(true, relativeHeight); true },
                )
                stateDescription = mainLabel
            }
            .pointerInput(width, height) {
                fun finish() {
                    dragging = false
                    val snapRight = position.x + 28f > width / 2
                    val relative = if (height <= 56f) 500 else (position.y / (height - 56f) * 1000).roundToInt().coerceIn(0, 1000)
                    position = Offset(if (snapRight) (width - 56f).coerceAtLeast(0f) else 0f, position.y)
                    onMove(snapRight, relative)
                }
                detectDragGestures(onDragStart = { dragging = true; expanded = false },
                    onDragEnd = ::finish, onDragCancel = ::finish) { change, amount ->
                    change.consume()
                    position = translationBubbleLayout(width, height, position + amount / density.density, right, expanded = false).main
                }
            }
            .clickable { expanded = !expanded }, solid, eInk, hazeState, true) {
                Text(stringResource(R.string.feature_manga_translation_bubble), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
    }
}

@Composable
private fun GlassBubble(modifier: Modifier, solid: Boolean, eInk: Boolean, haze: HazeState?,
                        active: Boolean, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val surface = if (active) colors.primaryContainer else colors.surfaceContainerHigh
    val foreground = if (active) colors.onPrimaryContainer else colors.onSurface
    val glass = if (!solid && haze != null) Modifier.regularHazeEffect(haze) else Modifier
    Box(modifier.shadow(if (eInk) 0.dp else 10.dp, CircleShape,
        ambientColor = colors.primary.copy(alpha = .2f), spotColor = colors.primary.copy(alpha = .35f))
        .clip(CircleShape).then(glass)
        .background(if (solid) Brush.linearGradient(listOf(surface, surface)) else
            Brush.linearGradient(listOf(Color.White.copy(alpha = .42f), surface.copy(alpha = .55f), colors.primary.copy(alpha = .3f))))
        .border(1.dp, Brush.linearGradient(if (solid) listOf(colors.outline, colors.outline) else
            listOf(Color.White.copy(alpha = .85f), Color.White.copy(alpha = .15f), colors.primary.copy(alpha = .4f))), CircleShape),
        contentAlignment = Alignment.Center) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides foreground) { content() }
    }
}
