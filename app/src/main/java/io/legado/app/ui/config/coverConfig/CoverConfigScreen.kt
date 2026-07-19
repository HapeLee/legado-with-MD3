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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
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
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.LocalContext
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CoverConfigRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToCoverAlbums: () -> Unit,
    viewModel: CoverConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CoverConfigEffect.ShowToast -> context.toastOnUi(effect.stringRes)
            }
        }
    }
    CoverConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onNavigateToCoverAlbums = onNavigateToCoverAlbums,
    )
}

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
                    checked = settings.loadOnlyOnWifi,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetLoadOnlyOnWifi(value))
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.cover_rule),
                    description = stringResource(R.string.cover_rule_summary),
                    onClick = { onIntent(CoverConfigIntent.ShowSheet(CoverConfigSheet.Rule)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.use_default_cover),
                    description = stringResource(R.string.use_default_cover_s),
                    checked = settings.useDefaultCover,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetUseDefaultCover(value))
                    }
                )

                ClickableSettingItem(
                    title = stringResource(R.string.default_cover),
                    description = selectedAlbum?.let {
                        "${it.name} · ${
                            stringResource(
                                R.string.cover_album_day_night_count,
                                it.lightImages.size,
                                it.darkImages.size,
                            )
                        }"
                    } ?: stringResource(R.string.cover_album_none),
                    onClick = { onIntent(CoverConfigIntent.ShowSheet(CoverConfigSheet.Album)) }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_shadow),
                    checked = settings.showShadow,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowShadow(value))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_stroke),
                    checked = settings.showStroke,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowStroke(value))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.default_color),
                    checked = settings.useDefaultColor,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetUseDefaultColor(value))
                    }
                )
            }

            SplicedColumnGroup {
                DropdownListSettingItem(
                    title = stringResource(R.string.cover_info_orientation),
                    selectedValue = settings.infoOrientation,
                    displayEntries = arrayOf(
                        stringResource(R.string.screen_portrait),
                        stringResource(R.string.screen_landscape)
                    ),
                    entryValues = arrayOf("0", "1"),
                    onValueChange = { value ->
                        onIntent(CoverConfigIntent.SetInfoOrientation(value))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.network_book_badge_setting)) {
                DropdownListSettingItem(
                    title = stringResource(R.string.network_book_badge_setting),
                    selectedValue = settings.exploreFilterState.toString(),
                    displayEntries = arrayOf(
                        stringResource(R.string.filter_show_all),
                        stringResource(R.string.filter_hide_in_shelf),
                        stringResource(R.string.filter_hide_same_name_author),
                        stringResource(R.string.filter_show_not_in_shelf_only)
                    ),
                    entryValues = arrayOf("0", "1", "2", "3"),
                    onValueChange = { value ->
                        onIntent(CoverConfigIntent.SetExploreFilterState(value.toInt()))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.day)) {
                ClickableSettingItem(
                    title = stringResource(R.string.text_color),
                    option = "#${Integer.toHexString(settings.textColor).uppercase()}",
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
                    title = stringResource(R.string.text_shadow_color),
                    option = "#${Integer.toHexString(settings.shadowColor).uppercase()}",
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
                    title = stringResource(R.string.cover_show_name),
                    description = stringResource(R.string.cover_show_name_summary),
                    checked = settings.showName,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowName(value))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_author),
                    description = stringResource(R.string.cover_show_author_summary),
                    checked = settings.showAuthor,
                    enabled = settings.showName,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowAuthor(value))
                    }
                )
            }

            SplicedColumnGroup(title = stringResource(R.string.night)) {
                ClickableSettingItem(
                    title = stringResource(R.string.text_color),
                    option = "#${Integer.toHexString(settings.textColorDark).uppercase()}",
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
                    title = stringResource(R.string.text_shadow_color),
                    option = "#${Integer.toHexString(settings.shadowColorDark).uppercase()}",
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
                    title = stringResource(R.string.cover_show_name),
                    description = stringResource(R.string.cover_show_name_summary),
                    checked = settings.showNameDark,
                    onCheckedChange = { value ->
                        onIntent(CoverConfigIntent.SetShowNameDark(value))
                    }
                )

                SwitchSettingItem(
                    title = stringResource(R.string.cover_show_author),
                    description = stringResource(R.string.cover_show_author_summary),
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
