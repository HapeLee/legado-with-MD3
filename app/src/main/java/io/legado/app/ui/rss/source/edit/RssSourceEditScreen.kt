package io.legado.app.ui.rss.source.edit

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.source.edit.SourceEditFieldCard
import io.legado.app.ui.book.source.edit.SourceEditFieldSheet
import io.legado.app.ui.book.source.edit.SourceEditOptionCard
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.card.resolveBaseCardShape
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.menu.AppChoiceDropdown
import io.legado.app.ui.widget.components.menu.AppChoiceOption
import io.legado.app.ui.widget.components.modalBottomSheet.MarkdownSheet
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.variable.VariableEditorSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable fun RssSourceEditScreen(state:RssSourceEditUiState,onIntent:(RssSourceEditIntent)->Unit,onBack:()->Unit){
    BackHandler(enabled = state.dirty) { onBack() };
    val tabs = RssSourceEditTab.entries;
    val pager = rememberPagerState(state.selectedTab.ordinal) { tabs.size };
    val scroll = GlassTopAppBarDefaults.defaultScrollBehavior();
    LaunchedEffect(state.selectedTab) {
        if (pager.settledPage != state.selectedTab.ordinal) pager.animateScrollToPage(
            state.selectedTab.ordinal
        )
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect {
            onIntent(
                RssSourceEditIntent.SelectTab(tabs[it])
            )
        }
    }
    AppScaffold(modifier=Modifier.nestedScroll(scroll.nestedScrollConnection),topBar={Column{GlassMediumFlexibleTopAppBar(
        title=stringResource(R.string.rss_source_edit),navigationIcon={TopBarNavigationButton(onClick=onBack)},scrollBehavior=scroll,
        actions={TopBarActionButton({onIntent(RssSourceEditIntent.SaveDebug)},Icons.Default.BugReport,stringResource(R.string.debug_source));RssEditMenu(onIntent)}
    ); AppTabRow(
        tabs.map { stringResource(it.title) },
        state.selectedTab.ordinal,
        { p -> onIntent(RssSourceEditIntent.SelectTab(tabs[p])) },
        Modifier.fillMaxWidth(),
        horizontalPadding = 12.dp,
    )
    }
    },
        floatingActionButton={AppFloatingActionButton({onIntent(RssSourceEditIntent.Save)},icon=Icons.Default.Save,tooltipText=stringResource(R.string.action_save))}){padding->
        HorizontalPager(pager,Modifier.fillMaxSize()){page->val tab=tabs[page];LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,padding.calculateTopPadding()+12.dp,16.dp,padding.calculateBottomPadding()+96.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(tab==RssSourceEditTab.Base)item("options"){RssEditOptions(state,onIntent)}
            items(state.fields[tab].orEmpty(),key={it.path}){field->SourceEditFieldCard(field,Modifier.fillMaxWidth()){onIntent(RssSourceEditIntent.EditField(field.path))}}
        }} }
    val field=state.fields.values.flatten().firstOrNull{it.path==state.selectedField};SourceEditFieldSheet(field,{onIntent(RssSourceEditIntent.EditField(null))}){k,v->onIntent(RssSourceEditIntent.UpdateField(k,v))}
    AppLogSheet(
        show = state.activeSheet is RssSourceEditSheet.Log,
        onDismissRequest = { onIntent(RssSourceEditIntent.DismissSheet) })
    val helpSheet = state.activeSheet as? RssSourceEditSheet.Help
    MarkdownSheet(
        show = helpSheet != null,
        title = stringResource(R.string.help),
        content = helpSheet?.content.orEmpty(),
        onDismissRequest = { onIntent(RssSourceEditIntent.DismissSheet) })
    val variableSheet = state.activeSheet as? RssSourceEditSheet.Variable
    VariableEditorSheet(
        state = variableSheet?.editor,
        onValueChange = { onIntent(RssSourceEditIntent.UpdateVariable(it)) },
        onSave = { onIntent(RssSourceEditIntent.SaveVariable) },
        onDismissRequest = { onIntent(RssSourceEditIntent.DismissSheet) },
    )
}
@Composable
private fun RssEditOptions(
    s: RssSourceEditUiState,
    on: (RssSourceEditIntent) -> Unit,
) {
    val types = stringArrayResource(R.array.rss_type)
    val styles = stringArrayResource(R.array.layout_type)
    val typeOptions = types.mapIndexed { i, t ->
        AppChoiceOption(id = i.toString(), label = t)
    }.toImmutableList()
    val styleOptions = styles.mapIndexed { i, t ->
        AppChoiceOption(id = i.toString(), label = t)
    }.toImmutableList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChoiceDropdown(
                options = typeOptions,
                selectedId = s.type.toString(),
                onSelect = { on(RssSourceEditIntent.SetType(it.toInt())) },
                title = "类型：${types.getOrNull(s.type).orEmpty()}",
                modifier = Modifier.weight(1f),
                compact = true,
                triggerShape = resolveBaseCardShape(),
            ) {
                SourceEditOptionCard(
                    title = "类型：${types.getOrNull(s.type).orEmpty()}",
                    onClick = null,
                    modifier = this.modifier,
                )
            }
            AppChoiceDropdown(
                options = styleOptions,
                selectedId = s.articleStyle.toString(),
                onSelect = { on(RssSourceEditIntent.SetArticleStyle(it.toInt())) },
                title = "布局：${styles.getOrNull(s.articleStyle).orEmpty()}",
                modifier = Modifier.weight(1f),
                compact = true,
                triggerShape = resolveBaseCardShape(),
            ) {
                SourceEditOptionCard(
                    title = "布局：${styles.getOrNull(s.articleStyle).orEmpty()}",
                    onClick = null,
                    modifier = this.modifier,
                )
            }
            SourceEditOptionCard(
                stringResource(R.string.is_enable),
                { on(RssSourceEditIntent.SetEnabled(!s.enabled)) },
                Modifier.weight(1f),
                checked = s.enabled,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceEditOptionCard(
                stringResource(R.string.single_url),
                { on(RssSourceEditIntent.SetSingleUrl(!s.singleUrl)) },
                Modifier.weight(1f),
                checked = s.singleUrl,
            )
            SourceEditOptionCard(
                stringResource(R.string.auto_save_cookie),
                { on(RssSourceEditIntent.SetCookieJar(!s.cookieJar)) },
                Modifier.weight(1f),
                checked = s.cookieJar,
            )
            SourceEditOptionCard(
                stringResource(R.string.enable_preload),
                { on(RssSourceEditIntent.SetPreload(!s.preload)) },
                Modifier.weight(1f),
                checked = s.preload,
            )
        }
    }
}
/** RSS 源编辑命令菜单的稳定 ID 与文案：UI 条目与 ID → Intent 映射共用这一份定义。 */
internal enum class RssSourceEditMenuAction(
    val id: String,
    @param:StringRes val labelRes: Int,
) {
    Login("login", R.string.login),
    AutoComplete("auto_complete", R.string.auto_complete),
    Copy("copy_source", R.string.copy_source),
    Paste("paste_source", R.string.paste_source),
    Variable("set_source_variable", R.string.set_source_variable),
    Share("str_share", R.string.str_share),
    ClearCookie("cookie", R.string.cookie),
    Log("log", R.string.log),
    Help("help", R.string.help),
}

