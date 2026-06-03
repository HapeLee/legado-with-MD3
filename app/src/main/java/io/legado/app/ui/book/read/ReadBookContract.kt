package io.legado.app.ui.book.read

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.searchContent.SearchResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class ReadBookMenuState(
    val visible: Boolean = false,
    val routeStack: ImmutableList<ReadBookMenuRoute> = persistentListOf(ReadBookMenuRoute.Main),
) {
    val currentRoute: ReadBookMenuRoute
        get() = routeStack.lastOrNull() ?: ReadBookMenuRoute.Main

    val canNavigateBack: Boolean
        get() = routeStack.size > 1
}

@Immutable
sealed interface ReadBookMenuRoute {
    data object Main : ReadBookMenuRoute
    data object ReadStyle : ReadBookMenuRoute
    data object TextTitle : ReadBookMenuRoute
    data object ReadAloud : ReadBookMenuRoute
    data object PaddingConfig : ReadBookMenuRoute
    data class Bookmark(val bookmark: io.legado.app.data.entities.Bookmark) : ReadBookMenuRoute
}

@Stable
data class ReadBookUiState(
    val book: Book? = null,
    val bookSource: BookSource? = null,
    val bookName: String = "",
    val chapterName: String = "",
    val chapterUrl: String = "",
    val chapterSize: Int = 0,
    val durChapterIndex: Int = 0,
    val durChapterPos: Int = 0,
    val durPageIndex: Int = 0,
    val isLocalBook: Boolean = true,
    val msg: String? = null,
    val isInitFinish: Boolean = false,
    // Menu
    val searchMenuVisible: Boolean = false,
    val isShowingSearchResult: Boolean = false,
    val searchResultList: ImmutableList<SearchResult> = persistentListOf(),
    val searchResultIndex: Int = 0,
    // Read aloud / auto page
    val isReadAloudRunning: Boolean = false,
    val isReadAloudPaused: Boolean = false,
    val isAutoPage: Boolean = false,
    // Seek bar
    val seekProgress: Int = 0,
    val seekMax: Int = 0,
    // Replace rules
    val replaceRuleEnabled: Boolean = false,
    val effectiveReplaceCount: Int = 0,
    // Translation
    val translationMode: Boolean = false,
    // Chapter info
    val curTextChapter: TextChapter? = null,
    // Time / battery (from EventBus)
    val time: String = "",
    val battery: Int = 0,
    val menuState: ReadBookMenuState = ReadBookMenuState(),
    // Active sheet / dialog
    val activeSheet: ReadBookSheet? = null,
    val activeDialog: ReadBookDialog? = null,
    // System UI
    val toolBarHide: Boolean = true,
    // Menu state (for overflow menu)
    val isLocalTxt: Boolean = false,
    val isEpub: Boolean = false,
    val useReplaceRule: Boolean = false,
    val reSegment: Boolean = false,
    val delRubyTag: Boolean = false,
    val delHTag: Boolean = false,
    val sameTitleRemoved: Boolean = false,
    val isReadingProgressSyncConfigured: Boolean = false,
    // Config update trigger (notifies ReadView to run upBg/upStyle etc.)
    val configUpdateTrigger: Int = 0,
    // Menu config (from ReadBookConfig via repository)
    val menuConfig: ReadMenuConfig = ReadMenuConfig(),
) {
    val menuVisible: Boolean
        get() = menuState.visible
}

@Stable
data class ReadMenuConfig(
    val titleBarIconPosition: Int = 0,
    val readMenuFloatingBottomBar: Boolean = false,
    val readMenuBottomCornerRadius: Int = 0,
    val readMenuIconItemsPerRow: Int = 5,
    val readMenuIconRowCount: Int = 1,
    val readMenuBorderWidth: Int = 0,
    val readMenuBorderColor: Int = 0,
    val readMenuBorderColorNight: Int = 0,
    val readMenuBlurAlpha: Int = 60,
    val readMenuBlurRadius: Int = 24,
    val readMenuLensRadius: Float = 24f,
    val readMenuLiquidGlass: Boolean = false,
    val readMenuIconStyle: Int = 0,
    val readMenuIconShowText: Boolean = true,
    val titleBarCustomIcons: Map<String, String> = emptyMap(),
    val readMenuCustomIcons: Map<String, String> = emptyMap(),
)

