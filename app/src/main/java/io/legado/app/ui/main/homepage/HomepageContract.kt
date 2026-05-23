package io.legado.app.ui.main.homepage

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class HomepageUiState(
    val modules: ImmutableList<HomepageModuleUi> = persistentListOf(),
    val isManageMode: Boolean = false,
    val isConfigMode: Boolean = false,
    val isRefreshing: Boolean = false,
    val manageState: HomepageManageUiState = HomepageManageUiState()
)

@Stable
data class HomepageManageUiState(
    val sets: ImmutableList<HomepageSourceManageUi> = persistentListOf(),
    val browseSources: ImmutableList<HomepageSourceManageUi> = persistentListOf(),
    val allJoinedModules: ImmutableList<HomepageModuleManageUi> = persistentListOf(),
    val sourceNames: Map<String, String> = emptyMap()
)

@Stable
data class HomepageManageActions(
    val onToggleSet: (String, Boolean) -> Unit = { _, _ -> },
    val onGetSourceModules: (String, String?) -> List<HomepageModuleManageUi> = { _, _ -> emptyList() },
    val onSyncSourceModules: (String) -> Unit = {},
    val onToggleModule: (String, Boolean) -> Unit = { _, _ -> },
    val onJoinModule: (String, String?, ModuleDef) -> Unit = { _, _, _ -> },
    val onAddCustomModule: (String, String?, ModuleDef) -> Unit = { _, _, _ -> },
    val onAddButtonGroupFromKinds: (String, String?, String, List<String>) -> Unit = { _, _, _, _ -> },
    val onGetExploreKinds: (String) -> List<Pair<String, String>> = { emptyList() },
    val onUpdateModule: (String, ModuleDef) -> Unit = { _, _ -> },
    val onDeleteModule: (String) -> Unit = {},
    val onReorderModules: (List<String>) -> Unit = {},
    val onReorderSets: (List<String>) -> Unit = {},
    val onSetCustomSetTitle: (String, String?) -> Unit = { _, _ -> },
    val onCreateCustomSet: (String) -> Unit = {},
    val onRenameCustomSet: (String, String) -> Unit = { _, _ -> },
    val onDeleteCustomSet: (String) -> Unit = {},
    val onAssignModuleToCustomSet: (String, String?) -> Unit = { _, _ -> },
)

@Stable
data class HomepageSourceManageUi(
    val sourceUrl: String,
    val sourceName: String,
    val sourceGroup: String?,
    val isSelected: Boolean = false,
    val moduleCount: Int = 0,
    val isCustomSet: Boolean = false,
)

@Stable
data class HomepageModuleManageUi(
    val id: String,
    val sourceUrl: String,
    val moduleKey: String,
    val title: String,
    val customSetTitle: String? = null,
    val customSetId: String? = null,
    val isVisible: Boolean,
    val type: String = "card",
    val url: String? = null,
    val args: String? = null,
    val layoutConfig: String? = null,
    val originalTitle: String = "",
)

@Stable
data class HomepageModuleUi(
    val sourceUrl: String,
    val setName: String,
    val globalId: String,
    val type: HomepageModuleType,
    val title: String,
    val exploreUrl: String? = null,
    val customSetId: String? = null,
    val layoutConfig: String? = null,
    val state: ModuleLoadState = ModuleLoadState.Loading,
    val config: Map<String, String> = emptyMap()
)

@Stable
sealed interface ModuleLoadState {
    @Stable
    data object Loading : ModuleLoadState

    @Stable
    data class Loaded(
        val books: ImmutableList<SearchBook>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val page: Int = 1
    ) : ModuleLoadState

    @Stable
    data class Buttons(val kinds: ImmutableList<ExploreKind>) : ModuleLoadState

    @Stable
    data class Error(val message: String) : ModuleLoadState
}
