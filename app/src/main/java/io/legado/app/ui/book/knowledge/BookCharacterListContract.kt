package io.legado.app.ui.book.knowledge

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class CharacterListUiState(
    val bookUrl: String = "",
    val isLoading: Boolean = false,
    val roleFilter: String = "",
    val characters: ImmutableList<CharacterListItemUi> = persistentListOf(),
)

@Stable
data class CharacterListItemUi(
    val id: String,
    val name: String,
    val avatarUri: String?,
    val role: String,
    val summary: String,
    val tags: String,
)

sealed interface CharacterListIntent {
    data class SetRoleFilter(val role: String) : CharacterListIntent
    data class OpenCharacter(val characterId: String) : CharacterListIntent
    data object AddCharacter : CharacterListIntent
}

sealed interface CharacterListEffect {
    data class OpenCharacterDetail(val bookUrl: String, val characterId: String?) :
        CharacterListEffect

    data class ShowToast(val message: String) : CharacterListEffect
}
