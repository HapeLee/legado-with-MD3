package io.legado.app.ui.book.readaloud

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.widget.components.text.AppText
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.PlaybackCapsuleGateway
import io.legado.app.domain.model.PlaybackCapsuleState
import io.legado.app.ui.book.readaloud.morph.CapsuleProgressRing
import io.legado.app.ui.book.readaloud.morph.CapsuleAnchorKind
import io.legado.app.ui.book.readaloud.morph.LocalReadAloudMorph
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import org.koin.compose.koinInject

/** 与主导航悬浮底栏同高，并且是正圆。 */
val ReadAloudBarCapsuleSize = 64.dp

/** 与悬浮底栏一致的外边距。 */
val ReadAloudBarCapsuleEndPadding = 16.dp

private val CapsuleCoverSize = 56.dp
private val CapsuleRingWidth = 2.dp

/**
 * 主页悬浮底栏用的听书胶囊：与悬浮底栏同高的**圆形封面按钮**。
 *
 * 短按打开播放页；长按与主页导航联动展开章节、进度和播放控制。
 * 液态玻璃与悬浮底栏共用同一套 `Backdrop`，所以底栏开玻璃时它也跟着开。
 *
 * 它同时是「胶囊 → 播放页」形变的起点：把自身矩形与封面矩形上报给
 * [ReadAloudMorphState]，收起时封面回到这里。
 */
