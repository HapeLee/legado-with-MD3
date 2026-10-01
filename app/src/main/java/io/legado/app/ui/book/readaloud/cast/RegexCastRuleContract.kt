package io.legado.app.ui.book.readaloud.cast

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.ui.widget.components.CastOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 正则角色管理页状态（朗读规则 → 正则角色管理）。
 *
 * [rows] 已经按分组排好并把摘要拼成中文，界面只管显示。[editTarget] 非空即弹出编辑框
 * （新建时是一条还没落库的空规则）。弹窗里「声音池选择 / 声音池」两栏一换，候选就要跟着换，
 * 所以池与条目的候选项也放在状态里，由 ViewModel 去两个池库里取。
 */
@Stable
data class RegexCastRuleUiState(
    val rows: ImmutableList<RegexCastRow> = persistentListOf(),
    val editTarget: RegexCastRule? = null,
    val isNew: Boolean = false,
    val deleteTarget: RegexCastRule? = null,
    val poolOptions: ImmutableList<CastOption> = persistentListOf(),
    val itemOptions: ImmutableList<CastOption> = persistentListOf(),
    val groupOptions: ImmutableList<CastOption> = persistentListOf(),
)

@Stable
data class RegexCastRow(
    val rule: RegexCastRule,
    /** 例：命中「爆炸」→ 播放 山体崩碎（配乐池） */
    val summary: String,
    /** 这一条所在的小节名，空 = 未分组。只在分组的第一行显示。 */
    val section: String? = null,
)

sealed interface RegexCastRuleIntent {
    data object Refresh : RegexCastRuleIntent
    data object ShowCreate : RegexCastRuleIntent
    data class ShowEdit(val rule: RegexCastRule) : RegexCastRuleIntent
    data object DismissEdit : RegexCastRuleIntent

    /** 弹窗里换了「声音池选择」或「声音池」：候选要跟着从对应的池库里重取。 */
    data class PickPool(val kind: String, val poolId: String) : RegexCastRuleIntent

    data class Save(val rule: RegexCastRule) : RegexCastRuleIntent

    /** 拖动排序：列表下标；拖进别的小节就等于换组。 */
    data class Move(val from: Int, val to: Int) : RegexCastRuleIntent
    data class Toggle(val rule: RegexCastRule, val enabled: Boolean) : RegexCastRuleIntent
    data class ShowDelete(val rule: RegexCastRule) : RegexCastRuleIntent
    data object DismissDelete : RegexCastRuleIntent
    data class Delete(val rule: RegexCastRule) : RegexCastRuleIntent
}
