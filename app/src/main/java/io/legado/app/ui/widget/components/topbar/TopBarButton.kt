package io.legado.app.ui.widget.components.topbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.isMiuixHyperOs4Enabled
import io.legado.app.ui.widget.components.button.series.AnimatedActionButtonCore
import io.legado.app.ui.widget.components.button.series.AnimatedIcon
import io.legado.app.ui.widget.components.button.series.MediumSeriesIconButtonSize
import io.legado.app.ui.widget.components.button.series.MediumSeriesIconSize
import io.legado.app.ui.widget.components.button.series.SeriesButton
import io.legado.app.ui.widget.components.button.series.SeriesIconButtonStyle
import io.legado.app.ui.widget.components.button.series.TopBarSeriesIconButtonSize
import io.legado.app.ui.widget.components.button.series.TopBarSeriesIconSize
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.mochi.keyboardClick
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.effect.elastic.mochiPressDeform
import ui.feice.mochi.effect.glass.MochiGlassSurface
import ui.feice.mochi.theme.LocalContentColor as MochiContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixContentColor
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults as MiuixIconButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.glass.GlassIconButton as MiuixGlassIconButton
import top.yukonga.miuix.kmp.glass.GlassShape as MiuixGlassShape
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults as MiuixGlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPanel

/** 顶栏按钮样式配置。 */
enum class TopBarButtonStyle(val storageValue: String) {
    Plain("plain"),
    Tonal("tonal"),
    Outlined("outlined"),
    SemiTransparent("glass"),
    LiquidGlass("liquid");

    companion object {
        fun fromStorage(value: String?): TopBarButtonStyle =
            entries.firstOrNull { it.storageValue == value } ?: Tonal
    }
}

/** M3 顶栏按钮是否共享一个容器。 */
internal val LocalTopBarMergeState = staticCompositionLocalOf { false }

@Composable
private fun currentTopBarButtonStyle(): TopBarButtonStyle =
    TopBarButtonStyle.fromStorage(LocalAppUiConfiguration.current.theme.topBarButtonStyle)

private val TopBarButtonStyle.seriesStyle: SeriesIconButtonStyle
    get() = when (this) {
        TopBarButtonStyle.Plain -> SeriesIconButtonStyle.Plain
        TopBarButtonStyle.Tonal, TopBarButtonStyle.SemiTransparent,
        TopBarButtonStyle.LiquidGlass -> SeriesIconButtonStyle.Tonal
        TopBarButtonStyle.Outlined -> SeriesIconButtonStyle.Outlined
    }

/** Plain 无容器背景，图标可以更大（40dp/24dp）；带容器/边框的样式用紧凑的 36dp/20dp。 */
private val TopBarButtonStyle.buttonSize: DpSize
    get() = when (this) {
        TopBarButtonStyle.Plain -> MediumSeriesIconButtonSize
        TopBarButtonStyle.Tonal, TopBarButtonStyle.Outlined, TopBarButtonStyle.SemiTransparent,
        TopBarButtonStyle.LiquidGlass ->
            TopBarSeriesIconButtonSize
    }

private val TopBarButtonStyle.iconSize: Dp
    get() = when (this) {
        TopBarButtonStyle.Plain -> MediumSeriesIconSize
        TopBarButtonStyle.Tonal, TopBarButtonStyle.Outlined, TopBarButtonStyle.SemiTransparent,
        TopBarButtonStyle.LiquidGlass ->
            TopBarSeriesIconSize
    }

/**
 * 顶栏 actions 槽按钮间距：Plain 无边框/容器，比带容器/边框的样式更紧凑，
 * 否则同样的间距会让裸图标显得更分散。
 */
private fun topBarActionSpacing(style: TopBarButtonStyle): Dp =
    if (style == TopBarButtonStyle.Plain) 4.dp else 8.dp

