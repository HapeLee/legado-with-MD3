package io.legado.app.ui.widget.components.menuItem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.itos.coui.menu.CouiMenuDefaults
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHasMiuixPopupHost
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.ProvideAppContentColor
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.theme.rememberOpaqueColorScheme
import io.legado.app.ui.widget.components.LocalAppGlassOverlay
import io.legado.app.ui.widget.components.mochi.AppMochiPopover
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.window.WindowListPopup

internal val LocalUseWindowPopupForMochiMenu = staticCompositionLocalOf { false }

/** True while menu rows sit inside a glass panel, so they must not paint their own fill. */
internal val LocalGlassPopupContent = compositionLocalOf { false }

/**
 * Anchor for an OS4 glass menu: the popup anchor plus the anchor control's root bounds.
 * [bounds] is only used for placement; [anchor] carries the shared morph/progress state.
 */
@Stable
class GlassMenuAnchor(
    val anchor: GlassPopupAnchor,
    val bounds: Rect,
    val icon: (@Composable () -> Unit)? = null,
)

/**
 * 当前是否该用 OS4 玻璃菜单。
 *
 * 判定集中在 `AppScaffold` 建立的玻璃设施上：`LocalAppGlassOverlay` 只在
 * 「Miuix + HyperOS 4 组件开关 + SDK 33+ + 原生采样可用」时才有值，
 * `LocalHasMiuixPopupHost` 保证页面有 popup 宿主。所有自绘锚点菜单
 * （[RoundDropdownMenu]、`MiuixGlassMenuHost` 的调用方）必须共用这一口径，
 * 否则同一页会出现「顶栏玻璃、列表 ⋮ 仍是普通面板」的错位。
 */
@Composable
internal fun isMiuixGlassMenuAvailable(): Boolean =
    LegadoTheme.composeEngine == ComposeEngine.Miuix &&
        LocalAppGlassOverlay.current != null &&
        LocalHasMiuixPopupHost.current &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        LocalAppUiConfiguration.current.theme.useHyperOs4Components

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = defaultRoundDropdownShape(),
    shadowElevation: Dp = 4.dp,
    verticalSpacing: Dp = defaultRoundDropdownSpacing(),
    mochiAnchorMaxWidth: Dp = Dp.Unspecified,
    glassAnchor: GlassMenuAnchor? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val popupContainerColor = LegadoTheme.colorScheme.surfaceContainer
    val engine = LegadoTheme.composeEngine
    val glassOverlay = LocalAppGlassOverlay.current

    if (isMiuixGlassMenuAvailable()) {
        val glassHost = glassOverlay ?: return
        val key = remember { Any() }
        val backdrop = LocalMiuixBarBackdrop.current
        val localContext = currentCompositionLocalContext
        var parentBounds by remember { mutableStateOf(Rect.Zero) }
        if (glassAnchor == null) {
            // Match WindowListPopup's anchor contract without adding layout space.
            Layout(
                content = {},
                modifier = Modifier.onGloballyPositioned {
                    parentBounds = it.parentLayoutCoordinates?.boundsInRoot() ?: Rect.Zero
                },
            ) { _, _ -> layout(0, 0) {} }
        }
        val anchorBounds = glassAnchor?.bounds ?: parentBounds
        val rows: @Composable ColumnScope.() -> Unit = {
            CompositionLocalProvider(LocalGlassPopupContent provides true) {
                content(onDismissRequest)
            }
        }
        val popupContent = remember(glassHost) { mutableStateOf<@Composable BoxScope.() -> Unit>({}) }
        SideEffect {
            popupContent.value = popup@{
                val origin = glassHost.origin ?: return@popup
                if (anchorBounds.isEmpty) return@popup
                CompositionLocalProvider(localContext) {
                    if (glassAnchor?.icon != null && origin == Offset.Zero) {
                        GlassTransformPopup(
                            show = expanded,
                            onDismissRequest = onDismissRequest,
                            anchor = glassAnchor.anchor,
                            anchorContent = glassAnchor.icon,
                            backdrop = backdrop,
                            modifier = modifier,
                            content = rows,
                        )
                    } else {
                        GlassDropdownPopup(
                            show = expanded,
                            onDismissRequest = onDismissRequest,
                            anchorBounds = anchorBounds.translate(-origin),
                            anchor = glassAnchor?.anchor,
                            backdrop = backdrop,
                            modifier = modifier,
                            content = rows,
                        )
                    }
                }
            }
        }
        DisposableEffect(glassHost, key) {
            glassHost.register(key) { popupContent.value(this) }
            onDispose { glassHost.remove(key) }
        }
        return
    }

    when {
        engine == ComposeEngine.Miuix -> {
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
        }
        engine == ComposeEngine.Mochi && !LocalUseWindowPopupForMochiMenu.current -> AppMochiPopover(
            expanded, onDismissRequest, modifier, maxAnchorWidth = mochiAnchorMaxWidth,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                content(onDismissRequest)
            }
        }
        else -> {
            val colorScheme = rememberOpaqueColorScheme()
            val popupContainerColor = if (engine == ComposeEngine.Coui) {
                CouiMenuDefaults.style().colors.container
            } else LegadoTheme.colorScheme.surfaceContainerLow

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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundDropdownMenuLazy(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = defaultRoundDropdownShape(),
    shadowElevation: Dp = 4.dp,
    verticalSpacing: Dp = defaultRoundDropdownSpacing(),
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
    val popupContainerColor = LegadoTheme.colorScheme.surfaceContainer

    when (LegadoTheme.composeEngine) {
        ComposeEngine.Miuix -> {
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
        }
        ComposeEngine.Mochi -> AppMochiPopover(expanded, onDismissRequest, modifier, menuWidth) {
            LazyMenuViewport(menuWidth, viewportHeight) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                    content(onDismissRequest)
                }
            }
        }
        ComposeEngine.Material, ComposeEngine.Coui -> {
            val colorScheme = rememberOpaqueColorScheme()
            val popupContainerColor = if (LegadoTheme.composeEngine == ComposeEngine.Coui) {
                CouiMenuDefaults.style().colors.container
            } else LegadoTheme.colorScheme.surfaceContainerLow

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
}

@Composable
private fun defaultRoundDropdownShape(): Shape =
    if (LegadoTheme.composeEngine == ComposeEngine.Coui) CouiMenuDefaults.shape
    else MaterialTheme.shapes.medium

@Composable
private fun defaultRoundDropdownSpacing(): Dp =
    if (LegadoTheme.composeEngine == ComposeEngine.Coui) 0.dp else 8.dp

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
