package io.legado.app.ui.book.changesource

import android.app.Application
import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.flow.StateFlow

class ChangeBookSourceComposeViewModel(application: Application) :
    ChangeBookSourceViewModel(application) {

    val searchGroup: String
        get() = ChangeSourceConfig.searchGroup

    val checkAuthor: Boolean
        get() = ChangeSourceConfig.checkAuthor

    val loadInfo: Boolean
        get() = ChangeSourceConfig.loadInfo

    val loadToc: Boolean
        get() = ChangeSourceConfig.loadToc

    val loadWordCount: Boolean
        get() = ChangeSourceConfig.loadWordCount

    fun onSearchGroupSelected(group: String) {
        if (ChangeSourceConfig.searchGroup == group) return
        ChangeSourceConfig.searchGroup = group
        if (refresh()) startSearch()
    }

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
}
