package io.legado.app.core.ui.bookshelf

import android.graphics.Paint
import android.graphics.Typeface
import android.icu.text.BreakIterator
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val CoverProjection = 0.66f
private const val CoverShear = -0.28f
private const val SpineShear = 0.065f

// Breathing room between spines; it sets the density of the whole bank.
private val SpineGap = 8.dp

private val FocusAirLeft = 6.dp
private val FocusCoverOverlap = 18.dp
private const val AdjacentLiftFraction = 0.28f
private const val RailReferenceSpan = 7f
private val RailTilt = 1.5.dp
private val RailCurvature = 0.5.dp

private fun coverWidth(viewportWidth: Dp) = (viewportWidth * 0.30f).coerceAtMost(116.dp)

private fun spineWidth(viewportWidth: Dp) = (coverWidth(viewportWidth) * 0.135f).coerceAtLeast(12.dp)

internal fun dimensionalShelfPitch(viewportWidth: Dp) = spineWidth(viewportWidth) + SpineGap

@Composable
internal fun dimensionalShelfSceneHeight(viewportWidth: Dp): Dp {
    val dimensions = rememberShelfDimensions(viewportWidth)
    return with(LocalDensity.current) { dimensions.sceneHeight.toDp() }
}

@Composable
private fun rememberShelfDimensions(viewportWidth: Dp): ShelfDimensions {
    val density = LocalDensity.current
    return remember(viewportWidth, density) {
        with(density) {
            val cover = coverWidth(viewportWidth)
            ShelfDimensions(
                viewportWidth = viewportWidth.toPx(),
                coverWidth = cover.roundToPx().coerceAtLeast(1),
                spineWidth = spineWidth(viewportWidth).roundToPx(),
                bodyHeight = (cover * 1.60f).roundToPx().coerceAtLeast(1),
                pitch = dimensionalShelfPitch(viewportWidth).toPx(),
                top = 10.dp.toPx(),
                floor = 12.dp.toPx(),
                airLeft = FocusAirLeft.toPx(),
                coverOverlap = FocusCoverOverlap.toPx(),
                railTilt = RailTilt.toPx(),
                railCurvature = RailCurvature.toPx(),
            )
        }
    }
}