sealed interface ReadBookIntent {
    // Initialization
    data class InitData(val intent: android.content.Intent) : ReadBookIntent
    data class InitReadBookConfig(val intent: android.content.Intent) : ReadBookIntent

    // Navigation
    data object NextPage : ReadBookIntent
    data object PrevPage : ReadBookIntent
    data object NextChapter : ReadBookIntent
    data object PrevChapter : ReadBookIntent
    data class OpenChapter(val index: Int, val pos: Int = 0) : ReadBookIntent
    data class SkipToPage(val pageIndex: Int) : ReadBookIntent

    // Menu
    data object ToggleMenu : ReadBookIntent
    data object ShowMenu : ReadBookIntent
    data object HideMenu : ReadBookIntent
    data class OpenReadMenuRoute(val route: ReadBookMenuRoute) : ReadBookIntent
    data object ReadMenuBack : ReadBookIntent

    // Search
    data class OpenSearch(val word: String?) : ReadBookIntent
    data object ExitSearch : ReadBookIntent
    data object ShowSearchMenu : ReadBookIntent
    data object HideSearchMenu : ReadBookIntent
    data class SetSearchResults(val results: List<SearchResult>, val index: Int) : ReadBookIntent
    data class SetSearchResultIndex(val index: Int) : ReadBookIntent
    data class SetShowingSearchResult(val value: Boolean) : ReadBookIntent
    data class NavigateToSearchResult(val result: SearchResult, val index: Int) : ReadBookIntent

    // Read aloud
    data object ToggleReadAloud : ReadBookIntent

    // Auto page
    data object ToggleAutoPage : ReadBookIntent
    data object StopAutoPage : ReadBookIntent

    // Content operations
    data object RefreshCurrentChapter : ReadBookIntent
    data object RefreshAllChapters : ReadBookIntent
    data object RefreshContentAfter : ReadBookIntent
    data class ChangeReplaceRule(val enabled: Boolean) : ReadBookIntent
    data object ToggleTranslation : ReadBookIntent

    // Change source
    data class ChangeSource(val book: Book, val toc: List<BookChapter>) : ReadBookIntent

    // Progress sync
    data class SureNewProgress(val progress: BookProgress) : ReadBookIntent
    data class SureSyncProgress(val progress: BookProgress) : ReadBookIntent

    // Bookmark
    data object AddBookmark : ReadBookIntent
    data class SaveBookmark(val bookmark: io.legado.app.data.entities.Bookmark) : ReadBookIntent
    data class DeleteBookmark(val bookmark: io.legado.app.data.entities.Bookmark) : ReadBookIntent

    // Text selection
    data object CancelSelect : ReadBookIntent

    // System UI
    data object UpSystemUiVisibility : ReadBookIntent
    data object UpContent : ReadBookIntent

    // Brightness
    data class SetBrightness(val value: Int) : ReadBookIntent
    data object ToggleBrightnessAuto : ReadBookIntent

    // Seek bar jump
    data class SeekToChapter(val index: Int) : ReadBookIntent

    // Sheet / Dialog
    data class ShowSheet(val sheet: ReadBookSheet) : ReadBookIntent
    data object DismissSheet : ReadBookIntent
    data class ShowDialog(val dialog: ReadBookDialog) : ReadBookIntent
    data object DismissDialog : ReadBookIntent

    // Source actions
    data object ShowLogin : ReadBookIntent
    data object PayAction : ReadBookIntent
    data object DisableSource : ReadBookIntent
    data object OpenSourceEdit : ReadBookIntent
    data object OpenBookInfo : ReadBookIntent
    data object OpenChapterList : ReadBookIntent

