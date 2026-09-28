package io.legado.app.ui.book.info

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.domain.model.BookshelfConflict
import io.legado.app.domain.model.ConflictBookSummary
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.ui.widget.components.variable.VariableEditorUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

const val READER_RESULT_DELETED = 100

@Stable
data class HighlightedTag(
    val matchedLabels: List<String>,
    val title: String?,
)

data class BookInfoUiState(
    val book: BookInfoBookUi? = null,
    val hasChapters: Boolean = false,
    val tocLoadFailed: Boolean = false,
    val webFiles: List<BookInfoWebFile> = emptyList(),
    val highlightedTags: List<HighlightedTag> = emptyList(),
    val kindLabels: List<String> = emptyList(),
    val groupNames: String? = null,
    val hasCustomGroup: Boolean = false,
    val readRecordTotalTime: Long = 0L,
    val readRecordTimelineDays: List<ReadRecordTimelineDay> = emptyList(),
    val inBookshelf: Boolean = false,
    val bookSource: BookSource? = null,
    val bookSourceUi: BookInfoSourceUi? = null,
    val relatedBooks: ImmutableList<RelatedBooksUi> = persistentListOf(),
    val characters: ImmutableList<BookInfoCharacterUi> = persistentListOf(),
    val knowledgeEntries: ImmutableList<BookInfoKnowledgeUi> = persistentListOf(),
    val recentEvents: ImmutableList<BookInfoEventUi> = persistentListOf(),
    val isTocLoading: Boolean = false,
    val isBusy: Boolean = false,
    val deleteAlertEnabled: Boolean = true,
    val deleteOriginal: Boolean = false,
    val showAppLogSheet: Boolean = false,
    val sheet: BookInfoSheet = BookInfoSheet.None,
    val dialog: BookInfoDialog? = null,
    val bookInfoFollowCoverColor: Boolean = true,
    val bookInfoNetworkCoverBackground: String = "on",
    val bookInfoDefaultCoverBackground: String = "on",
    val loadCoverOnlyOnWifi: Boolean = false,
    val defaultCover: String = "",
    val defaultCoverDark: String = "",
    val showMangaUi: Boolean = true,
    /** 加入书架时发现的疑似重复；非空时由冲突 Sheet 决定共存还是迁移。 */
    val shelfConflict: BookshelfConflict? = null,
    val isResolvingShelfConflict: Boolean = false,

    /**
     * 书架里同一部作品的**其他**副本（不含本书自身）。
     *
     * 两种用途：
     * - 本书**未入架**且非空：入架会弹冲突 Sheet，书架按钮提前标成冲突态；
     * - 本书**已入架**且非空：由「书架操作」Sheet 列出，供用户切到另一个副本。
     *
     * 判定口径与 [shelfConflict] 完全一致（同一个用例），因此「按钮是冲突态」⟺「点击真的会
     * 弹冲突 Sheet」；具体是哪几本仍由点击后的 Sheet 给出。
     */
    val shelfDuplicates: ImmutableList<ConflictBookSummary> = persistentListOf(),
    /** 本书是否私密（单本标记 ∪ 所属私密分组）；菜单里"标记/取消私密"读它 */
    val bookPrivate: Boolean = false,
    /** 进入本页时这本书是否需要验证（页面内刚标记私密不会立刻脱敏） */
    val privateLockedByEntry: Boolean = false,
    /** 本书为私密书籍且尚未解锁：详情页只显示锁定态 */
    val privateLocked: Boolean = false,
    val privateAccess: PrivateAccessState = PrivateAccessState(),
    /** 应用内密码解锁弹窗（生物不可用或用户选择密码时） */
    val showPrivatePasswordDialog: Boolean = false,
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
    val durChapterPos: Int,
    val remark: String?,
    val intro: String?,
)

@Stable
data class BookInfoSourceUi(
    val sourceUrl: String,
    val hasLogin: Boolean,
    val hasCustomButton: Boolean,
)

@Stable
data class BookInfoCharacterUi(
    val id: String,
    val name: String,
    val avatarUri: String?,
    val role: String,
    val tags: String,
    val summary: String,
)

@Stable
data class BookInfoKnowledgeUi(
    val id: String,
    val type: String,
    val title: String,
    val summary: String,
)

@Stable
data class BookInfoEventUi(
    val id: String,
    val chapterTitle: String,
    val eventTimeText: String,
    val content: String,
    val characterName: String,
)

