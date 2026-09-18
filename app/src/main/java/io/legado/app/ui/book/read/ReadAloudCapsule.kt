package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.VerticalDivider
import kotlin.time.Duration.Companion.milliseconds

/**
 * 朗读悬浮胶囊。
 *
 * 由宿主 Activity 叠加在所有导航之上（见 `ReadAloudShellHost`），因此只接受展示所需的
 * 原始值，不依赖阅读器 ViewModel；上下边距由宿主按所在界面传入。
 */
@Composable
fun ReadAloudCapsule(
    bookName: String?,
    author: String?,
    coverPath: String?,
    sourceOrigin: String?,
    isPaused: Boolean,
    offsetXDp: Float,
    offsetYDp: Float,
    progress: Float,
    autoCollapse: Boolean,
    bottomPadding: Dp,
    onPositionChanged: (xDp: Float, yDp: Float) -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val density = LocalDensity.current
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)

    val coverRotation = remember { Animatable(0f) }
    LaunchedEffect(isPaused) {
        while (!isPaused && isActive) {
            coverRotation.animateTo(
                targetValue = coverRotation.value + 360f,
                animationSpec = tween(durationMillis = 12_000, easing = LinearEasing),
            )
        }
    }

    var offsetX by remember {
        mutableFloatStateOf(with(density) { Dp(offsetXDp).toPx() })
    }
    var offsetY by remember {
        mutableFloatStateOf(with(density) { Dp(offsetYDp).toPx() })
    }
    LaunchedEffect(offsetXDp, offsetYDp, density) {
        offsetX = with(density) { Dp(offsetXDp).toPx() }
        offsetY = with(density) { Dp(offsetYDp).toPx() }
    }

    var collapsed by remember { mutableStateOf(false) }
    var touchTimestamp by remember { mutableFloatStateOf(0f) }

    // Auto-collapse timer
    LaunchedEffect(autoCollapse, collapsed) {
        if (autoCollapse && !collapsed) {
            delay(3000.milliseconds)
            collapsed = true
        }
    }

    // Reset collapse on any touch
    fun onTouched() {
        touchTimestamp = System.currentTimeMillis().toFloat()
        if (collapsed) {
            collapsed = false
        }
    }

    val capsuleHeight by animateDpAsState(
        targetValue = if (collapsed) 16.dp else 48.dp,
        animationSpec = tween(durationMillis = 250),
        label = "capsuleHeight",
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (collapsed) 8.dp else 28.dp,
        animationSpec = tween(durationMillis = 250),
        label = "cornerRadius",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = bottomPadding)
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .pointerInput(density) {
                    detectDragGestures(
                        onDragEnd = {
                            currentOnPositionChanged(
                                offsetX / density.density,
                                offsetY / density.density,
                            )
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        onTouched()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTouched() },
                    )
                },
            shape = RoundedCornerShape(cornerRadius),
            // 胶囊走极简日夜配色：日间纯白 + 深色内容，夜间纯黑 + 浅色内容。
            // 不跟随主题取色，避免在阅读页/听书页的背景之上忽明忽暗。
            color = capsuleSurfaceColor(),
            contentColor = capsuleContentColor(),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            AnimatedContent(
                targetState = collapsed,
                transitionSpec = {
                    fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.9f) togetherWith
                            fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f)
                },
                label = "capsuleContent",
            ) { isCollapsed ->
                if (isCollapsed) {
                    CollapsedCapsuleContent(
                        isPaused = isPaused,
                        onTogglePause = onTogglePause,
                        onExpand = { collapsed = false },
                    )
                } else {
                    ExpandedCapsuleContent(
                        bookName = bookName,
                        author = author,
                        coverPath = coverPath,
                        sourceOrigin = sourceOrigin,
                        isPaused = isPaused,
                        progress = progress,
                        coverRotation = coverRotation.value,
                        onTogglePause = onTogglePause,
                        onStop = onStop,
                        onOpenPlayer = onOpenPlayer,
                    )
                }
            }
        }
    }
}

/**
 * 胶囊底色：日间纯白、夜间纯黑。
 *
 * 刻意不用 `LegadoTheme.colorScheme`——胶囊是叠在任意界面之上的独立浮层，
 * 跟随主题取色会在不同底色上忽明忽暗；黑白两色在任何背景上都读得清。
 */
@Composable
private fun capsuleSurfaceColor(): Color =
    if (LegadoTheme.isDark) Color.Black else Color.White

/** 胶囊内容色：与底色构成最高对比。 */
@Composable
private fun capsuleContentColor(): Color =
    if (LegadoTheme.isDark) Color.White else Color.Black

/** 胶囊上的次要信息（进度圈、分隔线等）用的弱化色。 */
@Composable
private fun capsuleMutedColor(): Color =
    if (LegadoTheme.isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)

@Composable
private fun CollapsedCapsuleContent(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onExpand: () -> Unit,
) {
    val contentColor = capsuleContentColor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onTogglePause)
                .padding(all = 8.dp),
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(
                    if (isPaused) R.string.resume_read_aloud else R.string.pause_read_aloud
                ),
                modifier = Modifier
                    .size(16.dp),
                tint = contentColor,
            )
            Text(
                text = stringResource(
                    if (isPaused) R.string.resume_read_aloud else R.string.pause_read_aloud
                ),
                style = LegadoTheme.typography.labelSmall,
                color = contentColor,
                modifier = Modifier.padding(start = 2.dp),
            )
        }

        VerticalDivider(
            modifier = Modifier
                .padding(start = 4.dp)
                .height(10.dp)
                .width(1.dp),
            color = capsuleMutedColor(),
        )

        Icon(
            imageVector = Icons.Default.ExpandLess,
            contentDescription = stringResource(R.string.expand),
            modifier = Modifier
                .clickable(onClick = onExpand)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .size(16.dp),
            tint = contentColor,
        )
    }
}

@Composable
private fun ExpandedCapsuleContent(
    bookName: String?,
    author: String?,
    coverPath: String?,
    sourceOrigin: String?,
    isPaused: Boolean,
    progress: Float,
    coverRotation: Float,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(all = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = capsuleContentColor()
        val mutedColor = capsuleMutedColor()
        BookCoverImage(
            name = bookName,
            author = author,
            path = coverPath,
            sourceOrigin = sourceOrigin,
            // 听书胶囊也是书维度场景，本地优先不跑书源脚本
            bookUrl = null,
            preferCache = true,
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { rotationZ = coverRotation }
                .clip(CircleShape)
                .clickable(onClick = onOpenPlayer),
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                // 黑白配色下用弱化的中性底，不再用主题的 secondaryContainer
                .background(mutedColor.copy(alpha = 0.1f))
                .clickable(onClick = onTogglePause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(
                    if (isPaused) R.string.resume_read_aloud else R.string.pause_read_aloud
                ),
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                progress = { progress.coerceIn(0f, 1f) },
                strokeWidth = 2.dp,
                color = mutedColor,
                trackColor = mutedColor.copy(alpha = 0.25f),
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.stop_read_aloud),
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onStop),
                tint = contentColor,
            )
        }
    }
}
