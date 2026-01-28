package io.legado.app.ui.widget.components.rules

interface RuleActionState<T> {
    val items: List<T>
    val selectedIds: Set<Any>
    val searchKey: String
    val isSearch: Boolean
    val isUploading: Boolean
}

data class InteractionState(
    val isSearchMode: Boolean = false,
    val isUploading: Boolean = false,
    val isLoading: Boolean = false
)

interface SelectableItem<T> {
    val id: T
}