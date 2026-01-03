package io.legado.app.ui.book.bookmark

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.entities.Bookmark
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BookmarkGroupHeader(
    val bookName: String,
    val bookAuthor: String
) {
    override fun toString(): String = "$bookName|$bookAuthor"
}

sealed class BookmarkUiState {
    object Loading : BookmarkUiState()
    data class Success(val bookmarks: Map<BookmarkGroupHeader, List<Bookmark>>) : BookmarkUiState()
    data class Error(val throwable: Throwable) : BookmarkUiState()
}

class AllBookmarkViewModel(
    application: Application,
    private val bookmarkDao: BookmarkDao
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())
    val collapsedGroups = _collapsedGroups.asStateFlow()


    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val bookmarksState: StateFlow<BookmarkUiState> = _searchQuery
        .debounce(300L)
        .flatMapLatest { query ->
            val flow = if (query.isBlank()) {
                bookmarkDao.flowAll()
            } else {
                bookmarkDao.flowSearchAll(query)
            }
            flow.map<List<Bookmark>, BookmarkUiState> { list ->
                BookmarkUiState.Success(list.groupBy { BookmarkGroupHeader(it.bookName, it.bookAuthor) })
            }
                .onStart { emit(BookmarkUiState.Loading) }
                .catch { e ->
                    e.printStackTrace()
                    emit(BookmarkUiState.Error(e))
                }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BookmarkUiState.Loading
        )

    fun toggleGroupCollapse(groupKey: BookmarkGroupHeader) {
        val stringKey = groupKey.toString()
        val current = _collapsedGroups.value
        if (current.contains(stringKey)) {
            _collapsedGroups.value = current - stringKey
        } else {
            _collapsedGroups.value = current + stringKey
        }
    }

    fun toggleAllCollapse(currentKeys: Set<BookmarkGroupHeader>) {
        val stringKeys = currentKeys.map { it.toString() }.toSet()
        val currentCollapsed = _collapsedGroups.value
        if (currentCollapsed.containsAll(stringKeys) && currentKeys.isNotEmpty()) {
            _collapsedGroups.value = emptySet()
        } else {
            _collapsedGroups.value = stringKeys
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun updateBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarkDao.insert(bookmark)
            }
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarkDao.delete(bookmark)
            }
        }
    }

    fun exportBookmark(treeUri: Uri, isMarkdown: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val dateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
                val suffix = if (isMarkdown) "md" else "json"
                val fileName = "bookmark-${dateFormat.format(Date())}.$suffix"
                val dirDoc = FileDoc.fromUri(treeUri, true)
                val fileDoc = dirDoc.createFileIfNotExist(fileName)

                fileDoc.openOutputStream().getOrThrow().use { outputStream ->
                    if (isMarkdown) {
                        writeMarkdown(outputStream, bookmarkDao.all)
                    } else {
                        GSON.writeToOutputStream(outputStream, bookmarkDao.all)
                    }
                }

                withContext(Dispatchers.Main) {
                    context.toastOnUi("导出成功")
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun writeMarkdown(outputStream: java.io.OutputStream, bookmarks: List<Bookmark>) {
        var name = ""
        var author = ""
        bookmarks.forEach {
            if (it.bookName != name && it.bookAuthor != author) {
                name = it.bookName
                author = it.bookAuthor
                outputStream.write("## ${it.bookName} ${it.bookAuthor}\n\n".toByteArray())
            }
            outputStream.write("#### ${it.chapterName}\n\n".toByteArray())
            outputStream.write("###### 原文\n ${it.bookText}\n\n".toByteArray())
            outputStream.write("###### 摘要\n ${it.content}\n\n".toByteArray())
        }
    }
}