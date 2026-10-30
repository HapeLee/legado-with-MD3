package io.legado.app.feature.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import io.legado.app.R
import io.legado.app.ui.theme.LocalAppUiConfiguration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlin.math.*

@Stable
internal data class TranslationBubbleLayout(val main: Offset, val actions: ImmutableList<Offset>)

/** Coordinates are 48dp touch boxes; the 44dp main circle is centered inside its box. */
internal fun translationBubbleLayout(width: Float, height: Float, main: Offset, right: Boolean,
                                     expanded: Boolean = true): TranslationBubbleLayout {
    val maxX = (width - 48f).coerceAtLeast(0f)
    val maxY = (height - 48f).coerceAtLeast(0f)
    val origin = Offset(main.x.coerceIn(0f, maxX), main.y.coerceIn(0f, maxY))
    if (width >= 192f && height >= 224f) {
        val radius = 84f
        val vertical = radius * sin(55f * PI.toFloat() / 180f)
        val anchor = Offset(if (right) (width - 56f).coerceAtLeast(8f) else 8f,
            origin.y.coerceIn(vertical + 8f, height - 56f - vertical))
        val fan = listOf(-55f, 0f, 55f).map { degrees ->
            val angle = degrees * PI.toFloat() / 180f
            anchor + Offset((if (right) -1f else 1f) * radius * cos(angle), radius * sin(angle))
        }
        return TranslationBubbleLayout(if (expanded) anchor else origin, fan.toImmutableList())
    }
    val points = when {
        height >= 168f -> {
            val center = (origin.y + 24f).coerceIn(84f, height - 84f)
            val x = (origin.x + if (right) -56f else 56f).coerceIn(0f, maxX)
            List(3) { Offset(x, center - 80f + it * 56f) }
        }
        width >= 168f -> {
            val x = (origin.x - 56f).coerceIn(0f, width - 160f)
            val y = if (origin.y + 24f < height / 2f) maxY else 0f
            List(3) { Offset(x + it * 56f, y) }
        }
        else -> listOf(Offset(0f, 0f), Offset(maxX, 0f), Offset(if (right) 0f else maxX, maxY))
    }
    val adjusted = if (expanded && height < 168f) origin.copy(y = if (origin.y + 24 < height / 2) 0f else maxY) else origin
    return TranslationBubbleLayout(adjusted, points.toImmutableList())
}

