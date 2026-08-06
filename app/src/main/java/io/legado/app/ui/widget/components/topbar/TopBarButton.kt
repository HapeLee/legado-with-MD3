package io.legado.app.ui.widget.components.topbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.ThemeResolver
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/** 顶栏按钮样式配置，与 M3 / 渐进模糊解耦。Glass 预留液态玻璃外观。 */
enum class TopBarButtonStyle(val storageValue: String) {
    Plain("plain"),
    Tonal("tonal"),
    Outlined("outlined"),
    Glass("glass");

    companion object {
        fun fromStorage(value: String?): TopBarButtonStyle =
            entries.firstOrNull { it.storageValue == value } ?: Tonal
    }
}

/** 合并模式下的共享计数器，记录下一个按钮的索引。 */
internal class TopBarMergeCounter {
    var index = 0
}

/** 合并模式的共享状态；null 表示未处于合并模式。 */
internal val LocalTopBarMergeState = staticCompositionLocalOf<TopBarMergeCounter?> { null }

@Composable
private fun currentTopBarButtonStyle(): TopBarButtonStyle =
    TopBarButtonStyle.fromStorage(LocalAppUiConfiguration.current.theme.topBarButtonStyle)

private val TopBarButtonStyle.seriesStyle: SeriesIconButtonStyle
    get() = when (this) {
        TopBarButtonStyle.Plain -> SeriesIconButtonStyle.Plain
        TopBarButtonStyle.Tonal, TopBarButtonStyle.Glass -> SeriesIconButtonStyle.Tonal
        TopBarButtonStyle.Outlined -> SeriesIconButtonStyle.Outlined
    }

/** Plain 无容器背景，图标可以更大（40dp/24dp）；带容器/边框的样式用紧凑的 36dp/20dp。 */
private val TopBarButtonStyle.buttonSize: DpSize
    get() = when (this) {
        TopBarButtonStyle.Plain -> MediumSeriesIconButtonSize
        TopBarButtonStyle.Tonal, TopBarButtonStyle.Outlined, TopBarButtonStyle.Glass ->
            TopBarSeriesIconButtonSize
    }

private val TopBarButtonStyle.iconSize: Dp
    get() = when (this) {
        TopBarButtonStyle.Plain -> MediumSeriesIconSize
        TopBarButtonStyle.Tonal, TopBarButtonStyle.Outlined, TopBarButtonStyle.Glass ->
            TopBarSeriesIconSize
    }

/**
 * 顶栏 actions 槽按钮间距：Plain 无边框/容器，比带容器/边框的样式更紧凑，
 * 否则同样的间距会让裸图标显得更分散。
 */
@Composable
internal fun topBarActionSpacing(): Dp {
    val style = currentTopBarButtonStyle()
    return if (style == TopBarButtonStyle.Plain) 4.dp else 8.dp
}

/** 合并模式下按钮左侧的竖向分隔线（首个按钮不画）。 */
@Composable
private fun Modifier.mergedDivider(): Modifier {
    val dividerColor = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    return drawBehind {
        drawLine(
            color = dividerColor,
            start = Offset(0f, size.height * 0.3f),
            end = Offset(0f, size.height * 0.7f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * 顶栏 actions 的统一 Row。
 *
 * 开启「合并顶栏按钮」且样式为 Tonal/Outlined/Glass 时，把多个按钮的容器/边框
 * 融合成一个胶囊，按钮间用竖向分隔线隔开（复用 [TopBarMergeCounter] 自动分配索引）。
 * 单个按钮时胶囊自然退化为普通按钮。Plain 无容器，始终走普通间距 Row。
 */
@Composable
internal fun TopBarActionsRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val style = currentTopBarButtonStyle()
    val mergeEnabled = LocalAppUiConfiguration.current.theme.mergeTopBarActions
    if (!mergeEnabled || style == TopBarButtonStyle.Plain) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(topBarActionSpacing()),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        return
    }

    val counter = remember { TopBarMergeCounter() }
    counter.index = 0
    val capsuleShape = RoundedCornerShape(50)
    val capsuleBg = when (style) {
        TopBarButtonStyle.Tonal -> LegadoTheme.colorScheme.surfaceContainerLow
        TopBarButtonStyle.Glass -> GlassTopAppBarDefaults.controlContainerColor()
        else -> Color.Transparent // Outlined
    }
    Box(
        modifier = modifier
            .height(TopBarSeriesIconButtonSize.height)
            .clip(capsuleShape)
            .background(capsuleBg, capsuleShape)
            .then(
                if (style == TopBarButtonStyle.Outlined) {
                    Modifier.border(1.dp, LegadoTheme.colorScheme.outlineVariant, capsuleShape)
                } else {
                    Modifier
                }
            )
    ) {
        CompositionLocalProvider(LocalTopBarMergeState provides counter) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
private fun TopBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription: String? = null,
    style: TopBarButtonStyle = currentTopBarButtonStyle()
) {
    val mergeState = LocalTopBarMergeState.current
    if (mergeState != null) {
        val index = mergeState.index
        mergeState.index = index + 1
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(style.buttonSize)
                .then(if (index > 0) Modifier.mergedDivider() else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onClick
                )
        ) {
            AppIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(style.iconSize)
            )
        }
    } else {
        val containerColor = if (style == TopBarButtonStyle.Glass) {
            GlassTopAppBarDefaults.controlContainerColor()
        } else {
            null
        }
        SeriesButton(
            onClick = onClick,
            modifier = modifier,
            enforceMinimumInteractiveSize = false,
            size = style.buttonSize,
            style = style.seriesStyle,
            contentColor = LegadoTheme.colorScheme.onSurface,
            containerColor = containerColor
        ) { resolvedContentColor ->
            AppIcon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = resolvedContentColor,
                modifier = Modifier.size(style.iconSize)
            )
        }
    }
}

@Composable
fun TopBarNavigationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageVector: ImageVector = AppIcons.Back,
    contentDescription: String? = stringResource(id = R.string.back)
) {
    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    } else {
        TopBarButton(
            onClick = onClick,
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun TopBarActionButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            MiuixIcon(
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        }
    } else {
        TopBarButton(
            onClick = onClick,
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier
        )
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
    if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
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
                    modifier = buttonModifier
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        content = content
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
    } else {
        val topBarStyle = currentTopBarButtonStyle()
        val mergeState = LocalTopBarMergeState.current
        val mergeIndex = if (mergeState != null) {
            val index = mergeState.index
            mergeState.index = index + 1
            index
        } else {
            -1
        }
        val containerColor = if (mergeState == null && topBarStyle == TopBarButtonStyle.Glass) {
            GlassTopAppBarDefaults.controlContainerColor()
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
                    .then(if (mergeIndex > 0) Modifier.mergedDivider() else Modifier)
                if (mergeIndex >= 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = dividerModifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
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
                        modifier = dividerModifier,
                        enforceMinimumInteractiveSize = false,
                        selected = checked,
                        style = topBarStyle.seriesStyle,
                        contentColor = LegadoTheme.colorScheme.onSurface,
                        containerColor = containerColor
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
