package io.legado.app.ui.book.readaloud.cast

import androidx.compose.runtime.Stable
import android.net.Uri
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.help.readaloud.cast.CastGroupRow
import io.legado.app.help.readaloud.cast.CastPoolRow
import io.legado.app.ui.widget.components.CastOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * 正则角色管理页状态（朗读规则 → 正则角色管理）。
 *
 * 实现 [CastPoolView]：一条规则就是一个 [CastPoolRow]（`subtitle` 放规则摘要，界面对
 * hasMembers=false 的行画它），分组是可嵌套文件夹，两者拉平成同一条列表，拖动排序、折叠
 * 展开、组开关、改名/移动/删组复用角色声音池 / 背景音乐池那套 [PoolTreeList] + [CastPoolTree]。
 * 规则没有「成员」那一层，所以 wording 里 `hasMembers = false`（契约见 CastPoolWidgets）。
 *
 * 拖动落库链：RegexCastRuleIntent.MoveItem / SaveSortOrder → [CastPoolTree.savePlan] →
 * RegexCastRuleStore.saveSlots，三段下标口径必须一致。
 */
@Stable
data class RegexCastRuleUiState(
    override val pools: ImmutableList<CastPoolRow> = persistentListOf(),
    override val groups: ImmutableList<CastGroupRow> = persistentListOf(),
    override val rows: ImmutableList<PoolTreeRow> = persistentListOf(),
    override val collapsedGroups: ImmutableSet<String> = persistentSetOf(),
    override val searchActive: Boolean = false,
    override val searchQuery: String = "",
    override val dragTargetGroupId: String? = null,
    override val dragSourceGroupId: String? = null,
    override val expandedPools: ImmutableList<ExpandedPoolUi> = persistentListOf(),
    /** 规则编辑弹窗（非空 = 打开中）。 */
    val editTarget: RegexCastRuleHolder? = null,
    /** 删除确认（规则）。 */
    val deleteTarget: RegexCastRuleHolder? = null,
    /** 分组新建/重命名对话框。 */
    val groupDialog: GroupEditDialogState? = null,
    /** 分组「移动到」对话框目标组 id。 */
    val moveGroupTarget: String? = null,
    /** 分组删除确认目标组 id。 */
    val deleteGroupTarget: String? = null,
    /** 弹窗里「声音池」「音色」「分组」三栏的候选。 */
    val poolOptions: ImmutableList<CastOption> = persistentListOf(),
    val itemOptions: ImmutableList<CastOption> = persistentListOf(),
    val groupOptions: ImmutableList<CastOption> = persistentListOf(),
    /**
     * 弹窗里「变声器」那一栏的候选：开头一条「不变声」（key 为空串），后面是启用的预设名。
     * 取自 [io.legado.app.help.readaloud.effect.VoiceEffectStore.enabledNames]，随 ShowCreate /
     * ShowEdit / PickPool 一起装载；写回落在 `regex_cast_rules.voiceEffect`，
     * 朗读侧的消费方是 RegexCastRuleStore.effectsFor → 朗读单元的 voiceEffect。
     */
    val effectOptions: ImmutableList<CastOption> = persistentListOf(),
) : CastPoolView

/**
 * 弹窗要编辑的那条规则。[isNew] 区分「新建」与「编辑某条」：新建那条 [RegexCastRule.id]
 * 恒为 0，弹窗草稿按 holder 而不是按 id remember，两次新建的草稿才不会串在一起。
 */
@Stable
data class RegexCastRuleHolder(
    val rule: RegexCastRule,
    val isNew: Boolean,
)

sealed interface RegexCastRuleIntent {
    data object Refresh : RegexCastRuleIntent
    data object ShowCreate : RegexCastRuleIntent
    data class ShowEdit(val ruleId: Long) : RegexCastRuleIntent
    data object DismissEdit : RegexCastRuleIntent
    data class Save(val rule: RegexCastRule) : RegexCastRuleIntent

    /** 弹窗里换了「声音池选择」或「声音池」：下面那栏的候选跟着换。 */
    data class PickPool(
        val kind: String,
        val poolId: String,
        val keepItemId: String,
    ) : RegexCastRuleIntent

    data class ShowDelete(val ruleId: Long) : RegexCastRuleIntent
    data object DismissDelete : RegexCastRuleIntent
    data class Delete(val ruleId: Long) : RegexCastRuleIntent

    // ---- 列表卡片（CastPoolActions 翻译过来的动作，池 id 就是规则 id 的字符串） ----

    data class RuleEnabled(val ruleId: String, val enabled: Boolean) : RegexCastRuleIntent
    data class Query(val text: String) : RegexCastRuleIntent
    data object ToggleSearch : RegexCastRuleIntent

    /** 拖动过程中的一次挪动（绝对下标）；松手由 [SaveSortOrder] 落库。 */
    data class MoveItem(val from: Int, val to: Int) : RegexCastRuleIntent
    data object SaveSortOrder : RegexCastRuleIntent

    // ---- 分组 ----

    data class ToggleGroup(val groupId: String) : RegexCastRuleIntent
    data class GroupEnabled(val groupId: String, val enabled: Boolean) : RegexCastRuleIntent
    data class RenameGroup(val groupId: String) : RegexCastRuleIntent
    data class CreateGroup(val parentId: String) : RegexCastRuleIntent
    data class MoveGroup(val groupId: String) : RegexCastRuleIntent
    data class DeleteGroup(val groupId: String) : RegexCastRuleIntent
    data object DismissGroupDialog : RegexCastRuleIntent
    data class ConfirmGroup(
        val editingId: String?,
        val parentId: String,
        val name: String,
    ) : RegexCastRuleIntent

    data class ConfirmMoveGroup(val id: String, val parentId: String) : RegexCastRuleIntent
    data object DismissMoveGroup : RegexCastRuleIntent
    data object DismissDeleteGroup : RegexCastRuleIntent
    data class ConfirmDeleteGroup(val id: String) : RegexCastRuleIntent

    // ---- 导入 / 导出 ----
    //
    // 文件格式（按名字引用池与音色、分组用新 id 重建父子、合并不清空）全部在
    // [io.legado.app.help.readaloud.cast.RegexCastTransfer]，界面只负责选文件和报计数。

    /** 把当前全部规则与分组写进用户选中的 .json。 */
    data class ExportTo(val uri: Uri) : RegexCastRuleIntent

    /** 从用户选中的 .json 导入；只增不删，计数用 [RegexCastRuleEffect.ShowToast] 回报。 */
    data class ImportFrom(val uri: Uri) : RegexCastRuleIntent
}

/** 一次性动作：文件选择结果与导入计数都不属于页面状态，不能塞进 UiState。 */
sealed interface RegexCastRuleEffect {
    data class ShowToast(val message: String) : RegexCastRuleEffect
}
