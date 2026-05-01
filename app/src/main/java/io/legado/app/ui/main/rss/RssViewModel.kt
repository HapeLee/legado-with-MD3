package io.legado.app.ui.main.rss

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.RssRepository
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
                _uiState.update { state -> state.copy(items = sources.toImmutableList()) }
            }
            .launchIn(viewModelScope)
    }

    fun search(key: String) {
        searchKeyFlow.value = key
        _uiState.update { it.copy(searchKey = key, isSearch = key.isNotEmpty()) }
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

    fun openSource(rssSource: RssSource) {
        if (!rssSource.singleUrl) {
            _effects.tryEmit(RssEffect.OpenSort(rssSource.sourceUrl, null, null))
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
                context.toastOnUi(it.localizedMessage)
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
    data class OpenSort(
        val sourceUrl: String,
        val sortUrl: String?,
        val key: String?
    ) : RssEffect

    data class OpenRead(
        val title: String?,
        val origin: String,
        val link: String?,
        val openUrl: String?
    ) : RssEffect

    data class OpenExternalUrl(val url: String) : RssEffect
}
