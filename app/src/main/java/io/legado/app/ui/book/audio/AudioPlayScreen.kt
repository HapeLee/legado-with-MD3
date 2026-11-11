package io.legado.app.ui.book.audio

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import io.legado.app.R
import io.legado.app.constant.CoverRatio
import io.legado.app.constant.ReadAloudBgMode
import io.legado.app.constant.Status
import io.legado.app.core.ui.player.PlayerMorphAppearance
import io.legado.app.core.ui.player.PlayerMorphCover
import io.legado.app.core.ui.player.TrackPlayerMorphCoverPage
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.model.AudioPlay
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.hazeStyle.HazeLegado
import io.legado.app.ui.util.rememberBlurBackdrop
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.SmallAnimatedButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.menu.AppMenuAnchor
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.modalBottomSheet.OptionCard
import io.legado.app.ui.widget.components.modalBottomSheet.OptionSheet
import io.legado.app.ui.widget.components.player.AnimatedPlayPauseButton
import io.legado.app.ui.widget.components.player.PlayerAdjustmentSlider
import io.legado.app.ui.widget.components.player.PlayerBackground
import io.legado.app.ui.widget.components.player.PlayerProgressSlider
import io.legado.app.ui.widget.components.player.PlayerTocPage
import io.legado.app.ui.widget.components.player.playerBgModeLabel
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.blur.layerBackdrop
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AudioPlayScreenContent(
    state: AudioPlayUiState,
    onIntent: (AudioPlayIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalPagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { if (state.lyricLines.isEmpty()) 2 else 3 },
    )
    TrackPlayerMorphCoverPage(horizontalPagerState)
    val pagerHazeState = remember { HazeState() }
    val hazeEnabled =
        state.bgMode != ReadAloudBgMode.Solid && state.bgMode != ReadAloudBgMode.Transparent
    val textBackdrop = rememberBlurBackdrop()
    val flowingLightActive = state.bgMode == ReadAloudBgMode.FlowingLight
    val overlayHazeStyle = HazeLegado.ultraThinPlus(
        containerColor = LegadoTheme.colorScheme.surface,
    )
    val pageContentPadding = PaddingValues(
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 88.dp,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 216.dp,
    )
    AppScaffold(
        modifier = modifier.fillMaxSize(),
        alwaysDrawBehindBars = true,
        disableContentSampling = true,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            val hazeModifier = if (hazeEnabled) {
                Modifier.hazeBlur(
                    input = HazeInput.Sources(pagerHazeState),
                    style = overlayHazeStyle.then {
                        progressive(
                            HazeProgressive.verticalGradient(
                                startIntensity = 1f,
                                endIntensity = 0f,
                            ),
                        )
                    },
                )
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
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MediumPlainButton(
                        onClick = onBack,
                        icon = AppIcons.Back,
                        contentDescription = stringResource(R.string.back),
                    )

                    AppActionMenu(
                        groups = persistentListOf(
                            AppActionGroup(
                                id = "audio_play_actions",
                                actions = buildList {
                                    add(
                                        AppAction(
                                            id = "change_source",
                                            label = stringResource(R.string.change_origin),
                                            icon = Icons.Default.SwapHoriz,
                                        )
                                    )
                                    if (state.canLogin) {
                                        add(
                                            AppAction(
                                                id = "login",
                                                label = stringResource(R.string.login),
                                                icon = Icons.AutoMirrored.Filled.Login,
                                            )
                                        )
                                    }
                                    add(
                                        AppAction(
                                            id = "copy_play_url",
                                            label = stringResource(R.string.copy_play_url),
                                            icon = Icons.Default.ContentCopy,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "edit_source",
                                            label = stringResource(R.string.edit_book_source),
                                            icon = Icons.Default.Edit,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "skip_credits",
                                            label = stringResource(R.string.audio_play_skip_credits),
                                            icon = Icons.Default.FastForward,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "gain",
                                            label = stringResource(R.string.audio_play_gain),
                                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "cover_ratio",
                                            label = stringResource(R.string.audio_play_cover_ratio),
                                            icon = Icons.Default.Crop,
                                        )
                                    )
                                    // 勾选态表达开关，而不是「当前选中项」
                                    add(
                                        AppAction(
                                            id = "wake_lock",
                                            label = stringResource(R.string.audio_play_wake_lock),
                                            icon = Icons.Default.Lock,
                                            checked = state.wakeLockEnabled,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "media_control",
                                            label = stringResource(
                                                R.string.system_media_control_compatibility_change
                                            ),
                                            icon = Icons.Default.Headphones,
                                            checked = state.mediaControlEnabled,
                                        )
                                    )
                                    add(
                                        AppAction(
                                            id = "log",
                                            label = stringResource(R.string.log),
                                            icon = Icons.Default.Info,
                                        )
                                    )
                                }.toImmutableList(),
                            ),
                        ),
                        onAction = { id ->
                            when (id) {
                                "change_source" -> onIntent(AudioPlayIntent.ChangeSource)
                                "login" -> onIntent(AudioPlayIntent.Login)
                                "copy_play_url" -> onIntent(AudioPlayIntent.CopyPlayUrl)
                                "edit_source" -> onIntent(AudioPlayIntent.EditSource)
                                "skip_credits" ->
                                    onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.SkipCredits))

                                "gain" -> onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.Gain))
                                "cover_ratio" ->
                                    onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.CoverRatioOptions))

                                "wake_lock" -> onIntent(AudioPlayIntent.ToggleWakeLock)
                                "media_control" -> onIntent(AudioPlayIntent.ToggleMediaControl)
                                "log" -> onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.Log))
                            }
                        },
                        anchor = AppMenuAnchor.Row,
                        title = stringResource(R.string.more),
                    ) {
                        MediumPlainButton(
                            onClick = null,
                            icon = AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.more),
                        )
                    }
                }
            }
        },
        bottomBar = {
            val hazeModifier = if (hazeEnabled) {
                Modifier.hazeBlur(
                    input = HazeInput.Sources(pagerHazeState),
                    style = overlayHazeStyle.then {
                        progressive(
                            HazeProgressive.verticalGradient(
                                startIntensity = 0f,
                                endIntensity = 1f,
                            ),
                        )
                    },
                )
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
                    value = state.position.coerceIn(0, state.duration).toFloat(),
                    onValueChange = { onIntent(AudioPlayIntent.SeekTo(it.toInt())) },
                    valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppText(
                        formatAudioTime(state.position),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    AppText(
                        formatAudioTime(state.duration),
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
                        onClick = { onIntent(AudioPlayIntent.PreviousChapter) },
                        enabled = state.canPrevious,
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.previous_chapter),
                    )
                    AnimatedPlayPauseButton(
                        isPlaying = state.isPlaying,
                        isLoading = state.isLoading,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.pause else R.string.audio_play
                        ),
                        onClick = { onIntent(AudioPlayIntent.TogglePlay) },
                        onLongClick = { onIntent(AudioPlayIntent.Stop) },
                    )
                    MediumPlainButton(
                        onClick = { onIntent(AudioPlayIntent.NextChapter) },
                        enabled = state.canNext,
                        icon = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.next_chapter),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SmallAnimatedButton(
                        containerColor = Color.Transparent,
                        checked = false,
                        icon = Icons.Default.WbTwilight,
                        text = playerBgModeLabel(state.bgMode),
                        contentDescription = playerBgModeLabel(state.bgMode),
                        onCheckedChange = { onIntent(AudioPlayIntent.CycleBgMode) },
                    )
                    SmallAnimatedButton(
                        containerColor = Color.Transparent,
                        checked = false,
                        icon = playModeIcon(state.playMode),
                        text = playModeContentDescription(state.playMode),
                        contentDescription = playModeContentDescription(state.playMode),
                        onCheckedChange = { onIntent(AudioPlayIntent.ChangePlayMode) },
                    )
                    SmallAnimatedButton(
                        containerColor = Color.Transparent,
                        checked = false,
                        icon = Icons.Default.Speed,
                        text = stringResource(R.string.audio_play_speed),
                        contentDescription = stringResource(R.string.audio_play_speed),
                        onCheckedChange = {
                            onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.Speed))
                        },
                    )
                    SmallAnimatedButton(
                        containerColor = Color.Transparent,
                        checked = false,
                        icon = Icons.Default.Timer,
                        text = stringResource(R.string.set_timer),
                        contentDescription = stringResource(R.string.set_timer),
                        onCheckedChange = {
                            onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.Timer))
                        },
                    )
                }
            }
        },
    ) {
        Box(
            Modifier
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
                when (page) {
                    0 -> PlayerTocPage(
                        chapters = state.chapters,
                        currentIndex = state.chapterIndex,
                        isPaused = !state.isPlaying,
                        onSelect = { onIntent(AudioPlayIntent.SelectChapter(it)) },
                        contentPadding = pageContentPadding,
                    )

                    1 -> AudioCoverPage(state, pageContentPadding)
                    else -> AudioLyricPage(
                        state = state,
                        contentPadding = pageContentPadding,
                        onIntent = onIntent,
                    )
                }
            }
        }
    }

    AudioSkipCreditsSheet(
        show = state.activeSheet == AudioPlaySheet.SkipCredits,
        state = state,
        onDismissRequest = { onIntent(AudioPlayIntent.DismissSheet) },
        onIntent = onIntent,
    )
    AudioGainSheet(
        show = state.activeSheet == AudioPlaySheet.Gain,
        state = state,
        onDismissRequest = { onIntent(AudioPlayIntent.DismissSheet) },
        onIntent = onIntent,
    )
    AudioCoverRatioSheet(
        show = state.activeSheet == AudioPlaySheet.CoverRatioOptions,
        onDismissRequest = { onIntent(AudioPlayIntent.DismissSheet) },
        onIntent = onIntent,
    )
    AudioSpeedSheet(
        show = state.activeSheet == AudioPlaySheet.Speed,
        state = state,
        onDismissRequest = { onIntent(AudioPlayIntent.DismissSheet) },
        onIntent = onIntent,
    )
    AudioTimerSheet(
        show = state.activeSheet == AudioPlaySheet.Timer,
        state = state,
        onDismissRequest = { onIntent(AudioPlayIntent.DismissSheet) },
        onIntent = onIntent,
    )
    AppLogSheet(
        show = state.activeSheet == AudioPlaySheet.Log,
        onDismissRequest = { onIntent(AudioPlayIntent.DismissSheet) },
    )
}

