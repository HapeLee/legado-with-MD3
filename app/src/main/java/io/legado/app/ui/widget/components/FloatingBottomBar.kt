// Portions of this file are derived from weishu/KernelSU
// (https://github.com/tiann/KernelSU)
// Copyright (C) KernelSU contributors
// Licensed under GPL-3.0
package io.legado.app.ui.widget.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.legado.app.R
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme
import com.itos.coui.effect.couiNavigationFeedback
import io.legado.app.domain.model.settings.customColors
import io.legado.app.ui.animation.DampedDragAnimation
import io.legado.app.ui.animation.InteractiveHighlight
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.miuixCapsuleGlass
import io.legado.app.ui.widget.components.coui.LocalCouiFloatingNavigation
import io.legado.app.ui.widget.components.coui.couiFloatingCapsule
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import ui.feice.mochi.effect.glass.MochiGlassSurface
import ui.feice.mochi.theme.MochiTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private val LocalFloatingBottomBarTabInteractive = staticCompositionLocalOf { true }

val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalFloatingBottomBarTabScale.current
    val interactive = enabled && LocalFloatingBottomBarTabInteractive.current
    val isCoui = LegadoTheme.composeEngine == ComposeEngine.Coui
    val interaction = if (isCoui && interactive) {
        val source = remember { MutableInteractionSource() }
        Modifier
            .couiNavigationFeedback(LegadoTheme.isDark, source)
            .selectable(selected, source, indication = null, role = Role.Tab, onClick = onClick)
    } else if (interactive) {
        Modifier.clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
    } else Modifier
    Column(
        modifier
            .clip(if (isCoui) CouiCapsuleShape else ContinuousCapsule)
            // 背景采样副本与隐藏的导航项不能保留触摸节点，alpha=0 不会取消命中。
            .then(interaction)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val currentScale = scale()
                scaleX = currentScale
                scaleY = currentScale
            },
        verticalArrangement = Arrangement.spacedBy(if (isCoui) 2.dp else 1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    onReselected: (index: Int) -> Unit = {},
    backdrop: Backdrop?,
    tabsCount: Int,
    isBlurEnabled: Boolean = true,
    hasCustomIcons: Boolean = false,
    compactProgress: Float = 0f,
    expandedWidth: Dp? = null,
    onRestoreNavigation: () -> Unit = {},
    content: @Composable RowScope.() -> Unit
) {
    val navigationAlpha = (1f - compactProgress * 3f).coerceIn(0f, 1f)
    val menuAlpha = ((compactProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val isInLightTheme = !LegadoTheme.isDark
    val themeSettings = LocalAppUiConfiguration.current.theme
    val engine = LegadoTheme.composeEngine
    val isCoui = engine == ComposeEngine.Coui
    val couiNavigation = LocalCouiFloatingNavigation.current
    val capsuleShape = if (isCoui) CouiCapsuleShape else ContinuousCapsule
    val horizontalPadding = if (isCoui) 12.dp else 4.dp
    val barHeight = if (isCoui) lerp(56f, 64f, compactProgress).dp else 64.dp
    val materialBackdrop = backdrop.takeIf { engine == ComposeEngine.Material && isBlurEnabled }
    val miuixGlass = engine == ComposeEngine.Miuix && isBlurEnabled && LocalMiuixBarBackdrop.current != null
    val mochiBackdrop = if (engine == ComposeEngine.Mochi && isBlurEnabled) LocalMochiBackdrop.current else null
    val customColors = themeSettings.customColors(LegadoTheme.isDark)
    val hasCustomColors = themeSettings.appTheme == "12" &&
        themeSettings.enableDeepPersonalization
    val accentColor = if (hasCustomColors && customColors.primary != 0) {
        Color(customColors.primary)
    } else {
        LegadoTheme.colorScheme.primary
    }
    val containerColor = if (hasCustomColors && customColors.secondary != 0) {
        Color(customColors.secondary).copy(
            alpha = if (isBlurEnabled) themeSettings.bottomBarBlurAlpha / 100f else 1f
        )
    } else if (engine == ComposeEngine.Coui) {
        CouiTheme.colorScheme.surface
    } else if (isBlurEnabled) {
        LegadoTheme.colorScheme.surfaceContainer.copy(
            alpha = themeSettings.bottomBarBlurAlpha / 100f
        )
    } else {
        LegadoTheme.colorScheme.surfaceContainer
    }

    val tabsBackdrop = if (materialBackdrop != null) rememberLayerBackdrop() else null
    val indicatorColor = when {
        isCoui -> Color.Transparent
        isInLightTheme -> Color.Black.copy(alpha = 0.1f)
        else -> Color.White.copy(alpha = 0.1f)
    }
    val density = LocalDensity.current
    val totalPaddingPx = with(density) { (horizontalPadding * 2).toPx() }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember(density) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex()) }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragAnimationHolder() }

    val dampedDragAnimation = remember(animationScope, tabsCount, totalPaddingPx, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false

                val currentValue = anim.value
                val indicatorX = currentValue * tabWidthPx
                val padding = totalPaddingPx / 2f
                val globalTouchX = if (isLtr) {
                    val touchX = indicatorX + offset.x
                    padding + touchX
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                if (targetIndex != selectedIndex()) {
                    onSelected(targetIndex)
                } else {
                    onReselected(targetIndex)
                }
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex, dampedDragAnimation) {
        snapshotFlow { selectedIndex() }.collectLatest { index ->
            currentIndex = index
            dampedDragAnimation.animateToValue(index.toFloat())
        }
    }

    val interactiveHighlight =
        if (materialBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            remember(animationScope, tabWidthPx) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        Offset(
                            if (isLtr) {
                                (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                            } else {
                                size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                            },
                            size.height / 2f
                        )
                    }
                )
            }
        } else {
            null
        }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = panelOffset }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .then(
                    when {
                        engine == ComposeEngine.Coui && isBlurEnabled && couiNavigation != null &&
                            ThemeResolver.isCouiSupported() -> couiFloatingCapsule(
                                backdrop = couiNavigation.backdrop,
                                renderer = couiNavigation.renderer,
                            ) { size ->
                                val scale = with(density) { 16.dp.toPx() } / size.width.coerceAtLeast(1f)
                                1f + scale * dampedDragAnimation.pressProgress
                            }
                        materialBackdrop != null -> Modifier.drawBackdrop(
                            backdrop = materialBackdrop,
                            shape = { ContinuousCapsule },
                            effects = {
                                vibrancy()
                                blur(themeSettings.bottomBarBlurRadius.dp.toPx())
                                val radius = themeSettings.bottomBarLensRadius.dp.toPx()
                                lens(radius, radius)
                            },
                            highlight = { Highlight.Default },
                            shadow = {
                                Shadow.Default.copy(
                                    color = Color.Black.copy(if (isInLightTheme) 0.1f else 0.2f),
                                )
                            },
                            layerBlock = {
                                val progress = dampedDragAnimation.pressProgress
                                val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width.coerceAtLeast(1f), progress)
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        miuixGlass -> Modifier.miuixCapsuleGlass(
                            tint = containerColor,
                            blurRadius = themeSettings.bottomBarBlurRadius.dp,
                            lensRadius = themeSettings.bottomBarLensRadius.dp,
                        )
                        mochiBackdrop != null -> Modifier
                        else -> Modifier.shadow(4.dp, capsuleShape, clip = false)
                            .background(containerColor.copy(alpha = 1f), capsuleShape)
                    },
                )
                .then(
                    if (isBlurEnabled && interactiveHighlight != null) {
                        interactiveHighlight.modifier
                    } else {
                        Modifier
                    }
                )
                .height(barHeight)
                .then(if (compactProgress > 0f) Modifier.clip(capsuleShape) else Modifier),
        ) {
            if (mochiBackdrop != null) {
                MochiGlassSurface(Modifier.matchParentSize(), material = MochiTheme.materials.bar, backdrop = mochiBackdrop) {}
            }
            Row(
                Modifier
                    .then(
                        if (expandedWidth != null) Modifier
                            .wrapContentWidth(
                                Alignment.Start,
                                unbounded = true
                            )
                            .requiredWidth(expandedWidth) else Modifier
                    )
                    .height(barHeight)
                    .onGloballyPositioned { coords ->
                        totalWidthPx = coords.size.width.toFloat()
                        tabWidthPx = (totalWidthPx - totalPaddingPx).coerceAtLeast(0f) / tabsCount
                    }
                    .graphicsLayer { alpha = navigationAlpha }
                    .then(if (compactProgress > 0f) Modifier.clearAndSetSemantics {} else Modifier)
                    .then(if (isCoui) Modifier.selectableGroup() else Modifier)
                    .padding(horizontal = horizontalPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        if (materialBackdrop != null && tabsBackdrop != null) CompositionLocalProvider(
            LocalFloatingBottomBarTabScale provides {
                if (isBlurEnabled) {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                } else {
                    1f
                }
            },
            LocalFloatingBottomBarTabInteractive provides false,
        ) {
            Row(
                Modifier
                    .then(
                        if (expandedWidth != null) Modifier
                            .wrapContentWidth(
                                Alignment.Start,
                                unbounded = true
                            )
                            .requiredWidth(expandedWidth) else Modifier
                    )
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = materialBackdrop,
                        shape = { ContinuousCapsule },
                        effects = {
                            if (isBlurEnabled) {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(themeSettings.bottomBarBlurRadius.toFloat().dp.toPx())
                                lens(
                                    themeSettings.bottomBarLensRadius.dp.toPx() * progress,
                                    themeSettings.bottomBarLensRadius.dp.toPx() * progress
                                )
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(
                                alpha = if (isBlurEnabled) {
                                    dampedDragAnimation.pressProgress
                                } else {
                                    0f
                                }
                            )
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(
                        if (isBlurEnabled && interactiveHighlight != null) {
                            interactiveHighlight.modifier
                        } else {
                            Modifier
                        }
                    )
                    .height(56.dp)
                    .padding(horizontal = 4.dp)
                    .then(
                        if (hasCustomIcons) Modifier
                        else Modifier.graphicsLayer(colorFilter = ColorFilter.tint(accentColor))
                    ),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        if (tabWidthPx > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (compactProgress > 0f) Modifier.clip(capsuleShape) else Modifier)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = horizontalPadding)
                        .graphicsLayer {
                            alpha = navigationAlpha
                            val progressOffset = dampedDragAnimation.value * tabWidthPx

                            translationX = if (isLtr) {
                                progressOffset + panelOffset
                            } else {
                                -progressOffset + panelOffset
                            }
                        }
                        .then(
                            if (compactProgress <= 0f && isBlurEnabled && interactiveHighlight != null) {
                                interactiveHighlight.gestureModifier
                            } else {
                                Modifier
                            }
                        )
                        .then(if (compactProgress <= 0f) dampedDragAnimation.modifier else Modifier)
                        .then(
                            if (materialBackdrop != null && tabsBackdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = rememberCombinedBackdrop(materialBackdrop, tabsBackdrop),
                                    shape = { ContinuousCapsule },
                                    effects = {
                                        val progress = dampedDragAnimation.pressProgress
                                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, true)
                                    },
                                    highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                                    shadow = { Shadow(alpha = dampedDragAnimation.pressProgress) },
                                    innerShadow = {
                                        val progress = dampedDragAnimation.pressProgress
                                        InnerShadow(radius = 8f.dp * progress, alpha = progress)
                                    },
                                    layerBlock = {
                                        scaleX = dampedDragAnimation.scaleX
                                        scaleY = dampedDragAnimation.scaleY
                                        val velocity = dampedDragAnimation.velocity / 10f
                                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                                    },
                                    onDrawSurface = {
                                        val progress = dampedDragAnimation.pressProgress
                                        drawRect(color = indicatorColor, alpha = 1f - progress)
                                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                    },
                                )
                            } else Modifier.background(indicatorColor, capsuleShape),
                        )
                        .height(barHeight - 8.dp)
                        .width(with(density) { tabWidthPx.toDp() })
                )
            }
        }
        // 菜单位于根容器最上层，背景采样与指示器不参与它的触摸命中。
        if (compactProgress > 0f) {
            IconButton(
                onClick = onRestoreNavigation,
                enabled = compactProgress > 0.35f,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(barHeight)
                    .graphicsLayer { alpha = menuAlpha },
            ) {
                Icon(
                    Icons.Default.Menu, stringResource(R.string.home_capsule_restore_navigation),
                    tint = LegadoTheme.colorScheme.onSurface
                )
            }
        }
    }
}
