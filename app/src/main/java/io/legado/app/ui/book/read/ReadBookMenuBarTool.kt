package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import io.legado.app.ui.animation.DampedDragAnimation
import io.legado.app.ui.book.read.sheet.ReadMenuButtonInfo
import io.legado.app.ui.book.read.sheet.readMenuButtonInfos
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.reader.ReaderMenuSlider as BaseReaderMenuSlider
import kotlin.math.roundToInt

@Composable
internal fun ReadMenuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    backdrop: Backdrop?,
    glassThumbEnabled: Boolean,
    accessibilityLabel: String? = null,
    accessibilityValue: String? = null,
) {
    if (glassThumbEnabled && backdrop != null) {
        ReadMenuLiquidSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            visibilityThreshold = 0.001f,
            backdrop = backdrop,
            modifier = modifier,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            onValueCommit = onValueCommit,
            accessibilityLabel = accessibilityLabel,
            accessibilityValue = accessibilityValue,
        )
        return
    }

    val commitAction = onValueChangeFinished ?: onValueCommit?.let { commit -> { commit(value) } }

    BaseReaderMenuSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.padding(horizontal = 5.dp),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = commitAction,
        accessibilityLabel = accessibilityLabel,
        accessibilityValue = accessibilityValue,
    )
}

@Composable
private fun ReadMenuLiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    accessibilityLabel: String? = null,
    accessibilityValue: String? = null,
) {
    val accentColor = LegadoTheme.colorScheme.secondary
    val trackColor = LegadoTheme.colorScheme.surfaceContainerLow
    val thumbColor =
        Color.White.copy(alpha = 0.9f).compositeOver(LegadoTheme.colorScheme.surfaceContainerLow)
    val enabledAlpha = if (enabled) 1f else 0.38f

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .semantics {
                accessibilityLabel?.let { contentDescription = it }
                accessibilityValue?.let { stateDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange),
                    range = valueRange,
                    steps = steps,
                )
                if (!enabled) {
                    disabled()
                }
                setProgress { target ->
                    if (!enabled) {
                        false
                    } else {
                        val nextValue = target.coerceIn(valueRange)
                        onValueChange(nextValue)
                        onValueCommit?.invoke(nextValue) ?: onValueChangeFinished?.invoke()
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth
        val rangeStart = valueRange.start
        val rangeEnd = valueRange.endInclusive
        val range = rangeEnd - rangeStart
        val animationScope = rememberCoroutineScope()
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val dampedDragAnimation =
            remember(animationScope, trackWidth, rangeStart, rangeEnd, isLtr) {
                DampedDragAnimation(
                    animationScope = animationScope,
                    initialValue = value,
                    valueRange = valueRange,
                    visibilityThreshold = visibilityThreshold,
                    initialScale = 1f,
                    pressedScale = 1.5f,
                    onDragStarted = {},
                    onDragStopped = {
                        onValueChange(targetValue)
                        onValueCommit?.invoke(targetValue) ?: onValueChangeFinished?.invoke()
                    },
                    onDrag = { _, dragAmount ->
                        val delta = range * (dragAmount.x / trackWidth)
                        val nextValue = if (isLtr) {
                            (targetValue + delta).coerceIn(valueRange)
                        } else {
                            (targetValue - delta).coerceIn(valueRange)
                        }
                        updateValue(nextValue)
                        onValueChange(nextValue)
                    },
                )
            }

        LaunchedEffect(dampedDragAnimation, value) {
            if (dampedDragAnimation.targetValue != value) {
                dampedDragAnimation.updateValue(value)
            }
        }

        val progress = if (range == 0f) {
            0f
        } else {
            ((dampedDragAnimation.value - rangeStart) / range).coerceIn(0f, 1f)
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ContinuousCapsule },
                        effects = {},
                        highlight = null,
                        shadow = {
                            Shadow(
                                radius = 8.dp,
                                color = Color.Black.copy(alpha = 0.12f),
                            )
                        },
                        innerShadow = null,
                        onDrawSurface = {
                            drawRect(trackColor.copy(alpha = enabledAlpha))
                        },
                    )
                    .pointerInput(enabled, animationScope, isLtr, trackWidth) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { position ->
                            val delta = range * (position.x / trackWidth)
                            val targetValue =
                                (if (isLtr) rangeStart + delta else rangeEnd - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetValue)
                            onValueChange(targetValue)
                            onValueCommit?.invoke(targetValue) ?: onValueChangeFinished?.invoke()
                        }
                    }
                    .height(6f.dp)
                    .fillMaxWidth(),
            )
            Box(
                Modifier
                    .clip(ContinuousCapsule)
                    .background(accentColor.copy(alpha = enabledAlpha))
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * progress).roundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    },
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    alpha = enabledAlpha
                    translationX =
                        (-size.width / 2f + trackWidth * progress)
                            .coerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                                if (isLtr) 1f else -1f
                }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val pressProgress = dampedDragAnimation.pressProgress
                            val scaleX = 2f / 3f + (1f / 3f) * pressProgress
                            scale(scaleX, pressProgress) {
                                drawBackdrop()
                            }
                        },
                    ),
                    shape = { ContinuousCapsule },
                    effects = {
                        val pressProgress = dampedDragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - pressProgress))
                        lens(
                            10.dp.toPx() * pressProgress,
                            14.dp.toPx() * pressProgress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = dampedDragAnimation.pressProgress,
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 8.dp,
                            color = Color.Black.copy(alpha = 0.12f),
                        )
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 4.dp * dampedDragAnimation.pressProgress,
                            alpha = dampedDragAnimation.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val pressProgress = dampedDragAnimation.pressProgress
                        drawRect(thumbColor.copy(alpha = 1f - pressProgress))
                    },
                )
                .size(40f.dp, 24f.dp),
        )
    }
}

