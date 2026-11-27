package io.legado.app.core.ui.bookshelf

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val ShelfCaptionSpacing = 2.dp
private val ShelfPagerSpacing = 4.dp

@Stable
internal data class DimensionalShelfBook(
    val key: String,
    val bookUrl: String,
    val title: String,
    val author: String,
    val cover: String?,
    val origin: String?,
)

@Composable
internal fun dimensionalBookshelfHeight(viewportWidth: Dp): Dp =
    dimensionalShelfSceneHeight(viewportWidth) + rememberShelfCaptionHeights().total

@Composable
internal fun DimensionalBookshelf(
    books: ImmutableList<DimensionalShelfBook>,
    onOpen: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    modifier: Modifier = Modifier,
    preferCache: Boolean = false,
) {
    if (books.isEmpty()) return
    var selectedKey by rememberSaveable { mutableStateOf(books.first().key) }
    val bookKeys = remember(books) { books.map(DimensionalShelfBook::key) }

    // A changed book order gets a fresh scroll state anchored to the last selected book.
    key(bookKeys) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val shelfLabel = stringResource(R.string.dimensional_bookshelf)
            val viewportWidth = maxWidth
            val density = LocalDensity.current
            val dragStep = with(density) { dimensionalShelfPitch(viewportWidth).toPx() * 1.8f }
            val minimumFlingVelocity = with(density) { 110.dp.toPx() }
            val position = remember {
                mutableDoubleStateOf(bookKeys.indexOf(selectedKey).coerceAtLeast(0).toDouble())
            }
            val scrollState = rememberScrollableState { delta ->
                position.doubleValue += delta.toDouble() / dragStep
                delta
            }
            val selectedSlot by remember {
                derivedStateOf {
                    nearestBookSlot(position.doubleValue, bookKeys.size)
                }
            }
            val selectedIndex = selectedSlot.mod(books.size + 1)
            SideEffect { selectedKey = books[selectedIndex].key }
            val scope = rememberCoroutineScope()
            val animation = remember {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                    visibilityThreshold = 0.25f,
                )
            }
            val flingBehavior = remember(position, books.size, dragStep, minimumFlingVelocity, animation) {
                ShelfFlingBehavior(position, books.size, dragStep, minimumFlingVelocity, animation)
            }

            Column(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = shelfLabel },
            ) {
                DimensionalShelfScene(
                    books = books,
                    position = position,
                    viewportWidth = viewportWidth,
                    selectedSlot = selectedSlot,
                    onOpen = onOpen,
                    onPreview = onPreview,
                    preferCache = preferCache,
                    modifier = Modifier.scrollable(
                        state = scrollState,
                        orientation = Orientation.Horizontal,
                        reverseDirection = true,
                        enabled = books.size > 1,
                        flingBehavior = flingBehavior,
                    ),
                )
                ShelfSelectionDetails(
                    book = books[selectedIndex],
                    index = selectedIndex,
                    count = books.size,
                    onSelect = { step ->
                        scope.launch {
                            val start = position.doubleValue
                            val next = nearestBookSlot(start, books.size) + step
                            val target = if (next.mod(books.size + 1) == books.size) next + step else next
                            scrollState.animateScrollBy(((target - start) * dragStep).toFloat(), animation)
                        }
                    },
                )
            }
        }
    }
}

private fun nearestBookSlot(position: Double, bookCount: Int): Int {
    if (bookCount == 1) return 0
    val slot = position.roundToInt()
    // The divider occupies a slot between the last and first book.
    if (slot.mod(bookCount + 1) != bookCount) return slot
    return if (position < slot) slot - 1 else slot + 1
}

