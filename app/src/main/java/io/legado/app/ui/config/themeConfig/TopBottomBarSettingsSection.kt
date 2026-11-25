package io.legado.app.ui.config.themeConfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.feature.settings.search.SearchableSetting
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem

@Composable
internal fun TopBarSettingsSection(
    theme: ThemeSettings,
    onIntent: (ThemeConfigIntent) -> Unit,
) {
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) =
        onIntent(ThemeConfigIntent.UpdateTheme(transform))

    val engine = LegadoTheme.composeEngine
    val isMiuixEngine = engine == ComposeEngine.Miuix
    val nativeEffectsSupported = ThemeResolver.isBlurSupported()

    if (!isMiuixEngine) {
        SearchableSetting("theme.use_flexible_top_bar") {
            SwitchSettingItem(
                title = stringResource(R.string.use_flexible_top_bar),
                checked = theme.useFlexibleTopAppBar,
                onCheckedChange = { checked ->
                    updateTheme { current -> current.copy(useFlexibleTopAppBar = checked) }
                },
            )
        }
    }
    if (engine == ComposeEngine.Material) {
        SearchableSetting("theme.top_bar_button_style") {
            DropdownListSettingItem(
                title = stringResource(R.string.top_bar_button_style),
                selectedValue = theme.topBarButtonStyle,
                displayEntries = stringArrayResource(R.array.top_bar_button_style),
                entryValues = stringArrayResource(R.array.top_bar_button_style_value),
                onValueChange = { value -> updateTheme { it.copy(topBarButtonStyle = value) } },
            )
        }
        AnimatedVisibility(visible = theme.topBarButtonStyle != "plain") {
            SearchableSetting("theme.merge_top_bar_actions") {
                SwitchSettingItem(
                    title = stringResource(R.string.merge_top_bar_actions),
                    checked = theme.mergeTopBarActions,
                    onCheckedChange = { checked ->
                        updateTheme { current -> current.copy(mergeTopBarActions = checked) }
                    },
                )
            }
        }
    } else if (engine == ComposeEngine.Mochi) {
        SearchableSetting("theme.top_bar_button_liquid_glass") {
            SwitchSettingItem(
                title = stringResource(R.string.top_bar_button_liquid_glass),
                description = stringResource(R.string.top_bar_button_liquid_glass_summary),
                checked = nativeEffectsSupported && theme.mochiTopBarLiquidGlass,
                enabled = nativeEffectsSupported,
                onCheckedChange = { checked ->
                    updateTheme { it.copy(mochiTopBarLiquidGlass = checked) }
                },
            )
        }
    }

    when (engine) {
        ComposeEngine.Material -> {
            SearchableSetting("theme.is_blur_enable") {
                SwitchSettingItem(
                    title = stringResource(R.string.is_blur_enable),
                    checked = theme.enableBlur,
                    onCheckedChange = { onIntent(ThemeConfigIntent.SetBlurEnabled(it)) },
                )
            }
            AnimatedVisibility(visible = theme.enableBlur) {
                SearchableSetting("theme.is_blur_progressive_enable") {
                    SwitchSettingItem(
                        title = stringResource(R.string.is_blur_progressive_enable),
                        checked = theme.enableProgressiveBlur,
                        onCheckedChange = { enabled ->
                            updateTheme { it.copy(enableProgressiveBlur = enabled) }
                        },
                    )
                }
            }
        }
        ComposeEngine.Mochi -> {
            SearchableSetting("theme.is_blur_enable") {
                SwitchSettingItem(
                    title = stringResource(R.string.theme_top_bar_background_blur),
                    description = stringResource(R.string.theme_top_bar_background_blur_summary),
                    checked = nativeEffectsSupported && theme.mochiScrollEdgeBlurEnabled,
                    enabled = nativeEffectsSupported,
                    onCheckedChange = { enabled ->
                        updateTheme { it.copy(mochiScrollEdgeBlurEnabled = enabled) }
                    },
                )
            }
        }
        else -> Unit
    }

    val adjustableBlur =
        (engine == ComposeEngine.Material && theme.enableBlur) ||
            (isMiuixEngine && nativeEffectsSupported && !theme.useHyperOs4Components)
    val showOpacity =
        (engine == ComposeEngine.Material && !theme.enableBlur) ||
            (engine == ComposeEngine.Mochi && !(nativeEffectsSupported && theme.mochiScrollEdgeBlurEnabled))
    if (adjustableBlur) {
        SearchableSetting("theme.theme_manage_top_bar_blur_radius") {
            SliderSettingItem(
                title = stringResource(R.string.theme_manage_top_bar_blur_radius),
                description = stringResource(R.string.theme_config_blur_radius_performance_summary),
                value = theme.topBarBlurRadius.toFloat(),
                defaultValue = 24f,
                valueRange = 0f..30f,
                onValueChange = { value ->
                    updateTheme { current -> current.copy(topBarBlurRadius = value.toInt()) }
                },
            )
        }
        SearchableSetting("theme.theme_manage_top_bar_blur_opacity") {
            SliderSettingItem(
                title = stringResource(R.string.theme_manage_top_bar_blur_opacity),
                value = theme.topBarBlurAlpha.toFloat(),
                defaultValue = 73f,
                valueRange = 0f..100f,
                onValueChange = { value ->
                    updateTheme { current -> current.copy(topBarBlurAlpha = value.toInt()) }
                },
            )
        }
    } else if (showOpacity) {
        SearchableSetting("theme.top_bar_opacity") {
            SliderSettingItem(
                title = stringResource(R.string.top_bar_opacity),
                description = stringResource(
                    R.string.top_bar_opacity_summary,
                    theme.topBarOpacity
                ),
                value = theme.topBarOpacity.toFloat(),
                defaultValue = 100f,
                valueRange = 0f..100f,
                onValueChange = { value ->
                    updateTheme { current -> current.copy(topBarOpacity = value.toInt()) }
                },
            )
        }
    }
}

