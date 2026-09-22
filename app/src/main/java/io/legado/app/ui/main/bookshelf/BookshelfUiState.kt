package io.legado.app.ui.main.bookshelf

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.PrivateUnlockTarget
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.domain.model.settings.PrivateAccessSettings
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class BookshelfGroupSelectorState(
    val isInitialLoading: Boolean = true,
    val groups: ImmutableList<BookGroupUi> = persistentListOf(),
    val selectedGroupIndex: Int = 0,
    val selectedGroupId: Long = BookGroup.IdAll
)

sealed interface BookshelfOverlay {
    data object AddUrlDialog : BookshelfOverlay

    /** 应用内密码解锁（生物不可用、用户点了"使用密码"，或设备不支持时的兜底） */
    data class PrivatePassword(val target: PrivateUnlockTarget) : BookshelfOverlay
    data object ImportSheet : BookshelfOverlay
    data object ExportSheet : BookshelfOverlay
    data object ConfigSheet : BookshelfOverlay
    data object GroupManageSheet : BookshelfOverlay
    data object LogSheet : BookshelfOverlay
    data object GroupMenu : BookshelfOverlay
    data object GroupSelectSheet : BookshelfOverlay
    data class GroupEditSheet(val groupId: Long) : BookshelfOverlay
    data object BatchDownloadConfirmDialog : BookshelfOverlay
}

sealed interface BookshelfIntent {
    data class ChangeGroup(val groupId: Long) : BookshelfIntent
    data class SetSearchKey(val value: String) : BookshelfIntent
    data class SetSearchMode(val active: Boolean) : BookshelfIntent
    data class ShowOverlay(val overlay: BookshelfOverlay) : BookshelfIntent
    data object DismissOverlay : BookshelfIntent
    data object ToggleEditMode : BookshelfIntent
    data object ExitEditMode : BookshelfIntent
    data object ClearSelection : BookshelfIntent
    data object SelectAllVisible : BookshelfIntent
    data object InvertVisibleSelection : BookshelfIntent
    data class ToggleBookSelection(val bookUrl: String) : BookshelfIntent
    data class SetInFolderRoot(val value: Boolean) : BookshelfIntent
    data class MoveBooksToGroup(val bookUrls: Set<String>, val groupId: Long) : BookshelfIntent
    data class DownloadBooks(val bookUrls: Set<String>, val allChapters: Boolean = false) : BookshelfIntent
    data class RefreshBooks(val books: List<BookUiItem>) : BookshelfIntent
    data class StartDragging(val books: List<BookUiItem>) : BookshelfIntent
    data class MoveDragging(val from: Int, val to: Int, val books: List<BookUiItem>) : BookshelfIntent
    data object FinishDragging : BookshelfIntent
    data object ScrollToTop : BookshelfIntent
    data object RefreshAll : BookshelfIntent
    data class RefreshToc(val books: List<BookUiItem>) : BookshelfIntent
    data class AddBookByUrl(val urls: String) : BookshelfIntent
    data class ExportToUri(val uri: Uri, val books: List<BookUiItem>) : BookshelfIntent
    data class UploadBookshelf(val books: List<BookUiItem>) : BookshelfIntent
    data class ImportFromUri(val uri: Uri, val groupId: Long) : BookshelfIntent
    data class UpdateSetting(
        val transform: (BookshelfSettings) -> BookshelfSettings,
    ) : BookshelfIntent
    data class SetCustomTagColorsEnabled(val enabled: Boolean) : BookshelfIntent
    data class SetCustomTagColors(val colors: List<TagColorPair>) : BookshelfIntent
    data object UploadResultConsumed : BookshelfIntent

    /** 编辑态批量标记/取消书籍私密 */
    data class SetBooksPrivate(val bookUrls: Set<String>, val isPrivate: Boolean) :
        BookshelfIntent

    /** 点击私密书籍 / 进入私密分组时请求解锁；已解锁则直接执行目标动作 */
    data class RequestPrivateUnlock(val target: PrivateUnlockTarget) : BookshelfIntent

    /** 密码覆盖层提交；由宿主在生物不可用或用户选择密码时打开 */
    data class SubmitPrivatePassword(
        val target: PrivateUnlockTarget,
        val password: String,
    ) : BookshelfIntent

