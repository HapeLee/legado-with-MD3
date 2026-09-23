package io.legado.app.feature.settings.coverconfig

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.cover_album_day_night_count
import io.legado.app.feature.settings.res.cover_album_none
import io.legado.app.feature.settings.res.cover_config
import io.legado.app.feature.settings.res.cover_info_orientation
import io.legado.app.feature.settings.res.cover_rule
import io.legado.app.feature.settings.res.cover_rule_summary
import io.legado.app.feature.settings.res.cover_show_author
import io.legado.app.feature.settings.res.cover_show_author_summary
import io.legado.app.feature.settings.res.cover_show_name
import io.legado.app.feature.settings.res.cover_show_name_summary
import io.legado.app.feature.settings.res.cover_show_shadow
import io.legado.app.feature.settings.res.cover_show_stroke
import io.legado.app.feature.settings.res.day
import io.legado.app.feature.settings.res.default_color
import io.legado.app.feature.settings.res.default_cover
import io.legado.app.feature.settings.res.filter_hide_in_shelf
import io.legado.app.feature.settings.res.filter_hide_same_name_author
import io.legado.app.feature.settings.res.filter_show_all
import io.legado.app.feature.settings.res.filter_show_not_in_shelf_only
import io.legado.app.feature.settings.res.network_book_badge_setting
import io.legado.app.feature.settings.res.night
import io.legado.app.feature.settings.res.only_wifi
import io.legado.app.feature.settings.res.only_wifi_summary
import io.legado.app.feature.settings.res.screen_landscape
import io.legado.app.feature.settings.res.screen_portrait
import io.legado.app.feature.settings.res.text_color
import io.legado.app.feature.settings.res.text_shadow_color
import io.legado.app.feature.settings.res.use_default_cover
import io.legado.app.feature.settings.res.use_default_cover_s
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.jetbrains.compose.resources.stringResource

