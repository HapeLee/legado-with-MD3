package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppContentColor
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.rememberOpaqueColorScheme
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.window.WindowListPopup

val LocalUseMiuixWindowPopup = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    shadowElevation: Dp = 4.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val popupContainerColor = LegadoTheme.colorScheme.surfaceContainer

    if (isMiuix) {
        val popupContentColor = LegadoTheme.colorScheme.onSurface
        WindowListPopup(
            show = expanded,
            onDismissRequest = onDismissRequest,
            popupModifier = modifier
        ) {
            ProvideAppDensity {
                ProvideAppContentColor(popupContentColor) {
                    ListPopupColumn {
                        Column(modifier = Modifier.background(popupContainerColor)) {
                            Spacer(Modifier.height(12.dp))
                            content(onDismissRequest)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    } else {
        val colorScheme = rememberOpaqueColorScheme()
        val popupContainerColor = LegadoTheme.colorScheme.surfaceContainerLow

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            shape = shape,
            shadowElevation = shadowElevation,
            containerColor = popupContainerColor
        ) {
            ProvideAppDensity {
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = Typography(),
                    motionScheme = MotionScheme.expressive(),
                    shapes = Shapes()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                    ) {
                        content(onDismissRequest)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundDropdownMenuLazy(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    shadowElevation: Dp = 4.dp,
    verticalSpacing: Dp = 8.dp,
    maxHeight: Dp = Dp.Unspecified,
    menuWidth: Dp = 280.dp,
    content: LazyListScope.(dismiss: () -> Unit) -> Unit
) {
    // Resolve against the host window before entering Popup's separate composition.
    // Like the group menu, allow a long list to use the available height instead of a fixed cap.
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val safeInsets = WindowInsets.safeDrawing
    val viewportHeight = if (maxHeight.isSpecified) maxHeight else with(density) {
        (windowHeight - safeInsets.getTop(this) - safeInsets.getBottom(this))
            .coerceAtLeast(0).toDp().minus(48.dp).coerceAtLeast(48.dp)
    }
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)
    val popupContainerColor = LegadoTheme.colorScheme.surfaceContainer

    if (isMiuix) {
        val popupContentColor = LegadoTheme.colorScheme.onSurface
        WindowListPopup(
            show = expanded,
            onDismissRequest = onDismissRequest,
            popupModifier = modifier
        ) {
            ProvideAppDensity {
                ProvideAppContentColor(popupContentColor) {
                    ListPopupColumn {
                        LazyMenuViewport(menuWidth, viewportHeight) {
                            LazyColumn(
                                modifier = Modifier.background(popupContainerColor),
                                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
                            ) {
                                item { Spacer(Modifier.height(12.dp)) }
                                content(onDismissRequest)
                                item { Spacer(Modifier.height(12.dp)) }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val colorScheme = rememberOpaqueColorScheme()
        val popupContainerColor = LegadoTheme.colorScheme.surfaceContainerLow

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            shape = shape,
            shadowElevation = shadowElevation,
            containerColor = popupContainerColor
        ) {
            ProvideAppDensity {
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = Typography(),
                    motionScheme = MotionScheme.expressive(),
                    shapes = Shapes()
                ) {
                    LazyMenuViewport(menuWidth, viewportHeight) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                            content(onDismissRequest)
                        }
                    }
                }
            }
        }
    }
}

// Popup containers may query intrinsic sizes. Answer at the viewport boundary rather than
// forwarding those queries to LazyColumn's SubcomposeLayout. Actual height can still shrink
// for a short menu, and both dimensions remain constrained by the available window space.
//
// 注意：min/max intrinsic 高度都报 maxHeight 是有意的。LazyColumn 的 intrinsic 高度为 0，
// 若这里返回 0，父容器按 intrinsic 约束测量时菜单会变成 0 高度而不可见。代价是当父容器确实
// 用 intrinsic 决定高度时，短菜单会被撑到 maxHeight；走正常 measure 路径（见上面的 measure）
// 时仍会按内容收缩。修改前请同时更新 RoundDropdownMenuLazyTest 的 intrinsic 断言。
@Composable
internal fun LazyMenuViewport(width: Dp, maxHeight: Dp, content: @Composable () -> Unit) {
    val policy = remember(width, maxHeight) {
        object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>, constraints: Constraints,
            ): MeasureResult {
                val widthPx = constraints.constrainWidth(width.roundToPx())
                val heightPx = constraints.constrainHeight(maxHeight.roundToPx())
                val child = measurables.single().measure(
                    constraints.copy(
                        minWidth = widthPx,
                        maxWidth = widthPx,
                        minHeight = 0,
                        maxHeight = heightPx
                    )
                )
                return layout(
                    widthPx,
                    constraints.constrainHeight(child.height)
                ) { child.placeRelative(0, 0) }
            }

            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ) = width.roundToPx()

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int
            ) = width.roundToPx()

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ) = maxHeight.roundToPx()

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int
            ) = maxHeight.roundToPx()
        }
    }
    Layout(content = content, measurePolicy = policy)
}
