package io.legado.app.ui.dict.rule

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseRuleViewModel
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.DictRuleRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class DictRuleItemUi(
    override val id: String,
    val urlRule: String,
    val showRule: String,
    val isEnabled: Boolean,
    val rule: DictRule
) : SelectableItem<String>

data class DictRuleUiState(
    override val items: List<DictRuleItemUi> = emptyList(),
    override val selectedIds: Set<String> = emptySet(),
    override val searchKey: String = "",
    val interaction: InteractionState = InteractionState()
) : RuleActionState<DictRuleItemUi> {
    override val isSearch: Boolean get() = interaction.isSearchMode
    override val isUploading: Boolean get() = interaction.isUploading
}

class DictRuleViewModel(
    application: Application,
    uploadRepository: UploadRepository
) : BaseRuleViewModel<DictRuleItemUi, DictRule, String, DictRuleUiState>(
    application,
    DictRuleUiState(interaction = InteractionState(isLoading = true)),
    uploadRepository
) {
    private val repository = DictRuleRepository()

    override val rawDataFlow: Flow<List<DictRule>> = repository.flowAll()

    override fun filterData(data: List<DictRule>, key: String): List<DictRule> {
        val filtered = if (key.isEmpty()) data
        else data.filter { it.name.contains(key, ignoreCase = true) }
        return filtered.sortedBy { it.sortNumber }
    }

    override fun composeUiState(
        items: List<DictRuleItemUi>,
        selectedIds: Set<String>,
        isSearch: Boolean,
        isUploading: Boolean,
        importState: BaseImportUiState<DictRule>
    ): DictRuleUiState {
        return DictRuleUiState(
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

    override fun DictRule.toUiItem() = DictRuleItemUi(name, urlRule, showRule, enabled, this)
    override fun ruleItemToEntity(item: DictRuleItemUi): DictRule = item.rule

    override suspend fun generateJson(entities: List<DictRule>): String = GSON.toJson(entities)

    override fun parseImportRules(text: String): List<DictRule> {
        return when {
            text.isJsonArray() -> GSON.fromJsonArray<DictRule>(text).getOrThrow()
            text.isJsonObject() -> listOf(GSON.fromJsonObject<DictRule>(text).getOrThrow())
            else -> throw Exception("格式不正确")
        }
    }

    override fun hasChanged(newRule: DictRule, oldRule: DictRule): Boolean {
        return newRule.name != oldRule.name
                || newRule.urlRule != oldRule.urlRule
                || newRule.showRule != oldRule.showRule
                || newRule.enabled != oldRule.enabled
    }

    override suspend fun findOldRule(newRule: DictRule): DictRule? {
        return repository.findById(newRule.name)
    }

    override fun saveImportedRules() {
        val state = _importState.value as? BaseImportUiState.Success<DictRule> ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val rulesToSave = state.items
                .filter { it.isSelected }
                .map { it.data }
            repository.insert(*rulesToSave.toTypedArray())
            withContext(Dispatchers.Main) {
                _importState.value = BaseImportUiState.Idle
            }
        }
    }

    fun saveSortOrder() {
        val currentLocal = _localItems.value ?: return
        viewModelScope.launch {
            repository.moveOrder(currentLocal.map { it.rule })
            _localItems.value = null
        }
    }

    fun enableSelectionByIds(ids: Set<String>) {
        viewModelScope.launch { repository.enableByIds(ids) }
    }

    fun disableSelectionByIds(ids: Set<String>) {
        viewModelScope.launch { repository.disableByIds(ids) }
    }

    fun delSelectionByIds(ids: Set<String>) {
        viewModelScope.launch {
            repository.deleteByIds(ids)
            _selectedIds.update { it - ids }
        }
    }

    fun update(vararg rule: DictRule) = viewModelScope.launch { repository.update(*rule) }
    fun insert(vararg rule: DictRule) = viewModelScope.launch { repository.insert(*rule) }
    fun delete(vararg dictRule: DictRule) = viewModelScope.launch { repository.delete(*dictRule) }

    fun copyRule(dictRule: DictRule) {
        context.sendToClip(GSON.toJson(dictRule))
    }

    fun pasteRule(): DictRule? {
        val text = context.getClipText()
        if (text.isNullOrBlank()) {
            context.toastOnUi("剪贴板没有内容")
            return null
        }
        return try {
            GSON.fromJsonObject<DictRule>(text).getOrThrow()
        } catch (e: Exception) {
            context.toastOnUi("格式不对")
            null
        }
    }
}