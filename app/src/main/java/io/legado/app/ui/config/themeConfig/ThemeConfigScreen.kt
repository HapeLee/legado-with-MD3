package io.legado.app.ui.config.themeConfig

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import io.legado.app.R
import io.legado.app.base.AppContextWrapper
import io.legado.app.constant.EventBus
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.OldThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.TopBarNavigationButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.postEvent
import io.legado.app.utils.restart
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThemeConfigScreen(
    onBackClick: () -> Unit,
    viewModel: ThemeConfigViewModel = koinViewModel()
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var manageKey by remember { mutableStateOf<Boolean?>(null) }
    val context = LocalContext.current

    var selectedThemeMode by remember { mutableStateOf(ThemeConfig.themeMode) }
    var selectedTheme by remember { mutableStateOf(ThemeConfig.appTheme) }
    var useMiuixMonet by remember { mutableStateOf(ThemeConfig.useMiuixMonet) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var pickNightSeedColor by remember { mutableStateOf(false) }
    var showLauncherIconPicker by remember { mutableStateOf(false) }
    var showThemeListDialog by remember { mutableStateOf(false) }
    var saveThemeKey by remember { mutableStateOf<String?>(null) }
    var themeName by remember { mutableStateOf("") }

    val fontScaleValue = remember { mutableFloatStateOf(ThemeConfig.fontScale.toFloat()) }
    val primaryColorValue = remember { mutableIntStateOf(ThemeConfig.cPrimary) }
    val nightPrimaryColorValue = remember { mutableIntStateOf(ThemeConfig.cNPrimary) }

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
                val composeEngine = ThemeConfig.composeEngine
                val isMiuixEngine = remember(composeEngine) {
                    ThemeResolver.isMiuixEngine(composeEngine)
                }
                val isDarkTheme = when (selectedThemeMode) {
                    "1" -> false
                    "2" -> true
                    else -> isSystemInDarkTheme()
                }

            if (!isMiuixEngine) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeCard(
                        context = context,
                        value = selectedTheme,
                        isDark = isDarkTheme,
                        isAmoled = ThemeConfig.isPureBlack,
                        paletteStyle = ThemeConfig.paletteStyle,
                        customLightSeedColor = primaryColorValue.intValue,
                        customNightSeedColor = nightPrimaryColorValue.intValue
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val themeItems = stringArrayResource(R.array.themes_item)
            val themeValues = stringArrayResource(R.array.themes_value)
            val themes = remember(themeItems, themeValues) {
                themeItems.zip(themeValues).toList()
            }

            if (isMiuixEngine) {
                MiuixCard(
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(16.dp),
                    colors = MiuixCardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.primaryVariant,
                        contentColor = MiuixTheme.colorScheme.onPrimary
                    )
                ) {
                    MiuixText("Miuix 目前为测试主题，且不对基于View的界面生效！")
                }
            }


            SplicedColumnGroup(title = stringResource(R.string.theme)) {
                if (isMiuixEngine) {
                    DropdownListSettingItem(
                        title = stringResource(R.string.theme_mode),
                        selectedValue = selectedThemeMode,
                        displayEntries = stringArrayResource(R.array.theme_mode),
                        entryValues = stringArrayResource(R.array.theme_mode_v),
                        onValueChange = { mode ->
                            selectedThemeMode = mode
                            ThemeConfig.themeMode = mode
                            OldThemeConfig.applyDayNight(context)
                        }
                    )

                    SwitchSettingItem(
                        title = stringResource(R.string.miuix_monet),
                        description = stringResource(R.string.miuix_monet_summary),
                        checked = useMiuixMonet,
                        onCheckedChange = {
                            useMiuixMonet = it
                            ThemeConfig.useMiuixMonet = it
                            if (it && selectedTheme != "0" && selectedTheme != "12") {
                                selectedTheme = "0"
                                ThemeConfig.appTheme = "0"
                            }
                        }
                    )

                    if (useMiuixMonet) {
                        SwitchSettingItem(
                            title = stringResource(R.string.dynamic_colors),
                            description = stringResource(R.string.dynamic_colors_summary),
                            checked = selectedTheme == "0",
                            onCheckedChange = { checked ->
                                val newTheme = if (checked) "0" else "12"
                                val oldTheme = selectedTheme
                                selectedTheme = newTheme
                                ThemeConfig.appTheme = newTheme
                                if (oldTheme != newTheme) {
                                    showRestartDialog = true
                                }
                            }
                        )
                    }
                } else {
                    ThemeModeSelector(
                        selectedMode = selectedThemeMode,
                        onModeSelected = { mode ->
                            selectedThemeMode = mode
                            ThemeConfig.themeMode = mode
                            OldThemeConfig.applyDayNight(context)
                        }
                    )
                }

                if (!isMiuixEngine) {
                    Spacer(modifier = Modifier.height(16.dp))

                    ThemeColorSelector(
                        context = context,
                        themes = themes,
                        selectedTheme = selectedTheme,
                        isDark = isDarkTheme,
                        isAmoled = ThemeConfig.isPureBlack,
                        paletteStyle = ThemeConfig.paletteStyle,
                        customLightSeedColor = primaryColorValue.intValue,
                        customNightSeedColor = nightPrimaryColorValue.intValue,
                        onThemeSelected = { theme ->
                            if (theme == "13") {
                                val hasLightBg = !ThemeConfig.bgImageLight.isNullOrEmpty()
                                val hasDarkBg = !ThemeConfig.bgImageDark.isNullOrEmpty()
                                if (!hasLightBg || !hasDarkBg) {
                                    context.toastOnUi(R.string.transparent_theme_alarm)
                                    return@ThemeColorSelector
                                } else {
                                    AppConfig.containerOpacity = 0
                                }
                            }
                            val oldTheme = selectedTheme
                            selectedTheme = theme
                            ThemeConfig.appTheme = theme
                            val isDynamicSwitch = (oldTheme == "12" || theme == "12")
                            if (isDynamicSwitch) {
                                showRestartDialog = true
                            } else {
                                postEvent(EventBus.RECREATE, "")
                            }
                        }
                    )
                }
            }

            SplicedColumnGroup {
                DropdownListSettingItem(
                    title = stringResource(R.string.compose_engine),
                    selectedValue = ThemeConfig.composeEngine,
                    displayEntries = stringArrayResource(R.array.composeEngine),
                    entryValues = stringArrayResource(R.array.composeEngine_value),
                    onValueChange = {
                        ThemeConfig.composeEngine = it
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.change_icon),
                    description = stringResource(R.string.change_icon_summary),
                    onClick = { showLauncherIconPicker = true }
                )
                SwitchSettingItem(
                    title = stringResource(R.string.predictive_back),
                    description = stringResource(R.string.predictive_back_summary),
                    checked = ThemeConfig.isPredictiveBackEnabled,
                    onCheckedChange = {
                        ThemeConfig.isPredictiveBackEnabled = it
                        context.toastOnUi(R.string.restart_to_apply)
                    }
                )
                SliderSettingItem(
                    title = stringResource(R.string.font_scale),
                    description = stringResource(
                        R.string.font_scale_summary,
                        AppContextWrapper.getFontScale(context)
                    ),
                    value = fontScaleValue.floatValue,
                    defaultValue = 10f,
                    valueRange = 8f..16f,
                    steps = 7,
                    onValueChange = { value ->
                        fontScaleValue.floatValue = value
                        ThemeConfig.fontScale = value.toInt()
                    }
                )
            }

            val showCustomThemeOptions = selectedTheme == "12" && (!isMiuixEngine || useMiuixMonet)
            if (showCustomThemeOptions) {
                SplicedColumnGroup(title = stringResource(R.string.custom_theme)) {
                    ClickableSettingItem(
                        title = stringResource(R.string.seed_color),
                        description = stringResource(R.string.day),
                        option = formatColorOption(primaryColorValue.intValue)
                            ?: stringResource(R.string.click_to_select),
                        onClick = {
                            pickNightSeedColor = false
                            showColorPicker = true
                        },
                        trailingContent = { ColorSwatch(colorValue = primaryColorValue.intValue) }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.seed_color),
                        description = stringResource(R.string.night),
                        option = formatColorOption(nightPrimaryColorValue.intValue)
                            ?: stringResource(R.string.click_to_select),
                        onClick = {
                            pickNightSeedColor = true
                            showColorPicker = true
                        },
                        trailingContent = { ColorSwatch(colorValue = nightPrimaryColorValue.intValue) }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.palette_style),
                        selectedValue = ThemeConfig.paletteStyle,
                        displayEntries = stringArrayResource(R.array.paletteStyle),
                        entryValues = stringArrayResource(R.array.paletteStyle_value),
                        onValueChange = { ThemeConfig.paletteStyle = it }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.preferred_contrast),
                        selectedValue = ThemeConfig.customContrast,
                        displayEntries = stringArrayResource(R.array.customContrast),
                        entryValues = stringArrayResource(R.array.customContrast_value),
                        onValueChange = { ThemeConfig.customContrast = it }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.material_version),
                        selectedValue = ThemeConfig.materialVersion,
                        displayEntries = stringArrayResource(R.array.materialVersion),
                        entryValues = stringArrayResource(R.array.materialVersion_value),
                        onValueChange = { ThemeConfig.materialVersion = it }
                    )
                }
            }

            SplicedColumnGroup(title = stringResource(R.string.main_activity)) {
                SwitchSettingItem(
                    title = stringResource(R.string.show_discovery),
                    checked = ThemeConfig.showDiscovery,
                    onCheckedChange = { ThemeConfig.showDiscovery = it }
                )
                SwitchSettingItem(
                    title = stringResource(R.string.show_rss),
                    checked = ThemeConfig.showRss,
                    onCheckedChange = { ThemeConfig.showRss = it }
                )
                SwitchSettingItem(
                    title = stringResource(R.string.show_status),
                    checked = ThemeConfig.showStatusBar,
                    onCheckedChange = { ThemeConfig.showStatusBar = it }
                )
                //TODO:这个可以不要了，在删掉原来的设置页以后删
                SwitchSettingItem(
                    title = stringResource(R.string.show_swipe_animation),
                    checked = ThemeConfig.swipeAnimation,
                    onCheckedChange = { ThemeConfig.swipeAnimation = it }
                )
                SwitchSettingItem(
                    title = stringResource(R.string.show_bottom_nav),
                    description = stringResource(R.string.be_swiped),
                    checked = ThemeConfig.showBottomView,
                    onCheckedChange = { ThemeConfig.showBottomView = it }
                )
                SwitchSettingItem(
                    title = stringResource(R.string.floating_bottom_bar),
                    description = stringResource(R.string.floating_bottom_bar_summary),
                    checked = ThemeConfig.useFloatingBottomBar,
                    onCheckedChange = { ThemeConfig.useFloatingBottomBar = it }
                )
                AnimatedVisibility(visible = ThemeConfig.useFloatingBottomBar) {
                    SwitchSettingItem(
                        title = stringResource(R.string.floating_bottom_bar_liquid_glass),
                        description = stringResource(R.string.floating_bottom_bar_liquid_glass_summary),
                        checked = ThemeConfig.useFloatingBottomBarLiquidGlass,
                        onCheckedChange = { ThemeConfig.useFloatingBottomBarLiquidGlass = it }
                    )
                }
                DropdownListSettingItem(
                    title = stringResource(R.string.tabletInterface),
                    selectedValue = ThemeConfig.tabletInterface,
                    displayEntries = stringArrayResource(R.array.tabletInterface),
                    entryValues = stringArrayResource(R.array.tabletInterface_value),
                    onValueChange = { ThemeConfig.tabletInterface = it }
                )
                DropdownListSettingItem(
                    title = stringResource(R.string.nav_label_mode),
                    selectedValue = ThemeConfig.labelVisibilityMode,
                    displayEntries = stringArrayResource(R.array.label_vis_mode),
                    entryValues = stringArrayResource(R.array.label_vis_mode_value),
                    onValueChange = { ThemeConfig.labelVisibilityMode = it }
                )
                DropdownListSettingItem(
                    title = stringResource(R.string.default_home_page),
                    selectedValue = ThemeConfig.defaultHomePage,
                    displayEntries = stringArrayResource(R.array.default_home_page),
                    entryValues = stringArrayResource(R.array.default_home_page_value),
                    onValueChange = { ThemeConfig.defaultHomePage = it }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.share)) {
                ClickableSettingItem(
                    title = stringResource(R.string.theme_list),
                    description = stringResource(R.string.theme_list_summary),
                    onClick = { showThemeListDialog = true }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.save_theme_config),
                    description = stringResource(R.string.save_day_theme_summary),
                    onClick = {
                        saveThemeKey = "saveDayTheme"
                        themeName = ""
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.compose_related)) {
                if (!isMiuixEngine) {
                    SwitchSettingItem(
                        title = stringResource(R.string.pure_black),
                        checked = ThemeConfig.isPureBlack,
                        onCheckedChange = { ThemeConfig.isPureBlack = it }
                    )
                    SwitchSettingItem(
                        title = stringResource(R.string.use_flexible_top_bar),
                        checked = ThemeConfig.useFlexibleTopAppBar,
                        onCheckedChange = { ThemeConfig.useFlexibleTopAppBar = it }
                    )
                }
                SwitchSettingItem(
                    title = stringResource(R.string.is_blur_enable),
                    checked = ThemeConfig.enableBlur,
                    onCheckedChange = {
                        ThemeConfig.enableBlur = it
                        if (!it) ThemeConfig.enableProgressiveBlur = false
                    }
                )
                AnimatedVisibility(visible = ThemeConfig.enableBlur) {
                    SwitchSettingItem(
                        title = stringResource(R.string.is_blur_progressive_enable),
                        checked = ThemeConfig.enableProgressiveBlur,
                        onCheckedChange = { ThemeConfig.enableProgressiveBlur = it }
                    )
                }
                AnimatedVisibility(visible = !isMiuixEngine && !ThemeConfig.enableBlur) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SliderSettingItem(
                            title = stringResource(R.string.top_bar_opacity),
                            description = stringResource(
                                R.string.top_bar_opacity_summary,
                                ThemeConfig.topBarOpacity
                            ),
                            value = ThemeConfig.topBarOpacity.toFloat(),
                            defaultValue = 100f,
                            valueRange = 0f..100f,
                            steps = 99,
                            onValueChange = { ThemeConfig.topBarOpacity = it.toInt() }
                        )
                        SliderSettingItem(
                            title = stringResource(R.string.bottom_bar_opacity),
                            description = stringResource(
                                R.string.bottom_bar_opacity_summary,
                                ThemeConfig.bottomBarOpacity
                            ),
                            value = ThemeConfig.bottomBarOpacity.toFloat(),
                            defaultValue = 100f,
                            valueRange = 0f..100f,
                            steps = 99,
                            onValueChange = { ThemeConfig.bottomBarOpacity = it.toInt() }
                        )
                    }
                }
                if (!isMiuixEngine) {
                    SliderSettingItem(
                        title = stringResource(R.string.container_opacity),
                        description = stringResource(
                            R.string.container_opacity_summary,
                            ThemeConfig.containerOpacity
                        ),
                        value = ThemeConfig.containerOpacity.toFloat(),
                        defaultValue = 100f,
                        valueRange = 0f..100f,
                        steps = 99,
                        onValueChange = { ThemeConfig.containerOpacity = it.toInt() }
                    )
                }
            }

            SplicedColumnGroup(title = stringResource(R.string.day)) {
                val hasLightBg = !ThemeConfig.bgImageLight.isNullOrBlank()
                ClickableSettingItem(
                    title = stringResource(R.string.background_image),
                    description = if (hasLightBg) stringResource(R.string.click_to_delete) else stringResource(
                        R.string.select_image
                    ),
                    onClick = { manageKey = false }
                )

                if (hasLightBg) {
                    SliderSettingItem(
                        title = stringResource(R.string.background_image_blurring),
                        value = ThemeConfig.bgImageBlurring.toFloat(),
                        defaultValue = 0f,
                        valueRange = 0f..100f,
                        steps = 99,
                        onValueChange = {
                            ThemeConfig.bgImageBlurring = it.toInt()
                        }
                    )
                }
            }

                SplicedColumnGroup(title = stringResource(R.string.night)) {
                val hasDarkBg = !ThemeConfig.bgImageDark.isNullOrBlank()
                ClickableSettingItem(
                    title = stringResource(R.string.background_image),
                    description = if (hasDarkBg) stringResource(R.string.click_to_delete) else stringResource(
                        R.string.select_image
                    ),
                    onClick = { manageKey = true }
                )

                if (hasDarkBg) {
                    SliderSettingItem(
                        title = stringResource(R.string.background_image_blurring),
                        value = ThemeConfig.bgImageNBlurring.toFloat(),
                        defaultValue = 0f,
                        valueRange = 0f..100f,
                        steps = 99,
                        onValueChange = {
                            ThemeConfig.bgImageNBlurring = it.toInt()
                        }
                    )
                }
                }
            }
        }
    }


    AppAlertDialog(
        show = showRestartDialog,
        onDismissRequest = { showRestartDialog = false },
        title = stringResource(R.string.restart_required_message),
        onConfirm = {
            showRestartDialog = false
            Handler(Looper.getMainLooper()).postDelayed({
                context.restart()
            }, 100)
        },
        confirmText = stringResource(R.string.ok),
        onDismiss = {
            showRestartDialog = false
            context.toastOnUi(R.string.restart_later_message)
        },
        dismissText = stringResource(R.string.cancel)
    )


    manageKey?.let { isDark ->
        BackgroundImageManageSheet(
            show = true,
            isDarkTheme = isDark,
            onDismissRequest = { manageKey = null }
        )
    }


    ColorPickerSheet(
        show = showColorPicker,
        initialColor = if (pickNightSeedColor) {
            nightPrimaryColorValue.value
        } else {
            primaryColorValue.value
        },
        onDismissRequest = { showColorPicker = false },
        onColorSelected = { color ->
            if (pickNightSeedColor) {
                nightPrimaryColorValue.value = color
                ThemeConfig.cNPrimary = color
            } else {
                primaryColorValue.value = color
                ThemeConfig.cPrimary = color
                ThemeStore.editTheme(context)
                    .primaryColor(color)
                    .apply()
                DynamicColors.applyToActivitiesIfAvailable(
                    context.applicationContext as android.app.Application,
                    DynamicColorsOptions.Builder()
                        .setContentBasedSource(context.primaryColor)
                        .build()
                )
            }
        }
    )

    LauncherIconPickerSheet(
        show = showLauncherIconPicker,
        selectedValue = ThemeConfig.launcherIcon,
        onDismissRequest = { showLauncherIconPicker = false },
        onValueChange = {
            ThemeConfig.launcherIcon = it
            LauncherIconHelp.changeIcon(it)
        }
    )

    ThemeListDialog(
        show = showThemeListDialog,
        onDismissRequest = { showThemeListDialog = false }
    )

    AppAlertDialog(
        data = saveThemeKey,
        onDismissRequest = { saveThemeKey = null },
        title = stringResource(R.string.theme_name),
        content = {
            AppTextField(
                value = themeName,
                onValueChange = { themeName = it },
                label = "name",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = LegadoTheme.colorScheme.surface
            )
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { key ->
            when (key) {
                "saveDayTheme" -> OldThemeConfig.saveDayTheme(context, themeName)
                "saveNightTheme" -> OldThemeConfig.saveNightTheme(context, themeName)
            }
            saveThemeKey = null
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { saveThemeKey = null }
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

            ToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = { onModeSelected(value) },
                modifier = modifiers[index]
                    .semantics { role = Role.RadioButton },

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

                Text(text = label)
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
    val colors = getThemeColorPalette(
        context = context,
        value = value,
        isDark = isDark,
        isAmoled = isAmoled,
        paletteStyle = paletteStyle,
        customLightSeedColor = customLightSeedColor,
        customNightSeedColor = customNightSeedColor
    )
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
    val colors = getThemeColors(
        context = context,
        value = value,
        isDark = isDark,
        isAmoled = isAmoled,
        paletteStyle = paletteStyle,
        customLightSeedColor = customLightSeedColor,
        customNightSeedColor = customNightSeedColor
    )

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

private fun formatColorOption(colorValue: Int): String? {
    if (colorValue == 0) return null
    return "#${Integer.toHexString(colorValue).uppercase()}"
}

@Composable
private fun ColorSwatch(colorValue: Int) {
    if (colorValue == 0) return
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(colorValue))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                CircleShape
            )
    )
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
