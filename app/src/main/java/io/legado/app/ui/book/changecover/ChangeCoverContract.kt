package io.legado.app.ui.book.changecover

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.BookSearchScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** 封面换源网格的一个候选项；[albumPath] 非空表示它是本地图集图片，选中后需要固定副本。 */
@Stable
data class CoverOption(
    val key: String,
    val label: String,
    val coverUrl: String,
    val origin: String = "",
    val originOrder: Int = 0,
    val albumPath: String? = null,
    val isDefault: Boolean = false,
)

@Stable
data class CoverAlbumOption(
    val id: String,
    val name: String,
)

@Stable
data class CoverScopeUiState(
    val isAll: Boolean = true,
    val isSource: Boolean = false,
    val displayNames: ImmutableList<String> = persistentListOf(),
    val sourceUrls: ImmutableList<String> = persistentListOf(),
)

@Stable
data class ChangeCoverUiState(
    val options: ImmutableList<CoverOption> = persistentListOf(),
    val albums: ImmutableList<CoverAlbumOption> = persistentListOf(),
    /** 本地来源 tab：0 = 默认封面，1..albums.size = 对应封面图集。 */
    val selectedTabIndex: Int = 0,
    val isSearching: Boolean = false,
    val scope: CoverScopeUiState = CoverScopeUiState(),
    /** 选中结果（含本地图集图的固定副本路径），UI 消费后回 [ChangeCoverIntent.SelectionConsumed]。 */
    val pendingSelection: String? = null,
)

sealed interface ChangeCoverIntent {
    data class Shown(val name: String, val author: String, val isDark: Boolean) : ChangeCoverIntent
    data object Hidden : ChangeCoverIntent
    data object ToggleSearch : ChangeCoverIntent
    data class ApplyScope(
        val groupNames: List<String>,
        val sources: List<BookSearchScope.ScopeSourceItem>,
        val isSourceScope: Boolean,
    ) : ChangeCoverIntent

    data class SelectTab(val index: Int) : ChangeCoverIntent
    data class SelectOption(val option: CoverOption) : ChangeCoverIntent
    data object SelectionConsumed : ChangeCoverIntent
}

/**
 * 组合候选项：默认占位与本地图集项不受书源范围影响；在线项按范围过滤、按书源顺序排序，
 * 并按封面地址去重（多个书源常返回同一张图）。
 */
internal fun buildCoverOptions(
    default: CoverOption,
    album: List<CoverOption>,
    online: List<CoverOption>,
    allowedOrigins: Set<String>?,
): List<CoverOption> = buildList {
    add(default)
    addAll(album)
    addAll(
        online
            .filter { allowedOrigins == null || it.origin in allowedOrigins }
            .filter { it.coverUrl.isNotBlank() }
            .sortedBy { it.originOrder }
            .distinctBy { it.coverUrl }
    )
}
