package io.legado.app.ui.book.readaloud.cast

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.help.readaloud.cast.CastGroupRow
import io.legado.app.help.readaloud.cast.CastPoolRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * 一行配乐（配乐库里的一个文件副本）。
 *
 * [missing] = 库里还有记录但副本文件已经不在了（用户清了数据目录）；这种行不能播，
 * 只能删掉。池里的成员（[CastMemberUi]）按 id 回到这里取路径与丢失标记。
 */
@Stable
data class BgmTrackUi(
    val id: String,
    val name: String,
    val subtitle: String,
    val enabled: Boolean,
    val missing: Boolean,
    /** 副本绝对路径，播放按钮直接喂给 MediaPlayer，省一次查库。 */
    val path: String,
    /** 这条配乐自身的音量（0f–1f）；场景还能再压一档。 */
    val volume: Float = 1f,
)

/**
 * 背景音乐池页状态。
 *
 * 树/池/拖动那部分字段与角色声音池完全同构，所以直接实现 [CastPoolView]，
 * 界面复用同一套部件；下面多出来的只有配乐库、试听和淡入淡出开关。
 */
@Stable
data class BgmPoolUiState(
    val loading: Boolean = true,
    override val pools: ImmutableList<CastPoolRow> = persistentListOf(),
    override val groups: ImmutableList<CastGroupRow> = persistentListOf(),
    override val rows: ImmutableList<PoolTreeRow> = persistentListOf(),
    override val collapsedGroups: ImmutableSet<String> = persistentSetOf(),
    override val searchActive: Boolean = false,
    override val searchQuery: String = "",
    override val dragTargetGroupId: String? = null,
    override val dragSourceGroupId: String? = null,
    /** 展开中的池，支持同时展开多个（与角色声音池一致）。 */
    override val expandedPools: ImmutableList<ExpandedPoolUi> = persistentListOf(),
    val editDialog: PoolEditDialogState? = null,
    val deleteTarget: CastPoolRow? = null,
    val groupDialog: GroupEditDialogState? = null,
    val moveGroupTarget: String? = null,
    val deleteGroupTarget: String? = null,
    /** 「添加配乐到池」对话框（非空 = 打开中，值 = 加进哪个池）。 */
    val pickerPoolId: String? = null,
    val pickerQuery: String = "",
    val pickerCandidates: ImmutableList<CastMemberUi> = persistentListOf(),
    /** 配乐库（导入进来的音频副本）。 */
    val tracks: ImmutableList<BgmTrackUi> = persistentListOf(),
    /** 配乐库展开区是否打开。 */
    val libraryExpanded: Boolean = false,
    val trackDeleteTarget: BgmTrackUi? = null,
    /** 正在改音量的配乐（弹窗），null = 没开。 */
    val trackVolumeTarget: BgmTrackUi? = null,
    /** 正在改名的配乐（弹窗），null = 没开。 */
    val trackRenameTarget: BgmTrackUi? = null,
    /** 正在试听的配乐 id，null = 没在放。界面据此切播放/停止图标。 */
    val playingId: String? = null,
    /** 淡入淡出开关（切场景时用）。 */
    val fadeIn: Boolean = true,
    val fadeOut: Boolean = true,
) : CastPoolView

/**
 * 池/分组/成员那部分意图与角色声音池一一对应（界面共用一套部件，动作也必须同名同义），
 * 后面才是背景音乐特有的：配乐库、试听、淡入淡出。
 */
sealed interface BgmPoolIntent {
    data object Refresh : BgmPoolIntent
    data class ToggleGroup(val group: String) : BgmPoolIntent
    data class SetGroupEnabled(val groupId: String, val enabled: Boolean) : BgmPoolIntent
    data class SetSearch(val active: Boolean) : BgmPoolIntent
    data class UpdateSearch(val query: String) : BgmPoolIntent

    /** 长按拖动：先挪位置，松手才落库（与角色声音池一致）。 */
    data class MoveItem(val from: Int, val to: Int) : BgmPoolIntent
    data object SaveSortOrder : BgmPoolIntent

    data object ShowCreateDialog : BgmPoolIntent
    data class ShowEditDialog(val pool: CastPoolRow) : BgmPoolIntent
    data object DismissDialog : BgmPoolIntent
    data class SavePool(val editingId: String?, val name: String, val groupId: String) : BgmPoolIntent
    data class SetPoolEnabled(val poolId: String, val enabled: Boolean) : BgmPoolIntent
    data class AskDeletePool(val pool: CastPoolRow) : BgmPoolIntent
    data object DismissDelete : BgmPoolIntent
    data class ConfirmDeletePool(val poolId: String) : BgmPoolIntent

