package io.legado.app.ui.book.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import io.legado.app.R
import io.legado.app.constant.ReadAloudBgMode
import io.legado.app.constant.Status
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.model.AudioPlay
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.hazeStyle.HazeLegado
import io.legado.app.ui.util.rememberBlurBackdrop
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallAnimatedButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.player.PlayerAdjustmentSlider
import io.legado.app.ui.widget.components.player.PlayerBackground
import io.legado.app.ui.widget.components.player.PlayerProgressSlider
import io.legado.app.ui.widget.components.player.PlayerTocPage
import io.legado.app.ui.widget.components.player.playerBgModeLabel
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AudioPlayScreenContent(
    state: AudioPlayUiState,
    onIntent: (AudioPlayIntent) -> Unit,
    onBack: () -> Unit,
) {
    val verticalPagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val verticalPagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = verticalPagerState,
        orientation = Orientation.Vertical,
    )
    val coroutineScope = rememberCoroutineScope()
    var activeAdjustment by remember { mutableStateOf<AudioAdjustment?>(null) }
    var speedPreview by remember(state.speed) { mutableFloatStateOf(state.speed) }
    var timerPreview by remember(state.timerMinutes) {
        mutableFloatStateOf(state.timerMinutes.toFloat())
    }
    var menuExpanded by remember { mutableStateOf(false) }
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
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + when (
            activeAdjustment
        ) {
            null -> 216.dp
            AudioAdjustment.Speed -> 264.dp
            AudioAdjustment.Timer -> 344.dp
        },
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MediumTonalButton(
                        onClick = onBack,
                        icon = AppIcons.Back,
                        contentDescription = stringResource(R.string.back),
                    )

                    Box {
                        MediumTonalButton(
                            onClick = { menuExpanded = true },
                            icon = AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.more),
                        )
                        RoundDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) { dismiss ->
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.change_origin),
                                leadingIcon = {
                                    MenuItemIcon(Icons.Default.SwapHoriz)
                                },
                                onClick = {
                                    dismiss()
                                    onIntent(AudioPlayIntent.ChangeSource)
                                },
                            )
                            if (state.canLogin) {
                                RoundDropdownMenuItem(
                                    text = stringResource(R.string.login),
                                    leadingIcon = {
                                        MenuItemIcon(Icons.Default.Login)
                                    },
                                    onClick = {
                                        dismiss()
                                        onIntent(AudioPlayIntent.Login)
                                    },
                                )
                            }
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.copy_play_url),
                                leadingIcon = {
                                    MenuItemIcon(Icons.Default.ContentCopy)
                                },
                                onClick = {
                                    dismiss()
                                    onIntent(AudioPlayIntent.CopyPlayUrl)
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.edit_book_source),
                                leadingIcon = {
                                    MenuItemIcon(Icons.Default.Edit)
                                },
                                onClick = {
                                    dismiss()
                                    onIntent(AudioPlayIntent.EditSource)
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.audio_play_skip_credits),
                                leadingIcon = {
                                    MenuItemIcon(Icons.Default.FastForward)
                                },
                                onClick = {
                                    dismiss()
                                    onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.SkipCredits))
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.audio_play_gain),
                                leadingIcon = {
                                    MenuItemIcon(Icons.Default.VolumeUp)
                                },
                                onClick = {
                                    dismiss()
                                    onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.Gain))
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.audio_play_wake_lock),
                                leadingIcon = {
                                    MenuItemIcon(Icons.Default.Lock)
                                },
                                isSelected = state.wakeLockEnabled,
                                onClick = {
                                    dismiss()
                                    onIntent(AudioPlayIntent.ToggleWakeLock)
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.system_media_control_compatibility_change),
                                leadingIcon = {
                                    MenuItemIcon(Icons.Default.Headphones)
                                },
                                isSelected = state.mediaControlEnabled,
                                onClick = {
                                    dismiss()
                                    onIntent(AudioPlayIntent.ToggleMediaControl)
                                },
                            )
                            RoundDropdownMenuItem(
                                text = stringResource(R.string.log),
                                leadingIcon = {
                                    MenuItemIcon(Icons.Default.Info)
                                },
                                onClick = {
                                    dismiss()
                                    onIntent(AudioPlayIntent.OpenSheet(AudioPlaySheet.Log))
                                },
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
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
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediumPlainButton(
                        onClick = { onIntent(AudioPlayIntent.PreviousChapter) },
                        enabled = state.canPrevious,
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.previous_chapter),
                    )
                    Box(contentAlignment = Alignment.Center) {
                        MediumTonalButton(
                            onClick = { onIntent(AudioPlayIntent.TogglePlay) },
                            onLongClick = { onIntent(AudioPlayIntent.Stop) },
                            icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (state.isPlaying) R.string.pause else R.string.audio_play
                            ),
                            modifier = Modifier.size(72.dp),
                        )
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(56.dp),
                                strokeWidth = 3.dp,
                                color = LegadoTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
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
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                        iconChecked = Icons.Default.KeyboardArrowUp,
                        text = stringResource(
                            if (verticalPagerState.currentPage == 0) {
                                R.string.chapter_list
                            } else {
                                R.string.back
                            }
                        ),
                        contentDescription = stringResource(
                            if (verticalPagerState.currentPage == 0) {
                                R.string.chapter_list
                            } else {
                                R.string.back
                            }
                        ),
                        onCheckedChange = {
                            coroutineScope.launch {
                                verticalPagerState.animateScrollToPage(
                                    page = if (verticalPagerState.currentPage == 0) 1 else 0,
                                    animationSpec = tween(
                                        durationMillis = 520,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            }
                        },
                    )
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
                            activeAdjustment = activeAdjustment.toggle(AudioAdjustment.Speed)
                        },
                    )
                    SmallAnimatedButton(
                        containerColor = Color.Transparent,
                        checked = false,
                        icon = Icons.Default.Timer,
                        text = stringResource(R.string.set_timer),
                        contentDescription = stringResource(R.string.set_timer),
                        onCheckedChange = {
                            activeAdjustment = activeAdjustment.toggle(AudioAdjustment.Timer)
                        },
                    )
                }
                AnimatedVisibility(activeAdjustment == AudioAdjustment.Speed) {
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
                AnimatedVisibility(activeAdjustment == AudioAdjustment.Timer) {
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
            VerticalPager(
                state = verticalPagerState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                pageNestedScrollConnection = verticalPagerNestedScrollConnection,
            ) { page ->
                if (page == 0) {
                    AudioCoverPage(state, pageContentPadding)
                } else {
                    PlayerTocPage(
                        chapters = state.chapters,
                        currentIndex = state.chapterIndex,
                        isPaused = !state.isPlaying,
                        onSelect = { onIntent(AudioPlayIntent.SelectChapter(it)) },
                        contentPadding = pageContentPadding,
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
    AppLogSheet(
        show = state.activeSheet == AudioPlaySheet.Log,
        onDismissRequest = { onIntent(AudioPlayIntent.DismissSheet) },
    )
}

private const val AUDIO_GAIN_MIN = -6000
private const val AUDIO_GAIN_MAX = 6000
private const val CREDITS_MAX_SECONDS = 180

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
            BookCoverImage(
                name = state.bookName,
                author = state.author,
                path = state.coverPath,
                sourceOrigin = state.sourceOrigin,
                modifier = Modifier
                    .fillMaxWidth(0.64f)
                    .aspectRatio(5f / 7f)
                    .clip(RoundedCornerShape(8.dp))
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
                modifier = Modifier.padding(top = 16.dp),
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

private enum class AudioAdjustment { Speed, Timer }

private fun AudioAdjustment?.toggle(value: AudioAdjustment): AudioAdjustment? =
    if (this == value) null else value

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