@Composable
internal fun ToolButtonItem(
    button: ToolButtonDef,
    state: ReadBookUiState,
    colors: ReadMenuColors,
    backdrop: Backdrop?,
    glassEnabled: Boolean,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val badgeCount = when (button.id) {
        "replace_badge" -> state.effectiveReplaceCount
        else -> 0
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ReadMenuGlassButtonSurface(
            onClick = button.onClick,
            colors = colors,
            backdrop = backdrop,
            menuConfig = state.menuConfig,
            glassEnabled = glassEnabled,
            selected = button.isActive,
            onLongClick = button.onLongClick,
            contentDescription = button.description,
        ) { tint ->
            ToolButtonContent(
                button = button,
                tint = tint,
                badgeCount = badgeCount,
            )
        }
        if (state.menuConfig.readMenuIconShowText) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = button.description,
                style = LegadoTheme.typography.labelSmall.copy(
                    shadow = menuTextShadow
                ),
                color = labelColor,
                maxLines = 1,
                modifier = Modifier.wrapContentWidth(
                    align = Alignment.CenterHorizontally,
                    unbounded = true,
                ),
            )
        }
    }
}

@Composable
private fun ToolButtonContent(
    button: ToolButtonDef,
    tint: Color,
    badgeCount: Int,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (button.customIconPath.isNullOrBlank()) {
            Icon(
                imageVector = button.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        } else {
            AsyncImage(
                model = button.customIconPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        }
        if (badgeCount > 0) {
            Text(
                text = badgeCount.toString(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        LegadoTheme.colorScheme.error,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onError,
            )
        }
    }
}

internal data class ToolButtonDef(
    val id: String,
    val icon: ImageVector,
    val description: String,
    val customIconPath: String?,
    val isActive: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

internal fun loadToolButtons(
    context: Context,
    state: ReadBookUiState,
    eyeProtectionEnabled: Boolean,
    onIntent: (ReadBookIntent) -> Unit,
): List<ToolButtonDef> {
    val customIcons = state.menuConfig.readMenuCustomIcons
    fun ReadMenuButtonInfo.toButton(
        isActive: Boolean = false,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): ToolButtonDef {
        return ToolButtonDef(id, icon, label, customIcons[id], isActive, onClick, onLongClick)
    }

    val infoMap = readMenuButtonInfos(context).associateBy { it.id }
    val allButtons = listOf(
        infoMap.getValue("search").toButton {
            onIntent(ReadBookIntent.OpenSearch(null))
        },
        infoMap.getValue("catalog").toButton {
            onIntent(ReadBookIntent.OpenChapterList)
        },
        infoMap.getValue("read_aloud").toButton(
            isActive = state.isReadAloudRunning,
            onLongClick = {
                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadAloud))
            },
        ) {
            if (state.isReadAloudRunning) {
                onIntent(ReadBookIntent.ReadAloudAction)
            } else {
                onIntent(ReadBookIntent.ToggleReadAloud)
                onIntent(ReadBookIntent.HideMenu)
            }
        },
        infoMap.getValue("setting").toButton {
            onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadStyle))
        },
        infoMap.getValue("addBookmark").toButton {
            onIntent(ReadBookIntent.AddBookmark)
        },
        infoMap.getValue("theme").toButton {
            onIntent(ReadBookIntent.ToggleDayNight)
        },
        infoMap.getValue("eye_protection").toButton(
            isActive = eyeProtectionEnabled,
            onLongClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.EyeProtection)) },
        ) {
            onIntent(ReadBookIntent.ToggleEyeProtection)
        },
        infoMap.getValue("prev_chapter").toButton {
            onIntent(ReadBookIntent.PrevChapter)
        },
        infoMap.getValue("next_chapter").toButton {
            onIntent(ReadBookIntent.NextChapter)
        },
        infoMap.getValue("replace").toButton(
        ) {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TextProcessing))
        },
        infoMap.getValue("replace_badge").toButton {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TextProcessing))
        },
        infoMap.getValue("auto_page").toButton(isActive = state.isAutoPage) {
            if (state.isAutoPage) {
                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.AutoRead))
            } else {
                onIntent(ReadBookIntent.ToggleAutoPage)
                onIntent(ReadBookIntent.HideMenu)
            }
        },
        infoMap.getValue("translate").toButton(isActive = state.translationMode) {
            onIntent(ReadBookIntent.ToggleTranslation)
        },
        infoMap.getValue("refresh_current").toButton {
            onIntent(ReadBookIntent.RefreshCurrentChapter)
        },
        infoMap.getValue("ai_summary").toButton {
            onIntent(ReadBookIntent.OpenChapterSummary)
        },
        infoMap.getValue("ai_rewrite").toButton {
            onIntent(ReadBookIntent.OpenAiCurrentChapterRewrite)
        },
        infoMap.getValue("more_actions").toButton {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.MoreActions))
        },
    )

    val allMap = allButtons.associateBy { it.id }
    return state.menuConfig.bottomBarButtons
        .asSequence()
        .filter { it.enabled }
        .mapNotNull { allMap[it.id] }
        .toList()
}
