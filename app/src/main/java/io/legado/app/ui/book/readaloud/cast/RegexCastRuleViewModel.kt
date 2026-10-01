package io.legado.app.ui.book.readaloud.cast

import android.app.Application
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.help.readaloud.cast.BgmPoolStore
import io.legado.app.help.readaloud.cast.RegexCastRuleStore
import io.legado.app.help.readaloud.cast.VoicePoolStore
import io.legado.app.ui.widget.components.CastOption
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 正则角色管理 ViewModel。
 *
 * DAO 访问收口在 [RegexCastRuleStore]（架构护栏：VM 不直连 DAO）。改完即生效——朗读侧每次
 * 准备新章会重取规则快照，正在播的那一条不受影响，下一条起用新规则。
 */
class RegexCastRuleViewModel(
    application: Application,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(RegexCastRuleUiState())
    val uiState = _uiState.asStateFlow()

    /** 池名与条目名缓存：列表摘要与弹窗候选都从这里取，省掉逐行查库。 */
    private var poolNames: Map<String, String> = emptyMap()
    private var itemNames: Map<String, String> = emptyMap()

    init {
        refresh()
    }

    fun onIntent(intent: RegexCastRuleIntent) {
        when (intent) {
            RegexCastRuleIntent.Refresh -> refresh()

            RegexCastRuleIntent.ShowCreate -> {
                _uiState.update { it.copy(isNew = true, editTarget = RegexCastRule()) }
                launchIo { loadCandidates(RegexCastRule.POOL_ROLE, "") }
            }

            is RegexCastRuleIntent.ShowEdit -> {
                _uiState.update { it.copy(isNew = false, editTarget = intent.rule) }
                launchIo { loadCandidates(intent.rule.poolKind, intent.rule.poolId) }
            }

            RegexCastRuleIntent.DismissEdit -> _uiState.update { it.copy(editTarget = null) }

            // 弹窗里换了「声音池选择」或「声音池」：下面那栏的候选跟着换。
            // 草稿整体留在弹窗本地，这里只刷候选，别回头去改 editTarget（会把没提交的编辑冲掉）。
            is RegexCastRuleIntent.PickPool -> launchIo {
                _uiState.update { it.copy(itemOptions = persistentListOf()) }
                loadCandidates(intent.kind, intent.poolId)
            }

            is RegexCastRuleIntent.Save -> launchIo {
                RegexCastRuleStore.save(intent.rule)
                refresh()
                _uiState.update { it.copy(editTarget = null) }
            }

            is RegexCastRuleIntent.Toggle -> launchIo {
                RegexCastRuleStore.setEnabled(intent.rule, intent.enabled)
                refresh()
            }

            is RegexCastRuleIntent.ShowDelete -> _uiState.update { it.copy(deleteTarget = intent.rule) }
            RegexCastRuleIntent.DismissDelete -> _uiState.update { it.copy(deleteTarget = null) }
            is RegexCastRuleIntent.Delete -> launchIo {
                RegexCastRuleStore.delete(intent.rule)
                refresh()
                _uiState.update { it.copy(deleteTarget = null) }
            }
        }
    }

    private fun refresh() = launchIo {
        val pools = VoicePoolStore.listPools() + BgmPoolStore.listPools()
        poolNames = pools.associate { it.id to it.name }
        itemNames = VoicePoolStore.allVoicePairs().toMap() + BgmPoolStore.allTrackPairs().toMap()
        val rows = ArrayList<RegexCastRow>()
        var lastGroup: String? = null
        RegexCastRuleStore.all().forEach { rule ->
            // 小节名只在这个分组的第一行显示一次
            val section = rule.group.takeIf { it != lastGroup }
            lastGroup = rule.group
            rows += RegexCastRow(rule, summaryOf(rule), section)
        }
        _uiState.update { it.copy(rows = rows.toImmutableList()) }
    }

    /** 弹窗的「声音池」「音色/配乐」两栏候选。 */
    private suspend fun loadCandidates(kind: String, poolId: String) {
        val role = kind != RegexCastRule.POOL_BGM
        val pools = if (role) VoicePoolStore.listPools() else BgmPoolStore.listPools()
        val members = if (poolId.isBlank()) {
            emptyList()
        } else if (role) {
            runCatching { VoicePoolStore.poolDetail(poolId).members }.getOrNull().orEmpty()
        } else {
            runCatching { BgmPoolStore.poolDetail(poolId).members }.getOrNull().orEmpty()
        }
        val random = CastOption("", context.getString(R.string.regex_cast_random))
        _uiState.update {
            it.copy(
                poolOptions = (listOf(CastOption("", context.getString(R.string.regex_cast_pick_pool))) +
                    pools.filter { p -> p.enabled }.map { p -> CastOption(p.id, p.name) })
                    .toImmutableList(),
                itemOptions = (listOf(random) + members.map { m -> CastOption(m.id, m.displayName) })
                    .toImmutableList(),
                groupOptions = RegexCastRuleStore.groups().map { CastOption(it, it) }.toImmutableList(),
            )
        }
    }

    /** 一行中文摘要：命中什么 → 变成什么。id 反查不到名字就原样显示，别留空白让人以为没存。 */
    private fun summaryOf(rule: RegexCastRule): String {
        val pool = poolNames[rule.poolId] ?: rule.poolId
        val item = rule.itemId.takeIf { it.isNotBlank() }?.let { itemNames[it] ?: it }
            ?: context.getString(R.string.regex_cast_random)
        val action = if (rule.poolKind == RegexCastRule.POOL_BGM) {
            context.getString(R.string.regex_cast_summary_sound, item, pool)
        } else {
            context.getString(R.string.regex_cast_summary_voice, item, pool)
        }
        return context.getString(R.string.regex_cast_summary, rule.pattern, action)
    }

    private fun launchIo(block: suspend () -> Unit) {
        execute { runCatching { block() } }
    }
}