@Composable
internal fun BottomBarSettingsSection(
    appShell: AppShellSettings,
    theme: ThemeSettings,
    useNavigationRail: Boolean,
    onIntent: (ThemeConfigIntent) -> Unit,
    navigationAppearance: @Composable () -> Unit = {},
) {
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) =
        onIntent(ThemeConfigIntent.UpdateTheme(transform))

    val engine = LegadoTheme.composeEngine
    val isCoui = engine == ComposeEngine.Coui
    val isMiuix = engine == ComposeEngine.Miuix
    val nativeEffectsSupported = ThemeResolver.isBlurSupported()
    val useFloatingBottomBar = ThemeResolver.useFloatingBottomBar(engine, appShell.useFloatingBottomBar)

    if (engine == ComposeEngine.Oxygen) {
        SearchableSetting("theme.use_pager_top_bar") {
            SwitchSettingItem(
                title = stringResource(R.string.use_pager_top_bar),
                description = stringResource(R.string.use_pager_top_bar_summary),
                checked = appShell.usePagerTopBar,
                onCheckedChange = { onIntent(ThemeConfigIntent.SetUsePagerTopBar(it)) },
            )
        }
        if (appShell.usePagerTopBar) return
    }
    SearchableSetting("theme.show_bottom_nav") {
        SwitchSettingItem(
            title = stringResource(R.string.show_bottom_nav),
            description = stringResource(R.string.be_swiped),
            checked = appShell.showBottomView,
            onCheckedChange = { onIntent(ThemeConfigIntent.SetShowBottomView(it)) },
        )
    }
    if (!appShell.showBottomView) return
    if (useNavigationRail) {
        navigationAppearance()
        return
    }

    SearchableSetting("theme.floating_bottom_bar") {
        SwitchSettingItem(
            title = stringResource(R.string.floating_bottom_bar),
            description = stringResource(
                if (isCoui) R.string.coui_floating_bottom_bar_summary
                else R.string.floating_bottom_bar_summary,
            ),
            checked = useFloatingBottomBar,
            enabled = !isCoui,
            onCheckedChange = { onIntent(ThemeConfigIntent.SetUseFloatingBottomBar(it)) },
        )
    }

    navigationAppearance()

    val showGlassSetting = useFloatingBottomBar && engine != ComposeEngine.Oxygen
    val useGlass = showGlassSetting && nativeEffectsSupported && appShell.useFloatingBottomBarLiquidGlass
    if (showGlassSetting) {
        SearchableSetting("theme.floating_bottom_bar_liquid_glass") {
            SwitchSettingItem(
                title = stringResource(R.string.floating_bottom_bar_liquid_glass),
                description = stringResource(R.string.floating_bottom_bar_liquid_glass_summary),
                checked = useGlass,
                enabled = nativeEffectsSupported,
                onCheckedChange = {
                    onIntent(ThemeConfigIntent.SetUseFloatingBottomBarLiquidGlass(it))
                },
            )
        }
    }
    if (useGlass && (engine == ComposeEngine.Material || isMiuix)) {
        SearchableSetting("theme.theme_config_bottom_bar_lens_radius") {
            SliderSettingItem(
                title = stringResource(R.string.theme_config_bottom_bar_lens_radius),
                description = stringResource(R.string.theme_config_bottom_bar_lens_radius_summary),
                value = theme.bottomBarLensRadius,
                defaultValue = 24f,
                valueRange = 0f..50f,
                onValueChange = { value ->
                    updateTheme { current -> current.copy(bottomBarLensRadius = value) }
                },
            )
        }
    }
    if ((useGlass && engine == ComposeEngine.Material) ||
        (isMiuix && nativeEffectsSupported && (!useFloatingBottomBar || useGlass))
    ) {
        SearchableSetting("theme.theme_manage_bottom_bar_blur_radius") {
            SliderSettingItem(
                title = stringResource(R.string.theme_manage_bottom_bar_blur_radius),
                description = stringResource(R.string.theme_config_blur_radius_performance_summary),
                value = theme.bottomBarBlurRadius.toFloat(),
                defaultValue = 8f,
                valueRange = 0f..10f,
                onValueChange = { value ->
                    updateTheme { current -> current.copy(bottomBarBlurRadius = value.toInt()) }
                },
            )
        }
        SearchableSetting("theme.theme_manage_bottom_bar_blur_opacity") {
            SliderSettingItem(
                title = stringResource(R.string.theme_manage_bottom_bar_blur_opacity),
                value = theme.bottomBarBlurAlpha.toFloat(),
                defaultValue = 40f,
                valueRange = 0f..100f,
                onValueChange = { value ->
                    updateTheme { current -> current.copy(bottomBarBlurAlpha = value.toInt()) }
                },
            )
        }
    } else if (!useFloatingBottomBar &&
        ((engine == ComposeEngine.Material && !theme.enableBlur) || engine == ComposeEngine.Oxygen)
    ) {
        SearchableSetting("theme.bottom_bar_opacity") {
            SliderSettingItem(
                title = stringResource(R.string.bottom_bar_opacity),
                description = stringResource(
                    R.string.bottom_bar_opacity_summary,
                    theme.bottomBarOpacity
                ),
                value = theme.bottomBarOpacity.toFloat(),
                defaultValue = 100f,
                valueRange = 0f..100f,
                onValueChange = { value ->
                    updateTheme { current -> current.copy(bottomBarOpacity = value.toInt()) }
                },
            )
        }
    }
}
