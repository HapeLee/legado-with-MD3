package io.legado.app.ui.config.themeConfig

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import io.legado.app.R
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.FontFolderState
import io.legado.app.ui.widget.components.FontSelectSheet
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThemeConfigScreen(
    state: ThemeConfigUiState,
    onIntent: (ThemeConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToCustomTheme: () -> Unit,
    onNavigateToThemeManage: () -> Unit,
) {
    val appShell = state.appShell
    val theme = state.theme
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) =
        onIntent(ThemeConfigIntent.UpdateTheme(transform))
    val fontFolderState = remember(state.fontFolder) {
        FontFolderState.Loaded(state.fontFolder.takeIf { it.isNotEmpty() }?.let(android.net.Uri::parse))
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.theme_setting),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                val composeEngine = appShell.composeEngine
                val isMiuixEngine = remember(composeEngine) {
                    ThemeResolver.isMiuixEngine(composeEngine)
                }
                val isDarkTheme = LegadoTheme.isDark

                if (!isMiuixEngine) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeCard(
                            context = context,
                            value = theme.appTheme,
                            isDark = isDarkTheme,
                            isAmoled = theme.isPureBlack,
                            paletteStyle = theme.paletteStyle,
                            customLightSeedColor = theme.customPrimary,
                            customNightSeedColor = theme.customNightPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val themeItems = stringArrayResource(R.array.themes_item)
                val themeValues = stringArrayResource(R.array.themes_value)
                val themes = remember(themeItems, themeValues) {
                    themeItems.zip(themeValues).toList()
                }

                AnimatedVisibility(visible = theme.showRefactorTip) {
                    GlassCard(
                        cornerRadius = 16.dp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            AppText(
                                text = "仍有部分界面未用Compose重构，这些界面会与大部分界面有较大差异。",
                                style = LegadoTheme.typography.labelLargeEmphasized,
                                modifier = Modifier.weight(1f)
                            )
                            SmallPlainButton(
                                icon = AppIcons.Close,
                                contentDescription = stringResource(R.string.close),
                                onClick = {
                                    onIntent(ThemeConfigIntent.DismissRefactorTip)
                                }
                            )
                        }
                    }
                }


                SplicedColumnGroup(title = stringResource(R.string.theme)) {
                    if (isMiuixEngine) {
                        DropdownListSettingItem(
                            title = stringResource(R.string.theme_mode),
                            selectedValue = appShell.themeMode,
                            displayEntries = stringArrayResource(R.array.theme_mode),
                            entryValues = stringArrayResource(R.array.theme_mode_v),
                            onValueChange = { mode ->
                                onIntent(
                                    ThemeConfigIntent.SetThemeMode(mode)
                                )
                            }
                        )

                        SwitchSettingItem(
                            title = stringResource(R.string.miuix_monet),
                            description = stringResource(R.string.miuix_monet_summary),
                            checked = theme.useMiuixMonet,
                            onCheckedChange = {
                                onIntent(ThemeConfigIntent.SetMiuixMonet(it))
                            }
                        )

                        if (theme.useMiuixMonet) {
                            SwitchSettingItem(
                                title = stringResource(R.string.dynamic_colors),
                                description = stringResource(R.string.dynamic_colors_summary),
                                checked = theme.appTheme == "0",
                                onCheckedChange = {
                                    onIntent(ThemeConfigIntent.SetDynamicColors(it))
                                }
                            )
                        }
                    } else {
                        ThemeModeSelector(
                            selectedMode = appShell.themeMode,
                            onModeSelected = { mode ->
                                onIntent(
                                    ThemeConfigIntent.SetThemeMode(mode)
                                )
                            }
                        )
                    }

                    if (!isMiuixEngine) {
                        Spacer(modifier = Modifier.height(16.dp))

                        val visibleThemes = themes.filter { (_, value) ->
                            value != "4" || state.showEInkTheme
                        }
                        ThemeColorSelector(
                            context = context,
                            themes = visibleThemes,
                            selectedTheme = theme.appTheme,
                            isDark = isDarkTheme,
                            isAmoled = theme.isPureBlack,
                            paletteStyle = theme.paletteStyle,
                            customLightSeedColor = theme.customPrimary,
                            customNightSeedColor = theme.customNightPrimary,
                            onThemeSelected = {
                                onIntent(ThemeConfigIntent.SelectTheme(it))
                            }
                        )
                    }
                }

                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.font_setting),
                        onClick = { onIntent(ThemeConfigIntent.ShowSheet(ThemeConfigSheet.Font)) }
                    )
                    if (theme.appTheme == "12" && (!isMiuixEngine || theme.useMiuixMonet)) {
                        ClickableSettingItem(
                            title = stringResource(R.string.custom_theme_colors),
                            onClick = onNavigateToCustomTheme
                        )
                    }
                    DropdownListSettingItem(
                        title = stringResource(R.string.compose_engine),
                        selectedValue = appShell.composeEngine,
                        displayEntries = stringArrayResource(R.array.composeEngine),
                        entryValues = stringArrayResource(R.array.composeEngine_value),
                        onValueChange = {
                            onIntent(
                                ThemeConfigIntent.SetComposeEngine(it)
                            )
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.change_icon),
                        description = stringResource(R.string.change_icon_summary),
                        onClick = {
                            onIntent(ThemeConfigIntent.ShowSheet(ThemeConfigSheet.LauncherIcon))
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.predictive_back),
                        description = stringResource(R.string.predictive_back_summary),
                        checked = appShell.predictiveBackEnabled,
                        onCheckedChange = {
                            onIntent(
                                ThemeConfigIntent.SetPredictiveBackEnabled(it)
                            )
                        }
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.font_scale),
                        valueLabel = {
                            context.getString(R.string.font_scale_summary, it / 10f)
                        },
                        value = appShell.fontScale.toFloat(),
                        defaultValue = 10f,
                        valueRange = 8f..16f,
                        steps = 7,
                        onValueChange = { value ->
                            onIntent(
                                ThemeConfigIntent.SetFontScale(value.toInt())
                            )
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_pack),
                        description = stringResource(R.string.theme_pack_s),
                        onClick = onNavigateToThemeManage
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.main_activity)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.main_navigation_settings),
                        description = stringResource(R.string.main_navigation_settings_summary),
                        onClick = {
                            onIntent(ThemeConfigIntent.ShowSheet(ThemeConfigSheet.MainNavigation))
                        },
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.show_status),
                        checked = appShell.showStatusBar,
                        onCheckedChange = {
                            onIntent(
                                ThemeConfigIntent.SetShowStatusBar(it)
                            )
                        }
                    )
                    //TODO:这个可以不要了，在删掉原来的设置页以后删
                    SwitchSettingItem(
                        title = stringResource(R.string.show_swipe_animation),
                        checked = appShell.swipeAnimation,
                        onCheckedChange = {
                            onIntent(
                                ThemeConfigIntent.SetSwipeAnimation(it)
                            )
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.show_bottom_nav),
                        description = stringResource(R.string.be_swiped),
                        checked = appShell.showBottomView,
                        onCheckedChange = {
                            onIntent(
                                ThemeConfigIntent.SetShowBottomView(it)
                            )
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.floating_bottom_bar),
                        description = stringResource(R.string.floating_bottom_bar_summary),
                        checked = appShell.useFloatingBottomBar,
                        onCheckedChange = {
                            onIntent(
                                ThemeConfigIntent.SetUseFloatingBottomBar(it)
                            )
                        }
                    )
                    AnimatedVisibility(visible = appShell.useFloatingBottomBar) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            SwitchSettingItem(
                                title = stringResource(R.string.floating_bottom_bar_liquid_glass),
                                description = stringResource(R.string.floating_bottom_bar_liquid_glass_summary),
                                checked = appShell.useFloatingBottomBarLiquidGlass,
                                onCheckedChange = {
                                    onIntent(
                                        ThemeConfigIntent.SetUseFloatingBottomBarLiquidGlass(it)
                                    )
                                }
                            )
                            SliderSettingItem(
                                title = stringResource(R.string.theme_config_bottom_bar_lens_radius),
                                description = stringResource(R.string.theme_config_bottom_bar_lens_radius_summary),
                                value = theme.bottomBarLensRadius,
                                defaultValue = 24f,
                                valueRange = 0f..50f,
                                onValueChange = { value ->
                                    updateTheme { it.copy(bottomBarLensRadius = value) }
                                }
                            )
                        }

                    }
                    DropdownListSettingItem(
                        title = stringResource(R.string.tabletInterface),
                        selectedValue = appShell.tabletInterface,
                        displayEntries = stringArrayResource(R.array.tabletInterface),
                        entryValues = stringArrayResource(R.array.tabletInterface_value),
                        onValueChange = {
                            onIntent(
                                ThemeConfigIntent.SetTabletInterface(it)
                            )
                        }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.nav_label_mode),
                        selectedValue = appShell.labelVisibilityMode,
                        displayEntries = stringArrayResource(R.array.label_vis_mode),
                        entryValues = stringArrayResource(R.array.label_vis_mode_value),
                        onValueChange = {
                            onIntent(
                                ThemeConfigIntent.SetLabelVisibilityMode(it)
                            )
                        }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.book_info_page)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.book_info_follow_cover_color),
                        description = stringResource(R.string.book_info_follow_cover_color_summary),
                        checked = theme.bookInfoFollowCoverColor,
                        onCheckedChange = { value ->
                            updateTheme { it.copy(bookInfoFollowCoverColor = value) }
                        }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.book_info_network_cover_background),
                        selectedValue = theme.bookInfoNetworkCoverBackground,
                        displayEntries = stringArrayResource(R.array.book_info_background_blur_entries),
                        entryValues = stringArrayResource(R.array.book_info_background_blur_values),
                        onValueChange = { value ->
                            updateTheme { it.copy(bookInfoNetworkCoverBackground = value) }
                        }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.book_info_default_cover_background),
                        selectedValue = theme.bookInfoDefaultCoverBackground,
                        displayEntries = stringArrayResource(R.array.book_info_background_blur_entries),
                        entryValues = stringArrayResource(R.array.book_info_background_blur_values),
                        onValueChange = { value ->
                            updateTheme { it.copy(bookInfoDefaultCoverBackground = value) }
                        }
                    )
                }

                SplicedColumnGroup(title = stringResource(R.string.eye_protection)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.eye_protection_enabled),
                        description = stringResource(R.string.eye_protection_enabled_summary),
                        checked = theme.eyeProtectionEnabled,
                        onCheckedChange = { value ->
                            updateTheme { it.copy(eyeProtectionEnabled = value) }
                        }
                    )

                    AnimatedVisibility(visible = theme.eyeProtectionEnabled) {
                        Column {
                            SliderSettingItem(
                                title = stringResource(R.string.color_temperature),
                                description = stringResource(
                                    R.string.color_temperature_summary,
                                    theme.colorTemperature
                                ),
                                value = theme.colorTemperature.toFloat(),
                                defaultValue = 50f,
                                valueRange = 0f..100f,
                                onValueChange = { value ->
                                    updateTheme { it.copy(colorTemperature = value.toInt()) }
                                }
                            )

                            SwitchSettingItem(
                                title = stringResource(R.string.eye_protection_schedule),
                                description = stringResource(R.string.eye_protection_schedule_summary),
                                checked = theme.eyeProtectionSchedule,
                                onCheckedChange = { value ->
                                    updateTheme { it.copy(eyeProtectionSchedule = value) }
                                }
                            )

                            AnimatedVisibility(visible = theme.eyeProtectionSchedule) {
                                Column {
                                    ClickableSettingItem(
                                        title = stringResource(R.string.eye_protection_start_time),
                                        option = theme.eyeProtectionStartTime,
                                        onClick = {
                                            onIntent(
                                                ThemeConfigIntent.RequestTimePicker(
                                                    ThemeTimeField.EyeProtectionStart,
                                                    theme.eyeProtectionStartTime,
                                                )
                                            )
                                        }
                                    )
                                    ClickableSettingItem(
                                        title = stringResource(R.string.eye_protection_end_time),
                                        option = theme.eyeProtectionEndTime,
                                        onClick = {
                                            onIntent(
                                                ThemeConfigIntent.RequestTimePicker(
                                                    ThemeTimeField.EyeProtectionEnd,
                                                    theme.eyeProtectionEndTime,
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                SplicedColumnGroup(title = stringResource(R.string.compose_related)) {
                    if (!isMiuixEngine) {
                        SwitchSettingItem(
                            title = stringResource(R.string.pure_black),
                            checked = theme.isPureBlack,
                            onCheckedChange = { value ->
                                updateTheme { it.copy(isPureBlack = value) }
                            }
                        )
                        SwitchSettingItem(
                            title = stringResource(R.string.use_flexible_top_bar),
                            checked = theme.useFlexibleTopAppBar,
                            onCheckedChange = { value ->
                                updateTheme { it.copy(useFlexibleTopAppBar = value) }
                            }
                        )
                    }
                    SwitchSettingItem(
                        title = stringResource(R.string.is_blur_enable),
                        checked = theme.enableBlur,
                        onCheckedChange = { onIntent(ThemeConfigIntent.SetBlurEnabled(it)) }
                    )
                    AnimatedVisibility(visible = theme.enableBlur) {
                        SwitchSettingItem(
                            title = stringResource(R.string.is_blur_progressive_enable),
                            checked = theme.enableProgressiveBlur,
                            onCheckedChange = { value ->
                                updateTheme { it.copy(enableProgressiveBlur = value) }
                            }
                        )
                    }
                    if (theme.enableBlur) {
                        SliderSettingItem(
                            title = stringResource(R.string.theme_manage_top_bar_blur_radius),
                            description = stringResource(R.string.theme_config_blur_radius_performance_summary),
                            value = theme.topBarBlurRadius.toFloat(),
                            defaultValue = 24f,
                            valueRange = 0f..30f,
                            onValueChange = { value ->
                                updateTheme { it.copy(topBarBlurRadius = value.toInt()) }
                            }
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.theme_manage_bottom_bar_blur_radius),
                            description = stringResource(R.string.theme_config_blur_radius_performance_summary),
                            value = theme.bottomBarBlurRadius.toFloat(),
                            defaultValue = 8f,
                            valueRange = 0f..10f,
                            onValueChange = { value ->
                                updateTheme { it.copy(bottomBarBlurRadius = value.toInt()) }
                            }
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.theme_manage_top_bar_blur_opacity),
                            value = theme.topBarBlurAlpha.toFloat(),
                            defaultValue = 73f,
                            valueRange = 0f..100f,
                            onValueChange = { value ->
                                updateTheme { it.copy(topBarBlurAlpha = value.toInt()) }
                            }
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.theme_manage_bottom_bar_blur_opacity),
                            value = theme.bottomBarBlurAlpha.toFloat(),
                            defaultValue = 40f,
                            valueRange = 0f..100f,
                            onValueChange = { value ->
                                updateTheme { it.copy(bottomBarBlurAlpha = value.toInt()) }
                            }
                        )
                    }
                    AnimatedVisibility(visible = !isMiuixEngine && !theme.enableBlur) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
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
                                    updateTheme { it.copy(topBarOpacity = value.toInt()) }
                                }
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
                                    updateTheme { it.copy(bottomBarOpacity = value.toInt()) }
                                }
                            )
                        }
                    }
                    if (!isMiuixEngine) {
                        SliderSettingItem(
                            title = stringResource(R.string.container_opacity),
                            description = stringResource(
                                R.string.container_opacity_summary,
                                theme.containerOpacity
                            ),
                            value = theme.containerOpacity.toFloat(),
                            defaultValue = 100f,
                            valueRange = 0f..100f,
                            onValueChange = { value ->
                                updateTheme { it.copy(containerOpacity = value.toInt()) }
                            }
                        )
                    }
                }

                SplicedColumnGroup(title = stringResource(R.string.background_image)) {
                    val hasLightBg = !theme.backgroundImageLight.isNullOrBlank()
                    ClickableSettingItem(
                        title = stringResource(R.string.day),
                        description = if (hasLightBg) stringResource(R.string.click_to_delete) else stringResource(
                            R.string.select_image
                        ),
                        onClick = {
                            onIntent(
                                ThemeConfigIntent.ShowSheet(ThemeConfigSheet.Background(false))
                            )
                        }
                    )

                    if (hasLightBg) {
                        SliderSettingItem(
                            title = stringResource(R.string.background_image_blurring),
                            value = theme.backgroundImageBlurring.toFloat(),
                            defaultValue = 0f,
                            valueRange = 0f..100f,
                            onValueChange = { value ->
                                updateTheme { it.copy(backgroundImageBlurring = value.toInt()) }
                            }
                        )
                    }
                    val hasDarkBg = !theme.backgroundImageDark.isNullOrBlank()
                    ClickableSettingItem(
                        title = stringResource(R.string.night),
                        description = if (hasDarkBg) stringResource(R.string.click_to_delete) else stringResource(
                            R.string.select_image
                        ),
                        onClick = {
                            onIntent(
                                ThemeConfigIntent.ShowSheet(ThemeConfigSheet.Background(true))
                            )
                        }
                    )

                    if (hasDarkBg) {
                        SliderSettingItem(
                            title = stringResource(R.string.background_image_blurring),
                            value = theme.backgroundImageDarkBlurring.toFloat(),
                            defaultValue = 0f,
                            valueRange = 0f..100f,
                            onValueChange = { value ->
                                updateTheme { it.copy(backgroundImageDarkBlurring = value.toInt()) }
                            }
                        )
                    }
                }
            }

            // Container settings
            item {
                SplicedColumnGroup(title = stringResource(R.string.theme_manage_section_container)) {
                    SwitchSettingItem(
                        title = stringResource(R.string.disable_spliced_group_corner_radius),
                        description = stringResource(R.string.disable_spliced_group_corner_radius_summary),
                        checked = theme.disableSplicedColumnGroupCornerRadius,
                        onCheckedChange = { value ->
                            updateTheme {
                                it.copy(disableSplicedColumnGroupCornerRadius = value)
                            }
                        }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.base_card_corner_radius_override),
                        description = stringResource(R.string.base_card_override_summary),
                        checked = theme.overrideBaseCardCornerRadius,
                        onCheckedChange = { value ->
                            updateTheme { it.copy(overrideBaseCardCornerRadius = value) }
                        }
                    )
                    AnimatedVisibility(visible = theme.overrideBaseCardCornerRadius) {
                        SliderSettingItem(
                            title = stringResource(R.string.base_card_corner_radius),
                            description = "${theme.baseCardCornerRadius}dp",
                            value = theme.baseCardCornerRadius,
                            defaultValue = 16f,
                            valueRange = 0f..40f,
                            steps = 79,
                            decimal = true,
                            onValueChange = { value ->
                                updateTheme { it.copy(baseCardCornerRadius = value) }
                            }
                        )
                    }
                    SwitchSettingItem(
                        title = stringResource(R.string.base_card_border_override),
                        description = stringResource(R.string.base_card_override_summary),
                        checked = theme.overrideBaseCardBorder,
                        onCheckedChange = { value ->
                            updateTheme { it.copy(overrideBaseCardBorder = value) }
                        }
                    )
                    AnimatedVisibility(visible = theme.overrideBaseCardBorder) {
                        Column {
                            SliderSettingItem(
                                title = stringResource(R.string.border_width),
                                description = "${theme.baseCardBorderWidth}dp",
                                value = theme.baseCardBorderWidth,
                                defaultValue = 1f,
                                valueRange = 0f..5f,
                                steps = 49,
                                decimal = true,
                                onValueChange = { value ->
                                    updateTheme { it.copy(baseCardBorderWidth = value) }
                                }
                            )
                            BaseCardBorderColorSettingItem(
                                title = stringResource(R.string.base_card_border_color_day),
                                color = theme.baseCardBorderColor,
                                onClick = {
                                    onIntent(
                                        ThemeConfigIntent.ShowSheet(
                                            ThemeConfigSheet.BaseCardBorderColor(false)
                                        )
                                    )
                                }
                            )
                            BaseCardBorderColorSettingItem(
                                title = stringResource(R.string.base_card_border_color_night),
                                color = theme.baseCardBorderColorNight,
                                onClick = {
                                    onIntent(
                                        ThemeConfigIntent.ShowSheet(
                                            ThemeConfigSheet.BaseCardBorderColor(true)
                                        )
                                    )
                                }
                            )
                        }
                    }
                    SwitchSettingItem(
                        title = stringResource(R.string.show_divider_line),
                        checked = theme.enableItemDivider,
                        onCheckedChange = { value ->
                            updateTheme { it.copy(enableItemDivider = value) }
                        }
                    )
                    if (theme.enableItemDivider) {
                        SliderSettingItem(
                            title = stringResource(R.string.theme_config_divider_width),
                            description = "${theme.itemDividerWidth}dp",
                            value = theme.itemDividerWidth,
                            defaultValue = 1f,
                            valueRange = 0f..5f,
                            steps = 49,
                            decimal = true,
                            onValueChange = { value ->
                                updateTheme { it.copy(itemDividerWidth = value) }
                            }
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.theme_config_divider_length),
                            description = "${theme.itemDividerLength.toInt()}%",
                            value = theme.itemDividerLength,
                            defaultValue = 80f,
                            valueRange = 30f..100f,
                            steps = 14,
                            onValueChange = { value ->
                                updateTheme { it.copy(itemDividerLength = value) }
                            }
                        )
                        ClickableSettingItem(
                            title = stringResource(R.string.tip_divider_color),
                            option = if (theme.itemDividerColor != 0) "#${Integer.toHexString(theme.itemDividerColor).uppercase()}" else stringResource(R.string.click_to_select),
                            onClick = {
                                onIntent(
                                    ThemeConfigIntent.ShowSheet(ThemeConfigSheet.DividerColor)
                                )
                            },
                            trailingContent = {
                                if (theme.itemDividerColor != 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(theme.itemDividerColor))
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_config_reset_defaults),
                        description = stringResource(R.string.theme_config_reset_defaults_summary),
                        onClick = {
                            onIntent(
                                ThemeConfigIntent.ShowDialog(ThemeConfigDialog.ResetDefaults)
                            )
                        }
                    )
                }
            }

        }
    }

    AppAlertDialog(
        show = state.activeDialog == ThemeConfigDialog.ResetDefaults,
        onDismissRequest = { onIntent(ThemeConfigIntent.DismissDialog) },
        title = stringResource(R.string.theme_config_reset_defaults),
        text = stringResource(R.string.theme_config_reset_defaults_confirm),
        confirmText = stringResource(R.string.reset),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { onIntent(ThemeConfigIntent.ResetDefaults) },
        onDismiss = { onIntent(ThemeConfigIntent.DismissDialog) },
    )


    BackgroundImageManageSheet(
        isDarkTheme = (state.activeSheet as? ThemeConfigSheet.Background)?.dark,
        currentPath = (state.activeSheet as? ThemeConfigSheet.Background)?.let {
            if (it.dark) theme.backgroundImageDark else theme.backgroundImageLight
        },
        onDismissRequest = { onIntent(ThemeConfigIntent.DismissSheet) },
        onSelectImage = { onIntent(ThemeConfigIntent.RequestBackgroundImage(it)) },
        onRemoveImage = { onIntent(ThemeConfigIntent.RemoveBackground(it)) },
    )

    MainNavigationSettingsSheet(
        show = state.activeSheet == ThemeConfigSheet.MainNavigation,
        settings = appShell,
        onDismissRequest = { onIntent(ThemeConfigIntent.DismissSheet) },
        onSetVisible = { route, visible ->
            onIntent(ThemeConfigIntent.SetMainDestinationVisible(route, visible))
        },
        onSetOrder = { onIntent(ThemeConfigIntent.SetMainNavigationOrder(it)) },
        onSetDefault = { onIntent(ThemeConfigIntent.SetDefaultHomePage(it)) },
        onRequestNavigationIcon = { onIntent(ThemeConfigIntent.RequestNavigationIcon(it)) },
        onClearNavigationIcon = { onIntent(ThemeConfigIntent.SelectNavigationIcon(it, "")) },
    )


    LauncherIconPickerSheet(
        show = state.activeSheet == ThemeConfigSheet.LauncherIcon,
        selectedValue = appShell.launcherIcon,
        onDismissRequest = { onIntent(ThemeConfigIntent.DismissSheet) },
        onValueChange = { onIntent(ThemeConfigIntent.SelectLauncherIcon(it)) }
    )

    ColorPickerSheet(
        show = state.activeSheet == ThemeConfigSheet.DividerColor,
        initialColor = theme.itemDividerColor,
        onDismissRequest = { onIntent(ThemeConfigIntent.DismissSheet) },
        onColorSelected = { value ->
            updateTheme { it.copy(itemDividerColor = value) }
            onIntent(ThemeConfigIntent.DismissSheet)
        }
    )

    val baseCardBorderColorSheet = state.activeSheet as? ThemeConfigSheet.BaseCardBorderColor
    ColorPickerSheet(
        show = baseCardBorderColorSheet != null,
        initialColor = if (baseCardBorderColorSheet?.dark == true) {
            theme.baseCardBorderColorNight
        } else {
            theme.baseCardBorderColor
        },
        onDismissRequest = { onIntent(ThemeConfigIntent.DismissSheet) },
        onColorSelected = { value ->
            updateTheme {
                if (baseCardBorderColorSheet?.dark == true) {
                    it.copy(baseCardBorderColorNight = value)
                } else {
                    it.copy(baseCardBorderColor = value)
                }
            }
            onIntent(ThemeConfigIntent.DismissSheet)
        }
    )

    FontSelectSheet(
        show = state.activeSheet == ThemeConfigSheet.Font,
        title = stringResource(R.string.font_setting),
        folderState = fontFolderState,
        selectedFontPath = theme.appFontPath,
        onDismissRequest = { onIntent(ThemeConfigIntent.DismissSheet) },
        onSelectFont = { onIntent(ThemeConfigIntent.SelectAppFont(it)) },
        onOpenFolderPicker = { onIntent(ThemeConfigIntent.RequestFontFolder) },
        startAction = {
            SmallPlainButton(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.clear),
                onClick = {
                    onIntent(ThemeConfigIntent.ClearAppFont)
                    onIntent(ThemeConfigIntent.DismissSheet)
                }
            )
        },
        folderIcon = Icons.Default.Add,
        folderContentDescription = stringResource(R.string.select_folder),
        emptyText = stringResource(R.string.theme_config_no_font_files),
    )

}