/** 合并模式下按钮左侧的竖向分隔线（首个按钮不画）。 */
@Composable
private fun Modifier.mergedDivider(): Modifier {
    var showDivider by remember { mutableStateOf(false) }
    val dividerColor = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    return onPlaced { coordinates ->
        showDivider = coordinates.positionInParent().x > 0f
    }.then(
        if (showDivider) {
            Modifier.drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height * 0.3f),
                    end = Offset(0f, size.height * 0.7f),
                    strokeWidth = 1.dp.toPx()
                )
            }
        } else {
            Modifier
        }
    )
}

/**
 * 顶栏 actions 的统一 Row。
 *
 * 开启「合并顶栏按钮」且样式为 Tonal/Outlined/半透明/液态玻璃时，把多个按钮的容器/边框
 * 融合成一个胶囊，按钮间用竖向分隔线隔开。
 * 单个按钮时胶囊自然退化为普通按钮。Plain 无容器，始终走普通间距 Row。
 */
@Composable
internal fun TopBarActionsRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val engine = LegadoTheme.composeEngine
    if (engine != ComposeEngine.Material) {
        Row(
            modifier = modifier,
            horizontalArrangement = if (engine == ComposeEngine.Mochi) Arrangement.spacedBy(8.dp)
                else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }
    val style = currentTopBarButtonStyle()
    val mergeEnabled = LocalAppUiConfiguration.current.theme.mergeTopBarActions
    val liquidGlassEnabled = style == TopBarButtonStyle.LiquidGlass &&
            topBarLiquidGlassEnabled()
    if (!mergeEnabled || style == TopBarButtonStyle.Plain) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(topBarActionSpacing(style)),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        return
    }

    val capsuleShape = RoundedCornerShape(50)
    val capsuleBg = when (style) {
        TopBarButtonStyle.Tonal -> LegadoTheme.colorScheme.surfaceContainerLow
        TopBarButtonStyle.SemiTransparent, TopBarButtonStyle.LiquidGlass ->
            GlassTopAppBarDefaults.controlContainerColor()
        else -> Color.Transparent // Outlined
    }
    Box(
        modifier = modifier
            .height(TopBarSeriesIconButtonSize.height)
            .then(if (!liquidGlassEnabled) Modifier.clip(capsuleShape) else Modifier)
            .then(
                if (liquidGlassEnabled) {
                    Modifier.topBarLiquidGlass(capsuleShape)
                } else {
                    Modifier.background(capsuleBg, capsuleShape)
                }
            )
            .then(
                if (style == TopBarButtonStyle.Outlined) {
                    Modifier.border(1.dp, LegadoTheme.colorScheme.outlineVariant, capsuleShape)
                } else {
                    Modifier
                }
            )
    ) {
        CompositionLocalProvider(LocalTopBarMergeState provides true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String? = null,
    text: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    iconTint: Color = Color.Unspecified,
) {
    val engine = LegadoTheme.composeEngine
    if (engine == ComposeEngine.Mochi) {
        if (onLongClick == null) {
            // Match Catalog's 44dp bar surface. Pointer input still has Compose's
            // expanded touch target, without adding 2dp inside each 16dp edge margin.
            val buttonModifier = modifier.keyboardClick(true, onClick)
                .semantics(mergeDescendants = true) {}
            if (text != null) MochiBarButton(text, onClick, buttonModifier)
            else MochiBarIconButton(
                onClick, buttonModifier, contentKey = imageVector.name,
            ) { AppIcon(imageVector, contentDescription, Modifier.size(24.dp), tint = MochiContentColor.current) }
        } else {
            MochiTopBarPressable(
                onClick, modifier.size(MochiButtonDefaults.BarItemSize), onLongClick, onLongClickLabel,
            ) {
                AppIcon(imageVector, contentDescription, Modifier.size(24.dp), tint = MochiButtonDefaults.barColors().content)
            }
        }
        return
    }
    if (engine == ComposeEngine.Miuix) {
        val glass = LocalMiuixGlassTopBar.current
        val tint = if (iconTint == Color.Unspecified) MiuixContentColor.current else iconTint
        when {
            text != null && glass -> MiuixTopBarActionSurface(modifier) { actionModifier ->
                MiuixIconButton(onClick = onClick, modifier = actionModifier) {
                    MiuixText(
                        text = text,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MiuixTheme.textStyles.button,
                    )
                }
            }
            text != null -> MiuixTextButton(
                text = text,
                onClick = onClick,
                modifier = modifier,
                minWidth = MiuixIconButtonDefaults.MinWidth,
                minHeight = MiuixIconButtonDefaults.MinHeight,
                insideMargin = PaddingValues(horizontal = 8.dp),
            )
            onLongClick == null && glass -> MiuixGlassIconButton(
                onClick = onClick,
                modifier = modifier.semantics { role = Role.Button },
                backdrop = LocalMiuixBarBackdrop.current,
            ) {
                MiuixIcon(imageVector, contentDescription, tint = tint)
            }
            onLongClick == null -> MiuixIconButton(onClick, modifier) {
                MiuixIcon(imageVector, contentDescription, tint = tint)
            }
            else -> MiuixTopBarActionSurface(modifier) { actionModifier ->
                Box(
                    modifier = actionModifier.defaultMinSize(
                        minWidth = MiuixIconButtonDefaults.MinWidth,
                        minHeight = MiuixIconButtonDefaults.MinHeight,
                    ).combinedClickable(
                        role = Role.Button, onClick = onClick, onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel,
                    ),
                    contentAlignment = Alignment.Center,
                ) { MiuixIcon(imageVector, contentDescription) }
            }
        }
        return
    }
    val style = currentTopBarButtonStyle()
    val isMerged = LocalTopBarMergeState.current
    val liquidGlassEnabled = style == TopBarButtonStyle.LiquidGlass &&
            topBarLiquidGlassEnabled()
    if (isMerged) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(style.buttonSize)
                .mergedDivider()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = if (liquidGlassEnabled) null else ripple(bounded = true),
                    role = Role.Button,
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                    onClick = onClick
                )
        ) {
            if (text != null) {
                Text(
                    text = text,
                    color = LegadoTheme.colorScheme.onSurface,
                    style = LegadoTheme.typography.labelLarge,
                    maxLines = 1,
                )
            } else {
                AppIcon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = LegadoTheme.colorScheme.onSurface,
                    modifier = Modifier.size(style.iconSize)
                )
            }
        }
    } else {
        val containerColor = when {
            liquidGlassEnabled -> Color.Transparent
            style == TopBarButtonStyle.SemiTransparent ||
                    style == TopBarButtonStyle.LiquidGlass ->
                GlassTopAppBarDefaults.controlContainerColor()

            else -> null
        }
        SeriesButton(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
            modifier = if (liquidGlassEnabled) {
                modifier.topBarLiquidGlass(RoundedCornerShape(50))
            } else {
                modifier
            },
            enforceMinimumInteractiveSize = false,
            clipToShape = !liquidGlassEnabled,
            size = style.buttonSize,
            style = style.seriesStyle,
            contentColor = LegadoTheme.colorScheme.onSurface,
            containerColor = containerColor,
            indication = if (liquidGlassEnabled) null else ripple(bounded = true)
        ) { resolvedContentColor ->
            if (text != null) {
                Text(
                    text = text,
                    color = resolvedContentColor,
                    style = LegadoTheme.typography.labelLarge,
                    maxLines = 1,
                )
            } else {
                AppIcon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = resolvedContentColor,
                    modifier = Modifier.size(style.iconSize)
                )
            }
        }
    }
}

