package io.legado.app.ui.book.source.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.about.MarkdownSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.variable.VariableEditorSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourceEditScreen(
    state: BookSourceEditUiState,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onIntent: (BookSourceEditIntent) -> Unit,
) {
    BackHandler(enabled = state.dirty) {
        onIntent(BookSourceEditIntent.RequestBack)
    }
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val tabs = BookSourceEditTab.entries
    val pagerState = rememberPagerState(initialPage = state.selectedTab.ordinal) { tabs.size }
    var editingFieldPath by remember { mutableStateOf<String?>(null) }
    val editingField =
        state.fieldGroups.values.flatten().firstOrNull { it.path == editingFieldPath }
    androidx.compose.runtime.LaunchedEffect(state.selectedTab) {
        if (pagerState.settledPage != state.selectedTab.ordinal) {
            pagerState.animateScrollToPage(state.selectedTab.ordinal)
        }
    }
    androidx.compose.runtime.LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            tabs.getOrNull(page)?.let { onIntent(BookSourceEditIntent.SelectTab(it)) }
        }
    }
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                GlassMediumFlexibleTopAppBar(
                    title = stringResource(R.string.edit_book_source),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarNavigationButton(onClick = {
                            onIntent(
                                BookSourceEditIntent.RequestBack
                            )
                        })
                    },
                    actions = {
                        TopBarActionButton(
                            onClick = { onIntent(BookSourceEditIntent.SaveAndDebug) },
                            imageVector = Icons.Default.BugReport,
                            contentDescription = stringResource(R.string.debug_source),
                        )
                        TopBarActionButton(
                            onClick = { onMenuExpandedChange(true) },
                            imageVector = AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.more_menu),
                        )
                        BookSourceEditMenu(menuExpanded, onMenuExpandedChange, state, onIntent)
                    },
                )
                AppTabRow(
                    tabTitles = BookSourceEditTab.entries.map { stringResource(it.titleRes) },
                    selectedTabIndex = state.selectedTab.ordinal,
                    onTabSelected = { page ->
                        onIntent(BookSourceEditIntent.SelectTab(tabs[page]))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(BookSourceEditIntent.Save) },
                tooltipText = stringResource(R.string.action_save),
                icon = Icons.Default.Save,
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val tab = tabs[page]
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == BookSourceEditTab.Base) {
                    item(key = "options", contentType = "options") {
                        BookSourceOptions(state, onIntent)
                    }
                }
                items(
                    items = state.fieldGroups[tab].orEmpty(),
                    key = { it.path },
                    contentType = { "field" },
                ) { field ->
                    SourceEditFieldCard(
                        field = field,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { editingFieldPath = field.path },
                    )
                }
            }
        }
    }
    val variableSheet = state.activeSheet as? BookSourceEditSheet.Variable
    VariableEditorSheet(
        state = variableSheet?.editor,
        onValueChange = { onIntent(BookSourceEditIntent.UpdateVariable(it)) },
        onSave = { onIntent(BookSourceEditIntent.SaveVariable) },
        onDismissRequest = { onIntent(BookSourceEditIntent.DismissSheet) },
    )
    SourceEditFieldSheet(
        field = editingField,
        onDismissRequest = { editingFieldPath = null },
        onValueChange = { path, value ->
            onIntent(BookSourceEditIntent.UpdateField(path, value))
        },
    )
    AppLogSheet(
        show = state.activeSheet is BookSourceEditSheet.Log,
        onDismissRequest = { onIntent(BookSourceEditIntent.DismissSheet) },
    )
    val helpSheet = state.activeSheet as? BookSourceEditSheet.Help
    MarkdownSheet(
        show = helpSheet != null,
        title = stringResource(R.string.help),
        content = helpSheet?.content.orEmpty(),
        onDismissRequest = { onIntent(BookSourceEditIntent.DismissSheet) },
    )
}