/** 未知 ID 不产生动作，避免入口回调拼错时误触发其它 Intent。 */
internal fun rssSourceEditIntentFor(id: String): RssSourceEditIntent? = when (id) {
    RssSourceEditMenuAction.Login.id -> RssSourceEditIntent.SaveLogin
    RssSourceEditMenuAction.AutoComplete.id -> RssSourceEditIntent.ToggleAutoComplete
    RssSourceEditMenuAction.Copy.id -> RssSourceEditIntent.Copy
    RssSourceEditMenuAction.Paste.id -> RssSourceEditIntent.Paste
    RssSourceEditMenuAction.Variable.id -> RssSourceEditIntent.SetVariable
    RssSourceEditMenuAction.Share.id -> RssSourceEditIntent.Share
    RssSourceEditMenuAction.ClearCookie.id -> RssSourceEditIntent.ClearCookie
    RssSourceEditMenuAction.Log.id -> RssSourceEditIntent.ShowLog
    RssSourceEditMenuAction.Help.id -> RssSourceEditIntent.Help
    else -> null
}

@Composable
private fun RssEditMenu(on: (RssSourceEditIntent) -> Unit) {
    val actions = RssSourceEditMenuAction.entries.map { action ->
        AppAction(id = action.id, label = stringResource(action.labelRes))
    }.toImmutableList()
    AppActionMenu(
        groups = persistentListOf(
            AppActionGroup(id = "rss_source_edit_more", actions = actions)
        ),
        onAction = { id -> rssSourceEditIntentFor(id)?.let(on) },
    ) {
        AppIcon(
            imageVector = AppIcons.MoreVert,
            contentDescription = stringResource(R.string.more_menu),
            modifier = this.modifier,
        )
    }
}
