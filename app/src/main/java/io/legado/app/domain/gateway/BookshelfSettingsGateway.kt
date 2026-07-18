package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.BookshelfSettings
import kotlinx.coroutines.flow.Flow

interface BookshelfSettingsGateway {
    val currentSettings: BookshelfSettings
    val settings: Flow<BookshelfSettings>
    suspend fun update(update: BookshelfSettingsUpdate)
}

sealed interface BookshelfSettingsUpdate {
    data class BooleanValue(val setting: BookshelfBooleanSetting, val value: Boolean) : BookshelfSettingsUpdate
    data class IntValue(val setting: BookshelfIntSetting, val value: Int) : BookshelfSettingsUpdate
    data class SaveTabPosition(val value: Long) : BookshelfSettingsUpdate
}

enum class BookshelfBooleanSetting {
    ShowUnread,
    ShowUnreadNew,
    ShowTip,
    ShowBookCount,
    ShowLastUpdateTime,
    ShowBookIntro,
    ShowIntro,
    ShowTag,
    ShowLatestChapter,
    ShowWaitUpCount,
    ShowFastScroller,
    ShowExpandButton,
    LayoutCompact,
    ShowDivider,
    TitleSmallFont,
    TitleCenter,
    CoverShadow,
    SearchActionDirectToSearch,
    AutoRefresh,
}

enum class BookshelfIntSetting {
    BookGroupStyle,
    Sort,
    SortOrder,
    IntroMaxLines,
    RefreshingLimit,
    LayoutModePortrait,
    LayoutGridPortrait,
    LayoutModeLandscape,
    LayoutGridLandscape,
    LayoutListPortrait,
    LayoutListLandscape,
    FolderLayoutModePortrait,
    FolderLayoutGridPortrait,
    FolderLayoutModeLandscape,
    FolderLayoutGridLandscape,
    FolderLayoutListPortrait,
    FolderLayoutListLandscape,
    GridLayout,
    TitleMaxLines,
    CardColor,
    CardColorDark,
    GroupListStyle,
    GroupCoverCount,
    ListCoverWidth,
    GridCoverWidth,
}
