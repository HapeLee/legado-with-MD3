package io.legado.app.ui.dict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.DictRuleRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
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
                    pages = dictRules.map { DictPageUiState() }.toImmutableList(),
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
        if (index !in dictRules.indices || index !in state.pages.indices) return
        val page = state.pages[index]
        if (page.isLoading) {
            _uiState.update { it.copy(selectedIndex = index) }
            return
        }
        if (page.htmlContent.isNotBlank() || page.emptyReason == DictEmptyReason.NoResult) {
            _uiState.update { it.copy(selectedIndex = index) }
            return
        }
        searchRule(index = index, word = state.word)
    }

    private fun searchRule(index: Int, word: String) {
        dictJob?.cancel()
        val rule = dictRules.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                selectedIndex = index,
                emptyReason = null,
            ).updatePage(index) { page ->
                page.copy(
                    isLoading = true,
                    htmlContent = "",
                    emptyReason = null,
                )
            }
        }
        dictJob = viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    rule.search(word)
                }
            } catch (error: CancellationException) {
                _uiState.update { state ->
                    if (state.word == word && dictRules.getOrNull(index) === rule) {
                        state.updatePage(index) { page ->
                            if (page.isLoading) {
                                page.copy(isLoading = false)
                            } else {
                                page
                            }
                        }
                    } else {
                        state
                    }
                }
                throw error
            } catch (error: Throwable) {
                error.localizedMessage ?: "ERROR"
            }
            _uiState.update {
                if (it.word != word || dictRules.getOrNull(index) !== rule) {
                    return@update it
                }
                if (result.isBlank()) {
                    it.updatePage(index) { page ->
                        page.copy(
                            isLoading = false,
                            htmlContent = "",
                            emptyReason = DictEmptyReason.NoResult,
                        )
                    }
                } else {
                    it.updatePage(index) { page ->
                        page.copy(
                            isLoading = false,
                            htmlContent = result,
                            emptyReason = null,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        dictJob?.cancel()
        super.onCleared()
    }
}

private fun DictUiState.updatePage(
    index: Int,
    transform: (DictPageUiState) -> DictPageUiState,
): DictUiState {
    if (index !in pages.indices) return this
    return copy(
        pages = pages.mapIndexed { pageIndex, page ->
            if (pageIndex == index) transform(page) else page
        }.toImmutableList()
    )
}