internal fun translationBubbleHiddenX(width: Float, right: Boolean): Float = if (right) width - 24f else -24f
internal const val TranslationBubbleIdleMillis = 5000L

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
    var dragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var interaction by remember { mutableLongStateOf(0) }
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) }
    var exploring by remember(accessibility) { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(accessibility) {
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { exploring = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    // Touch exploration keeps the menu reachable while a screen reader moves its accessibility focus.
    LaunchedEffect(expanded, interaction, dragging, pressed, focused, exploring) {
        if (expanded && !dragging && !pressed && !focused && !exploring) {
            delay(TranslationBubbleIdleMillis)
            expanded = false
        }
    }
    BackHandler(enabled = canHandleBack && expanded) { expanded = false }
    val openness by animateFloatAsState(if (expanded || dragging) 1f else 0f,
        spring(dampingRatio = .85f, stiffness = 300f), label = "translation-bubble-dock")
    val reveal by animateFloatAsState(if (expanded) 1f else 0f,
        spring(dampingRatio = if (eInk) 1f else .78f, stiffness = 480f), label = "translation-liquid-fission")
    val glimmer = remember { Animatable(1f) }
    LaunchedEffect(expanded) {
        glimmer.snapTo(0f)
        glimmer.animateTo(1f, tween(if (expanded) 650 else 350))
    }
    val density = LocalDensity.current
    val solid = eInk
    val blurEnabled = !eInk && LocalAppUiConfiguration.current.theme.enableBlur && hazeState != null
    val colors = MaterialTheme.colorScheme
    val moveLeft = stringResource(R.string.feature_manga_translation_move_left)
    val moveRight = stringResource(R.string.feature_manga_translation_move_right)
    val mainLabel = stringResource(R.string.feature_manga_translation_title)
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()
        .then(if (expanded) Modifier.pointerInput(Unit) { detectTapGestures { expanded = false } } else Modifier)
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        val width = maxWidth.value
        val height = maxHeight.value
        var position by remember { mutableStateOf(Offset(
            if (right) (width - 48f).coerceAtLeast(0f) else 0f,
            (height - 48f).coerceAtLeast(0f) * relativeHeight.coerceIn(0, 1000) / 1000f)) }
        LaunchedEffect(width, height, right, relativeHeight) {
            if (!dragging) position = Offset(if (right) (width - 48f).coerceAtLeast(0f) else 0f,
                (height - 48f).coerceAtLeast(0f) * relativeHeight.coerceIn(0, 1000) / 1000f)
        }
        val layout = translationBubbleLayout(width, height, position, right, expanded)
        val visibleX = layout.main.x.coerceIn(minOf(8f, (width - 48f).coerceAtLeast(0f)), (width - 56f).coerceAtLeast(8f))
        val mainX = if (dragging) position.x else translationBubbleHiddenX(width, right) +
            (visibleX - translationBubbleHiddenX(width, right)) * openness
        val animatedY by animateFloatAsState(layout.main.y,
            spring(dampingRatio = .85f, stiffness = 300f), label = "translation-bubble-height")
        val main = Offset(mainX, if (dragging) position.y else animatedY)
        fun Modifier.at(point: Offset) = offset {
            with(density) { IntOffset(point.x.dp.roundToPx(), point.y.dp.roundToPx()) }
        }.graphicsLayer {
            // Preserve fractional pixels while keeping layout and hit targets at the dock position.
            val x = with(density) { point.x.dp.toPx() }
            val y = with(density) { point.y.dp.toPx() }
            translationX = x - x.roundToInt()
            translationY = y - y.roundToInt()
        }
        fun Modifier.activity(): Modifier = pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    pressed = event.changes.any { it.pressed }
                    interaction++
                }
            }
        }.onFocusChanged { focused = it.hasFocus }
        if ((reveal > .01f || expanded) && !dragging) {
            val amounts = List(3) { index -> ((reveal - index * .055f) / (1f - index * .055f)).coerceIn(0f, 1.12f) }
            val actionPoints = layout.actions.mapIndexed { index, target ->
                translationBubbleRevealPoint(main, target, amounts[index], index)
            }
            val centers = (actionPoints + main).map { it + Offset(24f, 24f) }
            val left = centers.minOf { it.x } - 44f
            val top = centers.minOf { it.y } - 44f
            MangaTranslationMembrane(
                modifier = Modifier.at(Offset(left, top)),
                root = centers.last() - Offset(left, top),
                children = centers.dropLast(1).map { it - Offset(left, top) }.toImmutableList(),
                size = Size(centers.maxOf { it.x } + 44f - left, centers.maxOf { it.y } + 44f - top),
                reveal = reveal, solid = solid, blurEnabled = blurEnabled, surface = colors.surfaceContainer, haze = hazeState,
            )
            if (!solid && !exploring && glimmer.value < 1f) {
                Canvas(Modifier.fillMaxSize()) {
                    val phase = glimmer.value
                    val fade = sin(phase * PI.toFloat()).coerceAtLeast(0f) * .38f
                    layout.actions.forEachIndexed { index, target ->
                        val start = main + Offset(24f, 24f)
                        val end = target + Offset(24f, 24f)
                        val point = translationBubbleRevealPoint(start, end, phase, index) + Offset(0f, -26f)
                        val center = Offset(point.x.dp.toPx(), point.y.dp.toPx())
                        val radius = (1.3f + index * .2f).dp.toPx()
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = fade * .2f), Color.Transparent), center, radius * 4f), radius * 4f, center)
                        drawLine(Color(0xFFE8F7FF).copy(alpha = fade), center - Offset(radius, 0f), center + Offset(radius, 0f), .7.dp.toPx(), StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = fade), center - Offset(0f, radius), center + Offset(0f, radius), .7.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
            val progress = stringResource(R.string.feature_manga_translation_progress, controls.completed, controls.total)
            val displayMode = stringResource(if (controls.showTranslation)
                R.string.feature_manga_translation_translated_short else R.string.feature_manga_translation_original_short)
            val automaticState = stringResource(if (controls.automatic)
                R.string.feature_manga_translation_enabled_status else R.string.feature_manga_translation_disabled)
            val automaticDescription = stringResource(R.string.feature_manga_translation_state_progress, automaticState, progress)
            val labels = listOf(progress, displayMode, stringResource(R.string.feature_manga_translation_clear_short))
            val descriptions = listOf(stringResource(R.string.feature_manga_translation_auto),
                stringResource(R.string.feature_manga_translation_switch), stringResource(R.string.feature_manga_translation_clear))
            layout.actions.forEachIndexed { index, _ ->
                val point = actionPoints[index]
                val amount = amounts[index]
                val active = index == 0 && controls.automatic || index == 1 && controls.showTranslation
                val interactionSource = remember { MutableInteractionSource() }
                val actionPressed by interactionSource.collectIsPressedAsState()
                val pressAmount by animateFloatAsState(if (actionPressed) 1f else 0f,
                    if (eInk) snap() else tween(120), label = "translation-action-press")
                Box(Modifier.at(point).size(48.dp).clip(CircleShape).graphicsLayer {
                    alpha = amount.coerceIn(0f, 1f)
                    scaleX = amount.coerceAtLeast(.001f)
                    scaleY = scaleX * (1f - .08f * sin(amount * PI.toFloat()))
                }
                    .activity().semantics {
                        contentDescription = descriptions[index]
                        selected = active
                        when (index) {
                            0 -> stateDescription = automaticDescription
                            1 -> stateDescription = displayMode
                        }
                        if (!expanded) hideFromAccessibility()
                    }.clickable(enabled = expanded && amount > .65f, role = Role.Button,
                        interactionSource = interactionSource, indication = null) {
                        interaction++
                        when (index) { 0 -> onAutomatic(); 1 -> onDisplay(); else -> { expanded = false; onClear() } }
                    }, contentAlignment = Alignment.Center) {
                    GlassBubble(Modifier.size(48.dp).graphicsLayer {
                        scaleX = if (eInk) 1f else 1f - .06f * pressAmount
                        scaleY = scaleX
                    }, solid, eInk, active, 1f, pressAmount) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TranslationGlassIcon(index, Modifier.size(24.dp), solid)
                            BasicText(labels[index], modifier = Modifier.width(40.dp).height(13.dp),
                                style = TextStyle(color = androidx.compose.material3.LocalContentColor.current,
                                    fontSize = 10.sp, textAlign = TextAlign.Center),
                                maxLines = 1, softWrap = false,
                                autoSize = TextAutoSize.StepBased(minFontSize = 2.sp, maxFontSize = 10.sp, stepSize = .25.sp))
                        }
                    }
                }
            }
        }
        Box(Modifier.at(main).size(48.dp).clip(CircleShape)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = .4f + .6f * openness.coerceIn(0f, 1f)
            }
            .activity().semantics {
                contentDescription = mainLabel
                customActions = listOf(
                    CustomAccessibilityAction(moveLeft) { onMove(false, relativeHeight); true },
                    CustomAccessibilityAction(moveRight) { onMove(true, relativeHeight); true })
            }.pointerInput(width, height, right) {
                fun finish() {
                    dragging = false
                    pressed = false
                    interaction++
                    val snapRight = position.x + 24f > width / 2
                    val relative = if (height <= 48f) 500 else (position.y / (height - 48f) * 1000).roundToInt().coerceIn(0, 1000)
                    position = Offset(if (snapRight) (width - 48f).coerceAtLeast(0f) else 0f, position.y)
                    onMove(snapRight, relative)
                }
                detectDragGestures(onDragStart = { dragging = true; expanded = false; interaction++ },
                    onDragEnd = ::finish, onDragCancel = ::finish) { change, amount ->
                    change.consume()
                    position = translationBubbleLayout(width, height, position + amount / density.density, right, false).main
                }
            }.clickable(role = Role.Button) { interaction++; expanded = !expanded },
            contentAlignment = Alignment.Center) {
            GlassBubble(Modifier.size(44.dp), solid, eInk, false, openness.coerceIn(0f, 1f)) {
                TranslationEntryIcon(main = true, modifier = Modifier.size(28.dp), solid = solid)
            }
        }
    }
}