private class ShelfFlingBehavior(
    private val position: State<Double>,
    private val bookCount: Int,
    private val dragStep: Float,
    private val minimumVelocity: Float,
    private val snapAnimation: AnimationSpec<Float>,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val start = position.value
        val speed = abs(initialVelocity)
        val travelLimit = dragStep * 4f
        val carry = if (speed >= minimumVelocity) {
            (initialVelocity * 0.24f).coerceIn(-travelLimit, travelLimit)
        } else {
            0f
        }
        val target = nearestBookSlot(start + carry.toDouble() / dragStep, bookCount)
        val travel = ((target - start) * dragStep).toFloat()
        if (abs(travel) < 0.001f) return 0f

        val animation = if (carry * travel > 0f) {
            val duration = (abs(travel) * 2650f / speed).roundToInt().coerceIn(260, 460)
            val slope = (speed * (duration / 1000f) / abs(travel)).coerceIn(1.5f, 3f)
            // End at zero velocity. This slope range keeps the cubic monotonic and decelerating.
            tween(durationMillis = duration, easing = Easing { t ->
                t * (slope + t * (3f - 2f * slope + t * (slope - 2f)))
            })
        } else {
            snapAnimation
        }

        var remainingVelocity = 0f
        AnimationState(initialValue = 0f).animateTo(travel, animation) {
            val delta = (value + (start - position.value) * dragStep).toFloat()
            val consumed = this@performFling.scrollBy(delta)
            if (abs(delta - consumed) > 0.5f) {
                remainingVelocity = velocity
                cancelAnimation()
            }
        }
        return remainingVelocity
    }
}

private data class ShelfCaptionHeights(val title: Dp, val author: Dp, val pager: Dp, val total: Dp)

@Composable
private fun rememberShelfCaptionHeights(): ShelfCaptionHeights {
    val typography = LegadoTheme.typography
    val titleStyle = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val authorStyle = typography.bodySmall
    val textMeasurer = rememberTextMeasurer(cacheSize = 3)
    // Fit common fallback glyphs without deriving row heights from the selected book.
    val captionSample = remember { AnnotatedString("Ag中📚") }
    val digits = remember { AnnotatedString("00") }
    val density = LocalDensity.current
    val titleHeight = with(density) {
        textMeasurer.measure(captionSample, style = titleStyle, maxLines = 1).size.height.toDp()
    }
    val authorHeight = with(density) {
        textMeasurer.measure(captionSample, style = authorStyle, maxLines = 1).size.height.toDp()
    }
    val pagerHeight = with(density) {
        textMeasurer.measure(digits, style = typography.labelSmall, maxLines = 1).size.height.toDp()
    }.coerceAtLeast(48.dp)
    val total = with(density) {
        val height = titleHeight.roundToPx() + authorHeight.roundToPx() + pagerHeight.roundToPx() +
            ShelfCaptionSpacing.roundToPx() + ShelfPagerSpacing.roundToPx()
        height.toDp()
    }
    return ShelfCaptionHeights(titleHeight, authorHeight, pagerHeight, total)
}

@Composable
private fun ShelfSelectionDetails(
    book: DimensionalShelfBook,
    index: Int,
    count: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = LegadoTheme.colorScheme
    val typography = LegadoTheme.typography
    val heights = rememberShelfCaptionHeights()
    Column(
        modifier = Modifier.fillMaxWidth().height(heights.total).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ShelfPagerSpacing),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ShelfCaptionSpacing),
        ) {
            AnimatedTextLine(
                text = book.title,
                // The book button already exposes the title.
                modifier = Modifier.height(heights.title).clearAndSetSemantics { },
                style = typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedTextLine(
                text = book.author.ifBlank { "" },
                modifier = Modifier.height(heights.author),
                style = typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (count > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .height(heights.pager)
                    .background(colors.onSurface.copy(alpha = 0.035f), RoundedCornerShape(24.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSelect(-1) }) {
                    AppIcon(
                        Icons.Default.ChevronLeft,
                        stringResource(R.string.dimensional_bookshelf_previous_book),
                        modifier = Modifier.size(19.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
                AppText(
                    text = stringResource(R.string.dimensional_bookshelf_counter, index + 1, count),
                    color = colors.onSurfaceVariant,
                    style = typography.labelSmall,
                    maxLines = 1,
                )
                IconButton(onClick = { onSelect(1) }) {
                    AppIcon(
                        Icons.Default.ChevronRight,
                        stringResource(R.string.dimensional_bookshelf_next_book),
                        modifier = Modifier.size(19.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        } else {
            Spacer(Modifier.height(heights.pager))
        }
    }
}