/** Mochi's bar icon button has no long-press API or slot for an expanding label. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MochiTopBarPressable(
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    MochiGlassSurface(
        modifier.mochiPressDeform().combinedClickable(
            interactionSource = interaction, indication = null, role = Role.Button,
            onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel,
        ),
        interactionSource = interaction,
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun TopBarNavigationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = AppIcons.Back,
    contentDescription: String? = stringResource(id = R.string.back)
) {
    TopBarButton(
        onClick = onClick,
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = if (LegadoTheme.composeEngine == ComposeEngine.Material) {
            modifier.padding(horizontal = 12.dp)
        } else modifier,
    )
}

@Composable
fun TopBarActionButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    TopBarButton(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
fun TopBarTextActionButton(
    onClick: () -> Unit,
    text: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    TopBarButton(
        onClick = onClick,
        text = text,
        contentDescription = contentDescription,
        modifier = modifier
    )
}

// Only the background follows the floating transition. The action and its label stay opaque.
@Composable
private fun MiuixTopBarActionSurface(
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (!LocalMiuixGlassTopBar.current) {
        content(modifier)
        return
    }
    val progress = LocalMiuixGlassTopBarActionProgress.current
    val showMaterial by remember(progress) { derivedStateOf { (progress?.value ?: 1f) > 0f } }
    val size = MiuixGlassTopAppBarDefaults.ButtonSize
    Box(
        modifier.defaultMinSize(minWidth = size, minHeight = size),
        propagateMinConstraints = true,
    ) {
        if (showMaterial) {
            val corner = CornerSize(50)
            val shape = MiuixGlassShape(corner, corner, corner, corner)
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { alpha = progress?.value ?: 1f }
                    .glassPanel(
                        backdrop = LocalMiuixBarBackdrop.current,
                        shape = shape,
                        material = MiuixGlassTopAppBarDefaults.buttonMaterial(),
                        stroke = MiuixGlassTopAppBarDefaults.buttonStroke(),
                        shading = false,
                        fallback = Modifier.clip(shape).background(MiuixGlassTopAppBarDefaults.buttonFill()),
                    ),
            )
        }
        content(Modifier)
    }
}

@Composable
fun TopBarAnimatedActionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconChecked: ImageVector,
    iconUnchecked: ImageVector,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier
) {
    val engine = LegadoTheme.composeEngine
    if (isMiuixHyperOs4Enabled()) {
        TopBarButton(
            onClick = { onCheckedChange(!checked) },
            modifier = modifier,
            imageVector = if (checked) iconChecked else iconUnchecked,
            contentDescription = if (checked) activeText else inactiveText,
            iconTint = if (checked) MiuixTheme.colorScheme.primary else Color.Unspecified,
        )
        return
    }
    if (engine == ComposeEngine.Miuix) {
        val contentColor by animateColorAsState(
            targetValue = if (checked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            animationSpec = tween(150),
            label = "MiuixActionButtonContent"
        )

        AnimatedActionButtonCore(
            checked = checked,
            onCheckedChange = onCheckedChange,
            iconChecked = iconChecked,
            iconUnchecked = iconUnchecked,
            activeText = activeText,
            inactiveText = inactiveText,
            modifier = modifier,
            iconSize = 24.dp,
            textStyle = LegadoTheme.typography.labelMedium,
            textStartPadding = 8.dp,
            contentColor = contentColor,
            button = { buttonModifier, onToggle, content ->
                MiuixIconButton(
                    onClick = { onToggle(!checked) },
                    modifier = buttonModifier.semantics {
                        contentDescription = if (checked) activeText else inactiveText
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        content = content,
                    )
                }
            },
            icon = { imageVector, iconModifier, tint ->
                MiuixIcon(
                    tint = tint ?: Color.Unspecified,
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = iconModifier
                )
            },
            text = { label, textModifier, style, color ->
                MiuixText(
                    text = label,
                    color = color ?: Color.Unspecified,
                    style = style,
                    modifier = textModifier,
                    maxLines = 1,
                    softWrap = false
                )
            }
        )
    } else if (engine == ComposeEngine.Mochi) {
        val size = MochiButtonDefaults.BarItemSize
        val colors = MochiButtonDefaults.barColors()
        AnimatedActionButtonCore(
            checked = checked,
            onCheckedChange = onCheckedChange,
            iconChecked = iconChecked,
            iconUnchecked = iconUnchecked,
            activeText = activeText,
            inactiveText = inactiveText,
            modifier = modifier.height(size).defaultMinSize(minWidth = size).semantics {
                contentDescription = if (checked) activeText else inactiveText
            },
            iconSize = 24.dp,
            textStyle = LegadoTheme.typography.labelMedium,
            textStartPadding = 8.dp,
            contentColor = if (checked) LegadoTheme.colorScheme.primary else colors.content,
            button = { buttonModifier, onToggle, content ->
                MochiTopBarPressable({ onToggle(!checked) }, buttonModifier) {
                    Row(
                        Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = content,
                    )
                }
            },
            icon = { imageVector, iconModifier, tint ->
                AppIcon(imageVector, null, iconModifier, tint ?: Color.Unspecified)
            },
            text = { label, textModifier, style, color ->
                Text(label, textModifier, color = color ?: colors.content, style = style, maxLines = 1, softWrap = false)
            },
        )
    } else {
        val topBarStyle = currentTopBarButtonStyle()
        val isMerged = LocalTopBarMergeState.current
        val containerColor = if (
            !isMerged &&
            (topBarStyle == TopBarButtonStyle.SemiTransparent ||
                    topBarStyle == TopBarButtonStyle.LiquidGlass)
        ) {
            if (topBarStyle == TopBarButtonStyle.LiquidGlass && topBarLiquidGlassEnabled()) {
                Color.Transparent
            } else GlassTopAppBarDefaults.controlContainerColor()
        } else {
            null
        }
        AnimatedActionButtonCore(
            checked = checked,
            onCheckedChange = onCheckedChange,
            iconChecked = iconChecked,
            iconUnchecked = iconUnchecked,
            activeText = activeText,
            inactiveText = inactiveText,
            modifier = modifier.height(36.dp),
            iconSize = 20.dp,
            textStyle = LegadoTheme.typography.labelMedium,
            textStartPadding = 8.dp,
            button = { buttonModifier, onToggle, content ->
                val dividerModifier = buttonModifier
                    .then(if (isMerged) Modifier.mergedDivider() else Modifier)
                if (isMerged) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = dividerModifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = if (topBarStyle == TopBarButtonStyle.LiquidGlass &&
                                topBarLiquidGlassEnabled()
                            ) null else ripple(bounded = true),
                            role = Role.Button,
                            onClick = { onToggle(!checked) }
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            content = content
                        )
                    }
                } else {
                    SeriesButton(
                        onClick = { onToggle(!checked) },
                        modifier = if (topBarStyle == TopBarButtonStyle.LiquidGlass &&
                            topBarLiquidGlassEnabled()
                        ) {
                            dividerModifier.topBarLiquidGlass(RoundedCornerShape(50))
                        } else dividerModifier,
                        enforceMinimumInteractiveSize = false,
                        clipToShape = !(topBarStyle == TopBarButtonStyle.LiquidGlass &&
                                topBarLiquidGlassEnabled()),
                        selected = checked,
                        style = topBarStyle.seriesStyle,
                        contentColor = LegadoTheme.colorScheme.onSurface,
                        containerColor = containerColor,
                        indication = if (topBarStyle == TopBarButtonStyle.LiquidGlass &&
                            topBarLiquidGlassEnabled()
                        ) null else ripple(bounded = true)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            content = content
                        )
                    }
                }
            },
            icon = { imageVector, iconModifier, _ ->
                AnimatedContent(
                    targetState = imageVector,
                    label = "IconAnimation"
                ) { targetIcon ->
                    AnimatedIcon(
                        modifier = iconModifier,
                        imageVector = targetIcon,
                        contentDescription = null
                    )
                }
            },
            text = { label, textModifier, style, color ->
                Text(
                    text = label,
                    style = style,
                    color = color ?: Color.Unspecified,
                    modifier = textModifier,
                    maxLines = 1,
                    softWrap = false
                )
            }
        )
    }
}
