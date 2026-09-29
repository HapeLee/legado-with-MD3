package io.legado.app.ui.book.readaloud.cast

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.help.readaloud.cast.CastGroupRow
import io.legado.app.help.readaloud.cast.CastPoolRow
import io.legado.app.help.readaloud.cast.VoicePoolStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

enum class CastTab { Pools, Assignments }

/** 新建/编辑声音池对话框状态（editingId = null 为新建）。groupId 空串 = 未分组。 */
@Stable
data class PoolEditDialogState(
    val editingId: String? = null,
    val name: String = "",
    val groupId: String = "",
    /** 保存失败的字符串资源 id（重名/非法名）。 */
    val errorRes: Int? = null,
)

/** 分组新建/重命名对话框（editingId = null 为在 [parentId] 下新建）。 */
@Stable
data class GroupEditDialogState(
    val editingId: String? = null,
    val parentId: String = "",
    val name: String = "",
    val errorRes: Int? = null,
)

/**
 * 声音池页的一行：分组头或池卡，按 DFS 拉平成一条列表，好让长按拖动同时覆盖两者。
 *
 * 拖动只改列表顺序，[depth] 保持不动；松手时按「落在哪个分组头的范围内」决定归属，
 * 见 MultiRoleRuleViewModel.dropParentOf。
 */
@Stable
data class PoolTreeRow(
    val key: String,
    val groupId: String,
    val isGroup: Boolean,
    /** true = 顶部的「未分组」表头：占位、可作放置目标，但不可拖动/改名/删除。 */
    val isUngroupedHeader: Boolean = false,
    val depth: Int = 0,
    val title: String = "",
    val group: CastGroupRow? = null,
    val pool: CastPoolRow? = null,
)

/** 分配表行：配音角色 → 音色。 */
@Stable
data class CastAssignmentRowUi(
    val characterId: String,
    val name: String,
    val poolLabel: String,
    val voiceName: String,
    val bookName: String,
    val lineCount: Int,
)

/** 池展开后的成员行：音色 + 来源引擎 + 池内启用复选框。 */
@Stable
data class CastMemberUi(
    val voiceId: String,
    val displayName: String,
    /** 音色来自哪个引擎，和「朗读引擎与音色」页的副标题一致；空 = 不显示第二行。 */
    val engineName: String = "",
    /** true = 该音色在本池已启用（复选框勾选），会出现在分配角色音色菜单。 */
    val checked: Boolean,
)

@Stable
data class MultiRoleRuleUiState(
    val tab: CastTab = CastTab.Pools,
    override val pools: ImmutableList<CastPoolRow> = persistentListOf(),
    /** 分组树（DFS 拉平、带 depth），池按 groupId + order 挂在下面。 */
    override val groups: ImmutableList<CastGroupRow> = persistentListOf(),
    /** 页面实际渲染的行顺序，拖动改的就是它，松手时回写数据库。 */
    override val rows: ImmutableList<PoolTreeRow> = persistentListOf(),
    /**
     * 拖动中：被拖行现在落在哪个「范围」里，松手就归入这个组（空串 = 未分组/根层）。
     * null = 没在拖，或落点非法（拖进自己/自己的子树）。界面用它高亮目标分组头。
     */
    override val dragTargetGroupId: String? = null,
    /**
     * 拖动中：被拖行**出发前**的父组（空串 = 未分组/根层）。和 [dragTargetGroupId] 一比
     * 就知道这一把是「放进某组」「拖出去」还是「只调顺序」，界面据此写落点提示。
     */
    override val dragSourceGroupId: String? = null,
    /** 收起的分组 id（空串 = 「未分组」段）。 */
    override val collapsedGroups: ImmutableSet<String> = persistentSetOf(),
    override val searchActive: Boolean = false,
    override val searchQuery: String = "",
    val editDialog: PoolEditDialogState? = null,
    val deleteTarget: CastPoolRow? = null,
    /** 分组新建/重命名对话框（非空 = 打开中）。 */
    val groupDialog: GroupEditDialogState? = null,
    /** 分组「移动到」对话框的目标组 id（非空 = 打开中）。 */
    val moveGroupTarget: String? = null,
    /** 分组删除确认对话框的目标组 id（非空 = 打开中）。 */
    val deleteGroupTarget: String? = null,
    /** 展开中的池（行内显示成员列表），支持同时展开多个，点开一个不把另一个顶掉。 */
    override val expandedPools: ImmutableList<ExpandedPoolUi> = persistentListOf(),
    /** 「添加音色到池」对话框（非空 = 打开中，值 = 加进哪个池）。 */
    val pickerPoolId: String? = null,
    val pickerQuery: String = "",
    val pickerCandidates: ImmutableList<CastMemberUi> = persistentListOf(),
    val assignments: ImmutableList<CastAssignmentRowUi> = persistentListOf(),
    /**
     * 待确认的外部格式导入（非空 = 确认对话框打开中）。
     * 解析时就已剔掉音效，这里只带角色，所以对话框上的数字即用户最终会得到多少。
     */
    val ttsServerPreview: TtsServerPreviewUi? = null,
    /** 导入的音色挂到哪个系统 TTS 引擎包名下（两种导出文件里都不带来源包名，只能让用户确认）。 */
    val ttsServerEngine: String = "",
) : CastPoolView

/**
 * TTS Server / multitts 导入确认页要显示的数量（解析结果的去敏视图）。
 *
 * 两种外部格式落库要做的事一样，所以共用这一份预览与同一个确认对话框，
 * 只有 [source] 决定标题、引擎包名提示与落点根分组。
 */
