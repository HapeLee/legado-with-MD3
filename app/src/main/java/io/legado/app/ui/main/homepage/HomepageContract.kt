package io.legado.app.ui.main.homepage

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.model.HomepageModuleType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class HomepageUiState(
    val modules: ImmutableList<HomepageModuleUi> = persistentListOf(),
    val isManageMode: Boolean = false,
    val isConfigMode: Boolean = false,
    val isRefreshing: Boolean = false,
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
