package io.legado.app.ui.book.manga

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.ui.book.manga.config.MangaScrollMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class MangaReaderUiState(
    val bookName: String = "",
    val bookAuthor: String = "",
    val bookUrl: String = "",
    val chapterName: String = "",
    val chapterUrl: String? = null,
    val sourceName: String = "",
    val sourceUrl: String? = null,
    val sourceType: Int? = null,
    val changeSourceBook: MangaBookSnapshot? = null,
    val pages: ImmutableList<MangaReaderItemUi> = persistentListOf(),
    val currentItemIndex: Int = 0,
    val currentPage: Int = 0,
    val pageCount: Int = 0,
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: MangaReaderText? = null,
    val pendingMessages: ImmutableList<MangaReaderMessage> = persistentListOf(),
    val menuVisible: Boolean = false,
    val autoReadEnabled: Boolean = false,
    val activeSheet: MangaReaderSheet? = null,
    val activeDialog: MangaReaderDialog? = null,
    val settingsCategory: MangaReaderSettingsCategory? = null,
    val inBookshelf: Boolean = true,
    val confirmAddToShelf: Boolean = true,
    val scrollRequest: MangaScrollRequest? = null,
    val settings: MangaReaderSettings = MangaReaderSettings(),
)

@Stable
sealed interface MangaReaderItemUi {
    val key: String

    @Stable
    data class Page(
        override val key: String,
        val imageUrl: String,
        val chapterIndex: Int,
        val chapterCount: Int,
        val pageIndex: Int,
        val pageCount: Int,
        val chapterName: String,
    ) : MangaReaderItemUi

    @Stable
    data class ChapterEdge(
        override val key: String,
        val message: String,
    ) : MangaReaderItemUi
}

@Stable
data class MangaReaderSettings(
    val scrollMode: Int = MangaScrollMode.WEBTOON,
    val sidePaddingPercent: Int = 0,
    val backgroundColor: Color = Color.Black,
    val disableScale: Boolean = true,
    val disableScrollAnimation: Boolean = false,
    val disableCrossFade: Boolean = false,
    val disableClickScroll: Boolean = false,
    val longPressEnabled: Boolean = true,
    val preDownloadCount: Int = 10,
    val autoReadSpeed: Int = 3,
    val volumeKeyPage: Boolean = false,
    val reverseVolumeKeyPage: Boolean = false,
    val hideMangaTitle: Boolean = false,
    val autoBrightness: Boolean = true,
    val brightness: Int = 0,
    val enableGray: Boolean = false,
    val enableEInk: Boolean = false,
    val eInkThreshold: Int = 150,
    val filterRed: Int = 0,
    val filterGreen: Int = 0,
    val filterBlue: Int = 0,
    val filterAlpha: Int = 0,
    val hideFooter: Boolean = false,
    val hideChapterName: Boolean = false,
    val hidePageNumber: Boolean = false,
    val hidePageNumberLabel: Boolean = false,
    val hideChapter: Boolean = false,
    val hideChapterLabel: Boolean = false,
    val hideProgress: Boolean = false,
    val hideProgressLabel: Boolean = false,
    val footerAlignment: Int = 0,
    val sourceOrigin: String? = null,
    val clickActions: ImmutableList<Int> = persistentListOf(-1, -1, 1, 2, 0, 1, 2, 1, 1),
)

@Stable
data class MangaScrollRequest(val id: Long, val itemIndex: Int, val animated: Boolean)

@Immutable
data class MangaReaderMessage(
    val id: Long,
    val content: MangaReaderText,
)

@Immutable
sealed interface MangaReaderText {
    data class Resource(
        @StringRes val resId: Int,
        val args: ImmutableList<String> = persistentListOf(),
    ) : MangaReaderText

    data class Dynamic(val value: String) : MangaReaderText
}

