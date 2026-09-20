package io.legado.app.domain.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

/**
 * 冲突 Sheet 里展示的一本书（待加入的书或书架已有作品）。
 *
 * 只携带展示与判定所需的最小字段，避免在 UI 状态里持有完整 Book 实体。
 */
@Stable
data class ConflictBookSummary(
    val bookUrl: String,
    val name: String,
    val author: String,
    val coverUrl: String?,
    val customCoverUrl: String?,
    val origin: String,
    val sourceName: String,
    val totalChapterNum: Int = 0,
    val latestChapterTitle: String? = null,
) {
    val displayCover: String?
        get() = customCoverUrl?.takeIf { it.isNotBlank() } ?: coverUrl?.takeIf { it.isNotBlank() }
}

/**
 * 加入书架时发现的疑似重复。
 *
 * @param incoming 正在加入、尚未入架的作品
 * @param candidates 书架中疑似同一作品的已有作品，按最近阅读时间倒序
 */
@Stable
data class BookshelfConflict(
    val incoming: ConflictBookSummary,
    val candidates: ImmutableList<ConflictBookSummary>,
)
