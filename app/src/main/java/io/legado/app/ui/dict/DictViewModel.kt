package io.legado.app.ui.dict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.DictRuleRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictViewModel(
    private val dictRuleRepository: DictRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DictUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DictEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var dictRules: List<DictRule> = emptyList()
    private var dictJob: Job? = null

    fun onIntent(intent: DictIntent) {
        when (intent) {
            is DictIntent.Load -> load(intent.word)
            is DictIntent.SelectRule -> selectRule(intent.index)
        }
    }

    private fun load(word: String) {
        dictJob?.cancel()
        val query = word.trim()
        _uiState.value = DictUiState(
            word = query,
            isLoading = query.isNotBlank(),
            emptyReason = if (query.isBlank()) DictEmptyReason.BlankWord else null,
        )
        if (query.isBlank()) return

        viewModelScope.launch {
            dictRules = withContext(Dispatchers.IO) {
                dictRuleRepository.getEnabled()
            }
            _uiState.update { state ->
                state.copy(
                    rules = dictRules.map { DictRuleUi(it.name) }.toImmutableList(),
                    isLoading = dictRules.isNotEmpty(),
                    emptyReason = if (dictRules.isEmpty()) DictEmptyReason.NoRules else null,
                )
            }
            if (dictRules.isNotEmpty()) {
                searchRule(index = 0, word = query)
            }
        }
    }

    private fun selectRule(index: Int) {
        val state = _uiState.value
        if (index !in dictRules.indices || state.selectedIndex == index && state.isLoading) return
        searchRule(index = index, word = state.word)
    }

    private fun searchRule(index: Int, word: String) {
        dictJob?.cancel()
        val rule = dictRules.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                selectedIndex = index,
                isLoading = true,
                htmlContent = "",
                emptyReason = null,
            )
        }
        dictJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    rule.search(word)
                }
            }.getOrElse { error ->
                error.localizedMessage ?: "ERROR"
            }
            _uiState.update {
                if (result.isBlank()) {
                    it.copy(
                        isLoading = false,
                        htmlContent = "",
                        emptyReason = DictEmptyReason.NoResult,
                    )
                } else {
                    it.copy(
                        isLoading = false,
                        htmlContent = result,
                        emptyReason = null,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        dictJob?.cancel()
        super.onCleared()
    }
}