@Composable
internal fun DimensionalShelfScene(
    books: ImmutableList<DimensionalShelfBook>,
    position: State<Double>,
    viewportWidth: Dp,
    selectedSlot: Int,
    onOpen: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    preferCache: Boolean,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.dimensional_bookshelf_open_book)
    val previewLabel = stringResource(R.string.dimensional_bookshelf_preview_book)
    val colors = LegadoTheme.colorScheme
    val shadowTint = Color.Black.copy(alpha = if (LegadoTheme.isDark) 0.64f else 0.26f)
    Box(modifier.fillMaxWidth().clipToBounds()) {
        val dimensions = rememberShelfDimensions(viewportWidth)
        val period = books.size + 1
        val slots = remember(position, dimensions, books.size) {
            derivedStateOf {
                if (books.size == 1) 0..0 else dimensions.slotsAround(position.value)
            }
        }.value
        // Repeated volumes remain tappable. Expose the closest copy of each through semantics.
        val accessibleSlots = remember(slots, selectedSlot, period) {
            slots.sortedBy { abs(it - selectedSlot) }.distinctBy { it.mod(period) }.toSet()
        }
        val sceneHeight = with(LocalDensity.current) { dimensions.sceneHeight.toDp() }
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .height(sceneHeight)
                .drawWithCache {
                    val beamCenter = Offset(size.width / 2f, size.height - dimensions.floor - dimensions.bodyHeight * 0.42f)
                    val beamRadius = dimensions.coverWidth * 1.85f
                    val beam = Brush.radialGradient(
                        0f to colors.surfaceBright.copy(alpha = 0.09f),
                        0.55f to colors.surfaceBright.copy(alpha = 0.025f),
                        1f to Color.Transparent,
                        center = beamCenter,
                        radius = beamRadius,
                    )
                    val shadow = Brush.radialGradient(
                        0f to shadowTint,
                        0.42f to shadowTint.copy(alpha = shadowTint.alpha * 0.55f),
                        1f to Color.Transparent,
                        center = Offset.Zero,
                        radius = 1f,
                    )
                    val shadowAngle = atan2(-dimensions.rise.toFloat(), dimensions.coverRun) * 180f / PI.toFloat()
                    val contactRadius = 3.5.dp.toPx()
                    val liftSpread = 8.dp.toPx()
                    val floorOffset = 1.dp.toPx()
                    onDrawBehind {
                        val focus = position.value
                        drawCircle(beam, radius = beamRadius, center = beamCenter)
                        for (slot in slots) {
                            val distance = (slot - focus).toFloat()
                            val divider = slot.mod(period) == books.size
                            val lift = if (divider) 0f else dimensions.liftFraction(distance)
                            // Fade contact shadows before adjacent volumes reach their full lift.
                            val floating = smoothStep(2f * lift / AdjacentLiftFraction)
                            val focusWeight = smoothStep(1f - abs(distance))
                            val floatingOpacity = 0.18f + 0.30f * focusWeight
                            val shadowOpacity = 1f - floating * (1f - floatingOpacity)
                            val left = dimensions.bookLeft(distance, divider)
                            val centerX = left + dimensions.volumeWidth * 0.47f
                            val centerY = dimensions.seatY(distance) + dimensions.volumeHeight -
                                dimensions.rise * 0.48f + floorOffset
                            withTransform({
                                translate(centerX, centerY)
                                rotate(shadowAngle, pivot = Offset.Zero)
                                scale(
                                    dimensions.volumeWidth * (0.57f + 0.10f * lift),
                                    contactRadius + liftSpread * lift,
                                    pivot = Offset.Zero,
                                )
                            }) {
                                drawCircle(shadow, radius = 1f, center = Offset.Zero, alpha = shadowOpacity)
                            }
                        }
                    }
                },
            content = {
                for (slot in slots) {
                    key(slot) {
                        val index = slot.mod(period)
                        val book = books.getOrNull(index)
                        val ordinal = if (book == null) "" else
                            stringResource(R.string.dimensional_bookshelf_spine_number, index + 1)
                        val semantics = if (book != null && slot in accessibleSlots) {
                            val positionLabel = stringResource(
                                R.string.dimensional_bookshelf_book_position, index + 1, books.size,
                            )
                            Modifier.semantics {
                                contentDescription = book.title.ifBlank { openLabel }
                                stateDescription = positionLabel
                                selected = slot == selectedSlot
                            }
                        } else {
                            Modifier
                                .focusProperties { canFocus = false }
                                .clearAndSetSemantics { hideFromAccessibility() }
                        }
                        val actions = if (book == null) {
                            // A tap on the divider must not fall through to an overlapping cover.
                            Modifier.pointerInput(Unit) { detectTapGestures {} }
                        } else {
                            Modifier.combinedClickable(
                                role = Role.Button,
                                onClickLabel = openLabel,
                                onLongClickLabel = previewLabel,
                                onClick = { onOpen(index) },
                                onLongClick = { onPreview(index) },
                            )
                        }
                        ShelfVolume(
                            book = book,
                            number = ordinal,
                            slot = slot,
                            position = position,
                            dimensions = dimensions,
                            preferCache = preferCache,
                            modifier = semantics.then(actions),
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val volumes = measurables.map {
                it.measure(Constraints.fixed(dimensions.volumeWidth, dimensions.volumeHeight))
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                val focus = position.value
                volumes.forEachIndexed { index, volume ->
                    val slot = slots.first + index
                    val distance = (slot - focus).toFloat()
                    val divider = slot.mod(period) == books.size
                    val lift = if (divider) 0f else dimensions.liftFraction(distance)
                    val x = dimensions.bookLeft(distance, divider)
                    val y = dimensions.seatY(distance) - dimensions.lift * lift
                    // Preserve the physical order of the overlapping faces.
                    volume.place(x.roundToInt(), y.roundToInt())
                }
            }
        }
    }
}

private class ShelfDimensions(
    viewportWidth: Float,
    val coverWidth: Int,
    val spineWidth: Int,
    val bodyHeight: Int,
    private val pitch: Float,
    private val top: Float,
    val floor: Float,
    airLeft: Float,
    coverOverlap: Float,
    private val railTilt: Float,
    private val railCurvature: Float,
) {
    val coverRun = coverWidth * CoverProjection
    val rise = (coverWidth * -CoverShear).roundToInt()
    val spineTilt = (spineWidth * SpineShear).roundToInt()
    val pull = coverRun * 0.37f
    // Clear the spine on the left and keep a slim overlap at the right edge of the cover.
    private val leftSpace = (airLeft + pull + spineWidth - pitch).coerceAtLeast(pitch * 0.34f)
    private val rightSpace = (spineWidth + coverRun - coverOverlap - pitch - pull).coerceAtLeast(pitch * 0.34f)
    val lift = bodyHeight * 0.28f
    val volumeWidth = (spineWidth + coverRun).roundToInt()
    val volumeHeight = rise + spineTilt + bodyHeight
    private val center = (viewportWidth - volumeWidth) / 2f + pull
    private val leftReach = ((center - leftSpace + volumeWidth) / pitch).coerceAtLeast(0f)
    private val rightReach = ((viewportWidth - center - rightSpace) / pitch).coerceAtLeast(0f)
    private val railScale = RailReferenceSpan / maxOf(RailReferenceSpan, rightReach)
    private val edgeDrop = maxOf(railOffset(-leftReach), railOffset(rightReach), 0f)
    val sceneHeight = (volumeHeight + top + lift + edgeDrop + floor).roundToInt()

    // Vary the seat height to bend both outlines while keeping one projection for every book.
    private fun railOffset(distance: Float): Float {
        val step = distance * railScale
        return step * (railTilt + step * railCurvature)
    }

    fun seatY(distance: Float) = top + lift + railOffset(distance)

    fun liftFraction(distance: Float): Float {
        val fromFocus = abs(distance)
        val focusedLift = smoothStep((0.88f - fromFocus) / 0.88f)
        val adjacentLift = smoothStep(1f - abs(fromFocus - 1f)) * AdjacentLiftFraction
        return focusedLift + adjacentLift
    }

    fun bookLeft(distance: Float, divider: Boolean): Float {
        val fromFocus = abs(distance)
        val spread = (if (distance < 0f) -leftSpace else rightSpace) * smoothStep(fromFocus)
        val pullWeight = if (divider) 0f else smoothStep((1f - fromFocus) / 0.88f)
        return center + distance * pitch + spread - pull * pullWeight
    }

    fun slotsAround(focus: Double) =
        (floor(focus - leftReach).toInt() - 1)..(ceil(focus + rightReach).toInt() + 1)

    fun topFace() = Path().apply {
        moveTo(0f, rise.toFloat())
        lineTo(coverRun, 0f)
        lineTo(volumeWidth.toFloat(), spineTilt.toFloat())
        lineTo(spineWidth.toFloat(), (rise + spineTilt).toFloat())
        close()
    }

    fun outline() = Path().apply {
        moveTo(0f, rise.toFloat())
        lineTo(coverRun, 0f)
        lineTo(volumeWidth.toFloat(), spineTilt.toFloat())
        lineTo(volumeWidth.toFloat(), (bodyHeight + spineTilt).toFloat())
        lineTo(spineWidth.toFloat(), volumeHeight.toFloat())
        lineTo(0f, (rise + bodyHeight).toFloat())
        close()
    }
}

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
private fun ShelfVolume(
    book: DimensionalShelfBook?,
    number: String,
    slot: Int,
    position: State<Double>,
    dimensions: ShelfDimensions,
    preferCache: Boolean,
    modifier: Modifier,
) {
    val colors = LegadoTheme.colorScheme
    val dark = LegadoTheme.isDark
    val faceColor = lerp(colors.surfaceContainerHigh, colors.surface, 0.3f)
    val spineColor = lerp(faceColor, colors.onSurface, 0.055f)
    val spineBrush = remember(spineColor, colors.surface) {
        Brush.horizontalGradient(listOf(spineColor, lerp(spineColor, colors.surface, 0.46f)))
    }
    val patternInk = colors.onSurface.copy(alpha = if (dark) 0.23f else 0.17f)
    val paperColor = if (dark) {
        lerp(faceColor, colors.onSurface, 0.21f)
    } else {
        lerp(faceColor, colors.surfaceBright, 0.82f)
    }
    val outlinePath = remember(dimensions) { dimensions.outline() }
    val outline = remember(outlinePath) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                Outline.Generic(outlinePath)
        }
    }
    Layout(
        modifier = Modifier
            .clip(outline).then(modifier)
            .drawWithCache {
                val top = dimensions.topFace()
                val upperCorner = Offset(0f, dimensions.rise.toFloat())
                val bindingCorner = Offset(dimensions.spineWidth.toFloat(), (dimensions.rise + dimensions.spineTilt).toFloat())
                val coverTop = Offset(dimensions.volumeWidth.toFloat(), dimensions.spineTilt.toFloat())
                val bindingFoot = Offset(bindingCorner.x, dimensions.volumeHeight.toFloat())
                val paper = Brush.linearGradient(
                    colors = listOf(paperColor, lerp(paperColor, faceColor, 0.48f)),
                    start = upperCorner,
                    end = Offset(dimensions.coverRun, 0f),
                )
                val outlineStroke = Stroke(0.6.dp.toPx())
                val rimWidth = 0.85.dp.toPx()
                val separatorWidth = 0.6.dp.toPx()
                val separator = colors.onSurface.copy(alpha = if (dark) 0.12f else 0.10f)
                val topSeparator = separator.copy(alpha = separator.alpha * 0.75f)
                val ink = colors.onSurface.copy(alpha = if (dark) 0.10f else 0.14f)
                val light = if (dark) colors.onSurface else Color.White
                onDrawWithContent {
                    drawPath(top, paper)
                    drawContent()
                    drawPath(outlinePath, ink, style = outlineStroke)
                    drawLine(
                        color = separator,
                        start = bindingCorner,
                        end = bindingFoot,
                        strokeWidth = separatorWidth,
                    )
                    drawLine(
                        color = topSeparator,
                        start = bindingCorner,
                        end = coverTop,
                        strokeWidth = separatorWidth,
                    )
                    // The upper edge and the outer spine face the same light.
                    val focusGlow = if (book == null) 0f else smoothStep(1f - abs(slot - position.value).toFloat())
                    drawLine(
                        color = light.copy(alpha = if (dark) 0.17f + focusGlow * 0.15f else 0.58f + focusGlow * 0.22f),
                        start = upperCorner,
                        end = Offset(dimensions.coverRun, 0f),
                        strokeWidth = rimWidth,
                    )
                    drawLine(
                        color = light.copy(alpha = if (dark) 0.10f + focusGlow * 0.13f else 0.35f + focusGlow * 0.22f),
                        start = upperCorner,
                        end = Offset(0f, (dimensions.rise + dimensions.bodyHeight).toFloat()),
                        strokeWidth = rimWidth,
                    )
                }
            },
        content = {
            val spineModifier = Modifier.clearAndSetSemantics { }.projectFace(1f, SpineShear)
            if (book == null) {
                Box(
                    spineModifier
                        .background(spineBrush)
                        .padding(vertical = 18.dp)
                        .dividerPattern(patternInk, cellSize = 15.dp),
                )
            } else {
                ShelfSpine(
                    title = book.title,
                    number = number,
                    brush = spineBrush,
                    modifier = spineModifier,
                )
            }
            Box(
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .projectFace(CoverProjection, CoverShear)
                    .background(faceColor)
                    .padding(horizontal = 8.dp, vertical = 14.dp),
            ) {
                if (book != null) {
                    CoilBookCover(
                        name = book.title,
                        author = book.author,
                        path = book.cover,
                        modifier = Modifier.fillMaxSize(),
                        sourceOrigin = book.origin,
                        bookUrl = book.bookUrl,
                        preferCache = preferCache,
                        radius = 1.5.dp,
                        builtInFallback = { ShelfFallbackCover(book.title, book.author) },
                    )
                } else {
                    Box(Modifier.fillMaxSize().dividerPattern(patternInk, cellSize = 22.dp))
                }
            }
        },
    ) { measurables, constraints ->
        val spine = measurables[0].measure(Constraints.fixed(dimensions.spineWidth, dimensions.bodyHeight))
        val cover = measurables[1].measure(Constraints.fixed(dimensions.coverWidth, dimensions.bodyHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            spine.place(0, dimensions.rise)
            cover.place(dimensions.spineWidth, dimensions.rise + dimensions.spineTilt)
        }
    }
}

private fun Modifier.dividerPattern(ink: Color, cellSize: Dp) = drawWithCache {
    val spacing = cellSize.toPx()
    val columns = (size.width / spacing).toInt().coerceAtLeast(1)
    val rows = (size.height / spacing).toInt()
    val halfWidth = minOf(spacing * 0.22f, size.width * 0.26f)
    val halfHeight = spacing * 0.30f
    val pattern = Path().apply {
        repeat(rows) { row ->
            val y = size.height / 2f + (row - (rows - 1) / 2f) * spacing
            repeat(columns) { column ->
                val x = size.width / 2f + (column - (columns - 1) / 2f) * spacing
                moveTo(x, y - halfHeight)
                lineTo(x + halfWidth, y)
                lineTo(x, y + halfHeight)
                lineTo(x - halfWidth, y)
                close()
            }
        }
    }
    val stroke = Stroke(0.7.dp.toPx())
    onDrawBehind { drawPath(pattern, ink, style = stroke) }
}

@Composable
private fun ShelfFallbackCover(title: String, author: String) {
    val colors = LegadoTheme.colorScheme
    val tint = lerp(colors.surfaceContainerHigh, colors.primary, 0.15f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint, lerp(tint, colors.surface, 0.52f))))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        AppText(
            text = title,
            style = LegadoTheme.typography.titleSmall,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (author.isNotBlank()) {
            AppText(
                text = author,
                style = LegadoTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShelfSpine(
    title: String,
    number: String,
    brush: Brush,
    modifier: Modifier,
) {
    val colors = LegadoTheme.colorScheme
    val typography = LegadoTheme.typography
    val measurer = rememberTextMeasurer()
    val upright = remember(title) {
        title.codePoints().anyMatch {
            when (Character.UnicodeScript.of(it)) {
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.HANGUL -> true
                else -> false
            }
        }
    }
    Box(
        modifier
            .background(brush)
            .drawWithCache {
                val availableLength = (size.height - 36.dp.toPx()).roundToInt().coerceAtLeast(1)
                // Keep a clear margin on both sides of the spine instead of letting the
                // glyphs fill its full width.
                val titleSize = minOf(7.5.dp.toPx(), size.width * 0.55f)
                val ink = colors.onSurface.copy(alpha = 0.92f)
                // A hairline imprint near the foot; it reads as a cloth spine instead of
                // a flat coloured slab.
                val ruleY = size.height - 22.dp.toPx()
                val ruleInset = 2.dp.toPx()
                val ruleWidth = 0.8.dp.toPx()
                val frame: DrawScope.() -> Unit = {
                    drawLine(
                        color = colors.onSurface.copy(alpha = 0.09f),
                        start = Offset(ruleInset, ruleY),
                        end = Offset(size.width - ruleInset, ruleY),
                        strokeWidth = ruleWidth,
                    )
                }
                if (upright) {
                    val cellHeight = titleSize + 2.dp.toPx()
                    val glyphs = spineGraphemes(title, (availableLength / cellHeight).toInt().coerceAtLeast(1))
                    // Draw isolated glyphs with Android's fallback fonts. A multiline
                    // paragraph can clip at the narrow width of the spine.
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = ink.toArgb()
                        typeface = Typeface.DEFAULT
                        textAlign = Paint.Align.CENTER
                        textSize = titleSize
                    }
                    val center = size.width / 2f
                    val firstBaseline = (size.height - (glyphs.size - 1) * cellHeight -
                        paint.ascent() - paint.descent()) / 2f
                    onDrawBehind {
                        frame()
                        drawIntoCanvas { canvas ->
                            glyphs.forEachIndexed { index, glyph ->
                                canvas.nativeCanvas.drawText(glyph, center, firstBaseline + index * cellHeight, paint)
                            }
                        }
                    }
                } else {
                    val label = measurer.measure(
                        AnnotatedString(title),
                        style = typography.labelMedium.copy(color = ink, fontSize = titleSize.toSp()),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        softWrap = false,
                        constraints = Constraints(maxWidth = availableLength),
                    )
                    onDrawBehind {
                        frame()
                        withTransform({
                            translate((size.width + label.size.height) / 2f, (size.height - label.size.width) / 2f)
                            rotate(90f, pivot = Offset.Zero)
                        }) {
                            drawText(label)
                        }
                    }
                }
            },
    ) {
        AppText(
            text = number,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp),
            style = typography.labelSmall,
            fontSize = 7.sp,
            color = colors.onSurfaceVariant.copy(alpha = 0.64f),
            maxLines = 1,
        )
    }
}

private fun spineGraphemes(title: String, limit: Int): List<String> {
    val text = title.trim().replace(Regex("\\s+"), " ")
    val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
    return buildList {
        var start = breaks.first()
        repeat(limit) { index ->
            val end = breaks.next()
            if (end == BreakIterator.DONE) return@buildList
            add(if (limit > 1 && index == limit - 1 && end < text.length) "…" else text.substring(start, end))
            start = end
        }
    }
}

private fun Modifier.projectFace(scale: Float, shear: Float) = drawWithCache {
    val projection = Matrix().apply {
        this[0, 0] = scale
        this[0, 1] = shear
    }
    onDrawWithContent {
        withTransform({ transform(projection) }) { this@onDrawWithContent.drawContent() }
    }
}
