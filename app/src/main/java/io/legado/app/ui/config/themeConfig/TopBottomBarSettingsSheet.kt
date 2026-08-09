package io.legado.app.ui.config.themeConfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.ThemeSettings
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
                .verticalScroll(rememberScrollState())
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
            CompactSwitchSettingItem(
                title = stringResource(R.string.show_bottom_nav),
                description = stringResource(R.string.be_swiped),
                checked = appShell.showBottomView,
                onCheckedChange = { onIntent(ThemeConfigIntent.SetShowBottomView(it)) },
            )
            CompactSwitchSettingItem(
                title = stringResource(R.string.floating_bottom_bar),
                description = stringResource(R.string.floating_bottom_bar_summary),
                checked = appShell.useFloatingBottomBar,
                onCheckedChange = { onIntent(ThemeConfigIntent.SetUseFloatingBottomBar(it)) },
            )
            AnimatedVisibility(visible = appShell.useFloatingBottomBar) {
                Column {
                    CompactSwitchSettingItem(
                        title = stringResource(R.string.floating_bottom_bar_liquid_glass),
                        description = stringResource(R.string.floating_bottom_bar_liquid_glass_summary),
                        checked = appShell.useFloatingBottomBarLiquidGlass,
                        onCheckedChange = {
                            onIntent(ThemeConfigIntent.SetUseFloatingBottomBarLiquidGlass(it))
                        },
                    )
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
            if (theme.enableBlur) {
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
                SliderSettingItem(
                    title = stringResource(R.string.theme_manage_top_bar_blur_opacity),
                    value = theme.topBarBlurAlpha.toFloat(),
                    defaultValue = 73f,
                    valueRange = 0f..100f,
                    onValueChange = { value ->
                        updateTheme { current -> current.copy(topBarBlurAlpha = value.toInt()) }
                    },
                )
                SliderSettingItem(
                    title = stringResource(R.string.theme_manage_bottom_bar_blur_opacity),
                    value = theme.bottomBarBlurAlpha.toFloat(),
                    defaultValue = 40f,
                    valueRange = 0f..100f,
                    onValueChange = { value ->
                        updateTheme { current -> current.copy(bottomBarBlurAlpha = value.toInt()) }
                    },
                )
            } else if (!isMiuixEngine) {
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