private const val AUDIO_GAIN_MIN = -6000
private const val AUDIO_GAIN_MAX = 6000
private const val CREDITS_MAX_SECONDS = 180

@Composable
private fun AudioLyricPage(
    state: AudioPlayUiState,
    contentPadding: PaddingValues,
    onIntent: (AudioPlayIntent) -> Unit,
) {
    val listState = rememberLazyListState()
    val activeLine = state.lyricLines.indexOfLast { it.timestampMs <= state.position }

    LaunchedEffect(state.lyricLines, activeLine) {
        if (activeLine !in state.lyricLines.indices) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val targetDistance = viewportHeight * 0.32f
        val targetOffset = layoutInfo.viewportStartOffset + targetDistance
        val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeLine }
        val scrollDistance = if (visibleItem != null) {
            visibleItem.offset - targetOffset
        } else {
            val approachDistance = (viewportHeight * 0.08f).coerceAtLeast(1f)
            listState.scrollToItem(
                index = activeLine,
                scrollOffset = -(targetDistance + approachDistance).roundToInt(),
            )
            approachDistance
        }
        if (abs(scrollDistance) > 1f) {
            listState.animateScrollBy(
                value = scrollDistance,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = state.lyricLines,
            key = { index, line -> "${line.timestampMs}:$index" },
            contentType = { _, _ -> "audio_lyric_line" },
        ) { index, line ->
            val active = index == activeLine
            AppText(
                text = line.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onIntent(AudioPlayIntent.SeekTo(line.timestampMs)) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                style = LegadoTheme.typography.titleLargeEmphasized,
                color = if (active) {
                    LegadoTheme.colorScheme.onSurface
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}

@Composable
private fun AudioSkipCreditsSheet(
    show: Boolean,
    state: AudioPlayUiState,
    onDismissRequest: () -> Unit,
    onIntent: (AudioPlayIntent) -> Unit,
) {
    var openPreview by remember(state.openCredits) {
        mutableFloatStateOf(state.openCredits.toFloat())
    }
    var closePreview by remember(state.closeCredits) {
        mutableFloatStateOf(state.closeCredits.toFloat())
    }
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.audio_play_skip_credits),
    ) {
        PlayerAdjustmentSlider(
            title = stringResource(R.string.audio_play_open_credits),
            value = openPreview,
            valueLabel = stringResource(R.string.seconds_m, openPreview.roundToInt()),
            startLabel = stringResource(R.string.seconds_m, 0),
            endLabel = stringResource(R.string.seconds_m, CREDITS_MAX_SECONDS),
            onValueChange = { openPreview = it.roundToInt().toFloat() },
            onValueChangeFinished = {
                onIntent(AudioPlayIntent.SetOpenCredits(openPreview.roundToInt()))
            },
            valueRange = 0f..CREDITS_MAX_SECONDS.toFloat(),
            steps = CREDITS_MAX_SECONDS - 1,
        )
        PlayerAdjustmentSlider(
            title = stringResource(R.string.audio_play_close_credits),
            value = closePreview,
            valueLabel = stringResource(R.string.seconds_m, closePreview.roundToInt()),
            startLabel = stringResource(R.string.seconds_m, 0),
            endLabel = stringResource(R.string.seconds_m, CREDITS_MAX_SECONDS),
            onValueChange = { closePreview = it.roundToInt().toFloat() },
            onValueChangeFinished = {
                onIntent(AudioPlayIntent.SetCloseCredits(closePreview.roundToInt()))
            },
            valueRange = 0f..CREDITS_MAX_SECONDS.toFloat(),
            steps = CREDITS_MAX_SECONDS - 1,
        )
    }
}

@Composable
private fun AudioGainSheet(
    show: Boolean,
    state: AudioPlayUiState,
    onDismissRequest: () -> Unit,
    onIntent: (AudioPlayIntent) -> Unit,
) {
    var gainPreview by remember(state.audioGain) {
        mutableFloatStateOf(state.audioGain.toFloat())
    }
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.audio_play_gain),
    ) {
        PlayerAdjustmentSlider(
            title = stringResource(R.string.audio_play_gain),
            value = gainPreview.coerceIn(AUDIO_GAIN_MIN.toFloat(), AUDIO_GAIN_MAX.toFloat()),
            valueLabel = formatGain(gainPreview.roundToInt()),
            startLabel = formatGain(AUDIO_GAIN_MIN),
            endLabel = formatGain(AUDIO_GAIN_MAX),
            onValueChange = { gainPreview = (it / 500f).roundToInt() * 500f },
            onValueChangeFinished = {
                onIntent(AudioPlayIntent.SetAudioGain(gainPreview.roundToInt()))
            },
            valueRange = AUDIO_GAIN_MIN.toFloat()..AUDIO_GAIN_MAX.toFloat(),
            steps = (AUDIO_GAIN_MAX - AUDIO_GAIN_MIN) / 500 - 1,
        )
    }
}