@Composable
private fun GlassBubble(modifier: Modifier, solid: Boolean, eInk: Boolean,
                        active: Boolean, intensity: Float, pressAmount: Float = 0f,
                        content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val surface = if (active) colors.primaryContainer else colors.surfaceContainerHigh
    Box(modifier.drawBehind {
        if (!eInk && !solid && intensity > .01f) {
            val radius = size.minDimension / 2f + 5.dp.toPx()
            drawCircle(Brush.radialGradient(listOf(colors.onSurface.copy(alpha = .10f * intensity),
                Color.Transparent), center, radius), radius, center)
        }
    }.clip(CircleShape)
        .background(if (solid) Brush.linearGradient(listOf(surface, surface)) else Brush.linearGradient(
            listOf(Color.White.copy(alpha = .4f), surface.copy(alpha = .25f), Color(0xFFBCD9EA).copy(alpha = .18f))))
        .drawBehind {
            if (active && !solid) drawCircle(colors.primary.copy(alpha = .24f))
            if (pressAmount > 0f) drawCircle(colors.onSurface.copy(alpha = .16f * pressAmount))
        }
        .border(.7.dp, Brush.linearGradient(if (solid) listOf(colors.outline, colors.outline) else
            listOf(lerp(colors.outline.copy(alpha = .6f), Color.White.copy(alpha = .65f), intensity),
                lerp(colors.outline.copy(alpha = .4f), Color.White.copy(alpha = .04f), intensity),
                lerp(colors.outline.copy(alpha = .5f), Color.White.copy(alpha = .15f), intensity))), CircleShape),
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides
            if (active) colors.onPrimaryContainer else colors.onSurface) { content() }
    }
}

