package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.ui.widget.components.text.AdaptiveAnimatedText
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun GlassMediumFlexibleTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    useCharMode: Boolean = false,
    subtitle: String? = null,
    scrollBehavior: GlassTopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable (ColumnScope.() -> Unit)? = null
) {

    val hazeState = LocalHazeState.current
    val themeSettings = LocalAppUiConfiguration.current.theme
    val composeEngine = LegadoTheme.composeEngine
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    val containerColor = if (!isMiuix) {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.containerColor() }
    } else {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.getMiuixAppBarColor() }
    }

    val scrolledColor = if (!isMiuix) {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.scrolledContainerColor() }
    } else {
        GlassDefaults.secondaryColorOr { GlassTopAppBarDefaults.getMiuixAppBarColor() }
    }

    val animatedColor = if (!isMiuix) {
        val fraction = scrollBehavior?.collapsedFraction ?: 0f
        lerp(containerColor, scrolledColor, fraction)
    } else {
        containerColor
    }

    val finalModifier = if (hazeState != null) {
        modifier
            .background(color = animatedColor)
            .responsiveHazeEffect(state = hazeState)
    } else {
        modifier.background(color = animatedColor)
    }

    val transparentColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent
    )
    val subtitleText = subtitle?.takeIf { it.isNotBlank() }

    Column(
        modifier = finalModifier
    ) {
        when {
            isMiuix -> {
                // Reserve constant status-bar space (ignoring visibility) instead
                // of MiuixTopAppBar's default animating status-bar padding. This
                // matches Material3's TopAppBar (systemBarsForVisualComponents) so
                // the bar/content doesn't reflow down when the status bar is
                // re-shown — e.g. returning from a reader that hid it.
                MiuixTopAppBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
                    title = title,
                    subtitle = subtitleText.orEmpty(),
                    navigationIcon = navigationIcon,
                    actions = actions,
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    scrollBehavior = (scrollBehavior as? MiuixGlassScrollBehavior)?.miuixBehavior
                )
            }

            else -> {
                if (themeSettings.useFlexibleTopAppBar) {
                    MediumFlexibleTopAppBar(
                        modifier = Modifier,
                        title = {
                            AdaptiveAnimatedText(
                                text = title,
                                useCharMode = useCharMode,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        subtitle = subtitleText?.let { text ->
                            {
                                AnimatedTextLine(text = text)
                            }
                        },
                        navigationIcon = navigationIcon,
                        actions = {
                            Box(modifier = Modifier.padding(end = 12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) { actions() }
                            }
                        },
                        scrollBehavior = (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior,
                        colors = transparentColors
                    )
                } else {
                    TopAppBar(
                        modifier = Modifier,
                        title = {
                            Column {
                                AdaptiveAnimatedText(
                                    text = title,
                                    useCharMode = useCharMode,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                subtitleText?.let { text ->
                                    AnimatedTextLine(
                                        text = text,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = navigationIcon,
                        actions = {
                            Box(modifier = Modifier.padding(end = 12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    actions()
                                }
                            }
                        },
                        scrollBehavior = (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior,
                        colors = transparentColors
                    )
                }
            }
        }

        bottomContent?.invoke(this)
    }
}

object GlassTopAppBarDefaults {

    @Composable
    fun getMiuixAppBarColor(): Color {
        val baseColor = GlassDefaults.secondaryColorOr { MiuixTheme.colorScheme.surface }
        return GlassDefaults.glassColor(
            noBlurColor = baseColor,
            blurAlpha = GlassDefaults.TransparentAlpha
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun defaultScrollBehavior(): GlassTopAppBarScrollBehavior {
        val configuration = LocalAppUiConfiguration.current
        val composeEngine = configuration.appShell.composeEngine

        return if (ThemeResolver.isMiuixEngine(composeEngine)) {
            val miuixBehavior = MiuixScrollBehavior()
            remember(miuixBehavior) { MiuixGlassScrollBehavior(miuixBehavior) }
        } else if (configuration.theme.useFlexibleTopAppBar) {
            val m3Behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            remember(m3Behavior) { M3GlassScrollBehavior(m3Behavior) }
        } else {
            val m3Behavior = TopAppBarDefaults.pinnedScrollBehavior()
            remember(m3Behavior) { M3GlassScrollBehavior(m3Behavior) }
        }
    }

    @Composable
    fun glassColors(): TopAppBarColors {

        val containerBaseColor = GlassDefaults.secondaryColorOr {
            MaterialTheme.colorScheme.surface
        }
        val containerColor = GlassDefaults.glassColor(
            noBlurColor = containerBaseColor,
            blurAlpha = GlassDefaults.TransparentAlpha
        )

        val scrolledBaseColor = GlassDefaults.secondaryColorOr {
            MaterialTheme.colorScheme.surfaceContainer
        }
        val scrolledContainerColor = if (LocalAppUiConfiguration.current.theme.enableBlur) {
            scrolledBaseColor.copy(alpha = GlassDefaults.TransparentAlpha)
        } else {
            scrolledBaseColor
        }

        return TopAppBarDefaults.topAppBarColors(
            containerColor = applyTopBarOpacity(containerColor),
            scrolledContainerColor = applyTopBarOpacity(scrolledContainerColor)
        )
    }

    @Composable
    fun containerColor(): Color {
        val baseColor = GlassDefaults.secondaryColorOr { MaterialTheme.colorScheme.surface }
        val glassColor = GlassDefaults.glassColor(
            noBlurColor = baseColor,
            blurAlpha = GlassDefaults.TransparentAlpha
        )
        return applyTopBarOpacity(glassColor)
    }

    @Composable
    fun scrolledContainerColor(): Color {
        val baseColor = GlassDefaults.secondaryColorOr {
            MaterialTheme.colorScheme.surfaceContainer
        }
        val glassColor = GlassDefaults.glassColor(
            noBlurColor = baseColor,
            blurAlpha = GlassDefaults.TransparentAlpha
        )
        return applyTopBarOpacity(glassColor)
    }

    @Composable
    fun controlContainerColor(): Color {
        val baseColor = GlassDefaults.glassColor(
            noBlurColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            blurAlpha = GlassDefaults.DefaultBlurAlpha
        )
        return applyTopBarOpacity(baseColor)
    }

    @Composable
    private fun applyTopBarOpacity(color: Color): Color {
        val opacity = (LocalAppUiConfiguration.current.theme.topBarOpacity.coerceIn(0, 100)) / 100f
        return color.copy(alpha = (color.alpha * opacity).coerceIn(0f, 1f))
    }
}