@Composable
private fun AudioCoverRatioSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onIntent: (AudioPlayIntent) -> Unit,
) {
    OptionSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.audio_play_cover_ratio),
    ) {
        OptionCard(
            icon = Icons.Default.CropSquare,
            text = stringResource(R.string.cover_ratio_square),
            onClick = {
                onDismissRequest()
                onIntent(AudioPlayIntent.SetCoverRatio(CoverRatio.Square))
            },
        )
        OptionCard(
            icon = Icons.Default.RadioButtonUnchecked,
            text = stringResource(R.string.cover_ratio_circle),
            onClick = {
                onDismissRequest()
                onIntent(AudioPlayIntent.SetCoverRatio(CoverRatio.Circle))
            },
        )
        OptionCard(
            icon = Icons.Default.Book,
            text = stringResource(R.string.cover_ratio_book),
            onClick = {
                onDismissRequest()
                onIntent(AudioPlayIntent.SetCoverRatio(CoverRatio.Book))
            },
        )
        OptionCard(
            icon = Icons.Default.CropFree,
            text = stringResource(R.string.cover_ratio_unrestricted),
            onClick = {
                onDismissRequest()
                onIntent(AudioPlayIntent.SetCoverRatio(CoverRatio.Unrestricted))
            },
        )
    }
}