    // Tools
    data class RefreshImage(val src: String) : ReadBookIntent
    data class SaveImage(val src: String) : ReadBookIntent
    data object ReverseContent : ReadBookIntent
    data object ReverseRemoveSameTitle : ReadBookIntent
    data object RetranslateCurrentChapter : ReadBookIntent

    // Menu actions (moved from Activity)
    data object MenuUpdateToc : ReadBookIntent
    data object MenuCoverProgress : ReadBookIntent
    data object MenuSameTitleRemoved : ReadBookIntent
    data class MenuImageStyle(val style: String) : ReadBookIntent
    data object MenuGetProgress : ReadBookIntent
    data object MenuChangeSource : ReadBookIntent
    data object MenuBookChangeSource : ReadBookIntent
    data object MenuChapterChangeSource : ReadBookIntent
    data object MenuSettingReplace : ReadBookIntent
    data object MenuTocRegex : ReadBookIntent
    data object MenuRefreshDur : ReadBookIntent
    data object MenuRefreshAfter : ReadBookIntent
    data object MenuRefreshAll : ReadBookIntent
    data object MenuEnableReplace : ReadBookIntent
    data object MenuReSegment : ReadBookIntent
    data object MenuDelRubyTag : ReadBookIntent
    data object MenuDelHTag : ReadBookIntent
    data object MenuReverseContent : ReadBookIntent

    // Page anim config (selector dialog, needs Activity context)
    data object ShowPageAnimConfig : ReadBookIntent

    // Replace editor (needs Activity context for ActivityResult)
    data class OpenReplaceEditor(val id: Long, val pattern: String?) : ReadBookIntent
    data object ReplaceRuleChanged : ReadBookIntent

    // Font folder picker (needs Activity context for ActivityResult)
    data object OpenFontFolderPicker : ReadBookIntent

    // Read style SAF actions
    data object OpenReadStyleImagePicker : ReadBookIntent
    data object OpenReadStyleImport : ReadBookIntent
    data object OpenReadStyleExport : ReadBookIntent
    data class ReadStyleImageSelected(val uri: Uri) : ReadBookIntent
    data class ReadStyleConfigImportSelected(val uri: Uri) : ReadBookIntent
    data class ReadStyleConfigExportSelected(val uri: Uri) : ReadBookIntent
    data object SaveReadStyleConfig : ReadBookIntent
    data object AddReadStyleConfig : ReadBookIntent
    data object DeleteCurrentReadStyleConfig : ReadBookIntent

    // Bookshelf
    data object RemoveFromBookshelf : ReadBookIntent

    // Config update (triggers ReadView upBg/upStyle etc.)
    data class OnConfigUpdated(val values: List<Int>) : ReadBookIntent

    // Typed config mutation — single entry point for all ReadBookConfig changes
    data class UpdateConfig(val update: ConfigUpdate) : ReadBookIntent

    // Tool buttons config saved — recreate to refresh toolbar
    data object RefreshToolButtons : ReadBookIntent
    data object RefreshTitleBarIcons : ReadBookIntent

    // BgTextConfig (needs Activity for DialogFragment)
    data class OpenBgTextConfig(val index: Int) : ReadBookIntent

    // Day/night toggle
    data object ToggleDayNight : ReadBookIntent

    // Default font picker (needs Activity for AlertDialog)
    // Text action menu (moved from Activity)
    data class TextActionAloud(val text: String) : ReadBookIntent
    data class TextActionBookmark(val text: String) : ReadBookIntent
    data class TextActionReplace(val text: String) : ReadBookIntent
    data class TextActionSearchContent(val text: String) : ReadBookIntent
    data class TextActionDict(val text: String) : ReadBookIntent

    // Screen / selection config
    data object KeepLightChanged : ReadBookIntent
    data class TextSelectAbleChanged(val enabled: Boolean) : ReadBookIntent

