package io.legado.app.ui.book.read

import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.feature.readaloud.overlay.CapsuleDefaultCenterFromBottomDp
import io.legado.app.feature.readaloud.overlay.capsuleDockSide
import io.legado.app.feature.readaloud.overlay.capsulePresentationOffsetX
import io.legado.app.feature.readaloud.overlay.snapCapsuleOffsetX
import io.legado.app.ui.book.readaloud.morph.CapsuleAnchorKind
import io.legado.app.ui.book.readaloud.morph.LocalReadAloudMorph
import io.legado.app.ui.book.readaloud.morph.computeCapsuleCoverAlpha
import io.legado.app.ui.book.readaloud.morph.computeMorphPanelAlpha
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

/**
 * 朗读悬浮胶囊。
 *
 * 由宿主 Activity 叠加在所有导航之上（见 `ReadAloudShellHost`），因此只接受展示所需的
 * 原始值，不依赖阅读器 ViewModel；应用内外共用相对屏幕底部中央的中心坐标。
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
    onPositionChanged: (xDp: Float, yDp: Float) -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onOpenPlayer: () -> Unit,
    /** 系统窗口由平台宿主管理位置，组件只报告拖拽，不创建 WindowManager。 */
    onWindowPositionChanged: ((xDp: Float, yDp: Float, expansion: Float) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    val configuration = LocalConfiguration.current
    val screenSize = remember(hostView, configuration) {
        val manager = hostView.context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = manager.maximumWindowMetrics.bounds
            IntSize(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRealMetrics(metrics)
            IntSize(metrics.widthPixels, metrics.heightPixels)
        }
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var containerScreenOffset by remember { mutableStateOf(Offset.Zero) }
    var capsuleSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val morph = LocalReadAloudMorph.current
    SideEffect { morph?.reportProgressRing(null) }
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
    // 胶囊脸色要上报给形变宿主：面板从胶囊长出来时，进度 0 必须是同一个颜色。
    val capsuleFaceColor = capsuleSurfaceColor()

    val coverRotation = remember { Animatable(0f) }
    LaunchedEffect(isPaused) {
        while (!isPaused && isActive) {
            coverRotation.animateTo(
                targetValue = coverRotation.value + 360f,
                animationSpec = tween(durationMillis = 20_000, easing = LinearEasing),
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

    val morphActive by remember(morph) {
        derivedStateOf { (morph?.progress?.value ?: 0f) > 0f }
    }
    val screenWidthDp = screenSize.width / density.density
    var collapsed by remember { mutableStateOf(capsuleDockSide(offsetXDp, screenWidthDp) != 0) }
    var dragging by remember { mutableStateOf(false) }
    var touchTimestamp by remember { mutableIntStateOf(0) }
    val settledOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (dragging) snap() else tween(220),
        label = "capsuleEdgeSnap",
    )
    // 封面始终是同一个节点；宽度、按钮揭示和贴边位置共用一个连续进度。
    val expansion by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "capsuleExpansion",
    )
    val currentWindowPositionChanged by rememberUpdatedState(onWindowPositionChanged)
    LaunchedEffect(settledOffsetX, offsetY, density, expansion) {
        currentWindowPositionChanged?.invoke(
            settledOffsetX / density.density,
            offsetY / density.density,
            expansion
        )
    }

    // Auto-collapse timer
    LaunchedEffect(autoCollapse, collapsed, morphActive, dragging, touchTimestamp) {
        if (autoCollapse && !collapsed && !morphActive && !dragging) {
            delay(3000.milliseconds)
            collapsed = true
        }
    }

    // Reset collapse on any touch
    fun onTouched() {
        touchTimestamp++
    }

    val cornerRadius = (24f + 4f * expansion).dp

    Box(
        modifier = if (onWindowPositionChanged == null) Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                containerSize = it.size
                containerScreenOffset = it.positionOnScreen()
            } else Modifier
            .wrapContentSize()
            .padding(16.dp),
    ) {
        Surface(
            modifier = Modifier
                .align(if (onWindowPositionChanged == null) Alignment.BottomCenter else Alignment.TopStart)
                .then(if (onWindowPositionChanged == null) Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        // 实际测量高度参与定位，折叠、字体缩放不改变屏幕中心。
                        placeable.place(
                            (screenSize.width / 2f - containerScreenOffset.x - containerSize.width / 2f +
                                    capsulePresentationOffsetX(
                                        settledOffsetX / density.density, screenWidthDp,
                                        placeable.width / density.density + 32f, expansion
                                    ) * density.density).toInt(),
                            (screenSize.height - containerScreenOffset.y - containerSize.height -
                                    CapsuleDefaultCenterFromBottomDp * density.density + placeable.height / 2f + offsetY).toInt(),
                        )
                    }
                } else Modifier)
                // 定位必须在透明度图层之外：alpha < 1 会建立离屏缓冲，
                // 若在图层内部移动内容，背景会被原位置的小缓冲区裁掉。
                .graphicsLayer { alpha = 1f - computeMorphPanelAlpha(morph?.progress?.value ?: 0f) }
                .onGloballyPositioned { coordinates ->
                    capsuleSize = coordinates.size
                    morph?.reportPanelStart(
                        bounds = coordinates.boundsInRoot(),
                        anchorKind = CapsuleAnchorKind.Global,
                        cornerRadiusPx = with(density) { cornerRadius.toPx() },
                        surfaceColor = capsuleFaceColor,
                    )
                }
                .pointerInput(density, screenSize) {
                    detectDragGestures(
                        onDragStart = {
                            offsetX = capsulePresentationOffsetX(
                                settledOffsetX / density.density, screenWidthDp,
                                capsuleSize.width / density.density + 32f, expansion
                            ) * density.density
                            dragging = true
                            onTouched()
                        },
                        onDragCancel = { dragging = false; onTouched() },
                        onDragEnd = {
                            offsetX = snapCapsuleOffsetX(
                                offsetX / density.density,
                                screenWidthDp
                            ) * density.density
                            if (capsuleDockSide(
                                    offsetX / density.density,
                                    screenWidthDp
                                ) != 0
                            ) collapsed = true
                            dragging = false
                            onTouched()
                            currentOnPositionChanged(
                                offsetX / density.density,
                                offsetY / density.density
                            )
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        // 中心允许拖到屏幕边线，松手后才吸附；不会在拖动迷你封面时展开。
                        val halfWidth = screenSize.width / 2f
                        val halfHeight = capsuleSize.height / 2f + 16f * density.density
                        val base = CapsuleDefaultCenterFromBottomDp * density.density
                        offsetX = (offsetX + dragAmount.x).coerceIn(-halfWidth, halfWidth)
                        offsetY = (offsetY + dragAmount.y).coerceIn(
                            (base - screenSize.height + halfHeight).coerceAtMost(base - halfHeight),
                            base - halfHeight,
                        )
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
            color = capsuleFaceColor.copy(alpha = expansion),
            contentColor = capsuleContentColor(),
            tonalElevation = 0.dp,
            // 形变期间不留原位投影，完全收回后才恢复。
            shadowElevation = 12.dp * shadowStrength * expansion,
        ) {
            CapsuleContent(
                bookName = bookName,
                author = author,
                coverPath = coverPath,
                sourceOrigin = sourceOrigin,
                isPaused = isPaused,
                progress = progress,
                coverRotation = coverRotation,
                expansion = expansion,
                onTogglePause = { onTouched(); onTogglePause() },
                onStop = onStop,
                onCoverClick = {
                    onTouched()
                    if (collapsed || expansion < 1f) collapsed = false else onOpenPlayer()
                },
            )
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
private fun CapsuleContent(
    bookName: String?,
    author: String?,
    coverPath: String?,
    sourceOrigin: String?,
    isPaused: Boolean,
    progress: Float,
    coverRotation: Animatable<Float, AnimationVector1D>,
    expansion: Float,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onCoverClick: () -> Unit,
) {
    val coverSize = (48f - 8f * expansion).dp
    val expandDescription = stringResource(R.string.expand)
    Row(
        modifier = Modifier.padding(all = (4f * expansion).dp),
        horizontalArrangement = Arrangement.spacedBy((4f * expansion).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = capsuleContentColor()
        val mutedColor = capsuleMutedColor()
        val coverMorph = LocalReadAloudMorph.current
        val coverDensity = LocalDensity.current
        // 旋转不改变布局；角度不能只依赖 onGloballyPositioned 更新。
        LaunchedEffect(coverMorph, coverRotation) {
            val target = coverMorph ?: return@LaunchedEffect
            snapshotFlow { coverRotation.value }.collect { target.reportCoverRotation(it) }
        }
        BookCoverImage(
            name = bookName,
            author = author,
            path = coverPath,
            sourceOrigin = sourceOrigin,
            // 听书胶囊也是书维度场景，本地优先不跑书源脚本
            bookUrl = null,
            preferCache = true,
            showLoadingPlaceholder = false,
            requestBuilder = { size(coil3.size.Size(1024, 1024)) },
            modifier = Modifier
                .size(coverSize)
                // 锚点要在旋转之前上报，否则旋转后的包围盒会让形变起点跳动。
                .onGloballyPositioned { coordinates ->
                    coverMorph?.reportCoverStart(
                        bounds = coordinates.boundsInRoot(),
                        anchorKind = CapsuleAnchorKind.Global,
                        cornerRadiusPx = with(coverDensity) { coverSize.toPx() / 2f },
                        rotationDeg = coverRotation.value,
                    )
                }
                .graphicsLayer {
                    rotationZ = coverRotation.value
                    alpha = computeCapsuleCoverAlpha(coverMorph?.progress?.value ?: 0f)
                }
                .clip(CircleShape)
                .then(if (expansion < 1f) Modifier.semantics {
                    contentDescription = expandDescription
                } else Modifier)
                .clickable(onClick = onCoverClick),
        )
        // 按钮始终以完整尺寸测量，仅连续揭示宽度；不切换两套封面或压缩按钮。
        Box(
            Modifier
                .width((84f * expansion).dp)
                .height(40.dp)
                .clipToBounds()
                .graphicsLayer { alpha = expansion },
        ) {
            Row(
                Modifier
                    .wrapContentSize(Alignment.CenterStart, unbounded = true)
                    .width(84.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        // 黑白配色下用弱化的中性底，不再用主题的 secondaryContainer
                        .background(mutedColor.copy(alpha = 0.1f))
                        .clickable(enabled = expansion >= 1f, onClick = onTogglePause),
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
                            .clickable(enabled = expansion >= 1f, onClick = onStop),
                        tint = contentColor,
                    )
                }
            }
        }
    }
}
