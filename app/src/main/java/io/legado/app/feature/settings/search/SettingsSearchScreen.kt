package io.legado.app.feature.settings.search

import android.os.Build
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import io.legado.app.R
import io.legado.app.feature.settings.reading.ReadSetting
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.adaptiveHorizontalPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.collections.immutable.toPersistentList
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsSearchRouteScreen(
    onBackClick: () -> Unit,
    onOpenSetting: (String) -> Unit,
    viewModel: SettingsSearchViewModel = koinViewModel(),
) {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val hasProgressiveBlurSetting = when (LegadoTheme.composeEngine) {
        ComposeEngine.Material -> true
        ComposeEngine.Miuix -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ComposeEngine.Mochi -> false
    }
    // 按当前配置解析文案，并在主题能力变化时更新可搜索条目。
    val index = remember(resources, configuration, hasProgressiveBlurSetting) {
        val reading = ReadSetting.entries.map { setting ->
            SettingsSearchEntry(
                id = setting.id,
                title = resources.getString(setting.titleRes),
                path = resources.getString(R.string.settings_search_path,
                    resources.getString(R.string.read_config), resources.getString(setting.section.titleRes)),
                description = setting.descriptionRes?.let { res ->
                    if (setting == ReadSetting.BrightnessPosition) {
                        resources.getString(res, resources.getString(R.string.brightness_bar_vertical))
                    } else resources.getString(res)
                }.orEmpty(),
                keywords = setting.keywordsRes?.let(resources::getString).orEmpty(),
            )
        }
        val availableItems = SettingsSearchCatalog.items.filterNot {
            !hasProgressiveBlurSetting && it.id == "theme.is_blur_progressive_enable"
        }
        val fixed = (SettingsSearchCatalog.categories + availableItems).map { item ->
            val pageTitle = resources.getString(item.page.titleRes)
            val parent = item.parentId?.let(SettingsSearchCatalog::find)
            val description = listOfNotNull(
                item.descriptionRes?.let(resources::getString),
                parent?.let { resources.getString(R.string.settings_search_condition, resources.getString(it.titleRes)) },
            ).joinToString("\n")
            SettingsSearchEntry(
                id = item.id,
                title = resources.getString(item.titleRes),
                path = when {
                    item.isCategory -> resources.getString(R.string.setting)
                    item.groupTitleRes != null -> resources.getString(R.string.settings_search_path,
                        pageTitle, resources.getString(item.groupTitleRes))
                    else -> pageTitle
                },
                description = description,
                keywords = when (item.page) {
                    SettingsSearchPage.Theme, SettingsSearchPage.CustomTheme, SettingsSearchPage.ThemeManage ->
                        resources.getString(R.string.settings_search_theme_keywords)
                    SettingsSearchPage.Other -> resources.getString(R.string.settings_search_advanced_keywords)
                    else -> ""
                },
            )
        }
        (reading + fixed).toPersistentList()
    }
    LaunchedEffect(index) { viewModel.onIntent(SettingsSearchIntent.IndexChanged(index)) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsSearchScreen(state, viewModel::onIntent, onBackClick, onOpenSetting)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSearchScreen(
    state: SettingsSearchUiState,
    onIntent: (SettingsSearchIntent) -> Unit,
    onBackClick: () -> Unit,
    onOpenSetting: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    AppScaffold(topBar = {
        GlassMediumFlexibleTopAppBar(
            title = stringResource(R.string.settings_search_title),
            navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
            bottomContent = {
                SearchBar(
                    modifier = Modifier.adaptiveHorizontalPadding().padding(bottom = 8.dp),
                    query = state.query,
                    onQueryChange = { onIntent(SettingsSearchIntent.QueryChanged(it)) },
                    placeholder = stringResource(R.string.settings_search_scope),
                    trailingIcon = {
                        if (state.query.isNotEmpty()) TopBarActionButton(
                            onClick = { onIntent(SettingsSearchIntent.QueryChanged("")) },
                            imageVector = AppIcons.Close,
                            contentDescription = stringResource(R.string.settings_search_clear),
                        )
                    },
                )
            },
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding(),
            contentPadding = adaptiveContentPadding(
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item("status") {
                AppText(
                    text = when {
                        state.query.isBlank() -> stringResource(R.string.settings_search_prompt)
                        state.results.isEmpty() -> stringResource(R.string.settings_search_empty)
                        else -> stringResource(R.string.settings_search_count, state.results.size)
                    },
                    modifier = Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            items(state.results, key = { it.id }) { entry ->
                val openSetting = dropUnlessResumed {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onOpenSetting(entry.id)
                }
                SplicedColumnGroup(modifier = Modifier.testTag("settings-search-result:${entry.id}")) {
                    ClickableSettingItem(
                        title = entry.title,
                        description = listOf(entry.path, entry.description).filter(String::isNotBlank).joinToString("\n"),
                        onClick = openSetting,
                    )
                }
            }
        }
    }
}