    // Media / TTS
    data class MediaButtonPressed(val play: Boolean) : ReadBookIntent
    data class TtsProgress(val chapterStart: Int) : ReadBookIntent

    // Dialog callback bridge
    data object ReadAloudAction : ReadBookIntent

    // Read aloud config (needs Activity for DialogFragment)
    data object ShowReadAloudConfig : ReadBookIntent
    data object SelectSpeakEngine : ReadBookIntent
    data object OpenPreDownloadNumPicker : ReadBookIntent
    data object OpenCacheCleanTimePicker : ReadBookIntent
    data class SelectFont(val path: String) : ReadBookIntent
    data class SelectSystemTypeface(val index: Int) : ReadBookIntent
    data class SelectRegexColorFont(val ruleIndex: Int) : ReadBookIntent
    data class ApplyRegexColorFont(val path: String) : ReadBookIntent
    data class ColorSelected(val dialogId: Int, val color: Int) : ReadBookIntent

    // Simulated reading apply (clear chapter cache + reinit)
    data object ApplySimulatedReading : ReadBookIntent

    // Page anim changed (reload content + update view)
    data object PageAnimChanged : ReadBookIntent

    // Download chapters
    data class DownloadChapters(val start: Int, val end: Int) : ReadBookIntent

    // Show stack trace dialog
    data class ShowStackTrace(val text: String) : ReadBookIntent

    // Save chapter content (from chapter source change)
    data class SaveChapterContent(val content: String) : ReadBookIntent
}

sealed interface ReadBookEffect {
    // Toast
    data class ShowToast(val message: String) : ReadBookEffect
    data class LongToast(val message: String) : ReadBookEffect

    // Navigation / lifecycle
    data object Finish : ReadBookEffect
    data object Recreate : ReadBookEffect

    // ReadView operations (require Activity/View reference)
    data class UpdateReadViewConfig(val values: List<Int>) : ReadBookEffect
    data class UpContent(val relativePosition: Int, val resetPageOffset: Boolean) : ReadBookEffect
    data class UpPageAnim(val upRecorder: Boolean) : ReadBookEffect
    data object UpTime : ReadBookEffect
    data class UpBattery(val level: Int) : ReadBookEffect
    data object UpAloudState : ReadBookEffect
    data object UpSeekBar : ReadBookEffect
    data object UpMenuView : ReadBookEffect
    data object PageChanged : ReadBookEffect
    data object ContentLoadFinish : ReadBookEffect
    data class LayoutPageCompleted(val index: Int, val page: TextPage) : ReadBookEffect
    data object RefreshBookContent : ReadBookEffect

    // Menu / UI actions
    data object AddBookmark : ReadBookEffect
    data object CancelSelect : ReadBookEffect
    data object UpSystemUiVisibility : ReadBookEffect
    data class SetBrightness(val value: Int) : ReadBookEffect
    data object ToggleBrightnessAuto : ReadBookEffect

    // Read aloud / auto page
    data object ToggleReadAloud : ReadBookEffect
    data object ToggleAutoPage : ReadBookEffect
    data object StopAutoPage : ReadBookEffect

    // Search
    data class OpenSearchActivity(val word: String?) : ReadBookEffect
    data class NavigateToSearchResult(val result: SearchResult) : ReadBookEffect
    data object ExitSearch : ReadBookEffect

    // Source actions
    data object ShowLogin : ReadBookEffect
    data object OpenSourceEdit : ReadBookEffect
    data object OpenBookInfo : ReadBookEffect
    data object OpenChapterList : ReadBookEffect
    data class ShowPayDialog(val book: Book, val chapter: BookChapter) : ReadBookEffect

    // Menu actions that need Activity
    data object MenuChangeSource : ReadBookEffect
    data object MenuBookChangeSource : ReadBookEffect
    data object MenuChapterChangeSource : ReadBookEffect
    data object MenuSettingReplace : ReadBookEffect
    data object MenuTocRegex : ReadBookEffect
    data class MenuImageStyleChanged(val style: String) : ReadBookEffect
    data class SyncBookProgress(val book: Book) : ReadBookEffect

