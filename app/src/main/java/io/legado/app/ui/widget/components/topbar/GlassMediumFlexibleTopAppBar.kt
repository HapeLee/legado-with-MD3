package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeEffect
import io.legado.app.ui.widget.components.AdaptiveAnimatedText
import io.legado.app.ui.widget.components.GlassDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlassMediumFlexibleTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    useCharMode: Boolean = false,
    subtitle: (@Composable () -> Unit)? = null,
    scrollBehavior: GlassTopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable (ColumnScope.() -> Unit)? = null
) {

    val hazeState = LocalHazeState.current
    val composeEngine = LegadoTheme.composeEngine
    val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)

    val containerColor = if (!isMiuix) {
        GlassTopAppBarDefaults.containerColor()
    } else {
        GlassTopAppBarDefaults.getMiuixAppBarColor()
    }

    val scrolledColor = if (!isMiuix) {
        GlassTopAppBarDefaults.scrolledContainerColor()
    } else {
        GlassTopAppBarDefaults.getMiuixAppBarColor()
    }

    val animatedColor = if (!isMiuix) {
        val fraction = scrollBehavior?.collapsedFraction ?: 0f
        lerp(containerColor, scrolledColor, fraction)
    } else {
        containerColor
    }

    val finalModifier = if (hazeState != null) {
        modifier.responsiveHazeEffect(state = hazeState)
    } else {
        modifier.background(color = animatedColor)
    }

    val transparentColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent
    )

    Column(
        modifier = finalModifier
    ) {
        when {
            isMiuix -> {
                MiuixTopAppBar(
                    modifier = Modifier,
                    title = title,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    color = Color.Transparent, // 内部强制透明，透出 Column 背景
                    scrollBehavior = (scrollBehavior as? MiuixGlassScrollBehavior)?.miuixBehavior
                )
            }

            else -> {
                if (ThemeConfig.useFlexibleTopAppBar) {
                    MediumFlexibleTopAppBar(
                        modifier = Modifier,
                        title = {
                            AdaptiveAnimatedText(
                                text = title,
                                useCharMode = useCharMode,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        subtitle = subtitle,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior,
                        colors = transparentColors // 应用透明背景
                    )
                } else {
                    TopAppBar(
                        modifier = Modifier,
                        title = {
                            AdaptiveAnimatedText(
                                text = title,
                                useCharMode = useCharMode,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = (scrollBehavior as? M3GlassScrollBehavior)?.m3Behavior,
                        colors = transparentColors // 应用透明背景
                    )
                }
            }
        }

        // 现在的 bottomContent 完美包裹在外层 Column 内，共享渐变和模糊效果！
        bottomContent?.invoke(this)
    }
}

object GlassTopAppBarDefaults {

    @Composable
    fun getMiuixAppBarColor() = GlassDefaults.glassColor(
        noBlurColor = MiuixTheme.colorScheme.surface,
        blurAlpha = GlassDefaults.TransparentAlpha
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun defaultScrollBehavior(): GlassTopAppBarScrollBehavior {
        val composeEngine = ThemeConfig.composeEngine

        return if (ThemeResolver.isMiuixEngine(composeEngine)) {
            MiuixGlassScrollBehavior(MiuixScrollBehavior())
        } else if (ThemeConfig.useFlexibleTopAppBar) {
            M3GlassScrollBehavior(TopAppBarDefaults.exitUntilCollapsedScrollBehavior())
        } else {
            M3GlassScrollBehavior(TopAppBarDefaults.pinnedScrollBehavior())
        }
    }

    @Composable
    fun glassColors(): TopAppBarColors {

        val containerColor = GlassDefaults.glassColor(
            noBlurColor = MaterialTheme.colorScheme.surface,
            blurAlpha = GlassDefaults.TransparentAlpha
        )

        val scrolledContainerColor = if (ThemeConfig.enableBlur) {
            MaterialTheme.colorScheme.surface.copy(alpha = GlassDefaults.TransparentAlpha)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }

        return TopAppBarDefaults.topAppBarColors(
            containerColor = applyTopBarOpacity(containerColor),
            scrolledContainerColor = applyTopBarOpacity(scrolledContainerColor)
        )
    }

    @Composable
    fun containerColor(): Color {
        val baseColor = GlassDefaults.glassColor(
            noBlurColor = MaterialTheme.colorScheme.surface,
            blurAlpha = GlassDefaults.TransparentAlpha
        )
        return applyTopBarOpacity(baseColor)
    }

    @Composable
    fun scrolledContainerColor(): Color {
        val baseColor = GlassDefaults.glassColor(
            noBlurColor = MaterialTheme.colorScheme.surfaceContainer,
            blurAlpha = GlassDefaults.TransparentAlpha
        )
        return applyTopBarOpacity(baseColor)
    }

    @Composable
    fun controlContainerColor(): Color {
        val baseColor = GlassDefaults.glassColor(
            noBlurColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            blurAlpha = GlassDefaults.DefaultBlurAlpha
        )
        return applyTopBarOpacity(baseColor)
    }

    private fun applyTopBarOpacity(color: Color): Color {
        val opacity = (ThemeConfig.topBarOpacity.coerceIn(0, 100)) / 100f
        return color.copy(alpha = (color.alpha * opacity).coerceIn(0f, 1f))
    }
}
