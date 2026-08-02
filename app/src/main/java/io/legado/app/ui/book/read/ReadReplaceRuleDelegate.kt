package io.legado.app.ui.book.read

import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.ReplaceRuleRepository
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 净化规则域（R2.2 续批）。
 *
 * 管阅读页内嵌的净化规则列表（TextProcessingSheet）：全量订阅、开关、拖拽排序、
 * 停用生效中的规则，以及本书「是否启用净化」的切换。规则列表仍住在
 * `ReadBookUiState.allReplaceRules`（sheet 直读，搬出去要改 UI 入参），
 * 故本 delegate **无自持状态**，一切读写经 [Host]。
 */
class ReadReplaceRuleDelegate(
    private val scope: CoroutineScope,
    private val replaceRuleRepository: ReplaceRuleRepository,
    private val host: Host,
) {

    interface Host {
        fun updateAllReplaceRules(rules: List<ReplaceRuleItemUi>)

        /** 本书「是否启用净化」切换后同步 UiState 的开关字段。 */
        fun updateUseReplaceRule(enabled: Boolean)
    }

    /** 订阅全量规则表，刷进 UiState。VM 构造时调一次。 */
    fun start() {
        scope.launch {
            replaceRuleRepository.flowAll().collect { rules ->
                host.updateAllReplaceRules(
                    rules.map { rule ->
                        ReplaceRuleItemUi(
                            id = rule.id,
                            name = rule.name,
                            group = rule.group,
                            pattern = rule.pattern,
                            replacement = rule.replacement,
                            enabled = rule.isEnabled,
                        )
                    }
                )
            }
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        scope.launch {
            replaceRuleRepository.setEnabled(id, enabled)
            rulesChanged()
        }
    }

    fun move(draggedId: Long, anchorId: Long, afterAnchor: Boolean) {
        scope.launch {
            replaceRuleRepository.moveReplaceRule(draggedId, anchorId, afterAnchor)
            rulesChanged()
        }
    }

    /** 「停用这条生效中的规则」：落一条禁用副本。 */
    fun disable(rule: ReplaceRule) {
        scope.launch {
            replaceRuleRepository.insert(rule.copy(isEnabled = false))
        }
    }

    /** 切换本书是否启用净化。 */
    fun changeUseReplaceRule(enabled: Boolean) {
        ReadBook.book?.let {
            it.setUseReplaceRule(enabled)
            ReadBook.saveRead()
            host.updateUseReplaceRule(enabled)
            rulesChanged()
        }
    }

    /** 规则集变化后让当前书重建净化管线并重载正文。外部规则编辑器返回时也会调。 */
    fun rulesChanged() {
        Coroutine.async(scope, Dispatchers.IO) {
            ReadBook.book?.let {
                ContentProcessor.get(it.name, it.origin).upReplaceRules()
                ReadBook.loadContent(resetPageOffset = false)
            }
        }
    }
}
