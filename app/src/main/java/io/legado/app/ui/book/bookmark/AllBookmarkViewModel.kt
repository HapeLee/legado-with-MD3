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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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

class AllBookmarkViewModel(
    application: Application,
    private val bookmarkDao: BookmarkDao
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())
    val collapsedGroups = _collapsedGroups.asStateFlow()

    private val refreshTrigger = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class) // FlowPreview 用于 debounce
    val bookmarksState: StateFlow<Map<String, List<Bookmark>>> = combine(
        refreshTrigger.onStart { emit(Unit) },
        _searchQuery
    ) { _, query -> query }
        .debounce(300L)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                bookmarkDao.flowAll()
            } else {
                bookmarkDao.flowSearchAll(query)
            }
        }
        .map { list ->
            list.groupBy { "${it.bookName}(${it.bookAuthor})" }
        }
        .catch { e -> e.printStackTrace() }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    private fun refreshBookmarks() {
        viewModelScope.launch {
            refreshTrigger.emit(Unit)
        }
    }

    fun toggleGroupCollapse(groupKey: String) {
        val current = _collapsedGroups.value
        if (current.contains(groupKey)) {
            _collapsedGroups.value = current - groupKey
        } else {
            _collapsedGroups.value = current + groupKey // 折叠
        }
    }

    fun toggleAllCollapse(currentKeys: Set<String>) {
        val currentCollapsed = _collapsedGroups.value
        if (currentCollapsed.containsAll(currentKeys) && currentKeys.isNotEmpty()) {
            _collapsedGroups.value = emptySet()
        } else {
            _collapsedGroups.value = currentKeys
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
            refreshBookmarks()
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarkDao.delete(bookmark)
            }
            refreshBookmarks()
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