    /** 生物验证成功并解出密码后回传给 ViewModel，仍走同一条密码校验 */
    data class UnlockPrivateWithBiometricPassword(
        val target: PrivateUnlockTarget,
        val password: String,
    ) : BookshelfIntent

    /** 待打开的私密书已经处理完，清空挂起目标 */
    data object ConsumePendingOpenBook : BookshelfIntent

}

sealed interface BookshelfEffect {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val url: String? = null,
    ) : BookshelfEffect

    /** 交给宿主弹出系统生物验证框（需要 FragmentActivity） */
    data class RequestBiometricUnlock(val target: PrivateUnlockTarget) : BookshelfEffect

    /** 私密功能需要本地密码，引导去设置页 */
    data object NavigateToLocalPasswordSettings : BookshelfEffect
}

@Stable
data class BookshelfUiState(
    override val items: ImmutableList<BookUiItem> = persistentListOf(),
    override val selectedIds: ImmutableSet<Any> = persistentSetOf(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val isInitialLoading: Boolean = true,
    val groups: ImmutableList<BookGroupUi> = persistentListOf(),
    val allGroups: ImmutableList<BookGroupUi> = persistentListOf(),
    val groupPreviews: ImmutableMap<Long, ImmutableList<BookUiItem>> = persistentMapOf(),
    val groupBookCounts: ImmutableMap<Long, Int> = persistentMapOf(),
    val currentGroupBookCount: Int = 0,
    val allBooksCount: Int = 0,
    val selectedGroupIndex: Int = 0,
    val selectedGroupId: Long = BookGroup.IdAll,
    val loadingText: String? = null,
    val upBooksCount: Int = 0,
    val updatingBooks: ImmutableSet<String> = persistentSetOf(),
    val activeOverlay: BookshelfOverlay? = null,
    val isEditMode: Boolean = false,
    val selectedBookUrls: ImmutableSet<String> = persistentSetOf(),
    val isInFolderRoot: Boolean = false,
    val isRefreshing: Boolean = false,
    val bookGroupStyle: Int = 0,
    val bookshelfSort: Int = 0,
    val bookshelfSortOrder: Int = 1,
    val title: String = "",
    val subtitle: String? = null,
    val currentGroupName: String? = null,
    val draggingBooks: ImmutableList<BookUiItem>? = null,
    val pendingSavedBooks: ImmutableList<BookUiItem>? = null,
    val visibleGroupBooks: ImmutableMap<Long, ImmutableList<BookUiItem>> = persistentMapOf(),
    val settings: BookshelfSettings = BookshelfSettings(),
    val useRaisedBottomInset: Boolean = false,
    val enableCustomTagColors: Boolean = false,
    val customTagColors: ImmutableList<TagColorPair> = persistentListOf(),
    val themeColor: Int = 0,
    val pendingUploadUrl: String? = null,
    /** 私密内容解锁状态；未设置本地密码时 hasPassword=false，私密功能不可用 */
    val privateAccess: PrivateAccessState = PrivateAccessState(),
    /** 验证时机与频率设置；决定哪些私密内容此刻处于锁定态 */
    val privateSettings: PrivateAccessSettings = PrivateAccessSettings(),

    /**
     * 等待解锁后打开的书：解锁是一件"一致性关键"的事，打开书籍因此落成状态而不是
     * 一次性 Effect——否则列表尚未重新过滤出这本书时事件就丢了。
     */
    val pendingOpenBookUrl: String? = null,
) : ListUiState<BookUiItem> {

    fun isBookLocked(bookUi: BookUiItem): Boolean =
        bookUi.isLocked(privateAccess, privateSettings.verifyOnOpenBook)

    /**
     * 某个分组当前是否处于锁定态。
     *
     * 判定放在这里而不是 ViewModel：只有锁定的是**这个分组自己的内容区**，
     * 分页容器与相邻分组保持可交互，否则用户会被卡在锁定页里无法切走。
     */
    fun isGroupLocked(group: BookGroupUi): Boolean =
        group.isPrivate &&
                privateSettings.verifyOnEnterGroup &&
                !privateAccess.isTargetGranted(null, group.groupId)

    /** 单独搜索分组模式下的当前分组是否锁定 */
    val selectedGroupLocked: Boolean
        get() = groups.firstOrNull { it.groupId == selectedGroupId }?.let(::isGroupLocked) == true
}
