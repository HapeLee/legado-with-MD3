package io.legado.app.ui.book.knowledge

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class CharacterDetailUiState(
    val bookUrl: String = "",
    val characterId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val aliasesText: String = "",
    val avatarUri: String = "",
    val tags: ImmutableList<String> = persistentListOf(),
    val tagInput: String = "",
    val role: String = "",
    val voiceGender: String = "unknown",
    val voiceAgeBand: String = "unknown",
    val personality: String = "",
    val summary: String = "",
    val events: ImmutableList<CharacterEventUi> = persistentListOf(),
    val relations: ImmutableList<CharacterRelationUi> = persistentListOf(),
)

@Stable
data class CharacterEventUi(
    val id: String,
    val title: String,
    val content: String,
)

@Stable
data class CharacterRelationUi(
    val id: String,
    val fromName: String,
    val toName: String,
    val relationType: String,
    val summary: String,
    val attitude: String,
)

sealed interface CharacterDetailIntent {
    data class SetName(val value: String) : CharacterDetailIntent
    data class SetAliasesText(val value: String) : CharacterDetailIntent
    data class SetAvatarUri(val value: String) : CharacterDetailIntent
    data class SetTagInput(val value: String) : CharacterDetailIntent
    data class AddTag(val tag: String) : CharacterDetailIntent
    data class RemoveTag(val index: Int) : CharacterDetailIntent
    data class SetRole(val value: String) : CharacterDetailIntent
    data class SetVoiceGender(val value: String) : CharacterDetailIntent
    data class SetVoiceAgeBand(val value: String) : CharacterDetailIntent
    data class SetPersonality(val value: String) : CharacterDetailIntent
    data class SetSummary(val value: String) : CharacterDetailIntent
    data object Save : CharacterDetailIntent
    data class Delete(
        val deleteRelations: Boolean,
        val deleteEvents: Boolean,
    ) : CharacterDetailIntent
}

sealed interface CharacterDetailEffect {
    data class ShowToast(val message: String) : CharacterDetailEffect
    data object NavigateBack : CharacterDetailEffect
}

@Stable
data class CharacterNetworkUiState(
    val bookUrl: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val characters: ImmutableList<CharacterNodeUi> = persistentListOf(),
    val relations: ImmutableList<CharacterRelationEditorUi> = persistentListOf(),
    val newFromName: String = "",
    val newToName: String = "",
    val newRelationType: String = "",
    val newSummary: String = "",
    val newAttitude: String = "",
)

@Stable
data class CharacterNodeUi(
    val id: String,
    val name: String,
    val avatarUri: String?,
    val summary: String,
)

@Stable
data class CharacterRelationEditorUi(
    val id: String,
    val fromCharacterId: String,
    val toCharacterId: String,
    val fromName: String,
    val toName: String,
    val relationType: String,
    val summary: String,
    val attitude: String,
)

sealed interface CharacterNetworkIntent {
    data class OpenCharacter(val characterId: String) : CharacterNetworkIntent
    data class SetNewFromName(val value: String) : CharacterNetworkIntent
    data class SetNewToName(val value: String) : CharacterNetworkIntent
    data class SetNewRelationType(val value: String) : CharacterNetworkIntent
    data class SetNewSummary(val value: String) : CharacterNetworkIntent
    data class SetNewAttitude(val value: String) : CharacterNetworkIntent
    data class SetRelationType(val relationId: String, val value: String) : CharacterNetworkIntent
    data class SetRelationSummary(val relationId: String, val value: String) :
        CharacterNetworkIntent

    data class SetRelationAttitude(val relationId: String, val value: String) :
        CharacterNetworkIntent

    data class SaveRelation(val relationId: String) : CharacterNetworkIntent
    data class DeleteRelation(val relationId: String) : CharacterNetworkIntent
    data object AddRelation : CharacterNetworkIntent
}

sealed interface CharacterNetworkEffect {
    data class OpenCharacterDetail(val bookUrl: String, val characterId: String) :
        CharacterNetworkEffect

    data class ShowToast(val message: String) : CharacterNetworkEffect
}