@Composable
fun ReadAloudBarCapsule(
    bookName: String?,
    author: String?,
    coverPath: String?,
    sourceOrigin: String?,
    progress: Float,
    onOpenPlayer: () -> Unit,
    onLongPress: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    onStop: () -> Unit = {},
    chapterTitle: String = "",
    chapterIndex: Int = -1,
    isPaused: Boolean = true,
    expansion: Float = 0f,
    controlsExpanded: Boolean = false,
    width: Dp = ReadAloudBarCapsuleSize,
    expandedWidth: Dp = ReadAloudBarCapsuleSize,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    isBlurEnabled: Boolean = false,
) {
    val morph = LocalReadAloudMorph.current
    val currentMorph by rememberUpdatedState(morph)
    val showCapsuleShadow by remember(morph) {
        derivedStateOf { (morph?.progress?.value ?: 0f) <= 0f }
    }
    // 开始形变立即关闭；完全收回后只恢复阴影强度，不改变胶囊几何。
    val restoredShadow by animateFloatAsState(
        targetValue = if (showCapsuleShadow) 1f else 0f,
        animationSpec = if (showCapsuleShadow) tween(180) else snap(),
        label = "capsuleShadowRestore",
    )
    val shadowStrength = if (showCapsuleShadow) restoredShadow else 0f
    // 展开动画进行中胶囊已被淡出，不能再抢点击。
    val idle by remember(morph) { derivedStateOf { (morph?.progress?.value ?: 0f) <= 0.001f } }
    val themeSettings = LocalAppUiConfiguration.current.theme
    val isDark = LegadoTheme.isDark
    val ringColor = LegadoTheme.colorScheme.primary
    val ringDensity = LocalDensity.current
    SideEffect {
        morph?.reportProgressRing(
            CapsuleProgressRing(
                progress,
                ringColor,
                with(ringDensity) { CapsuleRingWidth.toPx() })
        )
    }
    val containerColor = if (isBlurEnabled) {
        LegadoTheme.colorScheme.surfaceContainer.copy(
            alpha = themeSettings.bottomBarBlurAlpha / 100f
        )
    } else {
        LegadoTheme.colorScheme.surfaceContainer
    }
    val glass = isBlurEnabled &&
            backdrop != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val surfaceModifier = if (glass) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { ContinuousCapsule },
            effects = {
                vibrancy()
                blur(themeSettings.bottomBarBlurRadius.toFloat().dp.toPx())
                lens(
                    themeSettings.bottomBarLensRadius.dp.toPx(),
                    themeSettings.bottomBarLensRadius.dp.toPx(),
                )
            },
            highlight = { Highlight.Default },
            shadow = if (showCapsuleShadow) {
                { Shadow.Default.copy(color = Color.Black.copy(alpha = (if (isDark) 0.2f else 0.1f) * shadowStrength)) }
            } else null,
            onDrawSurface = { drawRect(containerColor) },
        )
    } else {
        Modifier
            .shadow(
                elevation = 12.dp * shadowStrength,
                shape = ContinuousCapsule,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .clip(ContinuousCapsule)
            .background(containerColor)
    }

    Box(
        modifier = modifier
            .width(width)
            .height(ReadAloudBarCapsuleSize)
            .graphicsLayer { alpha = 1f - (morph?.progress?.value ?: 0f) }
            .then(surfaceModifier)
            .clip(ContinuousCapsule)
            .onGloballyPositioned { coordinates ->
                val state = currentMorph ?: return@onGloballyPositioned
                state.reportPanelStart(
                    bounds = coordinates.boundsInRoot(),
                    anchorKind = CapsuleAnchorKind.HomeBar,
                    cornerRadiusPx = coordinates.size.height / 2f,
                    // 底栏式胶囊的脸色（玻璃开启时带透明度）：面板起点要与它一致。
                    surfaceColor = containerColor,
                )
            }
            .combinedClickable(
                enabled = idle,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenPlayer,
                onLongClick = onLongPress,
                onLongClickLabel = stringResource(
                    if (controlsExpanded) R.string.home_capsule_restore_navigation
                    else R.string.home_capsule_expand_controls
                ),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(CapsuleCoverSize)
        ) {
            BookCoverImage(
                name = bookName,
                author = author,
                path = coverPath,
                sourceOrigin = sourceOrigin,
                // 听书胶囊是书维度场景，本地优先不跑书源脚本
                bookUrl = null,
                preferCache = true,
                showLoadingPlaceholder = morph == null,
                requestBuilder = { if (morph != null) size(coil3.size.Size(1024, 1024)) },
                modifier = Modifier
                    .fillMaxSize()
                    // 锚点要在形变之前上报真实矩形。
                    .onGloballyPositioned { coordinates ->
                        val state = currentMorph ?: return@onGloballyPositioned
                        state.reportCoverStart(
                            bounds = coordinates.boundsInRoot(),
                            anchorKind = CapsuleAnchorKind.HomeBar,
                            cornerRadiusPx = coordinates.size.height / 2f,
                        )
                    }
                    .clip(CircleShape)
                    .graphicsLayer {
                        // 圆形端也只在完全收回后绘制，避免飞行封面与源封面叠影。
                        alpha = if ((morph?.progress?.value ?: 0f) <= 0f) 1f else 0f
                    },
            )
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = if ((morph?.progress?.value ?: 0f) <= 0f) 1f else 0f
                        }
                        .drawBehind {
                            val stroke = CapsuleRingWidth.toPx()
                            val inset = stroke / 2f
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        },
                )
            }
        }
        val detailsAlpha = ((expansion - 0.2f) / 0.8f).coerceIn(0f, 1f)
        val controlsEnabled = idle && expansion >= 0.99f
        Row(
            modifier = Modifier
                .padding(start = ReadAloudBarCapsuleSize)
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .requiredWidth((expandedWidth - ReadAloudBarCapsuleSize).coerceAtLeast(0.dp))
                .fillMaxHeight()
                .graphicsLayer { alpha = detailsAlpha }
                .then(if (!controlsEnabled) Modifier.clearAndSetSemantics {} else Modifier)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 5.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppText(
                    text = bookName.orEmpty(),
                    modifier = Modifier.basicMarquee(
                        iterations = 3,
                        repeatDelayMillis = 3000,
                        initialDelayMillis = 3000,
                        velocity = 16.dp
                    ),
                    style = LegadoTheme.typography.labelMediumEmphasized,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val chapter = chapterTitle.ifBlank {
                    if (chapterIndex >= 0) stringResource(
                        R.string.home_capsule_chapter,
                        chapterIndex + 1
                    )
                    else stringResource(R.string.no_chapter)
                }
                AppText(
                    text = chapter,
                    modifier = Modifier.basicMarquee(
                        iterations = 3,
                        repeatDelayMillis = 3000,
                        initialDelayMillis = 3000,
                        velocity = 16.dp
                    ),
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp),
                        color = ringColor,
                        trackColor = LegadoTheme.colorScheme.surfaceContainerHighest,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
            }
            MediumPlainButton(
                onClick = onTogglePause,
                enabled = controlsEnabled,
                icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(if (isPaused) R.string.resume else R.string.pause)
            )
            MediumPlainButton(
                onClick = onStop,
                enabled = controlsEnabled,
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.exit)
            )
        }
    }
}