sealed interface BookInfoSheet {
    data object None : BookInfoSheet
    data object CoverPicker : BookInfoSheet
    data object GroupPicker : BookInfoSheet
    /** 已入架书籍的操作面板：其他副本 / 分组 / 删除。 */
    data object ShelfActions : BookInfoSheet
    data class SourcePicker(val oldBook: Book) : BookInfoSheet
    data object ReadRecord : BookInfoSheet
    data class WebFiles(val openAfterImport: Boolean) : BookInfoSheet
    data class ArchiveEntries(
        val archiveUri: Uri,
        val entries: List<String>,
        val openAfterImport: Boolean,
    ) : BookInfoSheet
    data class Variable(val editor: VariableEditorUiState) : BookInfoSheet
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
    data class UpdateVariable(val value: String) : BookInfoIntent
    data object SaveVariable : BookInfoIntent
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

    data object DismissShelfConflict : BookInfoIntent
    data class OpenShelfConflictBook(val summary: ConflictBookSummary) : BookInfoIntent

    /** 「书架操作」Sheet：切到同名同作者的其他副本。 */
    data class OpenShelfDuplicate(val summary: ConflictBookSummary) : BookInfoIntent

    /** 「书架操作」Sheet：改分组（进入原有的分组选择 Sheet）。 */
    data object ShelfActionsGroup : BookInfoIntent

    /** 「书架操作」Sheet：删除本书（仍走原有的确认框 / 删除前提醒设置）。 */
    data object ShelfActionsDelete : BookInfoIntent
    data class CoexistWithShelfConflict(
        val existingBookUrl: String,
        val options: ChangeSourceMigrationOptions,
    ) : BookInfoIntent

    data class MigrateShelfConflict(
        val existingBookUrl: String,
        val options: ChangeSourceMigrationOptions,
    ) : BookInfoIntent

    data class ReplaceConflictingBook(
        val oldBook: Book,
        val source: BookSource,
        val book: Book,
        val toc: List<BookChapter>,
        val options: ChangeSourceMigrationOptions,
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
    data class CharacterClick(val characterId: String) : BookInfoIntent
    data object AddCharacterClick : BookInfoIntent
    data object CharacterNetworkClick : BookInfoIntent
    data object CharacterListClick : BookInfoIntent
    data object KnowledgeListClick : BookInfoIntent
    data object EventListClick : BookInfoIntent
    data class SetDefaultBookTreeUri(val value: String) : BookInfoIntent

    /** 简介 HTML 中 `<button>名称@onclick:脚本</button>` 的点击。 */
    data class IntroButtonClick(val name: String, val click: String) : BookInfoIntent

    /** 简介 HTML 图片携带 {"click":"脚本"} 参数时的点击。 */
    data class IntroImageClick(val click: String) : BookInfoIntent

    /** 简介 HTML 图片长按。 */
    data class IntroImageLongClick(val source: String) : BookInfoIntent

    /** 私密书籍未解锁时的验证入口 */
    data object RequestPrivateUnlock : BookInfoIntent
    data object ShowPrivatePassword : BookInfoIntent
    data class SubmitPrivatePassword(val password: String) : BookInfoIntent
    data object DismissPrivatePassword : BookInfoIntent
}

sealed interface BookInfoEffect {
    data class ShowMessage(val message: String) : BookInfoEffect

    /** 交给宿主弹出系统生物验证框（需要 FragmentActivity） */
    data object RequestBiometricUnlock : BookInfoEffect

    /** 私密功能需要本地密码：引导去设置页 */
    data object NavigateToLocalPasswordSettings : BookInfoEffect

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

    data class OpenCharacterDetail(
        val bookUrl: String,
        val characterId: String?,
    ) : BookInfoEffect

    data class OpenCharacterNetwork(
        val bookUrl: String,
    ) : BookInfoEffect

    data class OpenCharacterList(
        val bookUrl: String,
    ) : BookInfoEffect

    data class OpenKnowledgeList(
        val bookUrl: String,
    ) : BookInfoEffect

    data class OpenEventList(
        val bookUrl: String,
    ) : BookInfoEffect

    /** 简介按钮/图片触发的书源 JS 执行，由宿主（持有 Activity）用 SourceLoginJsExtensions 运行。 */
    data class RunIntroJs(
        val name: String,
        val click: String,
        val source: BookSource?,
        val book: Book,
    ) : BookInfoEffect
}

sealed interface BookInfoCallbackAction {
    data class Search(val keyword: String) : BookInfoCallbackAction
    data class ShareText(val chooserTitle: String, val text: String) : BookInfoCallbackAction
    data class CopyText(val text: String) : BookInfoCallbackAction
    data object ClearCache : BookInfoCallbackAction
    data object None : BookInfoCallbackAction
}

enum class BookInfoMenuAction {
    CustomButton,
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
    TogglePrivate,
}
