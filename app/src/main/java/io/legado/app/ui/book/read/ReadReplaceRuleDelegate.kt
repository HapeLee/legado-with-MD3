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
        // 规则表也可能被阅读页之外的入口改（主界面「替换净化」页、规则编辑器、导入、
        // Web API）。仓储写入后已重建 ContentProcessor 的规则缓存，这里只负责让当前
        // 正在读的书重新走一遍正文处理，否则页面会一直停在按旧规则算出的文本上。
        scope.launch {
            replaceRuleRepository.ruleChanges.collect {
                Coroutine.async(scope, Dispatchers.IO) {
                    ReadBook.book?.let { ReadBook.loadContent(resetPageOffset = false) }
                }
            }
        }
    }

    // 下面两个方法不再显式调 rulesChanged()：仓储写入时已经重建净化缓存并发出
    // ruleChanges，start() 的订阅会完成正文重载（避免同一动作重排两次）。
    fun setEnabled(id: Long, enabled: Boolean) {
        scope.launch {
            replaceRuleRepository.setEnabled(id, enabled)
        }
    }

    fun move(draggedId: Long, anchorId: Long, afterAnchor: Boolean) {
        scope.launch {
            replaceRuleRepository.moveReplaceRule(draggedId, anchorId, afterAnchor)
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
