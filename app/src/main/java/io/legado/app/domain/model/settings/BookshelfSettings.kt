package io.legado.app.domain.model.settings

import io.legado.app.data.entities.BookGroup

data class BookshelfSettings(
    val bookGroupStyle: Int = 0,
    val hideEmptyGroups: Boolean = true,
    val bookshelfSort: Int = 0,
    val bookshelfSortOrder: Int = 1,
    val showUnread: Boolean = true,
    val showUnreadNew: Boolean = true,
    val showTip: Boolean = false,
    val showBookCount: Boolean = true,
    val showLastUpdateTime: Boolean = false,
    val showBookIntro: Boolean = false,
    val bookshelfShowIntro: Boolean = true,
    val bookshelfShowTag: Boolean = true,
    val bookshelfShowCustomTags: Boolean = true,
    val bookshelfShowSourceTags: Boolean = true,
    val bookshelfShowWordCount: Boolean = true,
    val bookshelfShowLatestChapter: Boolean = true,
    val bookshelfIntroMaxLines: Int = 0,
    val showWaitUpCount: Boolean = false,
    val showBookshelfFastScroller: Boolean = false,
    val shouldShowExpandButton: Boolean = false,
    val bookshelfRefreshingLimit: Int = 0,
    val bookshelfLayoutModePortrait: Int = 1,
    val bookshelfLayoutGridPortrait: Int = 3,
    val bookshelfLayoutModeLandscape: Int = 1,
    val bookshelfLayoutGridLandscape: Int = 7,
    val bookshelfLayoutListPortrait: Int = 1,
    val bookshelfLayoutListLandscape: Int = 1,
    val bookshelfFolderLayoutModePortrait: Int = 1,
    val bookshelfFolderLayoutGridPortrait: Int = 3,
    val bookshelfFolderLayoutModeLandscape: Int = 1,
    val bookshelfFolderLayoutGridLandscape: Int = 7,
    val bookshelfFolderLayoutListPortrait: Int = 1,
    val bookshelfFolderLayoutListLandscape: Int = 1,
    val bookshelfGridLayout: Int = 0,
    val bookshelfLayoutCompact: Boolean = false,
    val bookshelfListCoverCenter: Boolean = true,
    val bookshelfListIntroBelowContent: Boolean = false,
    val bookshelfShowDivider: Boolean = true,
    val bookshelfTitleSmallFont: Boolean = false,
    val bookshelfTitleCenter: Boolean = true,
    val bookshelfTitleMaxLines: Int = 2,
    val bookshelfCoverShadow: Boolean = false,
    val bookshelfDimensionalShelf: Boolean = false,
    val bookshelfCardColor: Int = 0,
    val bookshelfCardColorDark: Int = 0,
    val bookshelfGroupListStyle: Int = 0,
    val bookshelfGroupCoverCount: Int = 4,
    val bookshelfListCoverWidth: Int = 84,
    val bookshelfGridCoverWidth: Int = 120,
    /** 网格固定列数；关闭后列数由 [bookshelfGridCoverWidth] 推出（两者至少开一个）。 */
    val bookshelfGridFixedColumns: Boolean = true,
    /** 网格固定封面宽度；关闭后封面铺满列宽（两者至少开一个）。 */
    val bookshelfGridFixedCoverWidth: Boolean = true,
    /**
     * 网格沉浸模式：封面之间只留 1dp、去掉左右屏幕边距与封面圆角。
     *
     * 只在紧凑/仅封面样式下有效——标准样式封面下方还有书名，贴边排版会把标题裁掉，
     * 判断口径统一走 [gridImmersiveActive]。
     */
    val bookshelfGridImmersive: Boolean = false,
    val bookshelfSearchActionDirectToSearch: Boolean = true,
    val autoRefreshBook: Boolean = false,
    val saveTabPosition: Long = BookGroup.IdAll,
) {
    /** 沉浸网格是否真正生效：样式为紧凑(1)/仅封面(2)时才贴边排布。 */
    val gridImmersiveActive: Boolean
        get() = bookshelfGridImmersive && bookshelfGridLayout != 0
}
