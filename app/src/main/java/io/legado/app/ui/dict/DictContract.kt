package io.legado.app.ui.dict

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class DictUiState(
    val word: String = "",
    val isLoading: Boolean = false,
    val rules: ImmutableList<DictRuleUi> = persistentListOf(),
    val selectedIndex: Int = 0,
    val htmlContent: String = "",
    val emptyReason: DictEmptyReason? = null,
)

@Stable
data class DictRuleUi(
    val name: String,
)

sealed interface DictEmptyReason {
    data object BlankWord : DictEmptyReason
    data object NoRules : DictEmptyReason
    data object NoResult : DictEmptyReason
}

sealed interface DictIntent {
    data class Load(val word: String) : DictIntent
    data class SelectRule(val index: Int) : DictIntent
}

sealed interface DictEffect {
    data class ShowToast(val message: String) : DictEffect
}