@Composable
private fun AudioSpeedSheet(
    show: Boolean,
    state: AudioPlayUiState,
    onDismissRequest: () -> Unit,
    onIntent: (AudioPlayIntent) -> Unit,
) {
    var speedPreview by remember(state.speed) { mutableFloatStateOf(state.speed) }
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.audio_play_speed),
    ) {
        PlayerAdjustmentSlider(
            title = stringResource(R.string.audio_play_speed),
            value = speedPreview.coerceIn(AUDIO_SPEED_MIN, AUDIO_SPEED_MAX),
            valueLabel = String.format(Locale.ROOT, "%.1fX", speedPreview),
            startLabel = "0.5X",
            endLabel = "5.0X",
            enabled = state.status != Status.STOP,
            onValueChange = { speedPreview = it },
            onValueChangeFinished = {
                onIntent(AudioPlayIntent.SetSpeed(speedPreview))
            },
            valueRange = AUDIO_SPEED_MIN..AUDIO_SPEED_MAX,
            steps = 44,
        )
    }
}

@Composable
private fun AudioTimerSheet(
    show: Boolean,
    state: AudioPlayUiState,
    onDismissRequest: () -> Unit,
    onIntent: (AudioPlayIntent) -> Unit,
) {
    var timerPreview by remember(state.timerMinutes) {
        mutableFloatStateOf(state.timerMinutes.toFloat())
    }
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.set_timer),
    ) {
        PlayerAdjustmentSlider(
            title = stringResource(R.string.set_timer),
            value = timerPreview.coerceIn(
                PlaybackTimer.MIN_MINUTES.toFloat(),
                PlaybackTimer.MAX_MINUTES.toFloat(),
            ),
            valueLabel = if (timerPreview == 0f) {
                stringResource(R.string.close)
            } else {
                stringResource(R.string.timer_m, timerPreview.roundToInt())
            },
            startLabel = stringResource(R.string.close),
            endLabel = stringResource(R.string.timer_m, PlaybackTimer.MAX_MINUTES),
            onValueChange = { timerPreview = it.roundToInt().toFloat() },
            onValueChangeFinished = {
                onIntent(AudioPlayIntent.SetTimer(timerPreview.roundToInt()))
            },
            valueRange = PlaybackTimer.MIN_MINUTES.toFloat()..PlaybackTimer.MAX_MINUTES.toFloat(),
            steps = PlaybackTimer.MAX_MINUTES - PlaybackTimer.MIN_MINUTES - 1,
        )
    }
}

