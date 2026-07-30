package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.BookshelfSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfSettingsMappingTest {

    @Test
    fun `Bookshelf 46 键写读映射逐字段对应`() {
        assertEquals(46, BookshelfSettings().toPrefMap().size)
        bookshelfMappingSamples().forEach { expected ->
            assertEquals(expected.expectedPrefMap(), expected.toPrefMap())
            assertEquals(
                expected,
                expected.expectedPrefMap().toTestPreferences().toBookshelfSettings(),
            )
        }
    }

    @Test
    fun `隐藏空分组通过真实原子路径只写对应键`() {
        val values = captureAtomicUpdateValues(
            current = BookshelfSettings(hideEmptyGroups = false),
            read = { it.toBookshelfSettings() },
            toPrefMap = BookshelfSettings::toPrefMap,
            transform = { it.copy(hideEmptyGroups = true) },
        )

        assertEquals(mapOf(PreferKey.hideEmptyGroups to true), values)
    }
}

private fun bookshelfMappingSamples(): List<BookshelfSettings> {
    val base = BookshelfSettings(
        bookGroupStyle = 101,
        hideEmptyGroups = false,
        bookshelfSort = 102,
        bookshelfSortOrder = 103,
        showUnread = false,
        showUnreadNew = false,
        showTip = false,
        showBookCount = false,
        showLastUpdateTime = false,
        showBookIntro = false,
        bookshelfShowIntro = false,
        bookshelfShowTag = false,
        bookshelfShowLatestChapter = false,
        bookshelfIntroMaxLines = 104,
        showWaitUpCount = false,
        showBookshelfFastScroller = false,
        shouldShowExpandButton = false,
        bookshelfRefreshingLimit = 105,
        bookshelfLayoutModePortrait = 106,
        bookshelfLayoutGridPortrait = 107,
        bookshelfLayoutModeLandscape = 108,
        bookshelfLayoutGridLandscape = 109,
        bookshelfLayoutListPortrait = 110,
        bookshelfLayoutListLandscape = 111,
        bookshelfFolderLayoutModePortrait = 112,
        bookshelfFolderLayoutGridPortrait = 113,
        bookshelfFolderLayoutModeLandscape = 114,
        bookshelfFolderLayoutGridLandscape = 115,
        bookshelfFolderLayoutListPortrait = 116,
        bookshelfFolderLayoutListLandscape = 117,
        bookshelfGridLayout = 118,
        bookshelfLayoutCompact = false,
        bookshelfShowDivider = false,
        bookshelfTitleSmallFont = false,
        bookshelfTitleCenter = false,
        bookshelfTitleMaxLines = 119,
        bookshelfCoverShadow = false,
        bookshelfCardColor = 120,
        bookshelfCardColorDark = 121,
        bookshelfGroupListStyle = 122,
        bookshelfGroupCoverCount = 123,
        bookshelfListCoverWidth = 124,
        bookshelfGridCoverWidth = 125,
        bookshelfSearchActionDirectToSearch = false,
        autoRefreshBook = false,
        saveTabPosition = 987654321L,
    )
    return listOf(
        base,
        base.copy(hideEmptyGroups = true),
        base.copy(showUnread = true),
        base.copy(showUnreadNew = true),
        base.copy(showTip = true),
        base.copy(showBookCount = true),
        base.copy(showLastUpdateTime = true),
        base.copy(showBookIntro = true),
        base.copy(bookshelfShowIntro = true),
        base.copy(bookshelfShowTag = true),
        base.copy(bookshelfShowLatestChapter = true),
        base.copy(showWaitUpCount = true),
        base.copy(showBookshelfFastScroller = true),
        base.copy(shouldShowExpandButton = true),
        base.copy(bookshelfLayoutCompact = true),
        base.copy(bookshelfShowDivider = true),
        base.copy(bookshelfTitleSmallFont = true),
        base.copy(bookshelfTitleCenter = true),
        base.copy(bookshelfCoverShadow = true),
        base.copy(bookshelfSearchActionDirectToSearch = true),
        base.copy(autoRefreshBook = true),
    )
}

private fun BookshelfSettings.expectedPrefMap(): Map<String, Any?> = mapOf(
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