    // Text action menu (needs Activity for View operations)
    data object TextActionAloudSelect : ReadBookEffect
    data class TextActionSpeak(val text: String) : ReadBookEffect
    data class TextActionReplace(val text: String) : ReadBookEffect

    // Screen / selection
    data object UpScreenTimeOut : ReadBookEffect
    data class UpTextSelectAble(val enabled: Boolean) : ReadBookEffect

    // TTS
    data class UpTtsAloudSpan(val chapterStart: Int) : ReadBookEffect

    // Dialogs (Activity-driven)
    data object ShowConfirmSkipToChapter : ReadBookEffect
    data object SelectSpeakEngine : ReadBookEffect
    data object OpenPreDownloadNumPicker : ReadBookEffect
    data object OpenCacheCleanTimePicker : ReadBookEffect

    // Replace editor (needs Activity context for ActivityResult)
    data class OpenReplaceEditor(val id: Long, val pattern: String?) : ReadBookEffect

    // Font folder picker
    data object OpenFontFolderPicker : ReadBookEffect

    // Read style SAF actions
    data object OpenReadStyleImagePicker : ReadBookEffect
    data object OpenReadStyleImport : ReadBookEffect
    data object OpenReadStyleExport : ReadBookEffect

    // Day/night toggle
    data object ToggleDayNight : ReadBookEffect

    // Page anim changed — Activity calls readView.upPageAnim() + ReadBook.loadContent(false)
    data object PageAnimChanged : ReadBookEffect

    // Download chapters — Activity calls CacheBook.start()
    data class DownloadChapters(val start: Int, val end: Int) : ReadBookEffect

    // Show stack trace dialog
    data class ShowStackTrace(val text: String) : ReadBookEffect
}

@Immutable
sealed interface ReadBookSheet {
    data object PageAnim : ReadBookSheet
    data object Download : ReadBookSheet
    data object Charset : ReadBookSheet
    data object SimulatedReading : ReadBookSheet
    data object ToolButtonConfig : ReadBookSheet
    data object TitleBarIconConfig : ReadBookSheet
    data object EffectiveReplaces : ReadBookSheet
    data object ContentEdit : ReadBookSheet
    data object AppLog : ReadBookSheet
    data class ChangeChapterSource(val chapterIndex: Int, val chapterTitle: String) : ReadBookSheet
    data object ChangeBookSource : ReadBookSheet
    data object ShadowSet : ReadBookSheet
    data object AutoRead : ReadBookSheet
    data object UnderlineConfig : ReadBookSheet
    data object FontSelect : ReadBookSheet
    data object RegexColorConfig : ReadBookSheet
    data object MoreConfig : ReadBookSheet
    data object BgTextConfig : ReadBookSheet
    data object ReadAloudConfig : ReadBookSheet
    data object ClickActionConfig : ReadBookSheet
    data object PageKeyConfig : ReadBookSheet
    data object InfoConfig : ReadBookSheet
    data class Dict(val word: String) : ReadBookSheet
    data class Bookmark(
        val bookmark: io.legado.app.data.entities.Bookmark,
        val editPos: Int = -1,
    ) : ReadBookSheet

    data class Photo(
        val src: String,
        val sourceOrigin: String? = null,
    ) : ReadBookSheet
}

@Immutable
sealed interface ReadBookDialog {
    data class ConfirmRestoreProgress(val progress: BookProgress) : ReadBookDialog
    data class SureSyncProgress(val progress: BookProgress) : ReadBookDialog
    data object ConfirmSkipToChapter : ReadBookDialog
}

/**
 * Typed config mutations — replaces direct `ReadBookConfig.xxx = value` + `postEvent(UP_CONFIG, ...)`.
 * Each variant carries [codes] that map to the legacy UP_CONFIG integer codes for rendering layer compatibility.
 */