private fun formatGain(gainMb: Int): String {
    val dB = gainMb / 1000f
    return if (dB == 0f) {
        "0.0 dB"
    } else {
        String.format(Locale.ROOT, "%+.1f dB", dB)
    }
}

private const val AUDIO_SPEED_MIN = 0.5f
private const val AUDIO_SPEED_MAX = 5.0f

@Composable
private fun AudioCoverPage(
    state: AudioPlayUiState,
    contentPadding: PaddingValues,
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
            val coverModifier = when (state.coverRatio) {
                // 边长取 0.8×宽 与 0.55×高 的较小值：既放大封面，又保证下方文字始终有空间
                CoverRatio.Square, CoverRatio.Circle ->
                    Modifier.aspectRatio(1f)

                CoverRatio.Book ->
                    Modifier
                        .fillMaxWidth(0.64f)
                        .aspectRatio(5f / 7f)

                else ->
                    Modifier.fillMaxSize(0.64f)
            }
            PlayerMorphCover(
                appearance = PlayerMorphAppearance(
                    state.bookName, state.author, state.coverPath, state.sourceOrigin, state.bgMode,
                ),
                modifier = coverModifier,
                circular = state.coverRatio == CoverRatio.Circle,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText(
                text = state.bookName,
                style = LegadoTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = state.chapterTitle,
                style = LegadoTheme.typography.titleMediumEmphasized,
                color = LegadoTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


private fun formatAudioTime(valueMs: Int): String {
    val totalSeconds = (valueMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun playModeContentDescription(mode: AudioPlay.PlayMode): String = when (mode) {
    AudioPlay.PlayMode.LIST_END_STOP -> stringResource(R.string.audio_play_mode_sequence)
    AudioPlay.PlayMode.SINGLE_LOOP -> stringResource(R.string.audio_play_mode_single_loop)
    AudioPlay.PlayMode.RANDOM -> stringResource(R.string.audio_play_mode_random)
    AudioPlay.PlayMode.LIST_LOOP -> stringResource(R.string.audio_play_mode_loop)
}

private fun playModeIcon(mode: AudioPlay.PlayMode): ImageVector = when (mode) {
    AudioPlay.PlayMode.LIST_END_STOP -> Icons.AutoMirrored.Filled.PlaylistPlay
    AudioPlay.PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
    AudioPlay.PlayMode.RANDOM -> Icons.Default.Shuffle
    AudioPlay.PlayMode.LIST_LOOP -> Icons.Default.Repeat
}
