package io.legado.app.ui.main.rss

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.RssRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RssViewModel(
    application: Application,
    private val rssRepository: RssRepository
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(RssUiState())
    val uiState = _uiState.asStateFlow()
    private val searchKeyFlow = MutableStateFlow("")
    private val groupFlow = MutableStateFlow("")
    private val _effects = MutableSharedFlow<RssEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        _uiState.update { it.copy(isLoading = true) }
        initGroupData()
        initRssData()
    }

    private fun initGroupData() {
        viewModelScope.launch {
            rssRepository.getEnabledGroups()
                .flowOn(IO)
                .collect { groups ->
                    _uiState.update { state -> state.copy(groups = groups.toImmutableList()) }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initRssData() {
        combine(
            searchKeyFlow,
            groupFlow
        ) { searchKey, group ->
            searchKey to group
        }
            .flatMapLatest { (searchKey, group) ->
                rssRepository.getEnabledSources(searchKey, group)
            }
            .flowOn(IO)
            .onEach { sources ->
                _uiState.update { state -> state.copy(items = sources.toImmutableList(), isLoading = false) }
            }
            .catch {
                _uiState.update { it.copy(isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun search(key: String) {
        searchKeyFlow.value = key
        _uiState.update { it.copy(searchKey = key, isSearch = key.isNotEmpty()) }
    }

    fun onIntent(intent: RssIntent) {
        when (intent) {
            is RssIntent.Search -> search(intent.query)
            is RssIntent.ToggleSearch -> toggleSearchVisible(intent.visible)
            is RssIntent.SetGroup -> setGroup(intent.group)
            is RssIntent.OpenSource -> openSource(intent.source)
            is RssIntent.TopSource -> topSource(intent.source)
            is RssIntent.EditSource -> openSourceEdit(intent.source)
            is RssIntent.DeleteSource -> del(intent.source)
            is RssIntent.DisableSource -> disable(intent.source)
            is RssIntent.Login -> login(intent.source)
            RssIntent.OpenRuleSub -> openRuleSub()
            RssIntent.OpenFavorites -> openFavorites()
            RssIntent.OpenSourceManage -> openSourceManage()
        }
    }

    fun setGroup(group: String) {
        groupFlow.value = group
        searchKeyFlow.value = ""
        _uiState.update { it.copy(group = group, searchKey = "", isSearch = false) }
    }

    fun toggleSearchVisible(visible: Boolean) {
        if (!visible) {
            searchKeyFlow.value = ""
        }
        _uiState.update {
            it.copy(isSearch = visible, searchKey = if (visible) it.searchKey else "")
        }
    }

    fun topSource(vararg sources: RssSource) {
        execute {
            rssRepository.topSources(*sources)
        }
    }

    fun bottomSource(vararg sources: RssSource) {
        execute {
            rssRepository.bottomSources(*sources)
        }
    }

    fun del(vararg rssSource: RssSource) {
        execute {
            rssRepository.deleteSources(rssSource.toList())
        }
    }

    fun disable(rssSource: RssSource) {
        execute {
            rssRepository.disableSource(rssSource)
        }
    }

    fun openSourceEdit(rssSource: RssSource) {
        _effects.tryEmit(RssEffect.OpenSourceEdit(rssSource.sourceUrl))
    }

    fun login(rssSource: RssSource) {
        _effects.tryEmit(RssEffect.Login(rssSource.sourceUrl))
    }

    fun openRuleSub() {
        _effects.tryEmit(RssEffect.OpenRuleSub)
    }

    fun openFavorites() {
        _effects.tryEmit(RssEffect.OpenFavorites)
    }

    fun openSourceManage() {
        _effects.tryEmit(RssEffect.OpenSourceManage)
    }

    fun openSource(rssSource: RssSource) {
        if (!rssSource.singleUrl) {
            if (rssSource.startHtml.isNullOrBlank()) {
                _effects.tryEmit(RssEffect.OpenSort(rssSource.sourceUrl, null, null))
            } else {
                _effects.tryEmit(
                    RssEffect.OpenRead(
                        title = rssSource.sourceName,
                        origin = rssSource.sourceUrl,
                        link = null,
                        openUrl = null,
                        startPage = true
                    )
                )
            }
            return
        }

        execute {
            resolveSingleUrl(rssSource)
        }.timeout(10000)
            .onSuccess { url ->
                if (url.startsWith("http", true)) {
                    _effects.tryEmit(
                        RssEffect.OpenRead(
                            title = rssSource.sourceName,
                            origin = url,
                            link = null,
                            openUrl = null
                        )
                    )
                } else {
                    _effects.tryEmit(RssEffect.OpenExternalUrl(url))
                }
            }.onError {
                _effects.tryEmit(RssEffect.ShowMessage(it.localizedMessage ?: "打开订阅源失败"))
            }
    }

    private suspend fun resolveSingleUrl(rssSource: RssSource): String {
        var sortUrl = rssSource.sortUrl
        if (!sortUrl.isNullOrBlank()) {
            if (sortUrl.startsWith("<js>", false)
                || sortUrl.startsWith("@js:", false)
            ) {
                val jsStr = if (sortUrl.startsWith("@")) {
                    sortUrl.substring(4)
                } else {
                    sortUrl.substring(4, sortUrl.lastIndexOf("<"))
                }
                val result = runScriptWithContext {
                    rssSource.evalJS(jsStr)?.toString()
                }
                if (!result.isNullOrBlank()) {
                    sortUrl = result
                }
            }
            return if (sortUrl.contains("::")) {
                sortUrl.split("::")[1]
            } else {
                sortUrl
            }
        }
        return rssSource.sourceUrl
    }
}

sealed interface RssEffect {
    data class ShowMessage(val message: String) : RssEffect

    data class OpenSort(
        val sourceUrl: String,
        val sortUrl: String?,
        val key: String?
    ) : RssEffect

    data class OpenRead(
        val title: String?,
        val origin: String,
        val link: String?,
        val openUrl: String?,
        val startPage: Boolean = false
    ) : RssEffect

    data class OpenExternalUrl(val url: String) : RssEffect
    data class OpenSourceEdit(val sourceUrl: String) : RssEffect
    data class Login(val sourceUrl: String) : RssEffect
    data object OpenRuleSub : RssEffect
    data object OpenFavorites : RssEffect
    data object OpenSourceManage : RssEffect
}

sealed interface RssIntent {
    data class Search(val query: String) : RssIntent
    data class ToggleSearch(val visible: Boolean) : RssIntent
    data class SetGroup(val group: String) : RssIntent
    data class OpenSource(val source: RssSource) : RssIntent
    data class TopSource(val source: RssSource) : RssIntent
    data class EditSource(val source: RssSource) : RssIntent
    data class DeleteSource(val source: RssSource) : RssIntent
    data class DisableSource(val source: RssSource) : RssIntent
    data class Login(val source: RssSource) : RssIntent
    data object OpenRuleSub : RssIntent
    data object OpenFavorites : RssIntent
    data object OpenSourceManage : RssIntent
}