sealed interface MangaReaderIntent {
    data class Initialize(
        val bookUrl: String?,
        val inBookshelf: Boolean,
        val chapterChanged: Boolean,
    ) : MangaReaderIntent
    data object ResumeSession : MangaReaderIntent
    data object PauseSession : MangaReaderIntent
    data object NetworkAvailable : MangaReaderIntent
    data object RefreshBookSource : MangaReaderIntent
    data object ReloadContent : MangaReaderIntent
    data class ApplyReadingProgress(val progress: BookProgress) : MangaReaderIntent
    data class OpenChapter(val chapterIndex: Int, val pageIndex: Int) : MangaReaderIntent
    data class ChangeSourceBook(
        val book: Book,
        val toc: List<BookChapter>,
    ) : MangaReaderIntent
    data class AddExternalBookToShelf(
        val book: Book,
        val toc: List<BookChapter>,
    ) : MangaReaderIntent
    data object AddCurrentBookToShelf : MangaReaderIntent
    data object DiscardCurrentBookAndExit : MangaReaderIntent
    data object DismissDialog : MangaReaderIntent
    data object DisableCurrentSource : MangaReaderIntent
    data object RequestPayCurrentChapter : MangaReaderIntent
    data object PayCurrentChapter : MangaReaderIntent
    data object OpenSourceLogin : MangaReaderIntent
    data object OpenSourceEdit : MangaReaderIntent
    data object BackPressed : MangaReaderIntent
    data object ToggleMenu : MangaReaderIntent
    data object HideMenu : MangaReaderIntent
    data object Retry : MangaReaderIntent
    data object PreviousChapter : MangaReaderIntent
    data object NextChapter : MangaReaderIntent
    data object OpenCatalog : MangaReaderIntent
    data object OpenBookInfo : MangaReaderIntent
    data object OpenChapterUrl : MangaReaderIntent
    data object ChangeSource : MangaReaderIntent
    data object RefreshChapter : MangaReaderIntent
    data class OpenSettings(val category: MangaReaderSettingsCategory) : MangaReaderIntent
    data object CloseSettings : MangaReaderIntent
    data object OpenSourceActions : MangaReaderIntent
    data object ToggleAutoRead : MangaReaderIntent
    data object DismissSheet : MangaReaderIntent
    data class UpdateSetting(
        val key: MangaReaderSettingKey,
        val value: Int,
    ) : MangaReaderIntent
    data class UpdateClickAction(val index: Int, val action: Int) : MangaReaderIntent
    data class PageStep(val direction: Int) : MangaReaderIntent
    data class SeekToPage(val pageIndex: Int) : MangaReaderIntent
    data class VisibleItemChanged(
        val itemIndex: Int,
        val currentChapterVisible: Boolean,
    ) : MangaReaderIntent
    data class LongPressPage(val imageUrl: String) : MangaReaderIntent
    data class MessageShown(val id: Long) : MangaReaderIntent
}

sealed interface MangaReaderSheet {
    data object Catalog : MangaReaderSheet
    data object ChangeSource : MangaReaderSheet
    data object SourceActions : MangaReaderSheet
}

enum class MangaReaderSettingsCategory { READER, FOOTER, FILTER, CLICK_ACTIONS, AUTO_READ }

sealed interface MangaReaderDialog {
    data object AddToShelf : MangaReaderDialog
    data class ConfirmPay(val chapterName: String) : MangaReaderDialog
    data class ConfirmProgress(val progress: BookProgress) : MangaReaderDialog
}

enum class MangaReaderSettingKey {
    SCROLL_MODE,
    SIDE_PADDING,
    BACKGROUND_RED,
    BACKGROUND_GREEN,
    BACKGROUND_BLUE,
    DISABLE_SCALE,
    DISABLE_SCROLL_ANIMATION,
    DISABLE_CROSS_FADE,
    DISABLE_CLICK_SCROLL,
    LONG_PRESS,
    PRE_DOWNLOAD,
    AUTO_READ_SPEED,
    VOLUME_KEY_PAGE,
    REVERSE_VOLUME_KEY_PAGE,
    HIDE_MANGA_TITLE,
    ENABLE_GRAY,
    ENABLE_EINK,
    EINK_THRESHOLD,
    FILTER_RED,
    FILTER_GREEN,
    FILTER_BLUE,
    FILTER_ALPHA,
    AUTO_BRIGHTNESS,
    BRIGHTNESS,
    HIDE_FOOTER,
    HIDE_CHAPTER_NAME,
    HIDE_PAGE_NUMBER,
    HIDE_PAGE_NUMBER_LABEL,
    HIDE_CHAPTER,
    HIDE_CHAPTER_LABEL,
    HIDE_PROGRESS,
    HIDE_PROGRESS_LABEL,
    FOOTER_ALIGNMENT,
}

sealed interface MangaReaderEffect {
    data class Finish(val bookshelfChanged: Boolean = false) : MangaReaderEffect
    data object OpenBookInfo : MangaReaderEffect
    data class OpenChapterUrl(val externalBrowser: Boolean) : MangaReaderEffect
    data class OpenSourceLogin(val sourceUrl: String) : MangaReaderEffect
    data class OpenSourceEdit(val sourceUrl: String) : MangaReaderEffect
    data class OpenPaymentUrl(
        val url: String,
        val sourceOrigin: String?,
        val sourceName: String?,
        val sourceType: Int?,
    ) : MangaReaderEffect
    data class SetWindowBrightness(val auto: Boolean, val brightness: Int) : MangaReaderEffect
    data class SetSystemBarsVisible(val visible: Boolean) : MangaReaderEffect
}
