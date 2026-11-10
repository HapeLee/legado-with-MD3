package io.legado.app.ui.widget.components.coui

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.CouiTopAppBarScrollBehavior
import com.itos.coui.topappbar.LargeTopAppBar
import com.itos.coui.topappbar.TopAppBar as CouiTopAppBar
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.AdaptiveAnimatedText
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import io.legado.app.ui.widget.components.topbar.M3GlassScrollBehavior

internal val LocalAppCouiTopBarHost = staticCompositionLocalOf<AppCouiTopBarHost?> { null }
internal val LocalAppCouiNativeToolbar = staticCompositionLocalOf { false }

internal class AppCouiTopBarHost(val backdrop: CouiBackdrop?, val view: View) {
    var scrollBehavior by mutableStateOf<CouiTopAppBarScrollBehavior?>(null)
}

internal class CouiGlassScrollBehavior(val nativeBehavior: CouiTopAppBarScrollBehavior) : GlassTopAppBarScrollBehavior {
    override val nestedScrollConnection: NestedScrollConnection get() = nativeBehavior.nestedScrollConnection
    override val collapsedFraction: Float get() = nativeBehavior.state.collapsedFraction
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppCouiTopBar(
    title: String,
    modifier: Modifier,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    subtitle: String? = null,
    useCharMode: Boolean = false,
    expanded: Boolean = false,
    scrollBehavior: GlassTopAppBarScrollBehavior? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val host = LocalAppCouiTopBarHost.current
    val backdrop = host?.backdrop
    val nativeBehavior = (scrollBehavior as? CouiGlassScrollBehavior)?.nativeBehavior
    val colors = CouiTopAppBarDefaults.colors()
    val subtitleContent: (@Composable () -> Unit)? = subtitle?.takeIf { it.isNotBlank() }?.let { value ->
        {
            AnimatedTextLine(
                text = value,
                style = LegadoTheme.typography.labelSmall,
                color = CouiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val nativeTitle: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AdaptiveAnimatedText(
                text = title,
                useCharMode = useCharMode,
                style = LocalCouiTextStyle.current,
                color = colors.titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitleContent?.invoke()
        }
    }
    val nativeNavigation: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalAppCouiNativeToolbar provides true,
            LocalContentColor provides colors.navigationIconColor,
            content = navigationIcon,
        )
    }
    val nativeActions: @Composable RowScope.() -> Unit = {
        CompositionLocalProvider(
            LocalAppCouiNativeToolbar provides true,
            LocalContentColor provides colors.actionIconColor,
        ) { actions() }
    }
    Column(modifier.fillMaxWidth()) {
        if (backdrop != null) {
            if (expanded) {
                DisposableEffect(host, nativeBehavior) {
                    host.scrollBehavior = nativeBehavior
                    onDispose {
                        if (host.scrollBehavior === nativeBehavior) host.scrollBehavior = null
                    }
                }
                LargeTopAppBar(
                    title = nativeTitle,
                    navigationIcon = nativeNavigation,
                    actions = nativeActions,
                    scrollBehavior = nativeBehavior,
                    colors = colors,
                    backdrop = backdrop,
                )
            } else {
                CouiTopAppBar(
                    title = nativeTitle,
                    navigationIcon = nativeNavigation,
                    actions = nativeActions,
                    colors = colors,
                    backdrop = backdrop,
                )
            }
        } else {
            val container = GlassTopAppBarDefaults.containerColor()
            val solidColors = TopAppBarDefaults.topAppBarColors(
                containerColor = container,
                scrolledContainerColor = container,
                navigationIconContentColor = colors.navigationIconColor,
                actionIconContentColor = colors.actionIconColor,
                titleContentColor = colors.titleColor,
            )
            val solidTitle: @Composable () -> Unit = {
                AdaptiveAnimatedText(
                    text = title,
                    useCharMode = useCharMode,
                    color = colors.titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) {
                val solidBehavior = if (nativeBehavior != null) {
                    val limit = with(LocalDensity.current) {
                        (CouiTopAppBarDefaults.collapsedHeight - CouiTopAppBarDefaults.expandedHeight).toPx()
                    }
                    val state = rememberTopAppBarState(initialHeightOffsetLimit = limit)
                    val fraction = nativeBehavior.state.collapsedFraction
                    SideEffect { state.heightOffset = state.heightOffsetLimit * fraction }
                    TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = state)
                } else (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior
                MediumFlexibleTopAppBar(
                    title = solidTitle,
                    subtitle = subtitleContent,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    collapsedHeight = CouiTopAppBarDefaults.collapsedHeight,
                    expandedHeight = CouiTopAppBarDefaults.expandedHeight,
                    windowInsets = CouiTopAppBarDefaults.windowInsets,
                    scrollBehavior = solidBehavior,
                    colors = solidColors,
                )
            } else {
                TopAppBar(
                    title = { Column { solidTitle(); subtitleContent?.invoke() } },
                    navigationIcon = navigationIcon,
                    actions = actions,
                    expandedHeight = CouiTopAppBarDefaults.height,
                    windowInsets = CouiTopAppBarDefaults.windowInsets,
                    colors = solidColors,
                )
            }
        }
        bottomContent?.invoke(this)
    }
}
