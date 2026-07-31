package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppVerticalSlider
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun MenuBottomBar(
    state: ReadBookUiState,
    eyeProtectionEnabled: Boolean,
    colors: ReadMenuColors,
    onIntent: (ReadBookIntent) -> Unit,
    context: Context,
    bottomPadding: Dp = 0.dp,
    buttonGlassEnabled: Boolean = false,
    backdrop: Backdrop? = null,
    labelColor: Color = LegadoTheme.colorScheme.onSurface,
    progressBarBehavior: String,
    onBrightnessPreview: (Int) -> Unit,
) {
    val seekMax = state.seekMax.coerceAtLeast(0)
    val sliderMax = seekMax.toFloat().coerceAtLeast(1f)
    var sliderValue by remember {
        mutableFloatStateOf(
            state.seekProgress.coerceIn(0, seekMax).toFloat()
        )
    }
    var sliderDragging by remember { mutableStateOf(false) }
    var previewPageIndex by remember { mutableIntStateOf(state.seekProgress.coerceIn(0, seekMax)) }
    val toolButtonsBottomPadding = if (buttonGlassEnabled) 6.dp else 0.dp
    val contentBottomPadding = if (bottomPadding > toolButtonsBottomPadding) {
        bottomPadding - toolButtonsBottomPadding
    } else {
        0.dp
    }
    val progressCurrent = sliderValue.roundToInt().coerceIn(0, seekMax) + 1
    val progressTotal = seekMax + 1
    val progressValueDescription = stringResource(
        if (progressBarBehavior == "page") {
            R.string.a11y_read_progress_page
        } else {
            R.string.a11y_read_progress_chapter
        },
        progressCurrent,
        progressTotal,
    )

    fun commitSliderValue(value: Float) {
        val target = value.roundToInt().coerceIn(0, seekMax)
        sliderDragging = false
        sliderValue = target.toFloat()
        previewPageIndex = target
        if (progressBarBehavior == "page") {
            onIntent(ReadBookIntent.SkipToPage(target))
        } else {
            onIntent(ReadBookIntent.SeekToChapter(target))
        }
    }

    fun updateSliderValue(value: Float) {
        val coercedValue = value.coerceIn(0f, sliderMax)
        sliderDragging = true
        sliderValue = coercedValue
        if (progressBarBehavior == "page") {
            val target = coercedValue.roundToInt().coerceIn(0, seekMax)
            if (target != previewPageIndex) {
                previewPageIndex = target
                onIntent(ReadBookIntent.SkipToPage(target))
            }
        }
    }

    LaunchedEffect(state.seekProgress, seekMax, progressBarBehavior) {
        val progress = state.seekProgress.coerceIn(0, seekMax)
        previewPageIndex = progress
        if (progressBarBehavior == "page" || !sliderDragging) {
            sliderValue = progress.toFloat()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
            )
            .padding(top = 8.dp, bottom = contentBottomPadding)
            .animateContentSize(),
    ) {
        if (state.menuConfig.showBrightnessView == "1") {
            BrightnessBar(
                brightness = state.menuConfig.readBrightness,
                onBrightnessChange = { value ->
                    onIntent(ReadBookIntent.SetBrightness(value))
                },
                brightnessAuto = state.menuConfig.brightnessAuto,
                onToggleAuto = {
                    onIntent(ReadBookIntent.ToggleBrightnessAuto(!state.menuConfig.brightnessAuto))
                },
                onTogglePosition = {},
                vertical = false,
                colors = colors,
                menuConfig = state.menuConfig,
                backdrop = backdrop,
                buttonGlassEnabled = buttonGlassEnabled,
                glassThumbEnabled = buttonGlassEnabled,
                onBrightnessPreview = onBrightnessPreview,
            )
            Spacer(Modifier.height(4.dp))
        }

        // Seek bar row: prev + slider + next
        AnimatedVisibility(visible = state.menuConfig.readSliderMode != "1") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BottomBarGlassIconButton(
                    onClick = { onIntent(ReadBookIntent.PrevChapter) },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    colors = colors,
                    backdrop = backdrop,
                    menuConfig = state.menuConfig,
                    glassEnabled = buttonGlassEnabled,
                    contentDescription = stringResource(R.string.previous_chapter),
                )

                ReadMenuSlider(
                    value = sliderValue.coerceIn(0f, sliderMax),
                    onValueChange = ::updateSliderValue,
                    onValueChangeFinished = {
                        commitSliderValue(sliderValue)
                    },
                    onValueCommit = ::commitSliderValue,
                    valueRange = 0f..sliderMax,
                    steps = (seekMax - 1).coerceAtLeast(0),
                    enabled = seekMax > 0,
                    backdrop = backdrop,
                    glassThumbEnabled = buttonGlassEnabled,
                    accessibilityLabel = stringResource(R.string.progress),
                    accessibilityValue = progressValueDescription,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                BottomBarGlassIconButton(
                    onClick = { onIntent(ReadBookIntent.NextChapter) },
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    colors = colors,
                    backdrop = backdrop,
                    menuConfig = state.menuConfig,
                    glassEnabled = buttonGlassEnabled,
                    contentDescription = stringResource(R.string.next_chapter),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tool buttons
        val toolButtons = remember(
            context,
            state.menuConfig.bottomBarButtons,
            state.menuConfig.readMenuCustomIcons,
            state.isReadAloudRunning,
            state.isAutoPage,
            state.translationMode,
            state.useReplaceRule,
            eyeProtectionEnabled,
        ) {
            loadToolButtons(
                context = context,
                state = state,
                eyeProtectionEnabled = eyeProtectionEnabled,
                onIntent = onIntent,
            )
        }
        val itemsPerRow = state.menuConfig.readMenuIconItemsPerRow
        val rowCount = state.menuConfig.readMenuIconRowCount
        val pageSize = (itemsPerRow * rowCount).coerceAtLeast(1)
        val pageCount = ceil(toolButtons.size / pageSize.toFloat()).roundToInt().coerceAtLeast(1)
        val pagerState = rememberPagerState(pageCount = { pageCount })

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth(),
        ) { page ->
            val pageButtons = toolButtons.drop(page * pageSize).take(pageSize)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = toolButtonsBottomPadding),
            ) {
                pageButtons.chunked(itemsPerRow).forEach { rowButtons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        rowButtons.forEach { button ->
                            ToolButtonItem(
                                button = button,
                                state = state,
                                colors = colors,
                                backdrop = backdrop,
                                glassEnabled = buttonGlassEnabled,
                                labelColor = labelColor,
                                modifier = Modifier.width(if (buttonGlassEnabled) 48.dp else 40.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomBarGlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    menuConfig: ReadMenuConfig,
    glassEnabled: Boolean,
    contentDescription: String? = null,
) {
    ReadMenuGlassIconButton(
        onClick = onClick,
        icon = icon,
        colors = colors,
        backdrop = backdrop,
        menuConfig = menuConfig,
        glassEnabled = glassEnabled,
        contentDescription = contentDescription,
    )
}

@Composable
internal fun BrightnessBar(
    brightness: Int,
    onBrightnessChange: (Int) -> Unit,
    brightnessAuto: Boolean,
    onToggleAuto: () -> Unit,
    onTogglePosition: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    colors: ReadMenuColors,
    menuConfig: ReadMenuConfig,
    backdrop: Backdrop? = null,
    buttonGlassEnabled: Boolean = false,
    glassThumbEnabled: Boolean = false,
    onBrightnessPreview: (Int) -> Unit,
) {
    var sliderValue by remember(vertical, buttonGlassEnabled) {
        mutableFloatStateOf(brightness.toFloat())
    }
    var sliderDragging by remember(vertical, buttonGlassEnabled) {
        mutableStateOf(false)
    }

    LaunchedEffect(brightness, brightnessAuto) {
        if (brightnessAuto) {
            sliderDragging = false
            sliderValue = brightness.toFloat()
        } else if (!sliderDragging) {
            sliderValue = brightness.toFloat()
        }
    }

    fun updateSliderValue(value: Float) {
        if (brightnessAuto) return
        sliderDragging = true
        sliderValue = value.coerceIn(0f, 100f)
        val target = value.roundToInt().coerceIn(0, 100)

        //直接先改亮度，如果在这里onBrightnessChange，会ANR
        onBrightnessPreview(target)
    }

    fun commitSliderValue(value: Float) {
        val target = value.roundToInt().coerceIn(0, 100)
        sliderDragging = false
        sliderValue = target.toFloat()
        if (brightnessAuto) return
        onBrightnessChange(target)
    }

    fun toggleAuto() {
        sliderDragging = false
        sliderValue = brightness.toFloat()
        onToggleAuto()
    }

    val brightnessValueDescription = stringResource(
        R.string.a11y_percent_value,
        sliderValue.roundToInt().coerceIn(0, 100),
    )

    if (vertical) {
        Column(
            modifier = modifier
                .width(if (buttonGlassEnabled) 64.dp else 56.dp)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReadMenuGlassIconButton(
                onClick = ::toggleAuto,
                icon = Icons.Filled.BrightnessAuto,
                colors = colors,
                backdrop = backdrop,
                menuConfig = menuConfig,
                glassEnabled = buttonGlassEnabled,
                selected = brightnessAuto,
                contentDescription = stringResource(R.string.brightness_follow_system),
            )
            AppVerticalSlider(
                value = sliderValue.coerceIn(0f, 100f),
                onValueChange = { value ->
                    if (brightnessAuto) return@AppVerticalSlider
                    updateSliderValue(value)
                },
                onValueChangeFinished = {
                    commitSliderValue(sliderValue)
                },
                valueRange = 0f..100f,
                enabled = !brightnessAuto,
                height = 168.dp,
                accessibilityLabel = stringResource(R.string.brightness),
                accessibilityValue = brightnessValueDescription,
            )
            ReadMenuGlassIconButton(
                onClick = onTogglePosition,
                icon = Icons.Filled.SwapHoriz,
                colors = colors,
                backdrop = backdrop,
                menuConfig = menuConfig,
                glassEnabled = buttonGlassEnabled,
                contentDescription = stringResource(R.string.brightness_bar_position),
            )
        }
    } else {
        val buttonSize = if (buttonGlassEnabled) 48.dp else 40.dp
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReadMenuGlassIconButton(
                onClick = ::toggleAuto,
                icon = Icons.Filled.BrightnessAuto,
                colors = colors,
                backdrop = backdrop,
                menuConfig = menuConfig,
                glassEnabled = buttonGlassEnabled,
                selected = brightnessAuto,
                contentDescription = stringResource(R.string.brightness_follow_system),
            )
            ReadMenuSlider(
                value = sliderValue.coerceIn(0f, 100f),
                onValueChange = { value ->
                    if (brightnessAuto) return@ReadMenuSlider
                    updateSliderValue(value)
                },
                onValueChangeFinished = {
                    commitSliderValue(sliderValue)
                },
                onValueCommit = ::commitSliderValue,
                valueRange = 0f..100f,
                enabled = !brightnessAuto,
                backdrop = backdrop,
                glassThumbEnabled = glassThumbEnabled,
                accessibilityLabel = stringResource(R.string.brightness),
                accessibilityValue = brightnessValueDescription,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Spacer(Modifier.width(buttonSize))
        }
    }
}
