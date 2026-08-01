package io.legado.app.ui.book.source.edit

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ConcurrentRateLimiter.Companion.concurrentRecordMap
import io.legado.app.help.RuleComplete
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.storage.ImportOldData
import io.legado.app.model.SharedJsScope
import io.legado.app.ui.widget.components.variable.VariableEditorUiState
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.jsonPath
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookSourceEditViewModel(
    private val application: Application,
    private val repository: BookSourceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookSourceEditUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<BookSourceEditEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var originalSource: BookSource? = null
    private var draftJson = JsonObject()
    private var baselineJson = ""

    fun onIntent(intent: BookSourceEditIntent) {
        when (intent) {
            is BookSourceEditIntent.Load -> load(intent.sourceUrl)
            is BookSourceEditIntent.SelectTab -> selectTab(intent.tab)
            is BookSourceEditIntent.UpdateField -> updateField(intent.path, intent.value)
            is BookSourceEditIntent.SetEnabled -> updateFlags { copy(enabled = intent.value) }
            is BookSourceEditIntent.SetExploreEnabled -> updateFlags { copy(enabledExplore = intent.value) }
            is BookSourceEditIntent.SetCookieJarEnabled -> updateFlags { copy(enabledCookieJar = intent.value) }
            is BookSourceEditIntent.SetEventListener -> updateFlags { copy(eventListener = intent.value) }
            is BookSourceEditIntent.SetCustomButton -> updateFlags { copy(customButton = intent.value) }
            is BookSourceEditIntent.SetSourceType -> updateFlags { copy(bookSourceType = intent.value) }
            is BookSourceEditIntent.ImportText -> importText(intent.text)
            BookSourceEditIntent.ToggleAutoComplete -> _uiState.update { it.copy(autoComplete = !it.autoComplete) }
            BookSourceEditIntent.Save -> save(BookSourceEditEffect::Finish)
            BookSourceEditIntent.SaveAndDebug -> save { BookSourceEditEffect.OpenDebug(it) }
            BookSourceEditIntent.SaveAndLogin -> save { BookSourceEditEffect.OpenLogin(it) }
            BookSourceEditIntent.SaveAndSearch -> save {
                BookSourceEditEffect.OpenSearch(
                    GSON.toJson(
                        currentSource()
                    )
                )
            }

            BookSourceEditIntent.Copy -> _effects.tryEmit(
                BookSourceEditEffect.CopyText(
                    GSON.toJson(
                        currentSource()
                    )
                )
            )

            BookSourceEditIntent.Share -> _effects.tryEmit(
                BookSourceEditEffect.ShareText(
                    GSON.toJson(
                        currentSource()
                    )
                )
            )

            BookSourceEditIntent.Paste -> _effects.tryEmit(BookSourceEditEffect.ReadClipboard)
            BookSourceEditIntent.ClearCookie -> clearCookie()
            BookSourceEditIntent.ShowLog -> _uiState.update { it.copy(activeSheet = BookSourceEditSheet.Log) }
            BookSourceEditIntent.ShowHelp -> showHelp()
            BookSourceEditIntent.DismissSheet -> _uiState.update { it.copy(activeSheet = null) }
            BookSourceEditIntent.SaveAndSetVariable -> save { BookSourceEditEffect.OpenVariable(it) }
            is BookSourceEditIntent.UpdateVariable -> updateVariable(intent.value)
            BookSourceEditIntent.SaveVariable -> saveVariable()
            BookSourceEditIntent.RequestBack -> if (_uiState.value.dirty) {
                _uiState.update {
                    it.copy(activeDialog = BookSourceEditDialog.ConfirmDiscard)
                }
            } else _effects.tryEmit(BookSourceEditEffect.Finish(""))

            BookSourceEditIntent.DismissDialog -> _uiState.update {
                it.copy(activeDialog = null)
            }
            BookSourceEditIntent.DiscardChanges -> _effects.tryEmit(BookSourceEditEffect.Finish(""))
        }
    }

    private fun load(sourceUrl: String?) = viewModelScope.launch(Dispatchers.IO) {
        val source = sourceUrl?.let { repository.getBookSource(it) } ?: BookSource()
        withContext(Dispatchers.Main) {
            applySource(source, asOriginal = true)
            if (sourceUrl == null) originalSource = null
        }
    }

    private fun showHelp() = viewModelScope.launch(Dispatchers.IO) {
        val content = application.assets
            .open("web/help/md/ruleHelp.md")
            .bufferedReader()
            .use { it.readText() }
        _uiState.update { it.copy(activeSheet = BookSourceEditSheet.Help(content)) }
    }

    private fun applySource(source: BookSource, asOriginal: Boolean = false) {
        if (asOriginal) originalSource = source
        draftJson = JsonParser.parseString(GSON.toJson(source)).asJsonObject
        if (asOriginal) baselineJson = GSON.toJson(source)
        val tab = _uiState.value.selectedTab
        _uiState.value = BookSourceEditUiState(
            loading = false,
            selectedTab = tab,
            fieldGroups = fieldGroups(),
            enabled = source.enabled,
            enabledExplore = source.enabledExplore,
            enabledCookieJar = source.enabledCookieJar == true,
            eventListener = source.eventListener,
            customButton = source.customButton,
            bookSourceType = source.bookSourceType,
            autoComplete = _uiState.value.autoComplete,
        )
    }

    private fun selectTab(tab: BookSourceEditTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    private fun updateField(path: String, value: String) {
        draftJson.setPath(path, value)
        _uiState.update { state ->
            state.copy(
                fieldGroups = state.fieldGroups.mapValues { (_, fields) ->
                    fields.map { if (it.path == path) it.copy(value = value) else it }
                        .toImmutableList()
                }.toImmutableMap(),
                dirty = isDirty(state),
            )
        }
    }

    private fun updateFlags(transform: BookSourceEditUiState.() -> BookSourceEditUiState) {
        _uiState.update { state -> transform(state).let { it.copy(dirty = isDirty(it)) } }
    }

    private fun isDirty(state: BookSourceEditUiState): Boolean =
        GSON.toJson(currentSource(state)) != baselineJson

    private fun currentSource(state: BookSourceEditUiState = _uiState.value): BookSource {
        return GSON.fromJson(draftJson, BookSource::class.java).apply {
            enabled = state.enabled
            enabledExplore = state.enabledExplore
            enabledCookieJar = state.enabledCookieJar
            eventListener = state.eventListener
            customButton = state.customButton
            bookSourceType = state.bookSourceType
            if (state.autoComplete) completeRules(this)
        }
    }

    private fun completeRules(source: BookSource) {
        source.getSearchRule().apply {
            name = RuleComplete.autoComplete(name, bookList)
            author = RuleComplete.autoComplete(author, bookList)
            kind = RuleComplete.autoComplete(kind, bookList)
            wordCount = RuleComplete.autoComplete(wordCount, bookList)
            lastChapter = RuleComplete.autoComplete(lastChapter, bookList)
            intro = RuleComplete.autoComplete(intro, bookList)
            coverUrl = RuleComplete.autoComplete(coverUrl, bookList, 3)
            bookUrl = RuleComplete.autoComplete(bookUrl, bookList, 2)
        }
        source.getExploreRule().apply {
            name = RuleComplete.autoComplete(name, bookList)
            author = RuleComplete.autoComplete(author, bookList)
            kind = RuleComplete.autoComplete(kind, bookList)
            wordCount = RuleComplete.autoComplete(wordCount, bookList)
            lastChapter = RuleComplete.autoComplete(lastChapter, bookList)
            intro = RuleComplete.autoComplete(intro, bookList)
            coverUrl = RuleComplete.autoComplete(coverUrl, bookList, 3)
            bookUrl = RuleComplete.autoComplete(bookUrl, bookList, 2)
        }
    }

    private fun save(effect: (String) -> BookSourceEditEffect) =
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(saving = true) }
            runCatching {
                val source = currentSource()
            if (source.bookSourceUrl.isBlank() || source.bookSourceName.isBlank()) {
                throw NoStackTraceException(application.getString(R.string.non_null_name_url))
            }
                val old = originalSource ?: BookSource()
                if (!source.equal(old)) {
                    source.lastUpdateTime = System.currentTimeMillis()
                    if (old.exploreUrl != source.exploreUrl) old.clearExploreKindsCache()
                    if (old.jsLib != source.jsLib) SharedJsScope.remove(old.jsLib)
                }
                originalSource?.let {
                    if (it.bookSourceUrl != source.bookSourceUrl) SourceHelp.deleteBookSource(it.bookSourceUrl)
                    else {
                        repository.delete(it); SourceConfig.removeSource(it.bookSourceUrl)
                    }
            }
            repository.insert(source)
            concurrentRecordMap.remove(source.bookSourceUrl)
                originalSource = source
                baselineJson = GSON.toJson(source)
                source.bookSourceUrl
            }.onSuccess { url ->
                _uiState.update { it.copy(saving = false, dirty = false) }
                when (val next = effect(url)) {
                    is BookSourceEditEffect.OpenVariable -> showVariable(next.sourceUrl)
                    else -> _effects.emit(next)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(saving = false) }
                _effects.emit(BookSourceEditEffect.ShowMessage(error.localizedMessage ?: "Error"))
        }
    }

    private suspend fun showVariable(sourceUrl: String) {
        val source = repository.getBookSource(sourceUrl) ?: return
        _uiState.update {
            it.copy(
                activeSheet = BookSourceEditSheet.Variable(
                    VariableEditorUiState(
                        title = application.getString(R.string.set_source_variable),
                        key = source.getKey(),
                        value = source.getVariable().orEmpty(),
                        comment = source.getDisplayVariableComment(
                            "源变量可在js中通过source.getVariable()获取"
                        ),
                    )
                )
            )
        }
    }

    private fun updateVariable(value: String) {
        _uiState.update { state ->
            val sheet = state.activeSheet as? BookSourceEditSheet.Variable ?: return@update state
            state.copy(activeSheet = sheet.copy(editor = sheet.editor.copy(value = value)))
        }
    }

    private fun saveVariable() = viewModelScope.launch(Dispatchers.IO) {
        val editor = (_uiState.value.activeSheet as? BookSourceEditSheet.Variable)?.editor
            ?: return@launch
        repository.getBookSource(editor.key)?.setVariable(editor.value)
        _uiState.update { it.copy(activeSheet = null) }
    }

    private fun importText(text: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { parseSource(text) }.onSuccess { source ->
            withContext(Dispatchers.Main) { applySource(source) }
            _uiState.update { it.copy(dirty = true) }
        }.onFailure {
            _effects.emit(
                BookSourceEditEffect.ShowMessage(
                    it.localizedMessage ?: "Error"
                )
            )
        }
    }

    private suspend fun parseSource(text: String): BookSource = when {
        text.isAbsUrl() -> parseSource(okHttpClient.newCallStrResponse { url(text) }.body.orEmpty())
        text.isJsonArray() -> if (text.contains("ruleSearchUrl") || text.contains("ruleFindUrl")) {
            val items: List<Map<String, Any>> = jsonPath.parse(text).read("$")
            ImportOldData.fromOldBookSource(jsonPath.parse(items[0]))
        } else GSON.fromJsonArray<BookSource>(text).getOrThrow().first()

        text.isJsonObject() -> if (text.contains("ruleSearchUrl") || text.contains("ruleFindUrl")) {
            ImportOldData.fromOldBookSource(jsonPath.parse(text))
        } else GSON.fromJsonObject<BookSource>(text).getOrThrow()

        else -> throw NoStackTraceException("格式不对")
    }

    private fun clearCookie() = viewModelScope.launch(Dispatchers.IO) {
        CookieStore.removeCookie(currentSource().bookSourceUrl)
        _effects.emit(BookSourceEditEffect.ShowMessage(application.getString(R.string.success)))
    }

    private fun fieldsFor(tab: BookSourceEditTab) = FIELD_SPECS.getValue(tab).map { spec ->
        BookSourceEditFieldUi(spec.path, spec.labelRes, spec.label, draftJson.stringAt(spec.path))
    }.toImmutableList()

    private fun fieldGroups() =
        BookSourceEditTab.entries.associateWith(::fieldsFor).toImmutableMap()

    private data class FieldSpec(
        val path: String,
        val labelRes: Int? = null,
        val label: String? = null
    )

    companion object {
        private fun f(path: String, label: Int) = FieldSpec(path, labelRes = label)
        private val FIELD_SPECS = mapOf(
            BookSourceEditTab.Base to listOf(
                f("bookSourceUrl", R.string.source_url),
                f("bookSourceName", R.string.source_name),
                f("bookSourceGroup", R.string.source_group),
                f("bookSourceComment", R.string.comment),
                f("loginUrl", R.string.login_url),
                f("loginUi", R.string.login_ui),
                f("loginCheckJs", R.string.login_check_js),
                f("coverDecodeJs", R.string.cover_decode_js),
                f("bookUrlPattern", R.string.book_url_pattern),
                f("header", R.string.source_http_header),
                f("variableComment", R.string.variable_comment),
                f("concurrentRate", R.string.concurrent_rate),
                FieldSpec("jsLib", label = "jsLib"),
            ),
            BookSourceEditTab.Search to listOf(
                f("searchUrl", R.string.r_search_url),
                f("ruleSearch.checkKeyWord", R.string.check_key_word),
                f("ruleSearch.bookList", R.string.r_book_list),
                f("ruleSearch.name", R.string.r_book_name),
                f("ruleSearch.author", R.string.r_author),
                f("ruleSearch.kind", R.string.rule_book_kind),
                f("ruleSearch.wordCount", R.string.rule_word_count),
                f("ruleSearch.lastChapter", R.string.rule_last_chapter),
                f("ruleSearch.intro", R.string.rule_book_intro),
                f("ruleSearch.coverUrl", R.string.rule_cover_url),
                f("ruleSearch.bookUrl", R.string.r_book_url),
            ),
            BookSourceEditTab.Explore to listOf(
                f("exploreUrl", R.string.r_find_url),
                f("ruleExplore.bookList", R.string.r_book_list),
                f("ruleExplore.name", R.string.r_book_name),
                f("ruleExplore.author", R.string.r_author),
                f("ruleExplore.kind", R.string.rule_book_kind),
                f("ruleExplore.wordCount", R.string.rule_word_count),
                f("ruleExplore.lastChapter", R.string.rule_last_chapter),
                f("ruleExplore.intro", R.string.rule_book_intro),
                f("ruleExplore.coverUrl", R.string.rule_cover_url),
                f("ruleExplore.bookUrl", R.string.r_book_url),
                f("homepageModules", R.string.homepage_modules),
            ),
            BookSourceEditTab.Info to listOf(
                f("ruleBookInfo.init", R.string.rule_book_info_init),
                f("ruleBookInfo.name", R.string.r_book_name),
                f("ruleBookInfo.author", R.string.r_author),
                f("ruleBookInfo.kind", R.string.rule_book_kind),
                f("ruleBookInfo.wordCount", R.string.rule_word_count),
                f("ruleBookInfo.lastChapter", R.string.rule_last_chapter),
                f("ruleBookInfo.intro", R.string.rule_book_intro),
                f("ruleBookInfo.coverUrl", R.string.rule_cover_url),
                f("ruleBookInfo.tocUrl", R.string.rule_toc_url),
                f("ruleBookInfo.canReName", R.string.rule_can_re_name),
                f("ruleBookInfo.downloadUrls", R.string.download_url_rule),
                f("ruleBookInfo.relatedBooks", R.string.related_books),
            ),
            BookSourceEditTab.Toc to listOf(
                f("ruleToc.preUpdateJs", R.string.pre_update_js),
                f("ruleToc.chapterList", R.string.rule_chapter_list),
                f("ruleToc.chapterName", R.string.rule_chapter_name),
                f("ruleToc.chapterUrl", R.string.rule_chapter_url),
                f("ruleToc.formatJs", R.string.format_js_rule),
                f("ruleToc.isVolume", R.string.rule_is_volume),
                f("ruleToc.updateTime", R.string.rule_update_time),
                f("ruleToc.isVip", R.string.rule_is_vip),
                f("ruleToc.isPay", R.string.rule_is_pay),
                f("ruleToc.nextTocUrl", R.string.rule_next_toc_url),
            ),
            BookSourceEditTab.Content to listOf(
                f("ruleContent.content", R.string.rule_book_content),
                f("ruleContent.subContent", R.string.rule_sub_content),
                f("ruleContent.title", R.string.rule_chapter_name),
                f("ruleContent.nextContentUrl", R.string.rule_next_content),
                f("ruleContent.webJs", R.string.rule_web_js),
                f("ruleContent.sourceRegex", R.string.rule_source_regex),
                f("ruleContent.replaceRegex", R.string.rule_replace_regex),
                f("ruleContent.imageStyle", R.string.rule_image_style),
                f("ruleContent.imageDecode", R.string.rule_image_decode),
                f("ruleContent.payAction", R.string.rule_pay_action),
                f("ruleContent.callBackJs", R.string.rule_call_back),
            ),
        )
    }
}

private fun JsonObject.stringAt(path: String): String {
    val parts = path.split('.')
    var current: JsonObject = this
    parts.dropLast(1).forEach {
        val child = current.get(it)
        if (child == null || !child.isJsonObject) return ""
        current = child.asJsonObject
    }
    return current.get(parts.last())?.takeUnless { it.isJsonNull }?.asString.orEmpty()
}

private fun JsonObject.setPath(path: String, value: String) {
    val parts = path.split('.')
    var current = this
    parts.dropLast(1).forEach { part ->
        val child = current.get(part)
        current = if (child != null && child.isJsonObject) child.asJsonObject
        else JsonObject().also { current.add(part, it) }
    }
    if (value.isBlank()) current.remove(parts.last()) else current.addProperty(parts.last(), value)
}
