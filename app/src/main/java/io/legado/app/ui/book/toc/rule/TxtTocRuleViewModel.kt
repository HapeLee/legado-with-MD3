package io.legado.app.ui.book.toc.rule

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.repository.TxtTocRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.rules.InteractionState
import io.legado.app.ui.widget.components.rules.RuleActionState
import io.legado.app.ui.widget.components.rules.SelectableItem
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Immutable
data class TxtTocRuleItemUi(
    override val id: Long,
    val name: String,
    val isEnabled: Boolean,
    val rule: TxtTocRule,
    val example: String = ""
) : SelectableItem<Long>

data class TxtTocRuleUiState(
    override val items: List<TxtTocRuleItemUi> = emptyList(),
    override val selectedIds: Set<Long> = emptySet(),
    override val searchKey: String = "",
    val interaction: InteractionState = InteractionState()
) : RuleActionState<TxtTocRuleItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isUploading: Boolean get() = interaction.isUploading
}


class TxtTocRuleViewModel(
    application: Application,
    uploadRepository: UploadRepository
) : BaseRuleViewModel<TxtTocRuleItemUi, TxtTocRule, Long, TxtTocRuleUiState>(
    application,
    TxtTocRuleUiState(interaction = InteractionState(isLoading = true)),
    uploadRepository
) {
    private val repository = TxtTocRuleRepository()

    override val rawDataFlow: Flow<List<TxtTocRule>> = repository.flowAll()

    override fun TxtTocRule.toUiItem() =
        TxtTocRuleItemUi(id, name, enable, this, example = example ?: "")

    override fun filterData(data: List<TxtTocRule>, key: String): List<TxtTocRule> {
        val filtered = if (key.isEmpty()) data
        else data.filter { it.name.contains(key, ignoreCase = true) }
        return filtered.sortedBy { it.serialNumber }
    }

    override fun composeUiState(
        items: List<TxtTocRuleItemUi>,
        selectedIds: Set<Long>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<TxtTocRule>
    ): TxtTocRuleUiState {
        return TxtTocRuleUiState(
            items = items,
            selectedIds = selectedIds,
            searchKey = _searchKey.value,
            interaction = InteractionState(
                isSearchMode = isSearch,
                isUploading = isUploading || (importState is BaseImportUiState.Loading),
                isLoading = false
            )
        )
    }

    fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch {
            repository.saveOrder(currentLocal.map { it.rule })
            _localItems.value = null
        }
    }

    fun save(rule: TxtTocRule) {
        viewModelScope.launch {
            if (rule.id == 0L) {
                repository.insert(rule)
            } else {
                repository.update(rule)
            }
        }
    }

    fun update(vararg rules: TxtTocRule) = viewModelScope.launch { repository.update(*rules) }
    fun insert(vararg rules: TxtTocRule) = viewModelScope.launch { repository.insert(*rules) }
    fun delete(vararg rules: TxtTocRule) = viewModelScope.launch { repository.delete(*rules) }
    fun enableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.enableByIds(ids, true) }

    fun disableSelectionByIds(ids: Set<Long>) =
        viewModelScope.launch { repository.enableByIds(ids, false) }

    fun delSelectionByIds(ids: Set<Long>) = viewModelScope.launch { repository.deleteByIds(ids) }

    override suspend fun generateJson(entities: List<TxtTocRule>): String = GSON.toJson(entities)

    override fun parseImportRules(text: String): List<TxtTocRule> {
        return when {
            text.isJsonArray() -> GSON.fromJsonArray<TxtTocRule>(text).getOrThrow()
            text.isJsonObject() -> listOf(GSON.fromJsonObject<TxtTocRule>(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: TxtTocRule, oldRule: TxtTocRule): Boolean {
        return newRule.name != oldRule.name || newRule.rule != oldRule.rule || newRule.enable != oldRule.enable
    }

    override suspend fun findOldRule(newRule: TxtTocRule): TxtTocRule? {
        return null
    }

    override fun ruleItemToEntity(item: TxtTocRuleItemUi): TxtTocRule = item.rule

    override fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<TxtTocRule> ?: return
        viewModelScope.launch {
            val rulesToSave = state.items.filter { it.isSelected }.map { it.data }
            repository.insert(*rulesToSave.toTypedArray())
            _importState.value = BaseImportUiState.Idle
        }
    }

    fun copyRule(rule: TxtTocRule) {
        context.sendToClip(GSON.toJson(rule))
    }

    fun pasteRule(): TxtTocRule? {
        val text = context.getClipText()
        if (text.isNullOrBlank()) {
            context.toastOnUi("剪贴板没有内容")
            return null
        }
        return try {
            GSON.fromJsonObject<TxtTocRule>(text).getOrThrow()
        } catch (e: Exception) {
            context.toastOnUi("格式不对")
            null
        }
    }
}