package io.legado.app.ui.config.themeConfig

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import io.legado.app.ui.widget.components.coui.appVerticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem

@Composable
fun TopBottomBarSettingsSheet(
    show: Boolean,
    appShell: AppShellSettings,
    theme: ThemeSettings,
    isMiuixEngine: Boolean,
    onDismissRequest: () -> Unit,
    onIntent: (ThemeConfigIntent) -> Unit,
) {
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) =
        onIntent(ThemeConfigIntent.UpdateTheme(transform))

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.top_bottom_bar_settings),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .appVerticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!isMiuixEngine) {
                CompactSwitchSettingItem(
                    title = stringResource(R.string.use_flexible_top_bar),
                    checked = theme.useFlexibleTopAppBar,
                    onCheckedChange = { checked ->
                        updateTheme { current -> current.copy(useFlexibleTopAppBar = checked) }
                    },
                )
            }
            val engine = LegadoTheme.composeEngine
            val isCoui = engine == ComposeEngine.Coui
            val useFloatingBottomBar = ThemeResolver.useFloatingBottomBar(engine, appShell.useFloatingBottomBar)
            val nativeEffectsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val miuixGlassStyle = engine == ComposeEngine.Miuix &&
                nativeEffectsSupported && theme.useHyperOs4Components
            if (engine == ComposeEngine.Material) {
                CompactDropdownSettingItem(
                    title = stringResource(R.string.top_bar_button_style),
                    selectedValue = theme.topBarButtonStyle,
                    displayEntries = stringArrayResource(R.array.top_bar_button_style),
                    entryValues = stringArrayResource(R.array.top_bar_button_style_value),
                    onValueChange = { value -> updateTheme { it.copy(topBarButtonStyle = value) } },
                )
                AnimatedVisibility(visible = theme.topBarButtonStyle != "plain") {
                    CompactSwitchSettingItem(
                        title = stringResource(R.string.merge_top_bar_actions),
                        checked = theme.mergeTopBarActions,
                        onCheckedChange = { checked ->
                            updateTheme { current -> current.copy(mergeTopBarActions = checked) }
                        },
                    )
                }
            } else if (engine == ComposeEngine.Mochi) {
                CompactSwitchSettingItem(
                    title = stringResource(R.string.top_bar_button_liquid_glass),
                    description = stringResource(R.string.top_bar_button_liquid_glass_summary),
                    checked = theme.mochiTopBarLiquidGlass,
                    enabled = nativeEffectsSupported,
                    onCheckedChange = { checked ->
                        updateTheme { it.copy(mochiTopBarLiquidGlass = checked) }
                    },
                )
            }
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_bottom_nav),
                description = stringResource(R.string.be_swiped),
                checked = appShell.showBottomView,
                onCheckedChange = { onIntent(ThemeConfigIntent.SetShowBottomView(it)) },
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.floating_bottom_bar),
                description = stringResource(
                    if (isCoui) R.string.coui_floating_bottom_bar_summary else R.string.floating_bottom_bar_summary,
                ),
                checked = useFloatingBottomBar,
                enabled = !isCoui,
                onCheckedChange = { onIntent(ThemeConfigIntent.SetUseFloatingBottomBar(it)) },
            )
            AnimatedVisibility(visible = useFloatingBottomBar) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactSwitchSettingItem(
                        title = stringResource(R.string.floating_bottom_bar_liquid_glass),
                        description = stringResource(R.string.floating_bottom_bar_liquid_glass_summary),
                        checked = appShell.useFloatingBottomBarLiquidGlass,
                        onCheckedChange = {
                            onIntent(ThemeConfigIntent.SetUseFloatingBottomBarLiquidGlass(it))
                        },
                    )
                    if (engine != ComposeEngine.Mochi && engine != ComposeEngine.Coui) {
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
            val blurSupported = engine == ComposeEngine.Material || nativeEffectsSupported
            val blurEnabled = when (engine) {
                ComposeEngine.Mochi -> theme.mochiScrollEdgeBlurEnabled
                // Miuix 的顶/底栏模糊常开，半径设置始终有意义。
                ComposeEngine.Miuix -> true
                else -> theme.enableBlur
            }
            if (blurEnabled && blurSupported && engine != ComposeEngine.Mochi && engine != ComposeEngine.Coui) {
                if (!miuixGlassStyle) {
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
                if (!miuixGlassStyle) {
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
                SliderSettingItem(
                    title = stringResource(R.string.theme_manage_bottom_bar_blur_opacity),
                    value = theme.bottomBarBlurAlpha.toFloat(),
                    defaultValue = 40f,
                    valueRange = 0f..100f,
                    onValueChange = { value ->
                        updateTheme { current -> current.copy(bottomBarBlurAlpha = value.toInt()) }
                    },
                )
            } else if (!isMiuixEngine && (!blurEnabled || !blurSupported)) {
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
                if (!isCoui) {
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
