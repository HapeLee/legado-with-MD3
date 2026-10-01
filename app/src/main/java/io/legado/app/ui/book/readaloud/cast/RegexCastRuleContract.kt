package io.legado.app.ui.book.readaloud.cast

import androidx.compose.runtime.Stable
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
 * 实现 [CastPoolView]：一条规则就是一行卡片，分组是可嵌套的文件夹，两者拉平成同一条列表，
 * 于是拖动排序、折叠展开、组开关、改名/移动/删组这些交互与角色声音池、背景音乐池
 * 走的是同一套代码（[PoolTreeList] + [CastPoolTree]），不再抄第三遍。
 * 规则没有「成员」那一层，所以 wording 里 `hasMembers = false`。
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
) : CastPoolView

/**
 * 弹窗要编辑的那条规则。
 *
 * 包一层是为了区分「新建（还没有 id）」和「编辑某条」：直接用 RegexCastRule 时新建那条
 * 的 id 是 0，`remember(rule.id)` 会把两次新建的草稿串在一起。
 */
@Stable
data class RegexCastRuleHolder(
    val rule: io.legado.app.data.entities.RegexCastRule,
    val isNew: Boolean,
)

sealed interface RegexCastRuleIntent {
    data object Refresh : RegexCastRuleIntent
    data object ShowCreate : RegexCastRuleIntent
    data class ShowEdit(val ruleId: Long) : RegexCastRuleIntent
    data object DismissEdit : RegexCastRuleIntent
    data class Save(val rule: io.legado.app.data.entities.RegexCastRule) : RegexCastRuleIntent

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
}