/** Automatic translation uses the PNG asset; display-mode and clear icons remain scalable drawings. */
@Composable
private fun TranslationGlassIcon(index: Int, modifier: Modifier, solid: Boolean) {
    if (index == 0) {
        TranslationEntryIcon(main = false, modifier = modifier, solid = solid)
        return
    }
    val foreground = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        scale(size.width / 32f, size.height / 32f, pivot = Offset.Zero) {
            val glass = Brush.linearGradient(listOf(Color.White, Color(0xFFDDEEF8), Color(0xFF9DB8C9)), Offset.Zero, Offset(26f, 30f))
            val ink = if (solid) foreground else Color(0xFF6D8798)
            fun card(x: Float, y: Float, w: Float = 14f, h: Float = 14f) {
                if (!solid) drawRoundRect(Color.Black.copy(alpha = .16f), Offset(x + .7f, y + 1.5f), Size(w, h), CornerRadius(3f))
                drawRoundRect(if (solid) Brush.linearGradient(listOf(foreground.copy(alpha = .15f), foreground.copy(alpha = .15f))) else glass,
                    Offset(x, y), Size(w, h), CornerRadius(3f))
                drawRoundRect(if (solid) foreground else Color.White.copy(alpha = .8f), Offset(x, y), Size(w, h), CornerRadius(3f), style = Stroke(.6f))
            }
            fun arrow(x1: Float, y: Float, x2: Float) {
                val c = if (solid) foreground else Color(0xFFF1FAFF)
                drawLine(c, Offset(x1, y), Offset(x2, y), 2f, StrokeCap.Round)
                val back = if (x2 > x1) -3f else 3f
                drawLine(c, Offset(x2 + back, y - 3f), Offset(x2, y), 2f, StrokeCap.Round)
                drawLine(c, Offset(x2 + back, y + 3f), Offset(x2, y), 2f, StrokeCap.Round)
            }
            when (index) {
                1 -> { card(2f, 3f, 11f, 11f); card(19f, 19f, 11f, 11f); arrow(18f, 8f, 29f); arrow(14f, 24f, 3f) }
                else -> rotate(34f, Offset(16f, 16f)) {
                    drawLine(if (solid) foreground else Color(0xFFF1FAFF), Offset(16f, 2f), Offset(16f, 18f), 3.5f, StrokeCap.Round)
                    val broom = Path().apply { moveTo(10f, 16f); lineTo(22f, 16f); lineTo(25f, 28f); quadraticTo(16f, 32f, 7f, 28f); close() }
                    drawPath(broom, if (solid) Brush.linearGradient(listOf(foreground.copy(alpha = .2f), foreground.copy(alpha = .2f))) else glass)
                    drawPath(broom, if (solid) foreground else Color.White, style = Stroke(1.3f))
                    listOf(12f, 16f, 20f).forEach { x -> drawLine(ink.copy(alpha = .65f), Offset(x, 22f), Offset(x, 28f), 1f, StrokeCap.Round) }
                }
            }
        }
    }
}
