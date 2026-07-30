package io.legado.app.ui.rss.source.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.RssSourceEditRepository
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppCacheManager
import io.legado.app.help.RuleComplete
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.removeSortCache
import io.legado.app.model.SharedJsScope
import io.legado.app.ui.book.source.edit.BookSourceEditFieldUi
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RssSourceEditViewModel(
    app: Application,
    private val repository: RssSourceEditRepository,
) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(RssSourceEditUiState()); val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<RssSourceEditEffect>(extraBufferCapacity = 16); val effects = _effects.asSharedFlow()
    private var original: RssSource? = null; private var json = JsonObject(); private var baseline = ""
    fun onIntent(i: RssSourceEditIntent) { when(i) {
        is RssSourceEditIntent.Load -> load(i.url); is RssSourceEditIntent.SelectTab -> _uiState.update { it.copy(selectedTab=i.tab) }
        is RssSourceEditIntent.UpdateField -> update(i.key,i.value); is RssSourceEditIntent.EditField -> _uiState.update { it.copy(selectedField=i.key) }
        is RssSourceEditIntent.SetEnabled -> flag { copy(enabled=i.value) }; is RssSourceEditIntent.SetSingleUrl -> flag { copy(singleUrl=i.value) }
        is RssSourceEditIntent.SetCookieJar -> flag { copy(cookieJar=i.value) }; is RssSourceEditIntent.SetPreload -> flag { copy(preload=i.value) }
        is RssSourceEditIntent.SetType -> flag { copy(type=i.value) }; is RssSourceEditIntent.SetArticleStyle -> flag { copy(articleStyle=i.value) }
        RssSourceEditIntent.ToggleAutoComplete -> _uiState.update { it.copy(autoComplete=!it.autoComplete) }
        RssSourceEditIntent.Save -> save { RssSourceEditEffect.Finish(it) }; RssSourceEditIntent.SaveDebug -> save { RssSourceEditEffect.Debug(it) }
        RssSourceEditIntent.SaveLogin -> save { RssSourceEditEffect.Login(it) }; RssSourceEditIntent.Copy -> _effects.tryEmit(RssSourceEditEffect.Copy(GSON.toJson(current())))
        RssSourceEditIntent.Paste -> _effects.tryEmit(RssSourceEditEffect.ReadClipboard); is RssSourceEditIntent.Import -> import(i.text)
        RssSourceEditIntent.Share -> _effects.tryEmit(RssSourceEditEffect.Share(GSON.toJson(current()))); RssSourceEditIntent.ClearCookie -> viewModelScope.launch(Dispatchers.IO){ CookieStore.removeCookie(current().sourceUrl) }
        RssSourceEditIntent.SetVariable -> save { RssSourceEditEffect.Variable(it) }; RssSourceEditIntent.ShowLog -> _uiState.update {
            it.copy(
                activeSheet = RssSourceEditSheet.Log
            )
        }

        RssSourceEditIntent.Help -> showHelp(); RssSourceEditIntent.DismissSheet -> _uiState.update {
            it.copy(
                activeSheet = null
            )
        }; RssSourceEditIntent.Back -> _effects.tryEmit(RssSourceEditEffect.Finish(""))
    } }
    private fun load(url:String?)=viewModelScope.launch(Dispatchers.IO){ apply(url?.let{repository.findByUrl(it)}?:RssSource(),true) }
    private fun showHelp() = viewModelScope.launch(Dispatchers.IO) {
        val content =
            getApplication<Application>().assets.open("web/help/md/ruleHelp.md").bufferedReader()
                .use { it.readText() }; _uiState.update {
        it.copy(
            activeSheet = RssSourceEditSheet.Help(
                content
            )
        )
    }
    }
    private fun apply(s:RssSource, asOriginal:Boolean=false){ if(asOriginal){original=s;baseline=GSON.toJson(s)};json=JsonParser.parseString(GSON.toJson(s)).asJsonObject;_uiState.value=RssSourceEditUiState(false,fields=groups(),enabled=s.enabled,singleUrl=s.singleUrl,cookieJar=s.enabledCookieJar==true,preload=s.preload,type=s.type,articleStyle=s.articleStyle) }
    private fun update(k:String,v:String){ if(v.isBlank())json.remove(k) else json.addProperty(k,v);_uiState.update{st->st.copy(fields=st.fields.mapValues{(_,fs)->fs.map{if(it.path==k)it.copy(value=v)else it}.toImmutableList()}.toImmutableMap(),dirty=true)} }
    private fun flag(f:RssSourceEditUiState.()->RssSourceEditUiState)=_uiState.update{f(it).copy(dirty=true)}
    private fun current():RssSource=GSON.fromJson(json,RssSource::class.java).apply{val s=_uiState.value;enabled=s.enabled;singleUrl=s.singleUrl;enabledCookieJar=s.cookieJar;preload=s.preload;type=s.type;articleStyle=s.articleStyle;if(s.autoComplete){ruleNextPage=RuleComplete.autoComplete(ruleNextPage,ruleArticles,2);ruleTitle=RuleComplete.autoComplete(ruleTitle,ruleArticles);rulePubDate=RuleComplete.autoComplete(rulePubDate,ruleArticles);ruleDescription=RuleComplete.autoComplete(ruleDescription,ruleArticles);ruleImage=RuleComplete.autoComplete(ruleImage,ruleArticles,3);ruleLink=RuleComplete.autoComplete(ruleLink,ruleArticles);ruleContent=RuleComplete.autoComplete(ruleContent,ruleArticles)}}
    private fun save(effect:(String)->RssSourceEditEffect)=viewModelScope.launch(Dispatchers.IO){runCatching{val s=current();if(s.sourceName.isBlank()||s.sourceUrl.isBlank())throw NoStackTraceException(getApplication<Application>().getString(R.string.non_null_name_url));val old=original?:RssSource();if(!s.equal(old)){s.lastUpdateTime=System.currentTimeMillis();if(old.sortUrl!=s.sortUrl)old.removeSortCache();if(old.jsLib!=s.jsLib)SharedJsScope.remove(old.jsLib)};if(repository.save(original,s))AppCacheManager.clearSourceVariables();original=s;baseline=GSON.toJson(s);s.sourceUrl}.onSuccess{_uiState.update{s->s.copy(dirty=false)};_effects.emit(effect(it))}.onFailure{_effects.emit(RssSourceEditEffect.Message(it.localizedMessage?:"Error"))}}
    private fun import(text:String)=runCatching{GSON.fromJson(text,RssSource::class.java)}.onSuccess{apply(it);_uiState.update{s->s.copy(dirty=true)}}.onFailure{_effects.tryEmit(RssSourceEditEffect.Message(it.localizedMessage?:"格式不对"))}
    private data class F(val k:String,val r:Int?=null,val l:String?=null)
    private fun groups()=SPECS.mapValues{(_,v)->v.map{BookSourceEditFieldUi(it.k,it.r,it.l,json.get(it.k)?.takeUnless{e->e.isJsonNull}?.asString.orEmpty())}.toImmutableList()}.toImmutableMap()
    companion object {
        private fun f(k:String,r:Int)=F(k,r)
        private val SPECS=mapOf(
        RssSourceEditTab.Base to listOf(f("sourceName",R.string.source_name),f("sourceUrl",R.string.source_url),f("sourceIcon",R.string.source_icon),f("sourceGroup",R.string.source_group),f("sourceComment",R.string.comment),f("searchUrl",R.string.r_search_url),f("sortUrl",R.string.sort_url),f("loginUrl",R.string.login_url),f("loginUi",R.string.login_ui),f("loginCheckJs",R.string.login_check_js),f("coverDecodeJs",R.string.cover_decode_js),f("header",R.string.source_http_header),f("variableComment",R.string.variable_comment),f("concurrentRate",R.string.concurrent_rate),F("jsLib",l="jsLib")),
        RssSourceEditTab.Start to listOf(f("startHtml",R.string.r_startHtml),f("startStyle",R.string.r_startStyle),f("startJs",R.string.r_startJs),f("preloadJs",R.string.r_preloadJs)),
        RssSourceEditTab.List to listOf(f("ruleArticles",R.string.r_articles),f("ruleNextPage",R.string.r_next),f("ruleTitle",R.string.r_title),f("rulePubDate",R.string.r_date),f("ruleDescription",R.string.r_description),f("ruleImage",R.string.r_image),f("ruleLink",R.string.r_link)),
        RssSourceEditTab.WebView to listOf(f("ruleContent",R.string.r_content),f("style",R.string.r_style),f("injectJs",R.string.r_inject_js),f("contentWhitelist",R.string.c_whitelist),f("contentBlacklist",R.string.c_blacklist),F("shouldOverrideUrlLoading",l="URL 跳转拦截 JS"))) }
}