/**
 * 胶囊的自包含入口：自己取朗读会话与设置判断显隐，隐藏时占位为零。
 *
 * 隐藏时保持零尺寸是刻意的：宿主把它和悬浮底栏放在同一行时，
 * 底栏能按剩余宽度自动让位（见 `MainScreen` 的悬浮底栏分支）。
 */
@Composable
fun ReadAloudBarCapsuleSlot(
    enabled: Boolean,
    onOpenPlayer: (PlaybackCapsuleState) -> Unit,
    morph: ReadAloudMorphState?,
    anchorPreview: PlaybackCapsuleState? = null,
    controlsExpanded: Boolean = false,
    expansion: Float = 0f,
    width: Dp = ReadAloudBarCapsuleSize,
    expandedWidth: Dp = ReadAloudBarCapsuleSize,
    onControlsExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    isBlurEnabled: Boolean = false,
    settingsGateway: ReadAloudSettingsGateway = koinInject(),
    playbackGateway: PlaybackCapsuleGateway = koinInject(),
    /** 只上报胶囊内容宽度；退场完成或宿主移除时归零，不包含宿主外边距。 */
    onOccupiedWidthChanged: (Int) -> Unit = {},
) {
    val currentOnOccupiedWidthChanged by rememberUpdatedState(onOccupiedWidthChanged)
    val settings by settingsGateway.settings.collectAsStateWithLifecycle(
        settingsGateway.currentSettings
    )
    val playerState by playbackGateway.state.collectAsStateWithLifecycle()

    val previewOnly = playerState.source == null
    val displayState = if (previewOnly) anchorPreview ?: playerState else playerState
    val visible = enabled && settings.showReadAloudCapsule &&
            (playerState.source != null || anchorPreview != null)
    LaunchedEffect(visible, previewOnly, expandedWidth, playerState.source, playerState.bookUrl) {
        if (!visible || previewOnly || expandedWidth < 160.dp) onControlsExpandedChange(false)
    }
    // 不可见时不提供锚点：退场动画期间胶囊仍在组合、仍会上报矩形，
    // 不掐断就会把「已经离开的胶囊」的位置和脸色写成下一次形变的起点。
    CompositionLocalProvider(LocalReadAloudMorph provides morph?.takeIf { visible }) {
        AnimatedVisibility(
            visible = visible,
            modifier = if (previewOnly) modifier
                .graphicsLayer { alpha = 0f }
                .clearAndSetSemantics { } else modifier,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            // AnimatedVisibility 退场后会移除内容，不保证再给宿主一次零尺寸布局回调。
            // 让占位随内容生命周期释放，淡出期间仍为可见胶囊保留空间。
            DisposableEffect(Unit) {
                onDispose { currentOnOccupiedWidthChanged(0) }
            }
            ReadAloudBarCapsule(
                bookName = displayState.bookName,
                author = displayState.author,
                coverPath = displayState.coverPath,
                sourceOrigin = displayState.sourceOrigin,
                progress = displayState.progress,
                onOpenPlayer = { if (!previewOnly) onOpenPlayer(playerState) },
                onLongPress = {
                    if (!previewOnly && (controlsExpanded || expandedWidth >= 160.dp)) {
                        onControlsExpandedChange(!controlsExpanded)
                    }
                },
                onTogglePause = { playerState.source?.let(playbackGateway::togglePause) },
                onStop = {
                    onControlsExpandedChange(false)
                    playerState.source?.let(playbackGateway::stop)
                },
                chapterTitle = displayState.chapterTitle,
                chapterIndex = displayState.chapterIndex,
                isPaused = displayState.isPaused,
                expansion = expansion,
                controlsExpanded = controlsExpanded,
                width = width,
                expandedWidth = expandedWidth,
                modifier = Modifier.onSizeChanged { currentOnOccupiedWidthChanged(it.width) },
                backdrop = backdrop,
                isBlurEnabled = isBlurEnabled,
            )
        }
    }
}
