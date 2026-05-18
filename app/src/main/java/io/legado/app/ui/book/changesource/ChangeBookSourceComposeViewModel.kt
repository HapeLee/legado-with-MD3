package io.legado.app.ui.book.changesource

import android.app.Application
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.SearchRepository
import io.legado.app.ui.book.search.SearchScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ChangeBookSourceComposeViewModel(
    application: Application,
    private val searchRepository: SearchRepository,
) : ChangeBookSourceViewModel(application) {

    val enabledGroups = searchRepository.enabledGroups
    val enabledSources = searchRepository.enabledSources

    val searchScope = SearchScope(ChangeSourceConfig.searchScope)

    data class ScopeUiState(
        val isAll: Boolean,
        val isSource: Boolean,
        val displayNames: List<String>,
        val sourceUrls: List<String>
    )

    private val _scopeUiState = MutableStateFlow(
        ScopeUiState(
            isAll = searchScope.isAll(),
            isSource = searchScope.isSource(),
            displayNames = searchScope.displayNames,
            sourceUrls = searchScope.sourceUrls
        )
    )
    val scopeUiState = _scopeUiState.asStateFlow()

    val checkAuthor: Boolean
        get() = ChangeSourceConfig.checkAuthor

    val loadInfo: Boolean
        get() = ChangeSourceConfig.loadInfo

    val loadToc: Boolean
        get() = ChangeSourceConfig.loadToc

    val loadWordCount: Boolean
        get() = ChangeSourceConfig.loadWordCount

    fun onCheckAuthorChange(enabled: Boolean) {
        if (ChangeSourceConfig.checkAuthor == enabled) return
        ChangeSourceConfig.checkAuthor = enabled
        refresh()
    }

    fun onLoadInfoChange(enabled: Boolean) {
        if (ChangeSourceConfig.loadInfo == enabled) return
        ChangeSourceConfig.loadInfo = enabled
    }

    fun onLoadTocChange(enabled: Boolean) {
        if (ChangeSourceConfig.loadToc == enabled) return
        ChangeSourceConfig.loadToc = enabled
    }

    fun onLoadWordCountChange(enabled: Boolean) {
        if (ChangeSourceConfig.loadWordCount == enabled) return
        ChangeSourceConfig.loadWordCount = enabled
        if (enabled) {
            onLoadWordCountChecked(true)
        } else {
            refresh()
        }
    }

    fun bookScoreFlow(searchBook: SearchBook): StateFlow<Int> {
        return ObservableSourceConfig.bookScoreFlow(searchBook)
    }

    fun onBookScoreClick(searchBook: SearchBook) {
        val currentScore = ObservableSourceConfig.getBookScore(searchBook)
        setBookScore(searchBook, if (currentScore > 0) 0 else 1)
    }

    fun selectAllScope() {
        searchScope.update("")
        saveScope()
    }

    fun toggleScopeGroup(groupName: String) {
        if (searchScope.isSource()) {
            searchScope.update("")
        }
        val selected = searchScope.displayNames.toMutableSet()
        if (selected.contains(groupName)) {
            selected.remove(groupName)
        } else {
            selected.add(groupName)
        }
        searchScope.update(selected.toList())
        saveScope()
    }

    fun toggleScopeSource(source: BookSourcePart) {
        val selectedUrls = if (searchScope.isSource()) {
            searchScope.sourceUrls.toMutableSet()
        } else {
            mutableSetOf()
        }

        if (selectedUrls.contains(source.bookSourceUrl)) {
            selectedUrls.remove(source.bookSourceUrl)
        } else {
            selectedUrls.add(source.bookSourceUrl)
        }

        if (selectedUrls.isEmpty()) {
            searchScope.update("")
        } else {
            val selectedSources = appDb.bookSourceDao.allEnabledPart.filter {
                selectedUrls.contains(it.bookSourceUrl)
            }
            searchScope.updateSources(selectedSources)
        }
        saveScope()
    }

    private fun saveScope() {
        ChangeSourceConfig.searchScope = searchScope.toString()
        _scopeUiState.update {
            ScopeUiState(
                isAll = searchScope.isAll(),
                isSource = searchScope.isSource(),
                displayNames = searchScope.displayNames,
                sourceUrls = searchScope.sourceUrls
            )
        }
        refresh()
    }
}
