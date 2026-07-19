package io.legado.app.ui.config.bookshelfConfig

import io.legado.app.domain.gateway.BookshelfSettingsGateway
import org.koin.core.context.GlobalContext

@Deprecated("使用 BookshelfSettingsGateway.currentSettings")
object BookshelfConfig {
    private val settings get() = GlobalContext.get().get<BookshelfSettingsGateway>().currentSettings
    val bookGroupStyle get() = settings.bookGroupStyle
    val bookshelfSort get() = settings.bookshelfSort
    val bookshelfSortOrder get() = settings.bookshelfSortOrder
    val showUnread get() = settings.showUnread
    val showUnreadNew get() = settings.showUnreadNew
    val showTip get() = settings.showTip
    val showBookCount get() = settings.showBookCount
    val showLastUpdateTime get() = settings.showLastUpdateTime
    val showBookIntro get() = settings.showBookIntro
    val bookshelfShowIntro get() = settings.bookshelfShowIntro
    val bookshelfShowTag get() = settings.bookshelfShowTag
    val bookshelfShowLatestChapter get() = settings.bookshelfShowLatestChapter
    val bookshelfIntroMaxLines get() = settings.bookshelfIntroMaxLines
    val showWaitUpCount get() = settings.showWaitUpCount
    val showBookshelfFastScroller get() = settings.showBookshelfFastScroller
    val shouldShowExpandButton get() = settings.shouldShowExpandButton
    val bookshelfRefreshingLimit get() = settings.bookshelfRefreshingLimit
    val bookshelfLayoutModePortrait get() = settings.bookshelfLayoutModePortrait
    val bookshelfLayoutGridPortrait get() = settings.bookshelfLayoutGridPortrait
    val bookshelfLayoutModeLandscape get() = settings.bookshelfLayoutModeLandscape
    val bookshelfLayoutGridLandscape get() = settings.bookshelfLayoutGridLandscape
    val bookshelfLayoutListPortrait get() = settings.bookshelfLayoutListPortrait
    val bookshelfLayoutListLandscape get() = settings.bookshelfLayoutListLandscape
    val bookshelfFolderLayoutModePortrait get() = settings.bookshelfFolderLayoutModePortrait
    val bookshelfFolderLayoutGridPortrait get() = settings.bookshelfFolderLayoutGridPortrait
    val bookshelfFolderLayoutModeLandscape get() = settings.bookshelfFolderLayoutModeLandscape
    val bookshelfFolderLayoutGridLandscape get() = settings.bookshelfFolderLayoutGridLandscape
    val bookshelfFolderLayoutListPortrait get() = settings.bookshelfFolderLayoutListPortrait
    val bookshelfFolderLayoutListLandscape get() = settings.bookshelfFolderLayoutListLandscape
    val bookshelfGridLayout get() = settings.bookshelfGridLayout
    val bookshelfLayoutCompact get() = settings.bookshelfLayoutCompact
    val bookshelfShowDivider get() = settings.bookshelfShowDivider
    val bookshelfTitleSmallFont get() = settings.bookshelfTitleSmallFont
    val bookshelfTitleCenter get() = settings.bookshelfTitleCenter
    val bookshelfTitleMaxLines get() = settings.bookshelfTitleMaxLines
    val bookshelfCoverShadow get() = settings.bookshelfCoverShadow
    val bookshelfCardColor get() = settings.bookshelfCardColor
    val bookshelfCardColorDark get() = settings.bookshelfCardColorDark
    val bookshelfGroupListStyle get() = settings.bookshelfGroupListStyle
    val bookshelfGroupCoverCount get() = settings.bookshelfGroupCoverCount
    val bookshelfListCoverWidth get() = settings.bookshelfListCoverWidth
    val bookshelfGridCoverWidth get() = settings.bookshelfGridCoverWidth
    val bookshelfSearchActionDirectToSearch get() = settings.bookshelfSearchActionDirectToSearch
    val autoRefreshBook get() = settings.autoRefreshBook
    val saveTabPosition get() = settings.saveTabPosition
}