@Composable
fun SourceEditFieldCard(
    field: BookSourceEditFieldUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val title = field.labelRes?.let { stringResource(it) } ?: field.label.orEmpty()
    GlassCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppText(
                text = title,
                style = LegadoTheme.typography.titleSmall,
            )
            AppText(
                text = field.value.ifBlank { "—" },
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SourceEditFieldSheet(
    field: BookSourceEditFieldUi?,
    onDismissRequest: () -> Unit,
    onValueChange: (String, String) -> Unit,
) {
    val title = field?.let {
        it.labelRes?.let { labelRes -> stringResource(labelRes) } ?: it.label.orEmpty()
    }
    AppModalBottomSheet(
        show = field != null,
        onDismissRequest = onDismissRequest,
        title = title,
        animateContentSize = false,
    ) {
        if (field != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                AppTextField(
                    value = field.value,
                    onValueChange = { onValueChange(field.path, it) },
                    label = title,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 24,
                )
            }
        }
    }
}

@Composable
private fun BookSourceOptions(
    state: BookSourceEditUiState,
    onIntent: (BookSourceEditIntent) -> Unit,
) {
    val sourceTypes = stringArrayResource(R.array.book_type)
    var typeMenuExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SourceEditOptionCard(
                    title = stringResource(R.string.book_type),
                    subtitle = sourceTypes.getOrNull(state.bookSourceType),
                    onClick = { typeMenuExpanded = true },
                )
                RoundDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                ) {
                    sourceTypes.forEachIndexed { index, title ->
                        RoundDropdownMenuItem(
                            text = title,
                            isSelected = state.bookSourceType == index,
                            onClick = {
                                typeMenuExpanded = false
                                onIntent(BookSourceEditIntent.SetSourceType(index))
                            },
                        )
                    }
                }
            }
            SourceEditOptionCard(
                title = stringResource(R.string.is_enable),
                checked = state.enabled,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(BookSourceEditIntent.SetEnabled(!state.enabled)) },
            )
            SourceEditOptionCard(
                title = stringResource(R.string.discovery),
                checked = state.enabledExplore,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(BookSourceEditIntent.SetExploreEnabled(!state.enabledExplore)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceEditOptionCard(
                title = stringResource(R.string.auto_save_cookie),
                checked = state.enabledCookieJar,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(BookSourceEditIntent.SetCookieJarEnabled(!state.enabledCookieJar)) },
            )
            SourceEditOptionCard(
                title = stringResource(R.string.is_event_listener),
                checked = state.eventListener,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(BookSourceEditIntent.SetEventListener(!state.eventListener)) },
            )
            SourceEditOptionCard(
                title = stringResource(R.string.custom_button),
                checked = state.customButton,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(BookSourceEditIntent.SetCustomButton(!state.customButton)) },
            )
        }
    }
}

@Composable
fun SourceEditOptionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    checked: Boolean? = null,
) {
    GlassCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = title,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .basicMarquee(),
                style = LegadoTheme.typography.labelSmall,
            )
            AnimatedVisibility(
                visible = !subtitle.isNullOrBlank()
            ) {
                TextCard(
                    text = subtitle,
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    cornerRadius = 16.dp
                )
            }
            AnimatedVisibility(
                visible = checked == true
            ) {
                AppIcon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    tint = LegadoTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BookSourceEditMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    state: BookSourceEditUiState,
    onIntent: (BookSourceEditIntent) -> Unit,
) {
    fun click(intent: BookSourceEditIntent) {
        onExpandedChange(false); onIntent(intent)
    }
    RoundDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
        RoundDropdownMenuItem(
            text = stringResource(R.string.login),
            onClick = { click(BookSourceEditIntent.SaveAndLogin) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.search),
            onClick = { click(BookSourceEditIntent.SaveAndSearch) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.cookie),
            onClick = { click(BookSourceEditIntent.ClearCookie) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.auto_complete),
            isSelected = state.autoComplete,
            onClick = { click(BookSourceEditIntent.ToggleAutoComplete) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.copy_source),
            onClick = { click(BookSourceEditIntent.Copy) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.paste_source),
            onClick = { click(BookSourceEditIntent.Paste) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.set_source_variable),
            onClick = { click(BookSourceEditIntent.SaveAndSetVariable) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.str_share),
            onClick = { click(BookSourceEditIntent.Share) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.log),
            onClick = { click(BookSourceEditIntent.ShowLog) })
        RoundDropdownMenuItem(
            text = stringResource(R.string.help),
            onClick = { click(BookSourceEditIntent.ShowHelp) })
    }
}