    data class ShowCreateGroup(val parentId: String = "") : BgmPoolIntent
    data class AskRenameGroup(val groupId: String) : BgmPoolIntent
    data object DismissGroupDialog : BgmPoolIntent
    data class ConfirmGroupDialog(val editingId: String?, val parentId: String, val name: String) : BgmPoolIntent
    data class AskMoveGroup(val groupId: String) : BgmPoolIntent
    data object DismissMoveGroup : BgmPoolIntent
    data class ConfirmMoveGroup(val groupId: String, val newParentId: String) : BgmPoolIntent
    data class AskDeleteGroup(val groupId: String) : BgmPoolIntent
    data object DismissDeleteGroup : BgmPoolIntent
    data class ConfirmDeleteGroup(val groupId: String) : BgmPoolIntent

    data class TogglePoolExpand(val poolId: String) : BgmPoolIntent
    data class UpdateMemberQuery(val poolId: String, val query: String) : BgmPoolIntent
    data class ToggleMemberEnabled(val poolId: String, val trackId: String, val enabled: Boolean) : BgmPoolIntent
    data class RemoveMember(val poolId: String, val trackId: String) : BgmPoolIntent

    /** 可同时展开多个池，加成员的对话框要指明进哪个池。 */
    data class ShowMemberPicker(val poolId: String) : BgmPoolIntent
    data object DismissMemberPicker : BgmPoolIntent
    data class UpdatePickerQuery(val query: String) : BgmPoolIntent
    data class TogglePickerSelection(val trackId: String, val checked: Boolean) : BgmPoolIntent
    data object SaveMemberPicker : BgmPoolIntent

    /** 配乐库：展开/收起、导入、开关、删除。 */
    data object ToggleLibrary : BgmPoolIntent
    data object AskImport : BgmPoolIntent
    data class FilesPicked(val uris: List<Uri>) : BgmPoolIntent
    data class SetTrackEnabled(val id: String, val enabled: Boolean) : BgmPoolIntent
    /** 这条导入配乐自身的音量（0f–1f），场景音量再乘在它上面。 */
    data class SetTrackVolume(val id: String, val volume: Float) : BgmPoolIntent
    data class AskTrackVolume(val id: String) : BgmPoolIntent
    data object DismissTrackVolume : BgmPoolIntent
    data class AskDeleteTrack(val id: String) : BgmPoolIntent
    data object DismissTrackDelete : BgmPoolIntent
    data class ConfirmDeleteTrack(val id: String) : BgmPoolIntent

    /**
     * 改这条导入配乐的显示名。空名与重名会被 [io.legado.app.help.readaloud.cast.BgmPoolStore.renameTrack]
     * 判非法（场景标记按名字引用曲目，重名会指错），界面提示而不写库。
     */
    data class AskRenameTrack(val id: String) : BgmPoolIntent
    data object DismissTrackRename : BgmPoolIntent
    data class ConfirmRenameTrack(val id: String, val name: String) : BgmPoolIntent

    /** 行尾播放按钮：正在放这条就停，否则换它。 */
    data class PlayToggle(val id: String) : BgmPoolIntent
    /** 播放器自己放完了，界面回来把图标复位。 */
    data object PlayFinished : BgmPoolIntent

    data class SetFadeIn(val enabled: Boolean) : BgmPoolIntent
    data class SetFadeOut(val enabled: Boolean) : BgmPoolIntent

    /**
     * 右上角「导入/导出」：整个背景音乐池（分组树 + 池 + 配乐 + 归属）连音频文件一起打包带走。
     *
     * zip 的格式只由 [io.legado.app.help.readaloud.cast.BgmPoolTransfer] 一处实现：
     * [AskExportPackage] 让 VM 发一个带建议文件名的保存效果，界面用 CreateDocument 拿到 uri
     * 交回 [ExportPackageTo]；[AskImportPackage] 同理，选回来的 uri 交给 [ImportPackagePicked] 解包。
     * 界面全程不认识包内部结构（清单字段与「音频落进接收端自己的 bgm 目录」的口径见那个文件）。
     */
    data object AskImportPackage : BgmPoolIntent
    data object AskExportPackage : BgmPoolIntent
    data class ImportPackagePicked(val uri: Uri) : BgmPoolIntent
    data class ExportPackageTo(val uri: Uri) : BgmPoolIntent
}

sealed interface BgmPoolEffect {
    data class ShowToast(val message: String) : BgmPoolEffect
    data object OpenFilePicker : BgmPoolEffect

    /** 选一个音乐包（zip）导进来；与「导入音频」的多个文件选择是两个契约。 */
    data object OpenPackagePicker : BgmPoolEffect

    /** 让用户挑保存位置（CreateDocument），[fileName] 是建议文件名。 */
    data class SavePackageTo(val fileName: String) : BgmPoolEffect

    data class Play(val id: String, val path: String, val volume: Float) : BgmPoolEffect
    data object Stop : BgmPoolEffect
}
