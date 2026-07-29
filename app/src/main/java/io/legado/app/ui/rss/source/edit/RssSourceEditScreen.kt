package io.legado.app.ui.rss.source.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.about.MarkdownSheet
import io.legado.app.ui.book.source.edit.SourceEditFieldCard
import io.legado.app.ui.book.source.edit.SourceEditFieldSheet
import io.legado.app.ui.book.source.edit.SourceEditOptionCard
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.launch

@Composable fun RssSourceEditScreen(state:RssSourceEditUiState,onIntent:(RssSourceEditIntent)->Unit,onBack:()->Unit){
    BackHandler{onBack()};val tabs=RssSourceEditTab.entries;val pager=rememberPagerState(state.selectedTab.ordinal){tabs.size};val scope=rememberCoroutineScope();val scroll=GlassTopAppBarDefaults.defaultScrollBehavior();var menu by remember{mutableStateOf(false)}
    LaunchedEffect(state.selectedTab){if(pager.currentPage!=state.selectedTab.ordinal)pager.animateScrollToPage(state.selectedTab.ordinal)}
    LaunchedEffect(pager){snapshotFlow{pager.currentPage}.collect{onIntent(RssSourceEditIntent.SelectTab(tabs[it]))}}
    AppScaffold(modifier=Modifier.nestedScroll(scroll.nestedScrollConnection),topBar={Column{GlassMediumFlexibleTopAppBar(
        title=stringResource(R.string.rss_source_edit),navigationIcon={TopBarNavigationButton(onClick=onBack)},scrollBehavior=scroll,
        actions={TopBarActionButton({onIntent(RssSourceEditIntent.SaveDebug)},Icons.Default.BugReport,stringResource(R.string.debug_source));TopBarActionButton({menu=true},Icons.Default.MoreVert,stringResource(R.string.more_menu));RssEditMenu(menu,{menu=false},onIntent)}
    );AppTabRow(tabs.map{stringResource(it.title)},state.selectedTab.ordinal,{p->onIntent(RssSourceEditIntent.SelectTab(tabs[p]));scope.launch{pager.animateScrollToPage(p)}},Modifier.fillMaxWidth())}},
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
}
@Composable private fun RssEditOptions(s:RssSourceEditUiState,on:(RssSourceEditIntent)->Unit){val types=stringArrayResource(R.array.rss_type);val styles=stringArrayResource(R.array.layout_type);var typeMenu by remember{mutableStateOf(false)};var styleMenu by remember{mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){SourceEditOptionCard("类型：${types.getOrNull(s.type).orEmpty()}",{typeMenu=true});RoundDropdownMenu(typeMenu,{typeMenu=false}){types.forEachIndexed{i,t->RoundDropdownMenuItem(text=t,isSelected=s.type==i,onClick={typeMenu=false;on(RssSourceEditIntent.SetType(i))})}}};Box(Modifier.weight(1f)){SourceEditOptionCard("布局：${styles.getOrNull(s.articleStyle).orEmpty()}",{styleMenu=true});RoundDropdownMenu(styleMenu,{styleMenu=false}){styles.forEachIndexed{i,t->RoundDropdownMenuItem(text=t,isSelected=s.articleStyle==i,onClick={styleMenu=false;on(RssSourceEditIntent.SetArticleStyle(i))})}}};SourceEditOptionCard(stringResource(R.string.is_enable),{on(RssSourceEditIntent.SetEnabled(!s.enabled))},Modifier.weight(1f),checked=s.enabled)}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SourceEditOptionCard(stringResource(R.string.single_url),{on(RssSourceEditIntent.SetSingleUrl(!s.singleUrl))},Modifier.weight(1f),checked=s.singleUrl);SourceEditOptionCard(stringResource(R.string.auto_save_cookie),{on(RssSourceEditIntent.SetCookieJar(!s.cookieJar))},Modifier.weight(1f),checked=s.cookieJar);SourceEditOptionCard(stringResource(R.string.enable_preload),{on(RssSourceEditIntent.SetPreload(!s.preload))},Modifier.weight(1f),checked=s.preload)}}
}
@Composable private fun RssEditMenu(show:Boolean,dismiss:()->Unit,on:(RssSourceEditIntent)->Unit){fun go(i:RssSourceEditIntent){dismiss();on(i)};RoundDropdownMenu(show,dismiss){RoundDropdownMenuItem(text=stringResource(R.string.login),onClick={go(RssSourceEditIntent.SaveLogin)});RoundDropdownMenuItem(text=stringResource(R.string.auto_complete),onClick={go(RssSourceEditIntent.ToggleAutoComplete)});RoundDropdownMenuItem(text=stringResource(R.string.copy_source),onClick={go(RssSourceEditIntent.Copy)});RoundDropdownMenuItem(text=stringResource(R.string.paste_source),onClick={go(RssSourceEditIntent.Paste)});RoundDropdownMenuItem(text=stringResource(R.string.set_source_variable),onClick={go(RssSourceEditIntent.SetVariable)});RoundDropdownMenuItem(text=stringResource(R.string.str_share),onClick={go(RssSourceEditIntent.Share)});RoundDropdownMenuItem(text=stringResource(R.string.cookie),onClick={go(RssSourceEditIntent.ClearCookie)});RoundDropdownMenuItem(text=stringResource(R.string.log),onClick={go(RssSourceEditIntent.ShowLog)});RoundDropdownMenuItem(text=stringResource(R.string.help),onClick={go(RssSourceEditIntent.Help)})}}
