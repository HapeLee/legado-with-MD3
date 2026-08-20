package io.legado.app.ui.book.readaloud.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import io.legado.app.R
import io.legado.app.constant.ReadAloudBgMode
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.hazeStyle.HazeLegado
import io.legado.app.ui.util.rememberBlurBackdrop
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.player.AnimatedPlayPauseButton
import io.legado.app.ui.widget.components.player.PlayerAdjustmentSlider
import io.legado.app.ui.widget.components.player.PlayerBackground
import io.legado.app.ui.widget.components.player.PlayerProgressSlider
import io.legado.app.ui.widget.components.player.PlayerTocPage
import io.legado.app.ui.widget.components.player.playerBgModeLabel
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ReadAloudPlayerScreenContent(
    state: ReadAloudPlayerUiState,
    onIntent: (ReadAloudPlayerIntent) -> Unit,
    onBack: () -> Unit,
) {
    val horizontalPagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val verticalPagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val verticalPagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = verticalPagerState,
        orientation = Orientation.Vertical,
    )
    val coroutineScope = rememberCoroutineScope()
    var activeAdjustment by remember { mutableStateOf<PlayerAdjustment?>(null) }
    var speedPreview by remember(state.speed) { mutableFloatStateOf(state.speed.toFloat()) }
    var timerPreview by remember(state.timerMinutes) {
        mutableFloatStateOf(state.timerMinutes.toFloat())
    }
    var isTextPageUserScrolling by remember { mutableStateOf(false) }
    val pagerHazeState = remember { HazeState() }
    val hazeEnabled =
        state.bgMode != ReadAloudBgMode.Solid && state.bgMode != ReadAloudBgMode.Transparent
    val textBackdrop = rememberBlurBackdrop()
    val flowingLightActive = state.bgMode == ReadAloudBgMode.FlowingLight
    val flowingTextModifier = if (flowingLightActive && textBackdrop != null) {
        Modifier.textureBlur(
            backdrop = textBackdrop,
            shape = RoundedCornerShape(4.dp),
            blurRadius = 150f,
            colors = BlurColors(blendColors = flowingTextBlend()),
            contentBlendMode = ComposeBlendMode.DstIn,
        )
    } else {
        Modifier
    }
    LaunchedEffect(horizontalPagerState.currentPage) {
        if (horizontalPagerState.currentPage != 1) isTextPageUserScrolling = false
    }
    val pageContentPadding = PaddingValues(
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 88.dp,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + when (
            activeAdjustment
        ) {
            null -> 216.dp
            PlayerAdjustment.Speed -> 264.dp
            PlayerAdjustment.Timer -> 344.dp
        },
    )
    val overlayHazeStyle = HazeLegado.ultraThinPlus(
        containerColor = LegadoTheme.colorScheme.surface,
    )
    AppScaffold(
        modifier = Modifier.fillMaxSize(),
        alwaysDrawBehindBars = true,
        disableHazeSource = true,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            val hazeModifier = if (hazeEnabled) {
                Modifier.hazeEffect(state = pagerHazeState, style = overlayHazeStyle) {
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                    )
                }
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .then(hazeModifier)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        AppText(
                            text = state.bookName,
                            style = LegadoTheme.typography.titleMediumEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AppText(
                            text = state.chapterTitle,
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MediumTonalButton(
                        onClick = { onIntent(ReadAloudPlayerIntent.OpenSettings) },
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.setting),
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !isTextPageUserScrolling,
                enter = fadeIn(tween(240)) + slideInVertically(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 5 },
                ),
                exit = fadeOut(tween(180)) + slideOutVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    targetOffsetY = { it / 5 },
                ),
            ) {
                val hazeModifier = if (hazeEnabled) {
                    Modifier.hazeEffect(state = pagerHazeState, style = overlayHazeStyle) {
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 0f,
                            endIntensity = 1f,
                        )
                    }
                } else {
                    Modifier
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape)
                        .then(hazeModifier)
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PlayerProgressSlider(
                        modifier = Modifier
                            .padding(horizontal = 24.dp),
                        value = state.chapterPosition.coerceIn(0, state.chapterLength).toFloat(),
                        onValueChange = { onIntent(ReadAloudPlayerIntent.SeekTo(it.toInt())) },
                        valueRange = 0f..state.chapterLength.coerceAtLeast(1).toFloat(),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AppText(
                            formatPosition(state.chapterPosition),
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                        AppText(
                            "${(state.chapterPosition * 100 / state.chapterLength.coerceAtLeast(1))}%",
                            style = LegadoTheme.typography.labelSmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediumPlainButton(
                            onClick = { onIntent(ReadAloudPlayerIntent.PreviousParagraph) },
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.prev_sentence),
                        )
                        MediumTonalButton(
                            onClick = { onIntent(ReadAloudPlayerIntent.PreviousChapter) },
                            icon = Icons.Default.FastRewind,
                            contentDescription = stringResource(R.string.previous_chapter),
                            modifier = Modifier.size(48.dp),
                        )
                        AnimatedPlayPauseButton(
                            isPlaying = !state.isPaused,
                            onClick = { onIntent(ReadAloudPlayerIntent.TogglePause) },
                            contentDescription = stringResource(
                                if (state.isPaused) R.string.resume else R.string.pause
                            ),
                        )
                        MediumTonalButton(
                            onClick = { onIntent(ReadAloudPlayerIntent.NextChapter) },
                            icon = Icons.Default.FastForward,
                            contentDescription = stringResource(R.string.next_chapter),
                            modifier = Modifier.size(48.dp),
                        )
                        MediumPlainButton(
                            onClick = { onIntent(ReadAloudPlayerIntent.NextParagraph) },
                            icon = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.next_sentence),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        SmallPlainButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (horizontalPagerState.currentPage != 0) {
                                        horizontalPagerState.animateScrollToPage(
                                            page = 0,
                                            animationSpec = tween(
                                                durationMillis = 520,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    verticalPagerState.animateScrollToPage(
                                        page = if (verticalPagerState.currentPage == 0) 1 else 0,
                                        animationSpec = tween(
                                            durationMillis = 520,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    )
                                }
                            },
                            icon = if (verticalPagerState.currentPage == 0) {
                                Icons.AutoMirrored.Filled.FormatListBulleted
                            } else {
                                Icons.Default.KeyboardArrowUp
                            },
                            contentDescription = stringResource(
                                if (verticalPagerState.currentPage == 0) {
                                    R.string.chapter_list
                                } else {
                                    R.string.back
                                }
                            ),
                        )
                        SmallPlainButton(
                            onClick = { onIntent(ReadAloudPlayerIntent.SwitchToClassic) },
                            icon = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.switch_to_classic_read_aloud),
                        )
                        SmallPlainButton(
                            onClick = { onIntent(ReadAloudPlayerIntent.CycleBgMode) },
                            icon = Icons.Default.WbTwilight,
                            contentDescription = playerBgModeLabel(state.bgMode)
                        )
                        SmallPlainButton(
                            onClick = {
                                activeAdjustment = activeAdjustment.toggle(PlayerAdjustment.Speed)
                            },
                            icon = Icons.Default.Speed,
                            contentDescription = stringResource(R.string.read_aloud_adjust_speed),
                            selected = activeAdjustment == PlayerAdjustment.Speed,
                        )
                        SmallPlainButton(
                            onClick = {
                                activeAdjustment = activeAdjustment.toggle(PlayerAdjustment.Timer)
                            },
                            icon = Icons.Default.Timer,
                            contentDescription = stringResource(R.string.set_timer),
                            selected = activeAdjustment == PlayerAdjustment.Timer,
                        )
                    }
                    AnimatedVisibility(activeAdjustment == PlayerAdjustment.Speed) {
                        PlayerAdjustmentSlider(
                            title = stringResource(R.string.read_aloud_adjust_speed),
                            value = speedPreview.coerceIn(
                                READ_ALOUD_SPEED_MIN.toFloat(),
                                READ_ALOUD_SPEED_MAX.toFloat(),
                            ),
                            valueLabel = formatReadAloudSpeedLabel(speedPreview.roundToInt()),
                            startLabel = stringResource(R.string.fast_rewind),
                            endLabel = stringResource(R.string.fast_forward),
                            onValueChange = { speedPreview = it },
                            onValueChangeFinished = {
                                onIntent(ReadAloudPlayerIntent.SetSpeed(speedPreview.roundToInt()))
                            },
                            valueRange = READ_ALOUD_SPEED_MIN.toFloat()..READ_ALOUD_SPEED_MAX.toFloat(),
                            steps = READ_ALOUD_SPEED_MAX - READ_ALOUD_SPEED_MIN - 1,
                        )
                    }
                    AnimatedVisibility(activeAdjustment == PlayerAdjustment.Timer) {
                        Column {
                            PlayerAdjustmentSlider(
                                title = stringResource(R.string.set_timer),
                                value = timerPreview.coerceIn(0f, 180f),
                                valueLabel = if (timerPreview == 0f) {
                                    stringResource(R.string.close)
                                } else {
                                    stringResource(R.string.timer_m, timerPreview.roundToInt())
                                },
                                startLabel = stringResource(R.string.close),
                                endLabel = stringResource(R.string.timer_m, 180),
                                onValueChange = { timerPreview = (it / 10f).roundToInt() * 10f },
                                onValueChangeFinished = {
                                    onIntent(ReadAloudPlayerIntent.SetTimer(timerPreview.roundToInt()))
                                },
                                valueRange = 0f..180f,
                                steps = 17,
                            )
                            TinySwitchSettingItem(
                                title = stringResource(
                                    R.string.finish_current_chapter_after_timer
                                ),
                                description = stringResource(
                                    R.string.finish_current_chapter_after_timer_summary
                                ),
                                checked = state.finishCurrentChapterAfterTimer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = LegadoTheme.colorScheme.surfaceContainerHigh,
                                onCheckedChange = {
                                    onIntent(
                                        ReadAloudPlayerIntent.SetFinishCurrentChapterAfterTimer(it)
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    ) {
        Box(Modifier
            .fillMaxSize()
            .then(if (hazeEnabled) Modifier.hazeSource(pagerHazeState) else Modifier)
        ) {
            PlayerBackground(
                name = state.bookName,
                author = state.author,
                path = state.coverPath,
                sourceOrigin = state.sourceOrigin,
                bgMode = state.bgMode,
                modifier = if (flowingLightActive && textBackdrop != null) {
                    Modifier.layerBackdrop(textBackdrop)
                } else {
                    Modifier
                },
            )
            HorizontalPager(
                state = horizontalPagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                if (page == 0) {
                    VerticalPager(
                        state = verticalPagerState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        pageNestedScrollConnection = verticalPagerNestedScrollConnection,
                    ) { verticalPage ->
                        if (verticalPage == 0) {
                            CoverPage(state, pageContentPadding, flowingTextModifier)
                        } else {
                            PlayerTocPage(
                                chapters = state.chapters,
                                currentIndex = state.chapterIndex,
                                isPaused = state.isPaused,
                                onSelect = {
                                    onIntent(ReadAloudPlayerIntent.SelectChapter(it))
                                },
                                contentPadding = pageContentPadding,
                            )
                        }
                    }
                } else {
                    ChapterTextPage(
                        state = state,
                        contentPadding = pageContentPadding,
                        flowingTextModifier = flowingTextModifier,
                        onIntent = onIntent,
                        onUserScrollChanged = { isTextPageUserScrolling = it },
                    )
                }
            }
        }
    }
}



@Composable
private fun CoverPage(
    state: ReadAloudPlayerUiState,
    contentPadding: PaddingValues,
    flowingTextModifier: Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            BookCoverImage(
                name = state.bookName,
                author = state.author,
                path = state.coverPath,
                sourceOrigin = state.sourceOrigin,
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .aspectRatio(5f / 7f)
                    .shadow(
                        elevation = 16.dp
                    )
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        AnimatedContent(
            targetState = state.currentText to state.nextText,
            transitionSpec = {
                (
                        fadeIn(
                            tween(400, easing = FastOutSlowInEasing)
                        ) + slideInVertically { it / 3 }
                        ).togetherWith(
                        fadeOut(
                            tween(400, easing = FastOutSlowInEasing)
                        ) + slideOutVertically { -it / 3 }
                    )
            },
            contentAlignment = Alignment.CenterStart,
            label = "cover_text_transition",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { (current, next) ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                AppText(
                    text = current.ifBlank {
                        stringResource(R.string.read_aloud_preparing_content)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(flowingTextModifier),
                    style = LegadoTheme.typography.bodyLargeEmphasized,
                    color = LegadoTheme.colorScheme.onSurface,
                    minLines = 1,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )

                if (next.isNotBlank()) {
                    AppText(
                        text = next,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .then(flowingTextModifier),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        minLines = 1,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
        TextCard(
            text = stringResource(
                R.string.read_aloud_speaker_engine,
                state.speakerName.ifBlank { stringResource(R.string.voice_role_narrator) },
                state.engineName.ifBlank { stringResource(R.string.read_aloud_default_tts) },
            ),
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun ChapterTextPage(
    state: ReadAloudPlayerUiState,
    contentPadding: PaddingValues,
    flowingTextModifier: Modifier,
    onIntent: (ReadAloudPlayerIntent) -> Unit,
    onUserScrollChanged: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val userScrollGeneration = remember { mutableIntStateOf(0) }
    val currentOnUserScrollChanged by rememberUpdatedState(onUserScrollChanged)
    val userScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    userScrollGeneration.intValue++
                    currentOnUserScrollChanged(true)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val releaseGeneration = userScrollGeneration.intValue
                coroutineScope.launch {
                    delay(BOTTOM_BAR_RESTORE_DELAY_MILLIS.milliseconds)
                    if (userScrollGeneration.intValue == releaseGeneration) {
                        currentOnUserScrollChanged(false)
                    }
                }
                return Velocity.Zero
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            userScrollGeneration.intValue++
            currentOnUserScrollChanged(false)
        }
    }

    LaunchedEffect(state.chapterIndex, state.activeTextLine, state.textLines.size) {
        val targetIndex = state.activeTextLine
        if (targetIndex !in state.textLines.indices) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val targetDistance = viewportHeight * 0.32f
        val targetOffset = layoutInfo.viewportStartOffset + targetDistance
        val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }

        val scrollDistance = if (visibleItem != null) {
            visibleItem.offset - targetOffset
        } else {
            // Do not animate through the whole chapter. Start close to the target and reveal it
            // with one short movement instead.
            val approachDistance = (viewportHeight * 0.08f).coerceAtLeast(1f)
            listState.scrollToItem(
                index = targetIndex,
                scrollOffset = -(targetDistance + approachDistance).roundToInt(),
            )
            approachDistance
        }
        if (abs(scrollDistance) > 1f) {
            listState.animateScrollBy(
                value = scrollDistance,
                animationSpec = tween(
                    durationMillis = 520,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (state.textLines.isEmpty()) {
            AppText(
                text = stringResource(R.string.read_aloud_preparing_content),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(contentPadding),
                style = LegadoTheme.typography.bodyLarge,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(userScrollConnection),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = state.textLines,
                    key = { _, line -> line.chapterPosition },
                    contentType = { _, _ -> "read_aloud_text_line" },
                ) { index, line ->
                    val active = index == state.activeTextLine
                    val backgroundColor by animateColorAsState(
                        targetValue = if (active) LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else LegadoTheme.colorScheme.primaryContainer.copy(alpha = 0f),
                        animationSpec = tween(durationMillis = 400),
                        label = "active_line_background"
                    )
                    AppText(
                        text = line.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onIntent(ReadAloudPlayerIntent.SeekTo(line.chapterPosition)) }
                            .background(backgroundColor)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .then(flowingTextModifier),
                        style = if (active) LegadoTheme.typography.bodyLargeEmphasized else LegadoTheme.typography.bodyLarge,
                        color = if (active) LegadoTheme.colorScheme.onPrimaryContainer
                        else LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        lineHeight = LegadoTheme.typography.bodyLarge.lineHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun formatPosition(value: Int): String = if (value < 1000) {
    stringResource(R.string.read_aloud_position_chars, value)
} else {
    stringResource(R.string.read_aloud_position_kchars, value / 1000f)
}

private enum class PlayerAdjustment { Speed, Timer }

private const val BOTTOM_BAR_RESTORE_DELAY_MILLIS = 650L

private fun PlayerAdjustment?.toggle(value: PlayerAdjustment): PlayerAdjustment? =
    if (this == value) null else value

@Composable
private fun flowingTextBlend(): List<BlendColorEntry> {
    val isDark = LegadoTheme.isDark
    return remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }
}