@Composable
private fun BaseCardBorderColorSettingItem(
    title: String,
    color: Int,
    onClick: () -> Unit,
) {
    ClickableSettingItem(
        title = title,
        option = if (color != 0) {
            "#${Integer.toHexString(color).uppercase()}"
        } else {
            stringResource(R.string.base_card_border_color_default)
        },
        onClick = onClick,
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        color.takeIf { it != 0 }?.let(::Color)
                            ?: LegadoTheme.colorScheme.outlineVariant
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeModeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf(
        Triple("0", stringResource(R.string.flow_sys), Icons.Filled.BrightnessMedium),
        Triple("1", stringResource(R.string.light_mode), Icons.Filled.LightMode),
        Triple("2", stringResource(R.string.dark_mode), Icons.Filled.DarkMode)
    )

    val selectedIndex = modes.indexOfFirst { it.first == selectedMode }
        .coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            ButtonGroupDefaults.ConnectedSpaceBetween
        )
    ) {
        val modifiers = listOf(Modifier.weight(1.2f), Modifier.weight(1f), Modifier.weight(1f))

        modes.forEachIndexed { index, (value, label, icon) ->
            val checked = selectedIndex == index
            ToggleButton(
                checked = checked,
                onCheckedChange = { onModeSelected(value) },
                modifier = modifiers[index]
                    .semantics {
                        role = Role.RadioButton
                    },

                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null
                )

                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))

                Text(
                    text = label,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ThemeColorSelector(
    context: Context,
    themes: List<Pair<String, String>>,
    selectedTheme: String,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: String?,
    customLightSeedColor: Int,
    customNightSeedColor: Int,
    onThemeSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(themes) { (label, value) ->
            ThemeColorButton(
                context = context,
                label = label,
                value = value,
                isSelected = selectedTheme == value,
                isDark = isDark,
                isAmoled = isAmoled,
                paletteStyle = paletteStyle,
                customLightSeedColor = customLightSeedColor,
                customNightSeedColor = customNightSeedColor,
                onClick = { onThemeSelected(value) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeColorButton(
    context: Context,
    label: String,
    value: String,
    isSelected: Boolean,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: String?,
    customLightSeedColor: Int,
    customNightSeedColor: Int,
    onClick: () -> Unit
) {
    // 配色方案由种子色实时生成，开销不小，缓存避免无关重组时重复计算
    val colors = remember(
        value, isDark, isAmoled, paletteStyle, customLightSeedColor, customNightSeedColor
    ) {
        getThemeColorPalette(
            context = context,
            value = value,
            isDark = isDark,
            isAmoled = isAmoled,
            paletteStyle = paletteStyle,
            customLightSeedColor = customLightSeedColor,
            customNightSeedColor = customNightSeedColor
        )
    }
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        label = "borderWidth"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(16.dp),
            border = if (isSelected) BorderStroke(
                borderWidth,
                LegadoTheme.colorScheme.primary
            ) else null,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(48.dp)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawArc(
                            color = colors.secondary,
                            startAngle = -90f,
                            sweepAngle = 180f,
                            useCenter = true,
                            size = size
                        )

                        drawArc(
                            color = colors.tertiary,
                            startAngle = 90f,
                            sweepAngle = 180f,
                            useCenter = true,
                            size = size
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .align(Alignment.Center)
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AppText(
            text = label,
            style = LegadoTheme.typography.labelSmall,
            color = if (isSelected) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ThemeCard(
    context: Context,
    value: String,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: String?,
    customLightSeedColor: Int,
    customNightSeedColor: Int
) {
    val colors = remember(
        value, isDark, isAmoled, paletteStyle, customLightSeedColor, customNightSeedColor
    ) {
        getThemeColors(
            context = context,
            value = value,
            isDark = isDark,
            isAmoled = isAmoled,
            paletteStyle = paletteStyle,
            customLightSeedColor = customLightSeedColor,
            customNightSeedColor = customNightSeedColor
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .width(128.dp)
                .height(256.dp),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, colors.primary),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            ConstraintLayout(
                modifier = Modifier.fillMaxSize()
            ) {
                val (colorTop, colorBook, colorBottom) = createRefs()

                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 16.dp)
                        .constrainAs(colorTop) {
                            top.linkTo(parent.top, margin = 12.dp)
                            start.linkTo(parent.start, margin = 12.dp)
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.onSurfaceVariant)
                )

                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 80.dp)
                        .constrainAs(colorBook) {
                            top.linkTo(colorTop.bottom, margin = 8.dp)
                            start.linkTo(colorTop.start)
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.secondaryContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(width = 16.dp, height = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.secondary)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .constrainAs(colorBottom) {
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start, margin = 4.dp)
                            end.linkTo(parent.end, margin = 4.dp)
                        }
                        .background(colors.surfaceContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                    )
                }
            }
        }
    }
}

data class ThemeColorPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surfaceContainer: Color
)

data class ThemeColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val secondaryContainer: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color
)

@SuppressLint("ResourceType")
private fun getThemeColorPalette(
    context: Context,
    value: String,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: String?,
    materialVersion: String? = null,
    customLightSeedColor: Int = 0,
    customNightSeedColor: Int = 0
): ThemeColorPalette {
    val appThemeMode = ThemeResolver.resolveThemeMode(value)
    val customSeedColor = if (isDark) customNightSeedColor else customLightSeedColor
    val colorScheme = ThemeEngine.getColorScheme(
        context = context,
        mode = appThemeMode,
        darkTheme = isDark,
        isAmoled = isAmoled,
        paletteStyle = paletteStyle,
        materialVersion = materialVersion,
        customSeedColor = customSeedColor
    )

    return ThemeColorPalette(
        primary = colorScheme.primary,
        secondary = colorScheme.secondaryContainer,
        tertiary = colorScheme.tertiaryContainer,
        surfaceContainer = colorScheme.surfaceContainer
    )
}

@SuppressLint("ResourceType")
private fun getThemeColors(
    context: Context,
    value: String,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: String?,
    materialVersion: String? = null,
    customLightSeedColor: Int = 0,
    customNightSeedColor: Int = 0
): ThemeColors {
    val appThemeMode = ThemeResolver.resolveThemeMode(value)
    val customSeedColor = if (isDark) customNightSeedColor else customLightSeedColor
    val colorScheme = ThemeEngine.getColorScheme(
        context = context,
        mode = appThemeMode,
        darkTheme = isDark,
        isAmoled = isAmoled,
        paletteStyle = paletteStyle,
        materialVersion = materialVersion,
        customSeedColor = customSeedColor
    )

    return ThemeColors(
        primary = colorScheme.primary,
        secondary = colorScheme.secondary,
        tertiary = colorScheme.tertiary,
        surface = colorScheme.surface,
        surfaceContainer = colorScheme.surfaceContainer,
        secondaryContainer = colorScheme.secondaryContainer,
        onSurface = colorScheme.onSurface,
        onSurfaceVariant = colorScheme.onSurfaceVariant
    )
}
