package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.model.settings.BookshelfSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class BookshelfSettingsRepository(
    private val preferences: PreferenceStore,
) : BookshelfSettingsGateway {
    override val currentSettings: BookshelfSettings
        get() = preferences.currentSnapshot().toBookshelfSettings()

    override val settings: Flow<BookshelfSettings> = preferences.observeSnapshot()
        .map { it.toBookshelfSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (BookshelfSettings) -> BookshelfSettings) {
        preferences.atomicUpdateSettings(
            read = { it.toBookshelfSettings() },
            toPrefMap = BookshelfSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun Map<String, PreferenceValue>.toBookshelfSettings() = BookshelfSettings(
    bookGroupStyle = compatInt(PreferKey.bookGroupStyle) ?: 0,
    hideEmptyGroups = compatBoolean(PreferKey.hideEmptyGroups) ?: true,
    bookshelfSort = compatInt(PreferKey.bookshelfSort) ?: 0,
    bookshelfSortOrder = compatInt(PreferKey.bookshelfSortOrder) ?: 1,
    showUnread = compatBoolean(PreferKey.showUnread) ?: true,
    showUnreadNew = compatBoolean(PreferKey.showUnreadNew) ?: true,
    showTip = compatBoolean(PreferKey.showTip) ?: false,
    showBookCount = compatBoolean(PreferKey.showBookCount) ?: true,
    showLastUpdateTime = compatBoolean(PreferKey.showLastUpdateTime) ?: false,
    showBookIntro = compatBoolean(PreferKey.showBookIntro) ?: false,
    bookshelfShowIntro = compatBoolean(PreferKey.bookshelfShowIntro) ?: true,
    bookshelfShowTag = compatBoolean(PreferKey.bookshelfShowTag) ?: true,
    bookshelfShowLatestChapter = compatBoolean(PreferKey.bookshelfShowLatestChapter) ?: true,
    bookshelfIntroMaxLines = compatInt(PreferKey.bookshelfIntroMaxLines) ?: 0,
    showWaitUpCount = compatBoolean(PreferKey.showWaitUpCount) ?: false,
    showBookshelfFastScroller = compatBoolean(PreferKey.showBookshelfFastScroller) ?: false,
    shouldShowExpandButton = compatBoolean(PreferKey.shouldShowExpandButton) ?: false,
    bookshelfRefreshingLimit = compatInt(PreferKey.bookshelfRefreshingLimit) ?: 0,
    bookshelfLayoutModePortrait = compatInt(PreferKey.bookshelfLayoutModePortrait) ?: 1,
    bookshelfLayoutGridPortrait = compatInt(PreferKey.bookshelfLayoutGridPortrait) ?: 3,
    bookshelfLayoutModeLandscape = compatInt(PreferKey.bookshelfLayoutModeLandscape) ?: 1,
    bookshelfLayoutGridLandscape = compatInt(PreferKey.bookshelfLayoutGridLandscape) ?: 7,
    bookshelfLayoutListPortrait = compatInt(PreferKey.bookshelfLayoutListPortrait) ?: 1,
    bookshelfLayoutListLandscape = compatInt(PreferKey.bookshelfLayoutListLandscape) ?: 1,
    bookshelfFolderLayoutModePortrait = compatInt(PreferKey.bookshelfFolderLayoutModePortrait) ?: 1,
    bookshelfFolderLayoutGridPortrait = compatInt(PreferKey.bookshelfFolderLayoutGridPortrait) ?: 3,
    bookshelfFolderLayoutModeLandscape = compatInt(PreferKey.bookshelfFolderLayoutModeLandscape) ?: 1,
    bookshelfFolderLayoutGridLandscape = compatInt(PreferKey.bookshelfFolderLayoutGridLandscape) ?: 7,
    bookshelfFolderLayoutListPortrait = compatInt(PreferKey.bookshelfFolderLayoutListPortrait) ?: 1,
    bookshelfFolderLayoutListLandscape = compatInt(PreferKey.bookshelfFolderLayoutListLandscape) ?: 1,
    bookshelfGridLayout = compatInt(PreferKey.bookshelfGridLayout) ?: 0,
    bookshelfLayoutCompact = compatBoolean(PreferKey.bookshelfLayoutCompact) ?: false,
    bookshelfListCoverCenter = compatBoolean(PreferKey.bookshelfListCoverCenter) ?: true,
    bookshelfListIntroBelowContent = compatBoolean(PreferKey.bookshelfListIntroBelowContent)
        ?: false,
    bookshelfShowDivider = compatBoolean(PreferKey.bookshelfShowDivider) ?: true,
    bookshelfTitleSmallFont = compatBoolean(PreferKey.bookshelfTitleSmallFont) ?: false,
    bookshelfTitleCenter = compatBoolean(PreferKey.bookshelfTitleCenter) ?: true,
    bookshelfTitleMaxLines = compatInt(PreferKey.bookshelfTitleMaxLines) ?: 2,
    bookshelfCoverShadow = compatBoolean(PreferKey.bookshelfCoverShadow) ?: false,
    bookshelfCardColor = compatInt(PreferKey.bookshelfCardColor) ?: 0,
    bookshelfCardColorDark = compatInt(PreferKey.bookshelfCardColorDark) ?: 0,
    bookshelfGroupListStyle = compatInt(PreferKey.bookshelfGroupListStyle) ?: 0,
    bookshelfGroupCoverCount = compatInt(PreferKey.bookshelfGroupCoverCount) ?: 4,
    bookshelfListCoverWidth = compatInt(PreferKey.bookshelfListCoverWidth) ?: 84,
    bookshelfGridCoverWidth = compatInt(PreferKey.bookshelfGridCoverWidth) ?: 120,
    bookshelfSearchActionDirectToSearch = compatBoolean(PreferKey.bookshelfSearchActionDirectToSearch) ?: true,
    autoRefreshBook = compatBoolean(PreferKey.autoRefresh) ?: false,
    saveTabPosition = compatLong(PreferKey.saveTabPosition) ?: BookGroup.IdAll,
)

internal fun BookshelfSettings.toPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.bookGroupStyle to bookGroupStyle,
    PreferKey.hideEmptyGroups to hideEmptyGroups,
    PreferKey.bookshelfSort to bookshelfSort,
    PreferKey.bookshelfSortOrder to bookshelfSortOrder,
    PreferKey.showUnread to showUnread,
    PreferKey.showUnreadNew to showUnreadNew,
    PreferKey.showTip to showTip,
    PreferKey.showBookCount to showBookCount,
    PreferKey.showLastUpdateTime to showLastUpdateTime,
    PreferKey.showBookIntro to showBookIntro,
    PreferKey.bookshelfShowIntro to bookshelfShowIntro,
    PreferKey.bookshelfShowTag to bookshelfShowTag,
    PreferKey.bookshelfShowLatestChapter to bookshelfShowLatestChapter,
    PreferKey.bookshelfIntroMaxLines to bookshelfIntroMaxLines,
    PreferKey.showWaitUpCount to showWaitUpCount,
    PreferKey.showBookshelfFastScroller to showBookshelfFastScroller,
    PreferKey.shouldShowExpandButton to shouldShowExpandButton,
    PreferKey.bookshelfRefreshingLimit to bookshelfRefreshingLimit,
    PreferKey.bookshelfLayoutModePortrait to bookshelfLayoutModePortrait,
    PreferKey.bookshelfLayoutGridPortrait to bookshelfLayoutGridPortrait,
    PreferKey.bookshelfLayoutModeLandscape to bookshelfLayoutModeLandscape,
    PreferKey.bookshelfLayoutGridLandscape to bookshelfLayoutGridLandscape,
    PreferKey.bookshelfLayoutListPortrait to bookshelfLayoutListPortrait,
    PreferKey.bookshelfLayoutListLandscape to bookshelfLayoutListLandscape,
    PreferKey.bookshelfFolderLayoutModePortrait to bookshelfFolderLayoutModePortrait,
    PreferKey.bookshelfFolderLayoutGridPortrait to bookshelfFolderLayoutGridPortrait,
    PreferKey.bookshelfFolderLayoutModeLandscape to bookshelfFolderLayoutModeLandscape,
    PreferKey.bookshelfFolderLayoutGridLandscape to bookshelfFolderLayoutGridLandscape,
    PreferKey.bookshelfFolderLayoutListPortrait to bookshelfFolderLayoutListPortrait,
    PreferKey.bookshelfFolderLayoutListLandscape to bookshelfFolderLayoutListLandscape,
    PreferKey.bookshelfGridLayout to bookshelfGridLayout,
    PreferKey.bookshelfLayoutCompact to bookshelfLayoutCompact,
    PreferKey.bookshelfListCoverCenter to bookshelfListCoverCenter,
    PreferKey.bookshelfListIntroBelowContent to bookshelfListIntroBelowContent,
    PreferKey.bookshelfShowDivider to bookshelfShowDivider,
    PreferKey.bookshelfTitleSmallFont to bookshelfTitleSmallFont,
    PreferKey.bookshelfTitleCenter to bookshelfTitleCenter,
    PreferKey.bookshelfTitleMaxLines to bookshelfTitleMaxLines,
    PreferKey.bookshelfCoverShadow to bookshelfCoverShadow,
    PreferKey.bookshelfCardColor to bookshelfCardColor,
    PreferKey.bookshelfCardColorDark to bookshelfCardColorDark,
    PreferKey.bookshelfGroupListStyle to bookshelfGroupListStyle,
    PreferKey.bookshelfGroupCoverCount to bookshelfGroupCoverCount,
    PreferKey.bookshelfListCoverWidth to bookshelfListCoverWidth,
    PreferKey.bookshelfGridCoverWidth to bookshelfGridCoverWidth,
    PreferKey.bookshelfSearchActionDirectToSearch to bookshelfSearchActionDirectToSearch,
    PreferKey.autoRefresh to autoRefreshBook,
    PreferKey.saveTabPosition to saveTabPosition,
)
