package io.legado.app.ui.config.themeConfig

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/**
 * 「顶栏与底栏」区块：折叠应用栏、顶栏按钮形态、底部导航与顶/底栏模糊参数。
 *
 * 直接内联在外观设置页的独立分组里（不再用浮层承载）。所有条件分支沿用原有判定：
 * 引擎不提供的形态不出现，模糊能力不可用时退回不透明度。
 */
@Composable
fun TopBottomBarSettingsSection(
    appShell: AppShellSettings,
    theme: ThemeSettings,
    onIntent: (ThemeConfigIntent) -> Unit,
) {
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) =
        onIntent(ThemeConfigIntent.UpdateTheme(transform))

    val engine = LegadoTheme.composeEngine
    val isMiuixEngine = engine == ComposeEngine.Miuix
    val isCoui = engine == ComposeEngine.Coui
    val nativeEffectsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val useFloatingBottomBar =
        ThemeResolver.useFloatingBottomBar(engine, appShell.useFloatingBottomBar)
    val miuixGlassStyle = isMiuixEngine && nativeEffectsSupported && theme.useHyperOs4Components

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    checked = theme.mochiTopBarLiquidGlass,
                    enabled = nativeEffectsSupported,
                    onCheckedChange = { checked ->
                        updateTheme { it.copy(mochiTopBarLiquidGlass = checked) }
                    },
                )
            }
        }
        if (engine == ComposeEngine.Oxygen) {
            SearchableSetting("theme.use_pager_top_bar") {
                SwitchSettingItem(
                    title = stringResource(R.string.use_pager_top_bar),
                    description = stringResource(R.string.use_pager_top_bar_summary),
                    checked = appShell.usePagerTopBar,
                    onCheckedChange = { onIntent(ThemeConfigIntent.SetUsePagerTopBar(it)) },
                )
            }
        }
        // 横划顶栏接管主页导航后底栏必然隐藏，相关开关与底栏参数不再有意义。
        if (engine != ComposeEngine.Oxygen || !appShell.usePagerTopBar) {
            SearchableSetting("theme.show_bottom_nav") {
                SwitchSettingItem(
                    title = stringResource(R.string.show_bottom_nav),
                    description = stringResource(R.string.be_swiped),
                    checked = appShell.showBottomView,
                    onCheckedChange = { onIntent(ThemeConfigIntent.SetShowBottomView(it)) },
                )
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
            AnimatedVisibility(visible = useFloatingBottomBar) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SearchableSetting("theme.floating_bottom_bar_liquid_glass") {
                        SwitchSettingItem(
                            title = stringResource(R.string.floating_bottom_bar_liquid_glass),
                            description = stringResource(R.string.floating_bottom_bar_liquid_glass_summary),
                            checked = appShell.useFloatingBottomBarLiquidGlass,
                            onCheckedChange = {
                                onIntent(ThemeConfigIntent.SetUseFloatingBottomBarLiquidGlass(it))
                            },
                        )
                    }
                    if (engine != ComposeEngine.Mochi && !isCoui) {
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
                }
            }
        }

        val blurSupported = engine == ComposeEngine.Material || nativeEffectsSupported
        val blurEnabled = when (engine) {
            ComposeEngine.Mochi -> theme.mochiScrollEdgeBlurEnabled
            // Miuix 的顶/底栏模糊常开，半径设置始终有意义。
            ComposeEngine.Miuix -> true
            else -> theme.enableBlur
        }
        if (blurEnabled && blurSupported && engine != ComposeEngine.Mochi && !isCoui) {
            if (!miuixGlassStyle) {
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
            }
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
            if (!miuixGlassStyle) {
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
        } else if (!isMiuixEngine && (!blurEnabled || !blurSupported)) {
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
            if (!isCoui) {
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
    }
}
