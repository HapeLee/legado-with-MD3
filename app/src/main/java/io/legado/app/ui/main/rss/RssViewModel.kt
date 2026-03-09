package io.legado.app.ui.main.rss

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RssViewModel(application: Application) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(RssUiState())
    val uiState = _uiState.asStateFlow()

    init {
        initGroupData()
        initRssData()
    }

    private fun initGroupData() {
        viewModelScope.launch {
            appDb.rssSourceDao.flowEnabledGroups()
                .flowOn(IO)
                .collect { groups ->
                    _uiState.update { state -> state.copy(groups = groups) }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initRssData() {
        combine(
            _uiState.map { it.searchKey }.distinctUntilChanged(),
            _uiState.map { it.group }.distinctUntilChanged()
        ) { searchKey, group ->
            searchKey to group
        }
            .flatMapLatest { (searchKey, group) ->
                when {
                    searchKey.isNotEmpty() -> appDb.rssSourceDao.flowEnabled(searchKey)
                    group.isNotEmpty() -> appDb.rssSourceDao.flowEnabledByGroup(group)
                    else -> appDb.rssSourceDao.flowEnabled()
                }
            }
            .flowOn(IO)
            .onEach { sources ->
                _uiState.update { state -> state.copy(items = sources) }
            }
            .launchIn(viewModelScope)
    }

    fun search(key: String) {
        _uiState.update { it.copy(searchKey = key, isSearch = key.isNotEmpty()) }
    }

    fun setGroup(group: String) {
        _uiState.update { it.copy(group = group, searchKey = "", isSearch = false) }
    }

    fun toggleSearchVisible(visible: Boolean) {
        _uiState.update {
            it.copy(isSearch = visible, searchKey = if (visible) it.searchKey else "")
        }
    }

    fun topSource(vararg sources: RssSource) {
        execute {
            sources.sortBy { it.customOrder }
            val minOrder = appDb.rssSourceDao.minOrder - 1
            val array = Array(sources.size) {
                sources[it].copy(customOrder = minOrder - it)
            }
            appDb.rssSourceDao.update(*array)
        }
    }

    fun bottomSource(vararg sources: RssSource) {
        execute {
            sources.sortBy { it.customOrder }
            val maxOrder = appDb.rssSourceDao.maxOrder + 1
            val array = Array(sources.size) {
                sources[it].copy(customOrder = maxOrder + it)
            }
            appDb.rssSourceDao.update(*array)
        }
    }

    fun del(vararg rssSource: RssSource) {
        execute {
            SourceHelp.deleteRssSources(rssSource.toList())
        }
    }

    fun disable(rssSource: RssSource) {
        execute {
            rssSource.enabled = false
            appDb.rssSourceDao.update(rssSource)
        }
    }

    fun getSingleUrl(rssSource: RssSource, onSuccess: (url: String) -> Unit) {
        execute {
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
                if (sortUrl.contains("::")) {
                    return@execute sortUrl.split("::")[1]
                } else {
                    return@execute sortUrl
                }
            }
            rssSource.sourceUrl
        }.timeout(10000)
            .onSuccess {
                onSuccess.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }
    }
}
