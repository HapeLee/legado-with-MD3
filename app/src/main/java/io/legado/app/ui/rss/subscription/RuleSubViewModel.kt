package io.legado.app.ui.rss.subscription

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.repository.RuleSubscriptionRepository
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RuleSubViewModel(
    application: Application,
    private val repository: RuleSubscriptionRepository,
) : ViewModel() {
    private val app = application
    private val searchKey = MutableStateFlow("")
    private val isSearch = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val editingRule = MutableStateFlow<RuleSub?>(null)
    private val _effects = MutableSharedFlow<RuleSubEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    val uiState = combine(
        searchKey,
        isSearch,
        selectedIds,
        editingRule,
        repository.observeAll(),
    ) { query, searching, selected, editor, items ->
        val filtered = if (searching && query.isNotBlank()) {
            items.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.url.contains(query, ignoreCase = true)
            }
        } else items
        RuleSubUiState(
            items = filtered.toImmutableList(),
            selectedIds = selected.toImmutableSet(),
            searchKey = query,
            isSearch = searching,
            editingRule = editor,
        )
    }.flowOn(IO).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RuleSubUiState(),
    )

    fun onIntent(intent: RuleSubIntent) {
        when (intent) {
            is RuleSubIntent.ToggleSearch -> {
                isSearch.value = intent.enabled
                if (!intent.enabled) searchKey.value = ""
            }
            is RuleSubIntent.Search -> searchKey.value = intent.query
            is RuleSubIntent.ToggleSelection -> selectedIds.update {
                if (intent.rule.id in it) it - intent.rule.id else it + intent.rule.id
            }
            RuleSubIntent.SelectAll -> selectedIds.value = uiState.value.items.map { it.id }.toSet()
            RuleSubIntent.InvertSelection -> {
                val all = uiState.value.items.map { it.id }.toSet()
                selectedIds.update { all - it }
            }
            RuleSubIntent.ClearSelection -> selectedIds.value = emptySet()
            RuleSubIntent.DeleteSelected -> viewModelScope.launch(IO) {
                val selected = selectedIds.value
                uiState.value.items.filter { it.id in selected }.forEach { repository.delete(it) }
                selectedIds.value = emptySet()
            }
            is RuleSubIntent.Delete -> viewModelScope.launch(IO) { repository.delete(intent.rule) }
            RuleSubIntent.ResetOrder -> viewModelScope.launch(IO) {
                val rules = repository.all()
                rules.forEachIndexed { index, rule -> rule.customOrder = index + 1 }
                repository.update(*rules.toTypedArray())
            }
            is RuleSubIntent.Edit -> editingRule.value = intent.rule
            RuleSubIntent.Add -> editingRule.value = RuleSub(customOrder = uiState.value.items.size + 1)
            RuleSubIntent.DismissEditor -> editingRule.value = null
            is RuleSubIntent.Save -> save(intent.rule)
            is RuleSubIntent.Open -> _effects.tryEmit(RuleSubEffect.OpenRule(intent.rule))
        }
    }

    private fun save(rule: RuleSub) {
        viewModelScope.launch {
            val existing = kotlinx.coroutines.withContext(IO) { repository.findByUrl(rule.url) }
            if (existing != null && existing.id != rule.id) {
                _effects.tryEmit(
                    RuleSubEffect.ShowMessage(
                        "${app.getString(R.string.url_already)}(${existing.name})"
                    )
                )
                return@launch
            }
            kotlinx.coroutines.withContext(IO) { repository.insert(rule) }
            editingRule.value = null
        }
    }
}

@Stable
data class RuleSubUiState(
    override val items: ImmutableList<RuleSub> = persistentListOf(),
    override val selectedIds: ImmutableSet<Long> = persistentSetOf(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val editingRule: RuleSub? = null,
) : ListUiState<RuleSub>

sealed interface RuleSubIntent {
    data class ToggleSearch(val enabled: Boolean) : RuleSubIntent
    data class Search(val query: String) : RuleSubIntent
    data class ToggleSelection(val rule: RuleSub) : RuleSubIntent
    data object SelectAll : RuleSubIntent
    data object InvertSelection : RuleSubIntent
    data object ClearSelection : RuleSubIntent
    data object DeleteSelected : RuleSubIntent
    data class Delete(val rule: RuleSub) : RuleSubIntent
    data object ResetOrder : RuleSubIntent
    data object Add : RuleSubIntent
    data class Edit(val rule: RuleSub) : RuleSubIntent
    data object DismissEditor : RuleSubIntent
    data class Save(val rule: RuleSub) : RuleSubIntent
    data class Open(val rule: RuleSub) : RuleSubIntent
}

sealed interface RuleSubEffect {
    data class OpenRule(val rule: RuleSub) : RuleSubEffect
    data class ShowMessage(val message: String) : RuleSubEffect
}
