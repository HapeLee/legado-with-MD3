package io.legado.app.ui.book.info

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

const val READER_RESULT_DELETED = 100

data class BookInfoUiState(
    val book: BookInfoBookUi? = null,
    val hasChapters: Boolean = false,
    val webFiles: List<BookInfoWebFile> = emptyList(),
    val kindLabels: List<String> = emptyList(),
    val groupNames: String? = null,
    val hasCustomGroup: Boolean = false,
    val readRecordTotalTime: Long = 0L,
    val readRecordTimelineDays: List<ReadRecordTimelineDay> = emptyList(),
    val inBookshelf: Boolean = false,
    val bookSource: BookInfoSourceUi? = null,
    val relatedBooks: ImmutableList<RelatedBooksUi> = persistentListOf(),
    val isTocLoading: Boolean = false,
    val isBusy: Boolean = false,
    val deleteAlertEnabled: Boolean = true,
    val deleteOriginal: Boolean = false,
    val showAppLogSheet: Boolean = false,
    val sheet: BookInfoSheet = BookInfoSheet.None,
    val dialog: BookInfoDialog? = null,
)

@Stable
data class BookInfoBookUi(
    val bookUrl: String,
    val name: String,
    val author: String,
    val realAuthor: String,
    val origin: String,
    val originName: String,
    val coverPath: String?,
    val group: Long,
    val isLocal: Boolean,
    val type: Int,
    val canUpdate: Boolean,
    val splitLongChapter: Boolean,
    val durChapterTitle: String?,
    val latestChapterTitle: String?,
    val totalChapterNum: Int,
    val durChapterIndex: Int,
    val remark: String?,
    val displayIntro: String?,
)

@Stable
data class BookInfoSourceUi(
    val sourceUrl: String,
    val hasLogin: Boolean,
)

sealed interface BookInfoSheet {
    data object None : BookInfoSheet
    data object CoverPicker : BookInfoSheet
    data object GroupPicker : BookInfoSheet
    data class SourcePicker(val oldBook: Book) : BookInfoSheet
    data object ReadRecord : BookInfoSheet
    data class WebFiles(val openAfterImport: Boolean) : BookInfoSheet
    data class ArchiveEntries(
        val archiveUri: Uri,
        val entries: List<String>,
        val openAfterImport: Boolean,
    ) : BookInfoSheet
}

sealed interface BookInfoDialog {
    data class DeleteBook(val isLocal: Boolean) : BookInfoDialog
    data class EditRemark(val remark: String?) : BookInfoDialog
    data class PhotoPreview(val path: String) : BookInfoDialog
    data class UnsupportedWebFile(
        val webFile: BookInfoWebFile,
        val openAfterImport: Boolean,
    ) : BookInfoDialog
}

data class BookInfoWebFile(
    val url: String,
    val name: String,
) {
    override fun toString(): String = name
}

data class RelatedBooksUi(
    val key: String,
    val title: String,
    val url: String,
    val resolvedUrl: String,
    val books: ImmutableList<SearchBook>,
)

sealed interface BookInfoIntent {
    data object DismissSheet : BookInfoIntent
    data object DismissDialog : BookInfoIntent
    data object DismissAppLogSheet : BookInfoIntent
    data class MenuAction(val action: BookInfoMenuAction) : BookInfoIntent
    data class AuthorClick(val longClick: Boolean) : BookInfoIntent
    data class BookNameClick(val longClick: Boolean) : BookInfoIntent
    data object OriginClick : BookInfoIntent
    data object ReadClick : BookInfoIntent
    data object ShelfClick : BookInfoIntent
    data object TocClick : BookInfoIntent
    data object CoverClick : BookInfoIntent
    data object CoverLongClick : BookInfoIntent
    data object GroupClick : BookInfoIntent
    data object ChangeSourceClick : BookInfoIntent
    data object ReadRecordClick : BookInfoIntent
    data object RemarkClick : BookInfoIntent
    data class SaveCover(val path: String) : BookInfoIntent
    data class ConfirmDelete(val deleteOriginal: Boolean) : BookInfoIntent
    data class UpdateRemark(val remark: String) : BookInfoIntent
    data class SelectGroup(val groupId: Long) : BookInfoIntent
    data class SelectCover(val coverUrl: String) : BookInfoIntent
    data class ReplaceWithSource(
        val source: BookSource,
        val book: Book,
        val toc: List<BookChapter>,
        val options: ChangeSourceMigrationOptions,
    ) : BookInfoIntent
    data class AddSourceAsNewBook(
        val book: Book,
        val toc: List<BookChapter>,
    ) : BookInfoIntent
    data class SelectWebFile(
        val webFile: BookInfoWebFile,
        val openAfterImport: Boolean,
    ) : BookInfoIntent
    data class OpenUnsupportedWebFile(
        val webFile: BookInfoWebFile,
    ) : BookInfoIntent
    data class SelectArchiveEntry(
        val archiveUri: Uri,
        val entryName: String,
        val openAfterImport: Boolean,
    ) : BookInfoIntent

    data class RelatedBookClick(val book: SearchBook) : BookInfoIntent
    data class RelatedBooksMore(val title: String, val url: String) : BookInfoIntent
}

sealed interface BookInfoEffect {
    data class Finish(
        val resultCode: Int? = null,
        val afterTransition: Boolean = false,
    ) : BookInfoEffect

    data class OpenBookInfoEdit(val bookUrl: String) : BookInfoEffect
    data class OpenToc(val bookUrl: String) : BookInfoEffect
    data class OpenReader(
        val book: Book,
        val inBookshelf: Boolean,
        val chapterChanged: Boolean,
    ) : BookInfoEffect
    data class OpenBookSourceEdit(val sourceUrl: String) : BookInfoEffect
    data class OpenSourceLogin(val sourceUrl: String) : BookInfoEffect
    data object OpenSelectBooksDir : BookInfoEffect
    data class OpenFile(val uri: Uri, val mimeType: String) : BookInfoEffect
    data class RunSourceCallback(
        val event: String,
        val source: BookSource?,
        val book: Book,
        val action: BookInfoCallbackAction,
    ) : BookInfoEffect
    data class ShowVariableDialog(
        val title: String,
        val key: String,
        val variable: String?,
        val comment: String,
    ) : BookInfoEffect

    data class NavigateToBookInfo(
        val name: String?,
        val author: String?,
        val bookUrl: String,
        val origin: String?,
        val coverPath: String?,
    ) : BookInfoEffect
    data class NavigateToExploreShow(
        val title: String?,
        val sourceUrl: String,
        val exploreUrl: String?,
    ) : BookInfoEffect
}

sealed interface BookInfoCallbackAction {
    data class Search(val keyword: String) : BookInfoCallbackAction
    data class ShareText(val chooserTitle: String, val text: String) : BookInfoCallbackAction
    data class CopyText(val text: String) : BookInfoCallbackAction
    data object ClearCache : BookInfoCallbackAction
}

enum class BookInfoMenuAction {
    Edit,
    Share,
    Upload,
    SyncRemote,
    Refresh,
    ReadRecord,
    Login,
    Top,
    SetSourceVariable,
    SetBookVariable,
    CopyBookUrl,
    CopyTocUrl,
    ToggleCanUpdate,
    ToggleSplitLongChapter,
    ToggleDeleteAlert,
    ClearCache,
    ShowLog,
}
