package io.legado.app.ui.book.bookmark

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookmarkRepository
import io.legado.app.domain.gateway.BookMarkingGateway
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.writeToOutputStream
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Stable
data class BookmarkBookKey(val name: String, val author: String)

@Stable
data class BookmarkBookUi(
    val key: BookmarkBookKey,
    val coverPath: String? = null,
    val sourceOrigin: String? = null,
    val bookUrl: String? = null,
    val bookmarkCount: Int,
    val markingCount: Int,
    val matchedMarkingId: String? = null,
)

sealed interface BookmarkDetailItemUi {
    val stableId: String
    val chapterIndex: Int
    val chapterPosition: Int
    val chapterName: String

    @Stable
    data class SavedBookmark(val bookmark: Bookmark) : BookmarkDetailItemUi {
        override val stableId = "bookmark:${bookmark.time}"
        override val chapterIndex = bookmark.chapterIndex
        override val chapterPosition = bookmark.chapterPos
        override val chapterName = bookmark.chapterName
    }

    @Stable
    data class Marking(
        val marking: BookMarking,
        val selectedText: String,
        override val chapterPosition: Int,
    ) : BookmarkDetailItemUi {
        override val stableId = "marking:${marking.id}"
        override val chapterIndex = marking.chapterIndex ?: 0
        override val chapterName = marking.chapterName
    }
}

@Stable
data class BookmarkUiState(
    val isLoading: Boolean = true,
    val books: ImmutableList<BookmarkBookUi> = persistentListOf(),
    val selectedBook: BookmarkBookUi? = null,
    val detailItems: ImmutableList<BookmarkDetailItemUi> = persistentListOf(),
    val searchQuery: String = "",
    val targetMarkingId: String? = null,
    val error: Throwable? = null,
)

sealed interface AllBookmarkIntent {
    data class SetSearchQuery(val query: String) : AllBookmarkIntent
    data class OpenBook(val key: BookmarkBookKey, val targetMarkingId: String? = null) :
        AllBookmarkIntent

    data object CloseBook : AllBookmarkIntent
    data object TargetConsumed : AllBookmarkIntent
    data class UpdateBookmark(val bookmark: Bookmark) : AllBookmarkIntent
    data class DeleteBookmark(val bookmark: Bookmark) : AllBookmarkIntent
    data class Export(val treeUri: Uri, val isMarkdown: Boolean, val book: BookmarkBookKey) :
        AllBookmarkIntent
}

sealed interface AllBookmarkEffect {
    data class ShowMessage(val message: String) : AllBookmarkEffect
}

