package io.legado.app.ui.config.themeConfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.button.TopbarNavigationButton
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeConfigScreen(
    onBackClick: () -> Unit,
    viewModel: ThemeConfigViewModel = koinViewModel()
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var manageKey by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.theme_setting)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopbarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SplicedColumnGroup {
                SwitchSettingItem(
                    title = stringResource(R.string.pure_black),
                    checked = ThemeConfig.isPureBlack,
                    onCheckedChange = { ThemeConfig.isPureBlack = it }
                )
                DropdownListSettingItem(
                    title = stringResource(R.string.palette_style),
                    selectedValue = ThemeConfig.paletteStyle,
                    displayEntries = arrayOf(
                        "柔和", "中性", "鲜艳", "表现力", "彩虹",
                        "水果拼盘", "单色", "仿真", "内容取色"
                    ),
                    entryValues = arrayOf(
                        "tonalSpot", "neutral", "vibrant", "expressive", "rainbow",
                        "fruitSalad", "monochrome", "fidelity", "content"
                    ),
                    onValueChange = { ThemeConfig.paletteStyle = it }
                )
            }

            SplicedColumnGroup(title = "Compose 相关") {
                SwitchSettingItem(
                    title = "使用折叠应用栏",
                    checked = ThemeConfig.useFlexibleTopAppBar,
                    onCheckedChange = { ThemeConfig.useFlexibleTopAppBar = it }
                )
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
                SwitchSettingItem(
                    title = stringResource(R.string.is_blur_enable),
                    checked = ThemeConfig.enableBlur,
                    onCheckedChange = { ThemeConfig.enableBlur = it }
                )
                if (ThemeConfig.enableBlur) {
                    SwitchSettingItem(
                        title = stringResource(R.string.is_blur_progressive_enable),
                        checked = ThemeConfig.enableProgressiveBlur,
                        onCheckedChange = { ThemeConfig.enableProgressiveBlur = it }
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
                    onClick = { manageKey = Pair(PreferKey.bgImage, false) }
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
                    onClick = { manageKey = Pair(PreferKey.bgImageN, true) }
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

    manageKey?.let { (key, isDark) ->
        BackgroundImageManageSheet(
            preferenceKey = key,
            isDarkTheme = isDark,
            onDismissRequest = { manageKey = null }
        )
    }
}
