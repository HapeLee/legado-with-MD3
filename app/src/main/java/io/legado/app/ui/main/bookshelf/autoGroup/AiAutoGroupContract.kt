package io.legado.app.ui.main.bookshelf.autoGroup

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class AiAutoGroupPhase {
    LoadingSource,
    Preflight,
    Analyzing,
    Reviewing,
    Revising,
    Applying,
    Result,
    Error,
}

@Stable
data class AiAutoGroupUiState(
    val phase: AiAutoGroupPhase = AiAutoGroupPhase.Preflight,
    val bookCount: Int = 0,
    val groupedBookCount: Int = 0,
    val existingGroupCount: Int = 0,
    val effectiveInputCharLimit: Int = 0,
    val estimatedRequestCount: Int = 0,
    val currentBatch: Int = 0,
    val totalBatches: Int = 0,
    val groupingInstruction: String = "",
    val incrementalOnly: Boolean = true,
    val includeBookIntro: Boolean = false,
    val enableDeepThinking: Boolean = false,
    val groups: ImmutableList<AiAutoGroupGroupUi> = persistentListOf(),
    val ignoredBooks: ImmutableList<AiAutoGroupIgnoredBookUi> = persistentListOf(),
    val revisionInstruction: String = "",
    val applyResult: AiAutoGroupApplyResultUi? = null,
    val error: AiAutoGroupErrorUi? = null,
    val showApplyConfirm: Boolean = false,
) {
    val assignedBookCount: Int get() = groups.sumOf { it.books.size }
    val newGroupCount: Int get() = groups.count { !it.reuseExisting }
}

@Stable
data class AiAutoGroupApplyResultUi(
    val createdGroupCount: Int,
    val reusedGroupCount: Int,
    val updatedBookCount: Int,
    val ignoredBookCount: Int,
)

sealed interface AiAutoGroupErrorUi {
    data object EmptyBookshelf : AiAutoGroupErrorUi
    data object MissingModel : AiAutoGroupErrorUi
    data object CapacityTooSmall : AiAutoGroupErrorUi
    data object GroupCapacityExceeded : AiAutoGroupErrorUi
    data object InvalidResponse : AiAutoGroupErrorUi
    data class Unexpected(val detail: String?) : AiAutoGroupErrorUi
}

enum class AiAutoGroupMessage {
    EnterRevisionInstruction,
    NoApplicablePlan,
    Cancelled,
    GroupNameRequired,
}

@Stable
data class AiAutoGroupGroupUi(
    val key: String,
    val name: String,
    val description: String,
    val reuseExisting: Boolean,
    val books: ImmutableList<AiAutoGroupBookUi> = persistentListOf(),
)

@Stable
data class AiAutoGroupBookUi(
    val bookUrl: String,
    val name: String,
    val author: String,
    val currentGroupNames: ImmutableList<String> = persistentListOf(),
    val reason: String,
)

@Stable
data class AiAutoGroupIgnoredBookUi(
    val bookUrl: String,
    val name: String,
    val author: String,
    val reason: String,
)

sealed interface AiAutoGroupIntent {
    data class StartSession(val sessionKey: Long) : AiAutoGroupIntent
    data object CloseSession : AiAutoGroupIntent
    data object Analyze : AiAutoGroupIntent
    data object DismissApplyConfirm : AiAutoGroupIntent
    data object RequestApply : AiAutoGroupIntent
    data object ConfirmApply : AiAutoGroupIntent
    data object Restart : AiAutoGroupIntent
    data object CancelRunning : AiAutoGroupIntent
    data class RenameGroup(val groupKey: String, val name: String) : AiAutoGroupIntent
    data class RemoveGroup(val groupKey: String) : AiAutoGroupIntent
    data class MoveBook(val bookUrl: String, val targetGroupKey: String) : AiAutoGroupIntent
    data class IgnoreBook(val bookUrl: String) : AiAutoGroupIntent
    data class AddGroup(val name: String) : AiAutoGroupIntent
    data class UpdateGroupingInstruction(val instruction: String) : AiAutoGroupIntent
    data class SetIncrementalOnly(val enabled: Boolean) : AiAutoGroupIntent
    data class SetIncludeBookIntro(val enabled: Boolean) : AiAutoGroupIntent
    data class SetDeepThinkingEnabled(val enabled: Boolean) : AiAutoGroupIntent
    data class UpdateRevisionInstruction(val instruction: String) : AiAutoGroupIntent
    data object Revise : AiAutoGroupIntent
}

sealed interface AiAutoGroupEffect {
    data class ShowMessage(val message: AiAutoGroupMessage) : AiAutoGroupEffect
    data class ShowError(val error: AiAutoGroupErrorUi) : AiAutoGroupEffect
    data object Applied : AiAutoGroupEffect
}
