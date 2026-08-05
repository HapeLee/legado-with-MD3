package io.legado.app.ui.book.readaloud.casting

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class BookVoiceCastingUiState(
    val bookUrl: String,
    val isLoading: Boolean = true,
    val items: ImmutableList<VoiceCastingItemUi> = persistentListOf(),
    val voices: ImmutableList<VoiceOptionUi> = persistentListOf(),
    val picker: VoicePickerUi? = null,
)

@Stable
data class VoiceCastingItemUi(
    val subjectType: String,
    val subjectId: String,
    val kind: CastingSubjectKind,
    val name: String,
    val description: String = "",
    val avatarUri: String? = null,
    val hasBinding: Boolean = false,
    val voiceName: String = "",
    val voiceAvailable: Boolean = false,
)

@Stable
data class VoiceOptionUi(
    val id: String,
    val name: String,
    val engineType: String,
    val engineName: String,
    val selectable: Boolean,
)

@Stable
data class VoicePickerUi(
    val subjectType: String,
    val subjectId: String,
    val kind: CastingSubjectKind,
    val name: String,
    val selectedVoiceId: String?,
)

enum class CastingSubjectKind {
    Narrator,
    UnknownMale,
    UnknownFemale,
    Unknown,
    Character,
}

sealed interface BookVoiceCastingIntent {
    data object Refresh : BookVoiceCastingIntent
    data class OpenVoicePicker(val subjectType: String, val subjectId: String) :
        BookVoiceCastingIntent
    data object DismissVoicePicker : BookVoiceCastingIntent
    data class AssignVoice(val voiceId: String) : BookVoiceCastingIntent
    data object ClearBinding : BookVoiceCastingIntent
}

sealed interface BookVoiceCastingEffect {
    data class ShowToast(val message: String) : BookVoiceCastingEffect
}
