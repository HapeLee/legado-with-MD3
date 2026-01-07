package io.legado.app.ui.book.bookmark

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

@Immutable
data class BookmarkItemUi(
    val id: Long,
    val content: String,
    val chapterName: String,
    val bookText: String,
    val bookName: String,
    val bookAuthor: String,
    val rawBookmark: Bookmark
)

@Immutable
data class BookmarkUiState(
    val isLoading: Boolean = false,
    val bookmarks: Map<BookmarkGroupHeader, List<BookmarkItemUi>> = emptyMap(),
    val error: Throwable? = null,
    val searchQuery: String = "",
    val collapsedGroups: Set<String> = emptySet()
)

sealed interface BookmarkListItem {
    val key: String

    data class Header(
        val header: BookmarkGroupHeader,
        val collapsed: Boolean
    ) : BookmarkListItem {
        override val key = "header:${header.bookName}|${header.bookAuthor}"
    }

    data class Item(
        val data: BookmarkItemUi
    ) : BookmarkListItem {
        override val key = "item:${data.id}"
    }
}


class AllBookmarkViewModel(
    application: Application,
    private val bookmarkDao: BookmarkDao
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BookmarkUiState> = combine(
        _searchQuery,
        _collapsedGroups,
        bookmarkDao.flowAll()
    ) { query, collapsed, allBookmarks ->

        val filteredList = if (query.isBlank()) {
            allBookmarks
        } else {
            allBookmarks.filter {
                it.bookName.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true) ||
                        it.bookAuthor.contains(query, ignoreCase = true)
            }
        }

        val grouped = filteredList.asSequence()
            .map { bookmark ->
                BookmarkItemUi(
                    id = bookmark.time,
                    content = bookmark.content,
                    chapterName = bookmark.chapterName,
                    bookText = bookmark.bookText,
                    bookName = bookmark.bookName,
                    bookAuthor = bookmark.bookAuthor,
                    rawBookmark = bookmark
                )
            }
            .groupBy { item ->
                BookmarkGroupHeader(item.bookName, item.bookAuthor)
            }

        BookmarkUiState(
            isLoading = false,
            bookmarks = grouped,
            searchQuery = query,
            collapsedGroups = collapsed
        )
    }.catch { e ->
        emit(BookmarkUiState(isLoading = false, error = e))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookmarkUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleGroupCollapse(groupKey: BookmarkGroupHeader) {
        val stringKey = groupKey.toString()
        _collapsedGroups.update { current ->
            if (current.contains(stringKey)) current - stringKey else current + stringKey
        }
    }

    fun toggleAllCollapse(currentKeys: Set<BookmarkGroupHeader>) {
        val stringKeys = currentKeys.map { it.toString() }.toSet()
        _collapsedGroups.update { current ->
            if (current.containsAll(stringKeys) && stringKeys.isNotEmpty()) {
                emptySet()
            } else {
                stringKeys
            }
        }
    }

    fun updateBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkDao.insert(bookmark)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkDao.delete(bookmark)
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
                    val allData = bookmarkDao.all
                    if (isMarkdown) {
                        writeMarkdown(outputStream, allData)
                    } else {
                        GSON.writeToOutputStream(outputStream, allData)
                    }
                }

                withContext(Dispatchers.Main) {
                    context.toastOnUi("导出成功: $fileName")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    getApplication<Application>().toastOnUi("导出失败: ${e.message}")
                }
            }
        }
    }

    private fun writeMarkdown(outputStream: java.io.OutputStream, bookmarks: List<Bookmark>) {
        val sb = StringBuilder()
        var lastHeader = ""

        bookmarks.forEach {
            val currentHeader = "${it.bookName}|${it.bookAuthor}"
            if (currentHeader != lastHeader) {
                lastHeader = currentHeader
                sb.append("\n## ${it.bookName} - ${it.bookAuthor}\n\n")
            }
            sb.append("#### ${it.chapterName}\n")
            sb.append("> **原文：** ${it.bookText}\n\n")
            sb.append("${it.content}\n\n")
            sb.append("---\n")
        }
        outputStream.write(sb.toString().toByteArray())
    }
}