class AllBookmarkViewModel(
    application: Application,
    private val bookmarkRepository: BookmarkRepository,
    private val markingGateway: BookMarkingGateway,
    private val bookRepository: BookRepository,
) : AndroidViewModel(application) {

    private val searchQuery = MutableStateFlow("")
    private val selectedBook = MutableStateFlow<BookmarkBookKey?>(null)
    private val targetMarkingId = MutableStateFlow<String?>(null)
    private val effectsFlow = MutableSharedFlow<AllBookmarkEffect>(extraBufferCapacity = 16)
    val effects = effectsFlow.asSharedFlow()

    private val library =
        combine(bookmarkRepository.flowAll(), markingGateway.flowAll()) { bookmarks, markings ->
            bookmarks to markings
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BookmarkUiState> = combine(
        library, searchQuery, selectedBook, targetMarkingId,
    ) { (bookmarks, markings), query, selectedKey, targetId ->
        RawState(bookmarks, markings, query.trim(), selectedKey, targetId)
    }.mapLatest(::buildUiState)
        .catch { emit(BookmarkUiState(isLoading = false, error = it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarkUiState())

    fun onIntent(intent: AllBookmarkIntent) {
        when (intent) {
            is AllBookmarkIntent.SetSearchQuery -> searchQuery.value = intent.query
            is AllBookmarkIntent.OpenBook -> {
                selectedBook.value = intent.key
                targetMarkingId.value = intent.targetMarkingId
                searchQuery.value = ""
            }

            AllBookmarkIntent.CloseBook -> {
                selectedBook.value = null
                targetMarkingId.value = null
                searchQuery.value = ""
            }

            AllBookmarkIntent.TargetConsumed -> targetMarkingId.value = null
            is AllBookmarkIntent.UpdateBookmark -> viewModelScope.launch(Dispatchers.IO) {
                bookmarkRepository.save(intent.bookmark)
            }

            is AllBookmarkIntent.DeleteBookmark -> viewModelScope.launch(Dispatchers.IO) {
                bookmarkRepository.delete(intent.bookmark)
            }

            is AllBookmarkIntent.Export -> exportBook(
                intent.treeUri,
                intent.isMarkdown,
                intent.book
            )
        }
    }

    private suspend fun buildUiState(raw: RawState): BookmarkUiState {
        val keys = (raw.bookmarks.map { BookmarkBookKey(it.bookName, it.bookAuthor) } +
                raw.markings.map { BookmarkBookKey(it.bookName, it.bookAuthor) }).distinct()
        val books = keys.map { key ->
            val bookMarkings = raw.markings.filter { it.matches(key) }
            val matchedMarking = if (raw.selectedBook == null && raw.query.isNotBlank()) {
                bookMarkings.firstOrNull { it.matchesNoteQuery(raw.query) }
            } else null
            val book = bookRepository.getBook(key.name, key.author)
            BookmarkBookUi(
                key = key,
                coverPath = book?.getDisplayCover(),
                sourceOrigin = book?.origin,
                bookUrl = book?.bookUrl,
                bookmarkCount = raw.bookmarks.count { it.matches(key) },
                markingCount = bookMarkings.size,
                matchedMarkingId = matchedMarking?.id,
            )
        }.filter { raw.selectedBook != null || raw.query.isBlank() || it.matchedMarkingId != null }
            .sortedBy { it.key.name }

        val selected = raw.selectedBook?.let { key ->
            books.firstOrNull { it.key == key } ?: run {
                val book = bookRepository.getBook(key.name, key.author)
                BookmarkBookUi(
                    key = key,
                    coverPath = book?.getDisplayCover(),
                    sourceOrigin = book?.origin,
                    bookUrl = book?.bookUrl,
                    bookmarkCount = raw.bookmarks.count { it.matches(key) },
                    markingCount = raw.markings.count { it.matches(key) },
                )
            }
        }
        val details = selected?.key?.let { key ->
            val allItems = raw.bookmarks.filter { it.matches(key) }
                .map { BookmarkDetailItemUi.SavedBookmark(it) } +
                    raw.markings.filter { it.matches(key) }.map { marking ->
                        val anchor =
                            GSON.fromJsonObject<TextProcessAnchor>(marking.anchorJson).getOrNull()
                        BookmarkDetailItemUi.Marking(
                            marking,
                            anchor?.selectedText.orEmpty(),
                            anchor?.chapterPosition ?: 0
                        )
                    }
            allItems.filter { raw.query.isBlank() || it.matchesDetailQuery(raw.query) }
                .sortedWith(compareBy({ it.chapterIndex }, { it.chapterPosition }))
                .toImmutableList()
        } ?: persistentListOf()
        return BookmarkUiState(
            isLoading = false,
            books = books.toImmutableList(),
            selectedBook = selected,
            detailItems = details,
            searchQuery = raw.query,
            targetMarkingId = raw.targetMarkingId,
        )
    }

    private fun exportBook(treeUri: Uri, markdown: Boolean, key: BookmarkBookKey) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val suffix = if (markdown) "md" else "json"
                val stamp = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault()).format(Date())
                val fileName = "${key.name}-notes-$stamp.$suffix"
                val bookmarks = bookmarkRepository.getByBook(key.name, key.author)
                val markings = markingGateway.getByBook(key.name, key.author, null)
                FileDoc.fromUri(treeUri, true).createFileIfNotExist(fileName)
                    .openOutputStream().getOrThrow().use { output ->
                        if (markdown) writeMarkdown(output, key, bookmarks, markings)
                        else GSON.writeToOutputStream(output, ExportData(bookmarks, markings))
                    }
                fileName
            }.onSuccess { effectsFlow.emit(AllBookmarkEffect.ShowMessage("导出成功: $it")) }
                .onFailure { effectsFlow.emit(AllBookmarkEffect.ShowMessage("导出失败: ${it.message}")) }
        }
    }

    private fun writeMarkdown(
        output: OutputStream,
        key: BookmarkBookKey,
        bookmarks: List<Bookmark>,
        markings: List<BookMarking>
    ) {
        val text = buildString {
            append("# ${key.name}\n\n")
            if (key.author.isNotBlank()) append("作者：${key.author}\n\n")
            append("## 书签（${bookmarks.size}）\n\n")
            bookmarks.forEach {
                append("### ${it.chapterName}\n\n> ${it.bookText}\n\n")
                if (it.content.isNotBlank()) append("${it.content}\n\n")
            }
            append("## 笔记（${markings.size}）\n\n")
            markings.forEach {
                val selectedText = GSON.fromJsonObject<TextProcessAnchor>(it.anchorJson)
                    .getOrNull()?.selectedText.orEmpty()
                append("### ${it.chapterName}\n\n> $selectedText\n\n")
                if (it.note.isNotBlank()) append("${it.note}\n\n")
            }
        }
        output.write(text.toByteArray())
    }

    private data class RawState(
        val bookmarks: List<Bookmark>, val markings: List<BookMarking>, val query: String,
        val selectedBook: BookmarkBookKey?, val targetMarkingId: String?,
    )

    private data class ExportData(val bookmarks: List<Bookmark>, val notes: List<BookMarking>)
}

private fun Bookmark.matches(key: BookmarkBookKey) =
    bookName == key.name && bookAuthor == key.author

private fun BookMarking.matches(key: BookmarkBookKey) =
    bookName == key.name && bookAuthor == key.author

private fun BookMarking.matchesNoteQuery(query: String): Boolean {
    val selectedText =
        GSON.fromJsonObject<TextProcessAnchor>(anchorJson).getOrNull()?.selectedText.orEmpty()
    return note.contains(query, true) || selectedText.contains(query, true) || chapterName.contains(
        query,
        true
    )
}

private fun BookmarkDetailItemUi.matchesDetailQuery(query: String): Boolean = when (this) {
    is BookmarkDetailItemUi.SavedBookmark -> bookmark.chapterName.contains(query, true) ||
            bookmark.bookText.contains(query, true) || bookmark.content.contains(query, true)

    is BookmarkDetailItemUi.Marking -> marking.chapterName.contains(query, true) ||
            selectedText.contains(query, true) || marking.note.contains(query, true)
}
