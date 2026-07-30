package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.compatDsBoolean
import io.legado.app.help.config.compatDsInt
import io.legado.app.help.config.compatDsLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class BookshelfSettingsRepository : BookshelfSettingsGateway {
    override val currentSettings: BookshelfSettings
        get() = AppConfigStore.preferences.toBookshelfSettings()

    override val settings: Flow<BookshelfSettings> = AppConfigStore.preferencesFlow
        .map(Preferences::toBookshelfSettings)
        .distinctUntilChanged()

    override suspend fun update(transform: (BookshelfSettings) -> BookshelfSettings) {
        AppConfigStore.atomicUpdate(
            read = Preferences::toBookshelfSettings,
            toPrefMap = BookshelfSettings::toPrefMap,
            transform = transform,
        )
    }
}

internal fun Preferences.toBookshelfSettings() = BookshelfSettings(
    bookGroupStyle = compatDsInt(PreferKey.bookGroupStyle) ?: 0,
    hideEmptyGroups = compatDsBoolean(PreferKey.hideEmptyGroups) ?: true,
    bookshelfSort = compatDsInt(PreferKey.bookshelfSort) ?: 0,
    bookshelfSortOrder = compatDsInt(PreferKey.bookshelfSortOrder) ?: 1,
    showUnread = compatDsBoolean(PreferKey.showUnread) ?: true,
    showUnreadNew = compatDsBoolean(PreferKey.showUnreadNew) ?: true,
    showTip = compatDsBoolean(PreferKey.showTip) ?: false,
    showBookCount = compatDsBoolean(PreferKey.showBookCount) ?: true,
    showLastUpdateTime = compatDsBoolean(PreferKey.showLastUpdateTime) ?: false,
    showBookIntro = compatDsBoolean(PreferKey.showBookIntro) ?: false,
    bookshelfShowIntro = compatDsBoolean(PreferKey.bookshelfShowIntro) ?: true,
    bookshelfShowTag = compatDsBoolean(PreferKey.bookshelfShowTag) ?: true,
    bookshelfShowLatestChapter = compatDsBoolean(PreferKey.bookshelfShowLatestChapter) ?: true,
    bookshelfIntroMaxLines = compatDsInt(PreferKey.bookshelfIntroMaxLines) ?: 0,
    showWaitUpCount = compatDsBoolean(PreferKey.showWaitUpCount) ?: false,
    showBookshelfFastScroller = compatDsBoolean(PreferKey.showBookshelfFastScroller) ?: false,
    shouldShowExpandButton = compatDsBoolean(PreferKey.shouldShowExpandButton) ?: false,
    bookshelfRefreshingLimit = compatDsInt(PreferKey.bookshelfRefreshingLimit) ?: 0,
    bookshelfLayoutModePortrait = compatDsInt(PreferKey.bookshelfLayoutModePortrait) ?: 1,
    bookshelfLayoutGridPortrait = compatDsInt(PreferKey.bookshelfLayoutGridPortrait) ?: 3,
    bookshelfLayoutModeLandscape = compatDsInt(PreferKey.bookshelfLayoutModeLandscape) ?: 1,
    bookshelfLayoutGridLandscape = compatDsInt(PreferKey.bookshelfLayoutGridLandscape) ?: 7,
    bookshelfLayoutListPortrait = compatDsInt(PreferKey.bookshelfLayoutListPortrait) ?: 1,
    bookshelfLayoutListLandscape = compatDsInt(PreferKey.bookshelfLayoutListLandscape) ?: 1,
    bookshelfFolderLayoutModePortrait = compatDsInt(PreferKey.bookshelfFolderLayoutModePortrait) ?: 1,
    bookshelfFolderLayoutGridPortrait = compatDsInt(PreferKey.bookshelfFolderLayoutGridPortrait) ?: 3,
    bookshelfFolderLayoutModeLandscape = compatDsInt(PreferKey.bookshelfFolderLayoutModeLandscape) ?: 1,
    bookshelfFolderLayoutGridLandscape = compatDsInt(PreferKey.bookshelfFolderLayoutGridLandscape) ?: 7,
    bookshelfFolderLayoutListPortrait = compatDsInt(PreferKey.bookshelfFolderLayoutListPortrait) ?: 1,
    bookshelfFolderLayoutListLandscape = compatDsInt(PreferKey.bookshelfFolderLayoutListLandscape) ?: 1,
    bookshelfGridLayout = compatDsInt(PreferKey.bookshelfGridLayout) ?: 0,
    bookshelfLayoutCompact = compatDsBoolean(PreferKey.bookshelfLayoutCompact) ?: false,
    bookshelfShowDivider = compatDsBoolean(PreferKey.bookshelfShowDivider) ?: true,
    bookshelfTitleSmallFont = compatDsBoolean(PreferKey.bookshelfTitleSmallFont) ?: false,
    bookshelfTitleCenter = compatDsBoolean(PreferKey.bookshelfTitleCenter) ?: true,
    bookshelfTitleMaxLines = compatDsInt(PreferKey.bookshelfTitleMaxLines) ?: 2,
    bookshelfCoverShadow = compatDsBoolean(PreferKey.bookshelfCoverShadow) ?: false,
    bookshelfCardColor = compatDsInt(PreferKey.bookshelfCardColor) ?: 0,
    bookshelfCardColorDark = compatDsInt(PreferKey.bookshelfCardColorDark) ?: 0,
    bookshelfGroupListStyle = compatDsInt(PreferKey.bookshelfGroupListStyle) ?: 0,
    bookshelfGroupCoverCount = compatDsInt(PreferKey.bookshelfGroupCoverCount) ?: 4,
    bookshelfListCoverWidth = compatDsInt(PreferKey.bookshelfListCoverWidth) ?: 84,
    bookshelfGridCoverWidth = compatDsInt(PreferKey.bookshelfGridCoverWidth) ?: 120,
    bookshelfSearchActionDirectToSearch = compatDsBoolean(PreferKey.bookshelfSearchActionDirectToSearch) ?: true,
    autoRefreshBook = compatDsBoolean(PreferKey.autoRefresh) ?: false,
    saveTabPosition = compatDsLong(PreferKey.saveTabPosition) ?: BookGroup.IdAll,
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