@Immutable
sealed interface ConfigUpdate {
    val codes: List<Int>

    // --- Text style ---
    data class TextSize(val value: Int) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class LetterSpacing(val value: Float) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class LineSpacing(val value: Int) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class ParagraphSpacing(val value: Int) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class ParagraphIndent(val value: String) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class TextItalic(val value: Boolean) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class TextBold(val value: Int) : ConfigUpdate { override val codes = listOf(8, 9, 6) }
    data class TextColor(val color: Int) : ConfigUpdate { override val codes = listOf(2, 5, 9) }
    data class TextAccentColor(val color: Int) : ConfigUpdate { override val codes = listOf(2, 5, 9) }

    // --- Title style ---
    data class TitleMode(val value: Int) : ConfigUpdate { override val codes = listOf(5) }
    data class TitleBold(val value: Int) : ConfigUpdate { override val codes = listOf(8, 9, 6) }
    data class TitleSegScaling(val value: Float) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class TitleLineSpacingExtra(val value: Int) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class TitleLineSpacingSub(val value: Int) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class TitleSize(val value: Int) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class TitleTopSpacing(val value: Int) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class TitleBottomSpacing(val value: Int) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class TitleColor(val color: Int) : ConfigUpdate { override val codes = listOf(2, 5, 9) }