/**
 * M5-13c：从 `:app` 的 `ui/config/coverConfig/CoverConfigScreen.kt` 迁来
 * **只有页面本体这一半**。那个文件里同时放着 `CoverConfigRouteScreen`（宿主壳：
 * `LocalContext` + `toastOnUi` 收 Effect）与本函数 —— 本片按职责拆开，壳留在 `:app`
 * （现住 `CoverConfigRouteScreen.kt`），本体搬到这里。
 *
 * 差异三类：
 *   ① 资源访问：CMP 的 `stringResource`、`R.string.*` → `Res.string.*`（29 条，**无数组** ——
 *      页面里的下拉是就地 `arrayOf(stringResource(...))`，所以不需要 `.toTypedArray()`）；
 *   ② `Integer.toHexString(x).uppercase()` → `x.toString(16).uppercase()`（见下）；
 *   ③ 无（其余结构逐字保留，含原文那些不齐的缩进）。
 *
 * ⚠️ **`Integer.toHexString` 是 JVM 专用**（`java.lang.Integer`），`commonMain` 里没有 ——
 * 改用 Kotlin 的 `Int.toString(radix)`。两者对非负整数**等价**（都是不带前导零的小写十六进制，
 * 颜色值恒非负），只差大小写 ⇒ 后面接的 `.uppercase()` 把这点也抹平了。
 * 这是本片唯一的非资源改动，与 M5-10a-pre 改写 `TimePickerDialog` 的 `Locale`/`Character.digit`
 * 是同一类处理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverConfigScreen(
    state: CoverConfigUiState,
    onIntent: (CoverConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToCoverAlbums: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val settings = state.settings
    val albumState = state.albumSelection
    val selectedAlbum = albumState.albums
        .firstOrNull { it.id == albumState.selectedAlbumId }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.cover_config),
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
                    title = stringResource(Res.string.only_wifi),
                    description = stringResource(Res.string.only_wifi_summary),
                    checked = settings.loadOnlyOnWifi,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetLoadOnlyOnWifi(value))
                    }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.cover_rule),
                    description = stringResource(Res.string.cover_rule_summary),
                    onClick = { onIntent(CoverConfigIntent.ShowSheet(CoverConfigSheet.Rule)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.use_default_cover),
                    description = stringResource(Res.string.use_default_cover_s),
                    checked = settings.useDefaultCover,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetUseDefaultCover(value))
                    }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.default_cover),
                    description = selectedAlbum?.let {
                        "${it.name} · ${
                            stringResource(
                                Res.string.cover_album_day_night_count,
                                it.lightImages.size,
                                it.darkImages.size,
                            )
                        }"
                    } ?: stringResource(Res.string.cover_album_none),
                    onClick = { onIntent(CoverConfigIntent.ShowSheet(CoverConfigSheet.Album)) }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.cover_show_shadow),
                    checked = settings.showShadow,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowShadow(value))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.cover_show_stroke),
                    checked = settings.showStroke,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowStroke(value))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.default_color),
                    checked = settings.useDefaultColor,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetUseDefaultColor(value))
                    }
                )
            }

            SplicedColumnGroup {
                DropdownListSettingItem(
                    title = stringResource(Res.string.cover_info_orientation),
                    selectedValue = settings.infoOrientation,
                    displayEntries = arrayOf(
                        stringResource(Res.string.screen_portrait),
                        stringResource(Res.string.screen_landscape)
                    ),
                    entryValues = arrayOf("0", "1"),
                    onValueChange = { value ->
                        onIntent(CoverConfigIntent.SetInfoOrientation(value))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(Res.string.network_book_badge_setting)) {
                DropdownListSettingItem(
                    title = stringResource(Res.string.network_book_badge_setting),
                    selectedValue = settings.exploreFilterState.toString(),
                    displayEntries = arrayOf(
                        stringResource(Res.string.filter_show_all),
                        stringResource(Res.string.filter_hide_in_shelf),
                        stringResource(Res.string.filter_hide_same_name_author),
                        stringResource(Res.string.filter_show_not_in_shelf_only)
                    ),
                    entryValues = arrayOf("0", "1", "2", "3"),
                    onValueChange = { value ->
                        onIntent(CoverConfigIntent.SetExploreFilterState(value.toInt()))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(Res.string.day)) {
                ClickableSettingItem(
                    title = stringResource(Res.string.text_color),
                    option = "#${settings.textColor.toString(16).uppercase()}",
                    onClick = {
                        onIntent(
                            CoverConfigIntent.ShowSheet(
                                CoverConfigSheet.Color(CoverColorField.Text)
                            )
                        )
                    },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(settings.textColor))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.text_shadow_color),
                    option = "#${settings.shadowColor.toString(16).uppercase()}",
                    onClick = {
                        onIntent(
                            CoverConfigIntent.ShowSheet(
                                CoverConfigSheet.Color(CoverColorField.Shadow)
                            )
                        )
                    },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(settings.shadowColor))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.cover_show_name),
                    description = stringResource(Res.string.cover_show_name_summary),
                    checked = settings.showName,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowName(value))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.cover_show_author),
                    description = stringResource(Res.string.cover_show_author_summary),
                    checked = settings.showAuthor,
                    enabled = settings.showName,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowAuthor(value))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(Res.string.night)) {
                ClickableSettingItem(
                    title = stringResource(Res.string.text_color),
                    option = "#${settings.textColorDark.toString(16).uppercase()}",
                    onClick = {
                        onIntent(
                            CoverConfigIntent.ShowSheet(
                                CoverConfigSheet.Color(CoverColorField.TextDark)
                            )
                        )
                    },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(settings.textColorDark))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                )

                ClickableSettingItem(
                    title = stringResource(Res.string.text_shadow_color),
                    option = "#${settings.shadowColorDark.toString(16).uppercase()}",
                    onClick = {
                        onIntent(
                            CoverConfigIntent.ShowSheet(
                                CoverConfigSheet.Color(CoverColorField.ShadowDark)
                            )
                        )
                    },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(settings.shadowColorDark))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.cover_show_name),
                    description = stringResource(Res.string.cover_show_name_summary),
                    checked = settings.showNameDark,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowNameDark(value))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(Res.string.cover_show_author),
                    description = stringResource(Res.string.cover_show_author_summary),
                    checked = settings.showAuthorDark,
                    enabled = settings.showNameDark,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowAuthorDark(value))
                    }
                )
                }
            }
        }
    }

    CoverRuleConfigSheet(
        show = state.activeSheet == CoverConfigSheet.Rule,
        state = state.rule,
        onIntent = onIntent,
        onDismissRequest = { onIntent(CoverConfigIntent.DismissSheet) },
    )

    if (state.activeSheet == CoverConfigSheet.Album) {
        CoverAlbumSelectSheet(
            show = true,
            state = albumState,
            onSelect = { onIntent(CoverConfigIntent.SelectAlbum(it)) },
            onManage = {
                onIntent(CoverConfigIntent.DismissSheet)
                onNavigateToCoverAlbums()
            },
            onDismissRequest = { onIntent(CoverConfigIntent.DismissSheet) },
        )
    }

    (state.activeSheet as? CoverConfigSheet.Color)?.field?.let { field ->
        val initialColor = when (field) {
            CoverColorField.Text -> settings.textColor
            CoverColorField.Shadow -> settings.shadowColor
            CoverColorField.TextDark -> settings.textColorDark
            CoverColorField.ShadowDark -> settings.shadowColorDark
        }

        ColorPickerSheet(
            show = true,
            initialColor = initialColor,
            onDismissRequest = { onIntent(CoverConfigIntent.DismissSheet) },
            onColorSelected = { color ->
                when (field) {
                    CoverColorField.Text -> onIntent(CoverConfigIntent.SetTextColor(color))
                    CoverColorField.Shadow -> onIntent(CoverConfigIntent.SetShadowColor(color))
                    CoverColorField.TextDark -> onIntent(CoverConfigIntent.SetTextColorDark(color))
                    CoverColorField.ShadowDark ->
                        onIntent(CoverConfigIntent.SetShadowColorDark(color))
                }
                onIntent(CoverConfigIntent.DismissSheet)
            }
        )
    }
}
