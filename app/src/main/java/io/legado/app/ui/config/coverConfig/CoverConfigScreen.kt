package io.legado.app.ui.config.coverConfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverConfigScreen(
    onBackClick: () -> Unit,
    viewModel: CoverConfigViewModel = koinViewModel()
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    var showCoverRuleSheet by remember { mutableStateOf(false) }
    var manageKey by remember { mutableStateOf<String?>(null) }
    var showColorPickerByField by remember { mutableStateOf<String?>(null) }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.cover_config),
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
                SplicedColumnGroup {
                SwitchSettingItem(
                    title = stringResource(R.string.only_wifi),
                    description = stringResource(R.string.only_wifi_summary),
                    checked = CoverConfig.loadCoverOnlyWifi,
                    onCheckedChange = { CoverConfig.loadCoverOnlyWifi = it }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.cover_rule),
                    description = stringResource(R.string.cover_rule_summary),
                    onClick = { showCoverRuleSheet = true }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.use_default_cover),
                    description = stringResource(R.string.use_default_cover_s),
                    checked = CoverConfig.useDefaultCover,
                    onCheckedChange = { CoverConfig.useDefaultCover = it }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_shadow),
                    checked = CoverConfig.coverShowShadow,
                    onCheckedChange = {
                        CoverConfig.coverShowShadow = it
                        viewModel.updateCoverStyle()
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_stroke),
                    checked = CoverConfig.coverShowStroke,
                    onCheckedChange = {
                        CoverConfig.coverShowStroke = it
                        viewModel.updateCoverStyle()
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.default_color),
                    checked = CoverConfig.coverDefaultColor,
                    onCheckedChange = {
                        CoverConfig.coverDefaultColor = it
                        viewModel.updateCoverStyle()
                    }
                )
            }

            SplicedColumnGroup {
                DropdownListSettingItem(
                    title = stringResource(R.string.cover_info_orientation),
                    selectedValue = CoverConfig.coverInfoOrientation,
                    displayEntries = arrayOf(
                        stringResource(R.string.screen_portrait),
                        stringResource(R.string.screen_landscape)
                    ),
                    entryValues = arrayOf("0", "1"),
                    onValueChange = {
                        CoverConfig.coverInfoOrientation = it
                        viewModel.updateCoverStyle()
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.day)) {
                val coverCount = CoverConfig.defaultCover.split(",").filter { it.isNotBlank() }.size
                ClickableSettingItem(
                    title = stringResource(R.string.default_cover),
                    description = if (coverCount > 0) "已选择 $coverCount 张图片" else stringResource(
                        R.string.select_image
                    ),
                    onClick = { manageKey = PreferKey.defaultCover }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.text_color),
                    option = "#${Integer.toHexString(CoverConfig.coverTextColor).uppercase()}",
                    onClick = { showColorPickerByField = "coverTextColor" },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(CoverConfig.coverTextColor))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.text_shadow_color),
                    option = "#${Integer.toHexString(CoverConfig.coverShadowColor).uppercase()}",
                    onClick = { showColorPickerByField = "coverShadowColor" },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(CoverConfig.coverShadowColor))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_name),
                    description = stringResource(R.string.cover_show_name_summary),
                    checked = CoverConfig.coverShowName,
                    onCheckedChange = { viewModel.updateShowName(it) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_author),
                    description = stringResource(R.string.cover_show_author_summary),
                    checked = CoverConfig.coverShowAuthor,
                    enabled = CoverConfig.coverShowName,
                    onCheckedChange = { viewModel.updateShowAuthor(it) }
                )
            }

                SplicedColumnGroup(title = stringResource(R.string.night)) {
                val coverCount =
                    CoverConfig.defaultCoverDark.split(",").filter { it.isNotBlank() }.size
                ClickableSettingItem(
                    title = stringResource(R.string.default_cover),
                    description = if (coverCount > 0) "已选择 $coverCount 张图片" else stringResource(
                        R.string.select_image
                    ),
                    onClick = { manageKey = PreferKey.defaultCoverDark }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.text_color),
                    option = "#${Integer.toHexString(CoverConfig.coverTextColorN).uppercase()}",
                    onClick = { showColorPickerByField = "coverTextColorN" },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(CoverConfig.coverTextColorN))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.text_shadow_color),
                    option = "#${Integer.toHexString(CoverConfig.coverShadowColorN).uppercase()}",
                    onClick = { showColorPickerByField = "coverShadowColorN" },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(CoverConfig.coverShadowColorN))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_name),
                    description = stringResource(R.string.cover_show_name_summary),
                    checked = CoverConfig.coverShowNameN,
                    onCheckedChange = { viewModel.updateShowName(it, true) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_author),
                    description = stringResource(R.string.cover_show_author_summary),
                    checked = CoverConfig.coverShowAuthorN,
                    enabled = CoverConfig.coverShowNameN,
                    onCheckedChange = { viewModel.updateShowAuthor(it, true) }
                )
                }
            }
        }
    }

    CoverRuleConfigSheet(
        show = showCoverRuleSheet,
        onDismissRequest = { showCoverRuleSheet = false }
    )

    manageKey?.let { key ->
        CoverManageSheet(
            show = true,
            preferenceKey = key,
            onDismissRequest = { manageKey = null },
            viewModel = viewModel
        )
    }

    showColorPickerByField?.let { field ->
        val initialColor = when (field) {
            "coverTextColor" -> CoverConfig.coverTextColor
            "coverShadowColor" -> CoverConfig.coverShadowColor
            "coverTextColorN" -> CoverConfig.coverTextColorN
            "coverShadowColorN" -> CoverConfig.coverShadowColorN
            else -> 0
        }

        ColorPickerSheet(
            show = showColorPickerByField != null,
            initialColor = initialColor,
            onDismissRequest = { showColorPickerByField = null },
            onColorSelected = { color ->
                when (field) {
                    "coverTextColor" -> CoverConfig.coverTextColor = color
                    "coverShadowColor" -> CoverConfig.coverShadowColor = color
                    "coverTextColorN" -> CoverConfig.coverTextColorN = color
                    "coverShadowColorN" -> CoverConfig.coverShadowColorN = color
                }
                viewModel.updateCoverStyle()
            }
        )
    }
}