    // --- Header / footer tips ---
    data class HeaderMode(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class FooterMode(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class TipHeaderLeft(val value: Int) : ConfigUpdate { override val codes = listOf(2, 6) }
    data class TipHeaderMiddle(val value: Int) : ConfigUpdate { override val codes = listOf(2, 6) }
    data class TipHeaderRight(val value: Int) : ConfigUpdate { override val codes = listOf(2, 6) }
    data class TipFooterLeft(val value: Int) : ConfigUpdate { override val codes = listOf(2, 6) }
    data class TipFooterMiddle(val value: Int) : ConfigUpdate { override val codes = listOf(2, 6) }
    data class TipFooterRight(val value: Int) : ConfigUpdate { override val codes = listOf(2, 6) }
    data class HeaderFontSize(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class TipHeaderColor(val color: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class TipFooterColor(val color: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class TipDividerColor(val color: Int) : ConfigUpdate { override val codes = listOf(2) }

    // --- Layout / style ---
    data class StyleSelect(val index: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class ShareLayout(val value: Boolean) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class PageAnim(val value: Int) : ConfigUpdate { override val codes = listOf(1) }

    // --- Menu colors ---
    data class MenuBgColor(val color: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class MenuAccentColor(val color: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class MenuContainerColor(val color: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class MenuBgColorNight(val color: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class MenuAccentColorNight(val color: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class MenuContainerColorNight(val color: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class MenuColorMode(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class ReadBarStyle(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }

    // --- Menu bar border ---
    data class BorderWidth(val value: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class BorderColor(val color: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }
    data class BorderColorNight(val color: Int) : ConfigUpdate { override val codes = listOf(1, 2, 5) }

    // --- Shadow ---
    data class TextShadow(val value: Boolean) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class ShadowRadius(val value: Float) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class ShadowDx(val value: Float) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class ShadowDy(val value: Float) : ConfigUpdate { override val codes = listOf(8, 5) }
    data class ShadowColor(val color: Int) : ConfigUpdate { override val codes = listOf(2, 5, 9) }

    // --- Underline ---
    data class Underline(val value: Boolean) : ConfigUpdate { override val codes = listOf(6, 9, 11) }
    data class DottedLine(val value: Boolean) : ConfigUpdate { override val codes = listOf(6, 9, 11) }
    data class UnderlineExtend(val value: Boolean) : ConfigUpdate { override val codes = listOf(6, 9, 11) }
    data class UnderlineHeight(val value: Int) : ConfigUpdate { override val codes = listOf(8, 9, 6) }
    data class UnderlinePadding(val value: Int) : ConfigUpdate { override val codes = listOf(8, 9, 6) }
    data class DottedBase(val value: Float) : ConfigUpdate { override val codes = listOf(6, 8, 10) }
    data class DottedRatio(val value: Float) : ConfigUpdate { override val codes = listOf(6, 8, 10) }
    data class UnderlineColor(val color: Int) : ConfigUpdate { override val codes = listOf(2) }

    // --- Body padding ---
    data class PaddingTop(val value: Int) : ConfigUpdate { override val codes = listOf(10, 5) }
    data class PaddingBottom(val value: Int) : ConfigUpdate { override val codes = listOf(10, 5) }
    data class PaddingLeft(val value: Int) : ConfigUpdate { override val codes = listOf(10, 5) }
    data class PaddingRight(val value: Int) : ConfigUpdate { override val codes = listOf(10, 5) }

    // --- Header padding ---
    data class HeaderPaddingTop(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class HeaderPaddingBottom(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class HeaderPaddingLeft(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class HeaderPaddingRight(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class ShowHeaderLine(val value: Boolean) : ConfigUpdate { override val codes = listOf(2) }

    // --- Footer padding ---
    data class FooterPaddingTop(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class FooterPaddingBottom(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class FooterPaddingLeft(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class FooterPaddingRight(val value: Int) : ConfigUpdate { override val codes = listOf(2) }
    data class ShowFooterLine(val value: Boolean) : ConfigUpdate { override val codes = listOf(2) }

    // --- Background / display ---
    data class BgAlpha(val value: Int) : ConfigUpdate { override val codes = listOf(3) }
    data class StatusIconDark(val value: Boolean) : ConfigUpdate { override val codes = listOf(5) }
    data class MenuIconShowText(val value: Boolean) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class MenuIconStyle(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class MenuIconItemsPerRow(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class MenuIconRowCount(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class MenuBottomCornerRadius(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class FloatingBottomBar(val value: Boolean) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class MenuLiquidGlass(val value: Boolean) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class MenuBlurRadius(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class MenuBlurAlpha(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class MenuLensRadius(val value: Float) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class MenuCustomIcon(val id: String, val path: String) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class TitleBarCustomIcon(val id: String, val path: String) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class TitleBarIconPosition(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }

    // --- System UI (also updates AppConfig) ---
    data class HideStatusBar(val value: Boolean) : ConfigUpdate { override val codes = listOf(0, 2) }
    data class HideNavigationBar(val value: Boolean) : ConfigUpdate { override val codes = listOf(0, 2) }

    // --- Display toggles ---
    data class PaddingDisplayCutouts(val value: Boolean) : ConfigUpdate { override val codes = listOf(2) }
    data class TitleBarMode(val value: String) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class TextFullJustify(val value: Boolean) : ConfigUpdate { override val codes = listOf(5) }
    data class TextBottomJustify(val value: Boolean) : ConfigUpdate { override val codes = listOf(5) }
    data class AdaptSpecialStyle(val value: Boolean) : ConfigUpdate { override val codes = listOf(5) }
    data class UseZhLayout(val value: Boolean) : ConfigUpdate { override val codes = listOf(5) }
    data class ShowBrightnessView(val value: Boolean) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class UseUnderlineGlobal(val value: Boolean) : ConfigUpdate { override val codes = listOf(5) }
    data class ReadSliderMode(val value: String) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class DoubleHorizontalPage(val value: String) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class ProgressBarBehavior(val value: String) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class NoAnimScrollPage(val value: Boolean) : ConfigUpdate { override val codes = emptyList<Int>() }
    data class ShowReadTitleAddition(val value: Boolean) : ConfigUpdate { override val codes = emptyList<Int>() }

    // --- Regex color rules ---
    data class RegexColorRules(val rules: List<io.legado.app.help.config.RegexColorRule>) : ConfigUpdate { override val codes = listOf(8, 5) }

    // --- Auto read ---
    data class AutoReadSpeed(val value: Int) : ConfigUpdate { override val codes = emptyList<Int>() }
}