@Stable
data class TtsServerPreviewUi(
    val poolNames: ImmutableList<String> = persistentListOf(),
    val voiceCount: Int = 0,
    val skippedEffects: Int = 0,
    val skippedDisabled: Int = 0,
    val source: CastImportSource = CastImportSource.TtsServer,
)

/** 外部声音池文件的来源：解析器、根分组名、默认引擎包名都按它分岔。 */
enum class CastImportSource {
    TtsServer,
    MultiTts,
}

sealed interface MultiRoleRuleIntent {
    data object Refresh : MultiRoleRuleIntent
    data class SwitchTab(val tab: CastTab) : MultiRoleRuleIntent
    data class ToggleGroup(val group: String) : MultiRoleRuleIntent
    data class SetGroupEnabled(val groupId: String, val enabled: Boolean) : MultiRoleRuleIntent
    data class SetSearch(val active: Boolean) : MultiRoleRuleIntent
    data class UpdateSearch(val query: String) : MultiRoleRuleIntent

    /** 长按拖动：先把行挪个位置，松手才落库（与书源/字典/标签规则页一致）。 */
    data class MoveItem(val from: Int, val to: Int) : MultiRoleRuleIntent
    data object SaveSortOrder : MultiRoleRuleIntent

    data object ShowCreateDialog : MultiRoleRuleIntent
    data class ShowEditDialog(val pool: CastPoolRow) : MultiRoleRuleIntent
    data object DismissDialog : MultiRoleRuleIntent
    data class SavePool(
        val editingId: String?,
        val name: String,
        val groupId: String,
    ) : MultiRoleRuleIntent

    data class SetPoolEnabled(val poolId: String, val enabled: Boolean) : MultiRoleRuleIntent
    data class AskDeletePool(val pool: CastPoolRow) : MultiRoleRuleIntent
    data object DismissDelete : MultiRoleRuleIntent
    data class ConfirmDeletePool(val poolId: String) : MultiRoleRuleIntent

    /** 分组：新建（parentId 空串 = 根层）/ 重命名 / 换父级 / 解散。 */
    data class ShowCreateGroup(val parentId: String = "") : MultiRoleRuleIntent
    data class AskRenameGroup(val groupId: String) : MultiRoleRuleIntent
    data object DismissGroupDialog : MultiRoleRuleIntent
    data class ConfirmGroupDialog(
        val editingId: String?,
        val parentId: String,
        val name: String,
    ) : MultiRoleRuleIntent
    data class AskMoveGroup(val groupId: String) : MultiRoleRuleIntent
    data object DismissMoveGroup : MultiRoleRuleIntent
    data class ConfirmMoveGroup(val groupId: String, val newParentId: String) : MultiRoleRuleIntent
    data class AskDeleteGroup(val groupId: String) : MultiRoleRuleIntent
    data object DismissDeleteGroup : MultiRoleRuleIntent
    data class ConfirmDeleteGroup(val groupId: String) : MultiRoleRuleIntent

    /** 池行展开/收起（行内成员列表），可同时展开多个。 */
    data class TogglePoolExpand(val poolId: String) : MultiRoleRuleIntent
    data class UpdateMemberQuery(val poolId: String, val query: String) : MultiRoleRuleIntent
    /** 成员复选框 = 池内启用开关（勾上才会出现在分配角色音色菜单）。 */
    data class ToggleMemberEnabled(val poolId: String, val voiceId: String, val enabled: Boolean) : MultiRoleRuleIntent
    /** 从池中移除该音色（行尾删除）。 */
    data class RemoveMember(val poolId: String, val voiceId: String) : MultiRoleRuleIntent

    /** 添加音色到池（对话框勾选，保存才加入）；可同时展开多个池，所以要带上目标池。 */
    data class ShowMemberPicker(val poolId: String) : MultiRoleRuleIntent
    data object DismissMemberPicker : MultiRoleRuleIntent
    data class UpdatePickerQuery(val query: String) : MultiRoleRuleIntent
    data class TogglePickerSelection(val voiceId: String, val checked: Boolean) : MultiRoleRuleIntent
    data object SaveMemberPicker : MultiRoleRuleIntent

    /** 分配表：删除全局配音角色（连同其分配行，仅用于清理）。 */
    data class DeleteCharacter(val characterId: String) : MultiRoleRuleIntent

    /**
     * 导入/导出弹窗的四个入口。选文件、写文件都在界面做（Activity 结果回调），
     * 这里只发 Effect 让界面去弹系统选择器。
     */
    data object AskImportTtsServer : MultiRoleRuleIntent
    data object AskImportMultiTts : MultiRoleRuleIntent
    data object AskImportPools : MultiRoleRuleIntent
    data object AskExportPools : MultiRoleRuleIntent
    /** TTS Server / multitts 列表文件内容（已读成文本，解析在 VM）。 */
    data class TtsServerFileRead(val text: String) : MultiRoleRuleIntent
    data class MultiTtsFileRead(val text: String) : MultiRoleRuleIntent
    data class UpdateTtsServerEngine(val engine: String) : MultiRoleRuleIntent
    data object ConfirmTtsServerImport : MultiRoleRuleIntent
    data object DismissTtsServerImport : MultiRoleRuleIntent
    /** 自有格式声音池文件内容 / 导出目标 uri。 */
    data class ImportPoolsText(val text: String) : MultiRoleRuleIntent
    data class ExportPoolsTo(val uri: Uri) : MultiRoleRuleIntent
}

sealed interface MultiRoleRuleEffect {
    data class ShowToast(val message: String) : MultiRoleRuleEffect
    data object OpenTtsServerPicker : MultiRoleRuleEffect
    data object OpenMultiTtsPicker : MultiRoleRuleEffect
    data object OpenPoolsPicker : MultiRoleRuleEffect
    data class SavePoolsTo(val fileName: String) : MultiRoleRuleEffect
}
