package io.legado.app.data.repository

import androidx.datastore.preferences.core.Preferences
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.gateway.BookshelfBooleanSetting
import io.legado.app.domain.gateway.BookshelfIntSetting
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.gateway.BookshelfSettingsUpdate
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

    override suspend fun update(update: BookshelfSettingsUpdate) {
        val (key, value) = when (update) {
            is BookshelfSettingsUpdate.SaveTabPosition -> PreferKey.saveTabPosition to update.value
            is BookshelfSettingsUpdate.BooleanValue -> when (update.setting) {
                BookshelfBooleanSetting.ShowUnread -> PreferKey.showUnread
                BookshelfBooleanSetting.ShowUnreadNew -> PreferKey.showUnreadNew
                BookshelfBooleanSetting.ShowTip -> PreferKey.showTip
                BookshelfBooleanSetting.ShowBookCount -> PreferKey.showBookCount
                BookshelfBooleanSetting.ShowLastUpdateTime -> PreferKey.showLastUpdateTime
                BookshelfBooleanSetting.ShowBookIntro -> PreferKey.showBookIntro
                BookshelfBooleanSetting.ShowIntro -> PreferKey.bookshelfShowIntro
                BookshelfBooleanSetting.ShowTag -> PreferKey.bookshelfShowTag
                BookshelfBooleanSetting.ShowLatestChapter -> PreferKey.bookshelfShowLatestChapter
                BookshelfBooleanSetting.ShowWaitUpCount -> PreferKey.showWaitUpCount
                BookshelfBooleanSetting.ShowFastScroller -> PreferKey.showBookshelfFastScroller
                BookshelfBooleanSetting.ShowExpandButton -> PreferKey.shouldShowExpandButton
                BookshelfBooleanSetting.LayoutCompact -> PreferKey.bookshelfLayoutCompact
                BookshelfBooleanSetting.ShowDivider -> PreferKey.bookshelfShowDivider
                BookshelfBooleanSetting.TitleSmallFont -> PreferKey.bookshelfTitleSmallFont
                BookshelfBooleanSetting.TitleCenter -> PreferKey.bookshelfTitleCenter
                BookshelfBooleanSetting.CoverShadow -> PreferKey.bookshelfCoverShadow
                BookshelfBooleanSetting.SearchActionDirectToSearch -> PreferKey.bookshelfSearchActionDirectToSearch
                BookshelfBooleanSetting.AutoRefresh -> PreferKey.autoRefresh
            } to update.value
            is BookshelfSettingsUpdate.IntValue -> when (update.setting) {
                BookshelfIntSetting.BookGroupStyle -> PreferKey.bookGroupStyle
                BookshelfIntSetting.Sort -> PreferKey.bookshelfSort
                BookshelfIntSetting.SortOrder -> PreferKey.bookshelfSortOrder
                BookshelfIntSetting.IntroMaxLines -> PreferKey.bookshelfIntroMaxLines
                BookshelfIntSetting.RefreshingLimit -> PreferKey.bookshelfRefreshingLimit
                BookshelfIntSetting.LayoutModePortrait -> PreferKey.bookshelfLayoutModePortrait
                BookshelfIntSetting.LayoutGridPortrait -> PreferKey.bookshelfLayoutGridPortrait
                BookshelfIntSetting.LayoutModeLandscape -> PreferKey.bookshelfLayoutModeLandscape
                BookshelfIntSetting.LayoutGridLandscape -> PreferKey.bookshelfLayoutGridLandscape
                BookshelfIntSetting.LayoutListPortrait -> PreferKey.bookshelfLayoutListPortrait
                BookshelfIntSetting.LayoutListLandscape -> PreferKey.bookshelfLayoutListLandscape
                BookshelfIntSetting.FolderLayoutModePortrait -> PreferKey.bookshelfFolderLayoutModePortrait
                BookshelfIntSetting.FolderLayoutGridPortrait -> PreferKey.bookshelfFolderLayoutGridPortrait
                BookshelfIntSetting.FolderLayoutModeLandscape -> PreferKey.bookshelfFolderLayoutModeLandscape
                BookshelfIntSetting.FolderLayoutGridLandscape -> PreferKey.bookshelfFolderLayoutGridLandscape
                BookshelfIntSetting.FolderLayoutListPortrait -> PreferKey.bookshelfFolderLayoutListPortrait
                BookshelfIntSetting.FolderLayoutListLandscape -> PreferKey.bookshelfFolderLayoutListLandscape
                BookshelfIntSetting.GridLayout -> PreferKey.bookshelfGridLayout
                BookshelfIntSetting.TitleMaxLines -> PreferKey.bookshelfTitleMaxLines
                BookshelfIntSetting.CardColor -> PreferKey.bookshelfCardColor
                BookshelfIntSetting.CardColorDark -> PreferKey.bookshelfCardColorDark
                BookshelfIntSetting.GroupListStyle -> PreferKey.bookshelfGroupListStyle
                BookshelfIntSetting.GroupCoverCount -> PreferKey.bookshelfGroupCoverCount
                BookshelfIntSetting.ListCoverWidth -> PreferKey.bookshelfListCoverWidth
                BookshelfIntSetting.GridCoverWidth -> PreferKey.bookshelfGridCoverWidth
            } to update.value
        }
        AppConfigStore.putAll(mapOf(key to value))
    }
}

private fun Preferences.toBookshelfSettings() = BookshelfSettings(
    bookGroupStyle = compatDsInt(PreferKey.bookGroupStyle) ?: 0,
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
