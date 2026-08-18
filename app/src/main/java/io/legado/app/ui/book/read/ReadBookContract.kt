package io.legado.app.ui.book.read

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.constant.ReadMenuBlurStyle
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.domain.model.settings.ReadStyleItem
import io.legado.app.domain.usecase.BookmarkTargetVerdict
import io.legado.app.model.translation.TranslationChapterStatus
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.sheet.ReaderBookSheetTab
import io.legado.app.ui.book.searchContent.SearchResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.uuid.Uuid

@Stable
data class ReminderUiState(
    val id: String = Uuid.random().toString(),
    val message: String,
    val actionText: String? = null,
    val actionIntent: ReadBookIntent? = null,
    val type: ReminderType? = null,
)

sealed interface ReminderType {
    data class DayNightReminder(val targetIsNight: Boolean) : ReminderType
}

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
    data object ReadAloud : ReadBookMenuRoute
    data object AutoRead : ReadBookMenuRoute
    data object TypographyConfig : ReadBookMenuRoute
    data object InformationConfig : ReadBookMenuRoute
    data object PaddingConfig : ReadBookMenuRoute
}

@Stable
data class ReadBookStyleConfig(
    val styleSelect: Int = 0,
    val styleName: String = "文字",
    val bgAlpha: Float = 1f,
    // Day mode
    val bgType: Int = 0,
    val bgStr: String = "#EEEEEE",
    val darkStatusIcon: Boolean = true,
    // Night mode
    val bgTypeNight: Int = 0,
    val bgStrNight: String = "#000000",
    val darkStatusIconNight: Boolean = false,
    // E-Ink mode
    val bgTypeEInk: Int = 0,
    val bgStrEInk: String = "#FFFFFF",
    val darkStatusIconEInk: Boolean = true,
    // Text
    val textSize: Int = 20,
    val textColor: String = "#3E3D3B",
    val textColorNight: String = "#CCCCCC",
    val textColorEInk: String = "#000000",
    val textFont: String = "",
    val titleFont: String = "",
    // Page anim
    val pageAnim: Int = 0,
    val pageAnimEInk: Int = 4,
    // Layout
    val shareLayout: Boolean = false,
    // 排版自带的阅读菜单配色，作为 DataStore 里 readMenu*Color 为 0（未自定义）时的回退
    val menuBgColorDay: Int = 0,
    val menuBgColorNight: Int = 0,
    val menuAccentColorDay: Int = 0,
    val menuAccentColorNight: Int = 0,
    // Config list for style selector
    val configCount: Int = 1,
    val styleItems: ImmutableList<ReadStyleItem> = persistentListOf(),
) {
    // Computed properties for background mode
    val isDayBgImage: Boolean get() = bgType != 0
    val isNightBgImage: Boolean get() = bgTypeNight != 0
}

@Stable
data class ReadSheetConfigUiState(
    /** 标题字号 <8 时按「正文字号 + 偏移」解释，故正文字号也要进弹层快照。 */
    val textSize: Int = 20,
    val letterSpacing: Float = 0f,
    val lineSpacing: Int = 0,
    val paragraphSpacing: Int = 0,
    val paragraphIndentCount: Int = 2,
    val textItalic: Boolean = false,
    val textBold: Int = 0,
    val chineseConverterType: Int = 0,
    val textColor: Int = 0,
    val textAccentColor: Int = 0,
    val titleMode: Int = 0,
    val titleBold: Int = 0,
    val titleSegType: Int = 0,
    val titleSegDistance: Int = 0,
    val titleSegFlag: String = "",
    val titleSegScaling: Float = 1f,
    val titleLineSpacingExtra: Int = 0,
    val titleLineSpacingSub: Int = 0,
    val titleSize: Int = 0,
    val titleTopSpacing: Int = 0,
    val titleBottomSpacing: Int = 0,
    val titleColor: Int = 0,
    val titleColorNight: Int = 0,
    val textColorDay: Int = 0,
    val textColorNight: Int = 0,
    val textShadow: Boolean = false,
    val textShadowColor: Int = 0,
    val shadowRadius: Float = 0f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0f,
    val underline: Boolean = false,
    val dottedLine: Boolean = false,
    val underlineExtend: Boolean = false,
    val underlineColor: Int = 0,
    val underlineHeight: Int = 0,
    val underlinePadding: Int = 0,
    val dottedBase: Float = 0f,
    val dottedRatio: Float = 0f,
    val paddingTop: Int = 0,
    val paddingBottom: Int = 0,
    val paddingLeft: Int = 0,
    val paddingRight: Int = 0,
    val headerPaddingTop: Int = 0,
    val headerPaddingBottom: Int = 0,
    val headerPaddingLeft: Int = 0,
    val headerPaddingRight: Int = 0,
    val footerPaddingTop: Int = 0,
    val footerPaddingBottom: Int = 0,
    val footerPaddingLeft: Int = 0,
    val footerPaddingRight: Int = 0,
    // 页眉页脚字体
    val headerFont: String = "",
    val footerFont: String = "",
    val headerFontSize: Int = 12,
    val footerFontSize: Int = 12,
    val applyHeaderStyle: Boolean = true,
    /** -1 表示"跟随主题分割线"，0 表示"跟随正文颜色"。 */
    val tipDividerColor: Int = 0,
    val headerMode: Int = 0,
    val footerMode: Int = 0,
    val showHeaderLine: Boolean = false,
    val showFooterLine: Boolean = false,
    val tipHeaderLeft: Int = 0,
    val tipHeaderMiddle: Int = 0,
    val tipHeaderRight: Int = 0,
    val tipFooterLeft: Int = 0,
    val tipFooterMiddle: Int = 0,
    val tipFooterRight: Int = 0,
    val customTipHeaderLeft: String = "",
    val customTipHeaderMiddle: String = "",
    val customTipHeaderRight: String = "",
    val customTipFooterLeft: String = "",
    val customTipFooterMiddle: String = "",
    val customTipFooterRight: String = "",
    /** 0 表示"跟随正文颜色"，展示时回退到 [textColorDay] / [textColorNight]。 */
    val tipHeaderColor: Int = 0,
    val tipHeaderColorNight: Int = 0,
    val tipFooterColor: Int = 0,
    val tipFooterColorNight: Int = 0,
    val textFullJustify: Boolean = true,
    val textBottomJustify: Boolean = true,
    val configNames: ImmutableList<String> = persistentListOf(),
)

/** 书签/笔记跳转前校验未通过的目标，弹「仍跳转」确认框。 */
@Stable
data class PendingBookmarkTarget(
    val chapterIndex: Int,
    val chapterPos: Int,
    val verdict: BookmarkTargetVerdict,
)

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
    val activeReminder: ReminderUiState? = null,
    // Search
    val searchMenuVisible: Boolean = false,
    val isShowingSearchResult: Boolean = false,
    val searchContentQuery: String = "",
    val searchResultList: ImmutableList<SearchResult> = persistentListOf(),
    val searchResultIndex: Int = 0,
    // Read aloud / auto page
    val isReadAloudRunning: Boolean = false,
    val isReadAloudPaused: Boolean = false,
    val readAloudEngineName: String = "",
    val readAloudCharacterName: String = "",
    val readAloudRoleType: SpeechRoleType = SpeechRoleType.Narrator,
    val readAloudChapterPosition: Int = 0,
    val readAloudChapterLength: Int = 0,
    val isAutoPage: Boolean = false,
    // Seek bar
    val seekProgress: Int = 0,
    val seekMax: Int = 0,
    // Replace rules
    val replaceRuleEnabled: Boolean = false,
    val effectiveReplaceCount: Int = 0,
    val effectiveContentProcessCount: Int = 0,
    val effectiveReplaceRules: ImmutableList<ReplaceRule> = persistentListOf(),
    val allReplaceRules: ImmutableList<ReplaceRuleItemUi> = persistentListOf(),
    val chineseConverterActive: Boolean = false,
    // Translation
    val translationMode: Boolean = false,
    val translationStatus: TranslationChapterStatus = TranslationChapterStatus.Idle,
    // Chapter info
    val curTextChapter: TextChapter? = null,
    // Time / battery (from EventBus)
    val time: String = "",
    val battery: Int = 0,
    val menuState: ReadBookMenuState = ReadBookMenuState(),
    // Active sheet / dialog
    val activeSheet: ReadBookSheet? = null,
    val activeDialog: ReadBookDialog? = null,
    /** 书签/笔记跳转前校验不通过时的待确认目标（弹确认框）。 */
    val pendingBookmarkTarget: PendingBookmarkTarget? = null,
    // Menu state (for overflow menu)
    val isLocalTxt: Boolean = false,
    val isEpub: Boolean = false,
    val useReplaceRule: Boolean = false,
    val reSegment: Boolean = false,
    val delRubyTag: Boolean = false,
    val delHTag: Boolean = false,
    val sameTitleRemoved: Boolean = false,
    val isReadingProgressSyncConfigured: Boolean = false,
    // Content edit
    // 正文编辑域状态见 ContentEditUiState —— 由 ReadContentEditDelegate 独立持有
    val preDownloadNum: Int = 10,
    val preSynthesisConcurrency: Int = 3,
    val audioCacheCleanTime: Int = 10,
    // Read aloud config
    val readAloudIgnoreAudioFocus: Boolean = false,
    val readAloudPauseOnPhoneCall: Boolean = false,
    val readAloudWakeLock: Boolean = false,
    val showReadAloudCapsule: Boolean = true,
    val capsuleAutoCollapse: Boolean = true,
    val readAloudCapsuleOffsetX: Float = 0f,
    val readAloudCapsuleOffsetY: Float = 0f,
    val readAloudMediaButtonPerNext: Boolean = false,
    val readAloudByPage: Boolean = false,
    val readAloudSystemMediaCompat: Boolean = true,
    val readAloudAndroidMediaControl: Boolean = false,
    val readAloudStreamAudio: Boolean = false,
    val readAloudTtsFollowSys: Boolean = false,
    val readAloudTtsSpeechRate: Int = 10,
    val readAloudTtsTimer: Int = 0,
    val readAloudFinishCurrentChapterAfterTimer: Boolean = false,
    val speechAnalysisMode: String = "rule",
    val useMultiSpeaker: Boolean = true,
    val defaultReadAloudInterface: String = ReadAloudSettingsRepository.DEFAULT_INTERFACE_CLASSIC,
    val readAloudParagraphInterval: Int = 0,
    // Style config (reactive state for ReadBookConfig)
    val styleConfig: ReadBookStyleConfig = ReadBookStyleConfig(),
    val sheetConfig: ReadSheetConfigUiState = ReadSheetConfigUiState(),
    // Menu config (from ReadBookConfig via repository)
    val menuConfig: ReadMenuConfig = ReadMenuConfig(),
    // AI 域状态见 ReadAiUiState —— 由 ReadAiDelegate 独立持有
    val eyeProtection: EyeProtectionUiState = EyeProtectionUiState(),
) {
    val menuVisible: Boolean
        get() = menuState.visible
}

/** 护眼模式设置，来源是 ThemeSettings，与外观设置共用同一份值。 */
@Stable
data class EyeProtectionUiState(
    val enabled: Boolean = false,
    val intensity: Int = 50,
    val autoNight: Boolean = false,
    val schedule: Boolean = false,
    val startTime: String = "22:00",
    val endTime: String = "07:00",
) {
    val configured: Boolean
        get() = enabled || autoNight
}

@Stable
data class ContentProcessConfigUiState(
    val isLoading: Boolean = false,
    val items: ImmutableList<ContentProcessItemUi> = persistentListOf(),
    val deleteItem: ContentProcessItemUi? = null,
    val errorMessage: String? = null,
)

@Stable
data class ContentProcessItemUi(
    val id: String,
    val kind: String,
    val actionType: String,
    val enabled: Boolean,
    val chapterIndex: Int,
    val selectedText: String,
    val replacementText: String,
    val createdAt: Long,
)

@Stable
data class ReplaceRuleItemUi(
    val id: Long,
    val name: String,
    val group: String?,
    val pattern: String,
    val replacement: String,
    val enabled: Boolean,
)

@Stable
data class ReadMenuConfig(
    val titleBarIconPosition: Int = 3,
    val showTitleBarIcons: Boolean = false,
    val readMenuFloatingBottomBar: Boolean = true,
    val readMenuBottomCornerRadius: Int = 32,
    val readMenuIconItemsPerRow: Int = 5,
    val readMenuIconRowCount: Int = 1,
    val readMenuBorderWidth: Int = 1,
    val readMenuBorderColor: Int = 0,
    val readMenuBorderColorNight: Int = 0,
    val readMenuTextColor: Int = 0,
    val readMenuTextColorNight: Int = 0,
    val readMenuBlurAlpha: Int = 100,
    val readMenuBlurColor: Int = 0,
    val readMenuBlurColorNight: Int = 0,
    val readMenuPaletteStyle: String = "",
    val readMenuBlurRadius: Int = 24,
    val readMenuLensRadius: Float = 24f,
    val readMenuTopBarBlurMode: Int = ReadMenuBlurMode.None,
    val readMenuBottomBarBlurMode: Int = ReadMenuBlurMode.None,
    val readMenuTopBarLiquidGlassButtons: Boolean = false,
    val readMenuTopBarMergeButtons: Boolean = false,
    val readMenuTopBarTitleCapsule: Boolean = false,
    val readMenuBottomBarLiquidGlassButtons: Boolean = false,
    val readMenuFloatingIconLiquidGlass: Boolean = false,
    val readMenuTopBarBlurStyle: Int = ReadMenuBlurStyle.Solid,
    val readMenuBottomBarBlurStyle: Int = ReadMenuBlurStyle.Solid,
    val readMenuIconStyle: Int = 1,
    val titleBarIconStyle: Int = 1,
    val readMenuIconShowText: Boolean = false,
    val readSliderMode: String = "0",
    val titleBarCustomIcons: ImmutableMap<String, String> = persistentMapOf(),
    val readMenuCustomIcons: ImmutableMap<String, String> = persistentMapOf(),
    val titleBarButtons: ImmutableList<ReadBookButtonConfigItem> = persistentListOf(),
    val bottomBarButtons: ImmutableList<ReadBookButtonConfigItem> = persistentListOf(),
    val moreActionItems: ImmutableList<ReadBookButtonConfigItem> = persistentListOf(),
    val showBrightnessView: String = "0",
    val brightnessVwPos: String = "1",
    val readBrightness: Int = 100,
    val brightnessAuto: Boolean = true,
    val showMenuIcon: Boolean = false,
    val titleBarCompact: Boolean = false,
)

@Immutable
data class ReadBookButtonConfigItem(
    val id: String,
    val enabled: Boolean,
)

internal val ReadBookButtonIds = listOf(
    "ai_summary",
    "ai_rewrite",
    "more_actions",
    "search",
    "auto_page",
    "catalog",
    "read_aloud",
    "eye_protection",
    "setting",
    "addBookmark",
    "theme",
    "prev_chapter",
    "next_chapter",
    "replace",
    "replace_badge",
    "translate",
    "refresh_current",
)

internal val MoreActionIds = listOf(
    "change_source", "refresh", "download", "edit_content", "add_bookmark",
    "text_processing", "reverse_content", "re_segment",
    "del_ruby", "del_h", "toc_rule", "charset", "image_style", "page_anim",
    "simulated_reading", "get_progress", "cover_progress", "highlight_rule",
    "bottom_button_config", "log",
)

@Immutable
data class ReadBookInitRequest(
    val bookUrl: String? = null,
    val inBookshelf: Boolean = true,
    val chapterChanged: Boolean = false,
    val chapterIndex: Int = -1,
    val chapterPos: Int = -1,
)

sealed interface ReadBookIntent {
    // Initialization
    data class InitData(val request: ReadBookInitRequest) : ReadBookIntent
    data class InitReadBookConfig(val request: ReadBookInitRequest) : ReadBookIntent
    data class CheckSwitchDayNight(val lux: Float) : ReadBookIntent
    data object DismissReminder : ReadBookIntent

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
    data class OpenSearch(
        val word: String?,
        val autoFocus: Boolean = true,
    ) : ReadBookIntent
    data object ExitSearch : ReadBookIntent
    data object ShowSearchMenu : ReadBookIntent
    data object HideSearchMenu : ReadBookIntent
    data class SetSearchResults(val results: List<SearchResult>, val index: Int, val query: String? = null) : ReadBookIntent
    data class SetSearchResultIndex(val index: Int) : ReadBookIntent
    data class SetShowingSearchResult(val value: Boolean) : ReadBookIntent
    data class NavigateSearchResultByOffset(val offset: Int) : ReadBookIntent
    data class NavigateToSearchResult(val result: SearchResult, val index: Int) : ReadBookIntent
    data object RestoreLastBookProgress : ReadBookIntent
    data object KeepCurrentBookProgress : ReadBookIntent

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
    data class SetReplaceRuleEnabled(val id: Long, val enabled: Boolean) : ReadBookIntent
    data class MoveReplaceRule(val draggedId: Long, val anchorId: Long, val afterAnchor: Boolean) :
        ReadBookIntent
    data object ToggleTranslation : ReadBookIntent
    data object OpenChapterSummary : ReadBookIntent
    data object OpenAiCurrentChapterRewrite : ReadBookIntent
    data object RetryChapterSummary : ReadBookIntent
    data class SetChapterSummaryReasoningLevel(val level: AiReasoningLevel) : ReadBookIntent
    data object LoadContentProcesses : ReadBookIntent
    data class ToggleContentProcess(val id: String, val enabled: Boolean) : ReadBookIntent
    data class RequestDeleteContentProcess(val item: ContentProcessItemUi) : ReadBookIntent
    data object ConfirmDeleteContentProcess : ReadBookIntent
    data object DismissDeleteContentProcess : ReadBookIntent

    // Change source
    data class ChangeSourceBook(val book: Book) : ReadBookIntent
    data class ChangeSource(val book: Book, val toc: List<BookChapter>) : ReadBookIntent
    data class AddSourceAsNewBook(val book: Book, val toc: List<BookChapter>) : ReadBookIntent

    // Activity result intents
    data class OpenChapterResult(val index: Int, val chapterPos: Int) : ReadBookIntent
    data object SourceEditResult : ReadBookIntent
    data object ReplaceRuleResult : ReadBookIntent
    data class BookInfoResult(val bookDeleted: Boolean) : ReadBookIntent
    data class FontFolderSelected(val uri: Uri) : ReadBookIntent

    // Progress sync
    data class SureNewProgress(val progress: BookProgress) : ReadBookIntent
    data class SureSyncProgress(val progress: BookProgress) : ReadBookIntent

    // Bookmark
    data object AddBookmark : ReadBookIntent

    /** 下滑手势：本页无书签则直接存，有则取消。 */
    data object ToggleBookmark : ReadBookIntent
    data class SaveBookmark(val bookmark: io.legado.app.data.entities.Bookmark) : ReadBookIntent
    data class DeleteBookmark(val bookmark: io.legado.app.data.entities.Bookmark) : ReadBookIntent

    // Text selection
    data object CancelSelect : ReadBookIntent

    // System UI
    data object UpSystemUiVisibility : ReadBookIntent
    data object UpContent : ReadBookIntent

    // Brightness
    data class SetBrightness(val value: Int) : ReadBookIntent
    data class ToggleBrightnessAuto(val auto: Boolean) : ReadBookIntent

    // Seek bar jump
    data class SeekToChapter(val index: Int) : ReadBookIntent

    // Sheet / Dialog
    data class ShowSheet(val sheet: ReadBookSheet) : ReadBookIntent
    data object DismissSheet : ReadBookIntent
    data class SetActiveSheet(val sheet: ReadBookSheet?) : ReadBookIntent
    data class ShowDialog(val dialog: ReadBookDialog) : ReadBookIntent
    data class ResolveReadRecordAlias(val merge: Boolean, val rememberChoice: Boolean = false) : ReadBookIntent
    /** 清除所有持久化的未知作者决定，使冲突可以再次由用户确认。 */
    data object ClearReadRecordAliasDecisions : ReadBookIntent
    data object DismissDialog : ReadBookIntent

    // Source actions
    data object ShowLogin : ReadBookIntent
    data object PayAction : ReadBookIntent
    data object ConfirmPayAction : ReadBookIntent
    data object DisableSource : ReadBookIntent
    data object OpenSourceEdit : ReadBookIntent
    data class OpenSourceEditByUrl(val sourceUrl: String) : ReadBookIntent
    data object OpenBookInfo : ReadBookIntent
    data object OpenBookInfoDirect : ReadBookIntent
    data object OpenChapterList : ReadBookIntent
    data object OpenChapterUrl : ReadBookIntent
    data class SourceCustomButton(val longClick: Boolean) : ReadBookIntent
    data object ToggleReadUrlInBrowser : ReadBookIntent

    // Content edit
    data object OpenContentEdit : ReadBookIntent
    data object LoadContentEdit : ReadBookIntent
    data class SaveContentEdit(val content: String, val saveToSource: Boolean) : ReadBookIntent
    data object ResetContentEdit : ReadBookIntent
    data class SetContentEditText(val text: String) : ReadBookIntent
    data class SetContentEditSaveToSource(val value: Boolean) : ReadBookIntent

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
    data class TocRegexResult(val tocRegex: String) : ReadBookIntent
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
    data class DisableEffectiveReplace(val rule: ReplaceRule) : ReadBookIntent
    data object DisableChineseConverter : ReadBookIntent
    data object DisableReSegment : ReadBookIntent

    // Font folder picker (needs Activity context for ActivityResult)
    data object OpenFontFolderPicker : ReadBookIntent

    // Read style SAF actions
    data object OpenReadStyleImagePicker : ReadBookIntent
    data class OpenReadStyleImagePickerForMode(val isNight: Boolean) : ReadBookIntent
    data object OpenReadStyleImport : ReadBookIntent
    data object OpenReadStyleExport : ReadBookIntent
    data class ReadStyleImageSelected(val uri: Uri) : ReadBookIntent
    data class ReadStyleImageSelectedForMode(val uri: Uri, val isNight: Boolean) : ReadBookIntent
    data class BookmarkBadgeImageSelected(val uri: Uri) : ReadBookIntent
    data object ClearBookmarkBadgeImage : ReadBookIntent
    data class ReadStyleConfigImportSelected(val uri: Uri) : ReadBookIntent
    data class ReadStyleConfigExportSelected(val uri: Uri) : ReadBookIntent
    data object SaveReadStyleConfig : ReadBookIntent
    data object AddReadStyleConfig : ReadBookIntent
    data object DeleteCurrentReadStyleConfig : ReadBookIntent
    data class ApplyPresetTheme(val presetIndex: Int) : ReadBookIntent

    // Bookshelf
    data object RemoveFromBookshelf : ReadBookIntent

    // Typed config mutation — single entry point for all ReadBookConfig changes
    data class UpdateConfig(val update: ConfigUpdate) : ReadBookIntent

    // Highlight rules
    data object AddHighlightRule : ReadBookIntent
    data class EditHighlightRule(val rule: HighlightRule) : ReadBookIntent
    data class ToggleHighlightRule(val rule: HighlightRule, val enabled: Boolean) : ReadBookIntent
    data class SaveHighlightRule(val rule: HighlightRule) : ReadBookIntent
    data object DismissHighlightRuleEdit : ReadBookIntent
    data class RequestDeleteHighlightRule(val rule: HighlightRule) : ReadBookIntent
    data object ConfirmDeleteHighlightRule : ReadBookIntent
    data object DismissDeleteHighlightRule : ReadBookIntent
    data class MoveHighlightRule(val from: Int, val to: Int) : ReadBookIntent
    data object SaveHighlightRuleOrder : ReadBookIntent
    data class ImportHighlightRuleSource(val text: String) : ReadBookIntent
    data object OpenHighlightRuleImportPicker : ReadBookIntent
    data class HighlightRuleImportFileSelected(val uri: Uri) : ReadBookIntent
    data object CancelHighlightRuleImport : ReadBookIntent
    data class ToggleHighlightRuleImportSelection(val index: Int) : ReadBookIntent
    data class ToggleHighlightRuleImportAll(val isSelected: Boolean) : ReadBookIntent
    data class UpdateHighlightRuleImportItem(
        val index: Int,
        val rule: HighlightRule,
    ) : ReadBookIntent
    data object SaveImportedHighlightRules : ReadBookIntent
    data object ExportHighlightRules : ReadBookIntent
    data object ExportHighlightRulesAsUrl : ReadBookIntent
    data class ExportHighlightRulesToFile(val uri: Uri) : ReadBookIntent

    // Icon picker — file IO handled by ViewModel
    data class SaveMenuCustomIcon(val id: String, val uri: Uri) : ReadBookIntent
    data class SaveTitleBarCustomIcon(val id: String, val uri: Uri) : ReadBookIntent
    data class OpenMenuCustomIconPicker(val id: String) : ReadBookIntent
    data class OpenTitleBarCustomIconPicker(val id: String) : ReadBookIntent
    data class SaveMenuButtonConfig(val items: List<ReadBookButtonConfigItem>) : ReadBookIntent
    data class SaveTitleBarButtonConfig(val items: List<ReadBookButtonConfigItem>) : ReadBookIntent
    data class SaveMoreActionsConfig(val items: List<ReadBookButtonConfigItem>) : ReadBookIntent

    // BgTextConfig (needs Activity for DialogFragment)
    data class OpenBgTextConfig(val index: Int) : ReadBookIntent

    // Day/night toggle
    data object ToggleDayNight : ReadBookIntent
    data object ToggleEyeProtection : ReadBookIntent
    data class EyeProtectionEnabledChanged(val value: Boolean) : ReadBookIntent
    data class EyeProtectionIntensityChanged(val value: Int) : ReadBookIntent
    data class EyeProtectionAutoNightChanged(val value: Boolean) : ReadBookIntent
    data class EyeProtectionScheduleChanged(val value: Boolean) : ReadBookIntent
    data class EyeProtectionStartTimeChanged(val value: String) : ReadBookIntent
    data class EyeProtectionEndTimeChanged(val value: String) : ReadBookIntent

    // Default font picker (needs Activity for AlertDialog)
    // Text action menu (moved from Activity)
    data class TextActionAloud(val text: String, val selectStartPos: TextPos?) : ReadBookIntent
    data class TextActionBookmark(val bookmark: Bookmark) : ReadBookIntent
    data class OpenMarking(val selection: Bookmark) : ReadBookIntent

    /** 从正文处理 Sheet 点标记项进入编辑模式。 */
    data class EditMarking(val id: String) : ReadBookIntent
    data object DismissMarking : ReadBookIntent
    data class SaveMarking(val style: TextProcessStyle, val note: String) : ReadBookIntent
    data object DeleteMarking : ReadBookIntent

    /** 书签/笔记跳转：先校验定位（源/标题），不通过则弹确认框。 */
    data class NavigateToBookmark(val bookmark: Bookmark) : ReadBookIntent
    data class NavigateToMarking(val marking: BookMarking) : ReadBookIntent
    data object ConfirmBookmarkTargetJump : ReadBookIntent
    data object CancelBookmarkTargetJump : ReadBookIntent
    data class TextActionReplace(val text: String) : ReadBookIntent
    data class TextActionSearchContent(val text: String) : ReadBookIntent
    data class TextActionDict(val text: String) : ReadBookIntent
    data class OpenAiTextClean(
        val text: String,
        val chapterIndex: Int,
        val chapterPosition: Int,
    ) : ReadBookIntent

    data object RetryAiTextClean : ReadBookIntent
    data class SetAiTextCleanReasoningLevel(val level: AiReasoningLevel) : ReadBookIntent
    data object ConfirmAiTextClean : ReadBookIntent
    data class OpenAiTextRewrite(
        val text: String,
        val chapterIndex: Int,
        val chapterPosition: Int,
    ) : ReadBookIntent

    data class SelectAiRewritePreset(val presetId: String) : ReadBookIntent
    data class SetAiRewriteTemporaryInstruction(val instruction: String) : ReadBookIntent
    data class SelectAiRewriteHistory(val artifactId: String) : ReadBookIntent
    data object GenerateAiTextRewrite : ReadBookIntent
    data object RetryAiTextRewrite : ReadBookIntent
    data class SetAiTextRewriteReasoningLevel(val level: AiReasoningLevel) : ReadBookIntent
    data object ConfirmAiTextRewrite : ReadBookIntent
    data object OpenAiRewritePresetConfig : ReadBookIntent
    data object CloseAiRewritePresetConfig : ReadBookIntent
    data object AddAiRewritePreset : ReadBookIntent
    data class EditAiRewritePreset(val preset: AiRewritePresetUi) : ReadBookIntent
    data class SetAiRewritePresetName(val name: String) : ReadBookIntent
    data class SetAiRewritePresetInstruction(val instruction: String) : ReadBookIntent
    data object SaveAiRewritePreset : ReadBookIntent
    data object CancelAiRewritePresetEdit : ReadBookIntent
    data class RequestDeleteAiRewritePreset(val preset: AiRewritePresetUi) : ReadBookIntent
    data object ConfirmDeleteAiRewritePreset : ReadBookIntent
    data object DismissDeleteAiRewritePreset : ReadBookIntent

    // Screen / selection config
    data class KeepLightChanged(val value: String) : ReadBookIntent
    data class SetOrientation(val value: String) : ReadBookIntent
    data class TextSelectAbleChanged(val enabled: Boolean) : ReadBookIntent

    // Media / TTS
    data class MediaButtonPressed(val play: Boolean) : ReadBookIntent
    data class TtsProgress(val chapterStart: Int) : ReadBookIntent

    // Dialog callback bridge
    data object ReadAloudAction : ReadBookIntent
    data object ConfirmAddCurrentBookToBookshelf : ReadBookIntent
    data object ExitWithoutAddingCurrentBookToBookshelf : ReadBookIntent

    // Read aloud config (needs Activity for DialogFragment)
    data object ShowReadAloudConfig : ReadBookIntent
    data object OpenPreDownloadNumPicker : ReadBookIntent
    data object OpenPreSynthesisConcurrencyPicker : ReadBookIntent
    data object OpenParagraphIntervalPicker : ReadBookIntent
    data object OpenCacheCleanTimePicker : ReadBookIntent
    data class ApplyPreDownloadNum(val value: Int) : ReadBookIntent
    data class ApplyPreSynthesisConcurrency(val value: Int) : ReadBookIntent
    data class ApplyAudioCacheCleanTime(val value: Int) : ReadBookIntent
    data class ApplyParagraphInterval(val value: Int) : ReadBookIntent
    data class SetReadAloudIgnoreAudioFocus(val value: Boolean) : ReadBookIntent
    data class SetReadAloudPauseOnPhoneCall(val value: Boolean) : ReadBookIntent
    data class SetReadAloudWakeLock(val value: Boolean) : ReadBookIntent
    data class SetShowReadAloudCapsule(val value: Boolean) : ReadBookIntent
    data class SetCapsuleAutoCollapse(val value: Boolean) : ReadBookIntent
    data object ResetReadAloudCapsulePosition : ReadBookIntent
    data class SetReadAloudCapsulePosition(val x: Float, val y: Float) : ReadBookIntent
    data class SetReadAloudMediaButtonPerNext(val value: Boolean) : ReadBookIntent
    data class SetReadAloudByPage(val value: Boolean) : ReadBookIntent
    data class SetReadAloudSystemMediaCompat(val value: Boolean) : ReadBookIntent
    data class SetReadAloudAndroidMediaControl(val value: Boolean) : ReadBookIntent
    data class SetReadAloudStreamAudio(val value: Boolean) : ReadBookIntent
    data object ReadAloudPrevParagraph : ReadBookIntent
    data object ReadAloudTogglePause : ReadBookIntent
    data object ReadAloudStop : ReadBookIntent
    data object ReadAloudNextParagraph : ReadBookIntent
    data object ReadAloudPrevChapter : ReadBookIntent
    data object ReadAloudNextChapter : ReadBookIntent
    data class SetReadAloudTtsTimer(val value: Int) : ReadBookIntent
    data class SetFinishCurrentChapterAfterTimer(val value: Boolean) : ReadBookIntent
    data class SetReadAloudTtsFollowSys(val value: Boolean) : ReadBookIntent
    data class SetReadAloudTtsSpeechRate(val value: Int) : ReadBookIntent
    data class SetSpeechAnalysisMode(val value: String) : ReadBookIntent
    data class SetUseMultiSpeaker(val value: Boolean) : ReadBookIntent
    data class SetDefaultReadAloudInterface(val value: String) : ReadBookIntent
    data object OpenSystemTtsSettings : ReadBookIntent
    data object ClearTtsCache : ReadBookIntent
    data object OpenTtsEnginesAndVoices : ReadBookIntent
    data object OpenTtsCache : ReadBookIntent
    data object OpenBookVoiceCasting : ReadBookIntent
    data object OpenReadAloudPlayer : ReadBookIntent
    data object OpenClassicReadAloudControls : ReadBookIntent
    data class SelectFont(val path: String) : ReadBookIntent
    data class SelectTitleFont(val path: String) : ReadBookIntent
    data class SelectTitleSystemTypeface(val index: Int) : ReadBookIntent
    data class SelectSystemTypeface(val index: Int) : ReadBookIntent
    data class ColorSelected(val dialogId: Int, val color: Int) : ReadBookIntent

    // Simulated reading apply (clear chapter cache + reinit)
    data object ApplySimulatedReading : ReadBookIntent

    // Page anim changed (reload content + update view)
    data object PageAnimChanged : ReadBookIntent

    // Download chapters
    data class DownloadChapters(val start: Int, val end: Int) : ReadBookIntent

    // Save chapter content (from chapter source change)
    data class SaveChapterContent(val content: String, val chapterIndex: Int) : ReadBookIntent

    // Lifecycle (from route DisposableEffect)
    data object OnResume : ReadBookIntent
    data object OnPause : ReadBookIntent
    data object OnDispose : ReadBookIntent
    data class CloseReadBook(val keepReadAloud: Boolean = false) : ReadBookIntent
    data object OpenBooksDirPicker : ReadBookIntent
    data class BooksDirSelected(val uri: Uri) : ReadBookIntent
}

sealed interface ReadBookEffect {
    // Toast
    data class ShowToast(val message: String) : ReadBookEffect
    data class LongToast(val message: String) : ReadBookEffect
    data class TtsCacheCleared(val message: String) : ReadBookEffect

    // Navigation / lifecycle
    data object Finish : ReadBookEffect

    // ReadView operations (require Activity/View reference)
    data class UpdateReadViewConfig(val actions: Set<ConfigUpdateAction>) : ReadBookEffect
    data class UpContent(
        val relativePosition: Int,
        val resetPageOffset: Boolean,
        val success: (() -> Unit)? = null,
    ) : ReadBookEffect
    data class UpPageAnim(val upRecorder: Boolean) : ReadBookEffect
    data object UpTime : ReadBookEffect
    data class UpBattery(val level: Int) : ReadBookEffect
    data object UpAloudState : ReadBookEffect
    data object UpSeekBar : ReadBookEffect
    data object UpMenuView : ReadBookEffect
    // R2.3：PageChanged / ContentLoadFinish / LayoutPageCompleted 已内联进
    // ReadBookController 的渲染回调——它们只在 controller 内部自产自销，不是 VM 的对外协议。
    data object RefreshBookContent : ReadBookEffect

    // Menu / UI actions
    data object AddBookmark : ReadBookEffect

    /** 书签集合变化后刷新三页的右上角书签角标。 */
    data object UpBookmarkBadge : ReadBookEffect
    data object CancelSelect : ReadBookEffect
    data object UpSystemUiVisibility : ReadBookEffect
    data class SetBrightness(val value: Int) : ReadBookEffect
    data class ToggleBrightnessAuto(val auto: Boolean, val value: Int) : ReadBookEffect

    // Read aloud / auto page
    data object ToggleReadAloud : ReadBookEffect
    data object ToggleAutoPage : ReadBookEffect
    data object StopAutoPage : ReadBookEffect

    // Search
    data class OpenSearch(
        val word: String?,
        val bookUrl: String,
        val autoFocus: Boolean = true,
    ) : ReadBookEffect
    data class NavigateToSearchResult(
        val result: SearchResult,
        val chapterIndex: Int,
        val pageIndex: Int,
        val lineIndex: Int,
        val startCharIndex: Int,
        val endRelativePage: Int,
        val endLineIndex: Int,
        val endCharIndex: Int,
    ) : ReadBookEffect
    data object ExitSearch : ReadBookEffect

    // Source actions
    data class ShowLogin(val sourceUrl: String) : ReadBookEffect
    data class OpenSourceEdit(val sourceUrl: String) : ReadBookEffect
    data class OpenBookInfo(val name: String, val author: String, val bookUrl: String) : ReadBookEffect
    data class OpenChapterList(val bookUrl: String) : ReadBookEffect
    data class OpenWebView(
        val title: String,
        val url: String,
        val sourceOrigin: String?,
        val sourceName: String?,
        val sourceType: Int?,
        val html: String? = null,
    ) : ReadBookEffect

    data class RunSourceCustomButton(
        val event: String,
        val source: BookSource,
        val book: Book,
        val chapter: BookChapter?,
    ) : ReadBookEffect

    // Menu actions that need Activity
    data object MenuChangeSource : ReadBookEffect
    data object MenuBookChangeSource : ReadBookEffect
    data object MenuChapterChangeSource : ReadBookEffect
    data object MenuSettingReplace : ReadBookEffect
    data class MenuTocRegex(val bookUrl: String, val tocRegex: String?) : ReadBookEffect
    data class MenuImageStyleChanged(val style: String) : ReadBookEffect
    data class SyncBookProgress(val book: Book) : ReadBookEffect

    // Text action menu (needs Activity for View operations)
    data class TextActionAloudSelect(val selectStartPos: TextPos) : ReadBookEffect
    data class TextActionSpeak(val text: String) : ReadBookEffect
    data class TextActionReplace(val text: String, val bookName: String?, val bookSourceUrl: String?) : ReadBookEffect

    // Screen / selection
    data object UpScreenTimeOut : ReadBookEffect
    data class UpTextSelectAble(val enabled: Boolean) : ReadBookEffect

    // TTS

    // Dialogs (Activity-driven)
    data object ShowConfirmSkipToChapter : ReadBookEffect
    // Replace editor (needs Activity context for ActivityResult)
    data class OpenReplaceEditor(val id: Long, val pattern: String?) : ReadBookEffect

    // Font folder picker
    data object OpenFontFolderPicker : ReadBookEffect

    // Read style SAF actions
    data object OpenReadStyleImagePicker : ReadBookEffect
    data class OpenReadStyleImagePickerForMode(val isNight: Boolean) : ReadBookEffect
    data object OpenReadStyleImport : ReadBookEffect
    data class OpenReadStyleExport(val fileName: String) : ReadBookEffect
    data class OpenMenuCustomIconPicker(val id: String) : ReadBookEffect
    data class OpenTitleBarCustomIconPicker(val id: String) : ReadBookEffect
    data object OpenSystemTtsSettings : ReadBookEffect
    data object OpenTtsEnginesAndVoices : ReadBookEffect
    data object OpenTtsCache : ReadBookEffect
    data class OpenBookVoiceCasting(val bookUrl: String) : ReadBookEffect
    data object OpenHighlightRuleImportPicker : ReadBookEffect
    data object OpenHighlightRuleExportPicker : ReadBookEffect

    // Day/night toggle
    data object ToggleDayNight : ReadBookEffect

    // Page anim changed — Activity calls readView.upPageAnim() + ReadBook.loadContent(false)
    data object PageAnimChanged : ReadBookEffect

    // Download chapters — Activity calls CacheBook.start()
    data class DownloadChapters(val start: Int, val end: Int) : ReadBookEffect

    // Lifecycle — route-level Activity operations
    data object RegisterTimeBatteryReceiver : ReadBookEffect
    data object UnregisterTimeBatteryReceiver : ReadBookEffect
    data object RegisterNetworkListener : ReadBookEffect
    data object UnregisterNetworkListener : ReadBookEffect
    data object SetOrientation : ReadBookEffect
    data object OpenBooksDirPicker : ReadBookEffect
    data object BackupNow : ReadBookEffect

    // Export — Activity handles file writing
    data class ExportJson(val json: String) : ReadBookEffect
}

@Immutable
sealed interface ReadBookSheet {
    data object MoreActions : ReadBookSheet
    data class BookNavigation(val initialTab: ReaderBookSheetTab) : ReadBookSheet
    data object PageAnim : ReadBookSheet
    data object Download : ReadBookSheet
    data object Charset : ReadBookSheet
    data object SimulatedReading : ReadBookSheet
    data object ToolButtonConfig : ReadBookSheet
    data object EyeProtection : ReadBookSheet
    data object FloatingBarIconConfig : ReadBookSheet
    data object EffectiveReplaces : ReadBookSheet
    data object ContentProcesses : ReadBookSheet
    data object TextProcessing : ReadBookSheet
    data object ContentEdit : ReadBookSheet
    data object ChapterSummary : ReadBookSheet
    data object AiTextClean : ReadBookSheet
    data object AiTextRewrite : ReadBookSheet
    data object AiRewritePresetConfig : ReadBookSheet
    data object AppLog : ReadBookSheet
    data class ChangeChapterSource(val chapterIndex: Int, val chapterTitle: String) : ReadBookSheet
    data object ChangeBookSource : ReadBookSheet
    data object ShadowSet : ReadBookSheet
    data object UnderlineConfig : ReadBookSheet
    data object FontSelect : ReadBookSheet
    data object TitleFontSelect : ReadBookSheet
    data object HighlightRuleConfig : ReadBookSheet
    data object Marking : ReadBookSheet
    data object MoreConfig : ReadBookSheet
    data object BgTextConfig : ReadBookSheet
    data object ReadAloudConfig : ReadBookSheet
    data object ReadAloudPlayer : ReadBookSheet
    data object PreDownloadConfig : ReadBookSheet
    data object PreSynthesisConcurrencyConfig : ReadBookSheet
    data object AudioCacheCleanConfig : ReadBookSheet
    data object ParagraphIntervalConfig : ReadBookSheet
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
    data class ReadRecordAliasConflict(
        val bookName: String,
        val author: String,
        val readTime: Long,
    ) : ReadBookDialog
    data class ConfirmRestoreProgress(val progress: BookProgress) : ReadBookDialog
    data class SureSyncProgress(val progress: BookProgress) : ReadBookDialog
    data object RestoreLastBookProgress : ReadBookDialog
    data object ConfirmSkipToChapter : ReadBookDialog
    data class ConfirmChapterPay(val chapterTitle: String) : ReadBookDialog
    data class ConfirmAddToBookshelf(val bookName: String) : ReadBookDialog
}

/**
 * Typed config update actions — replaces magic integer codes.
 * Each action represents a specific UI update operation.
 */
@Immutable
sealed interface ConfigUpdateAction {
    data object UpdateSystemUi : ConfigUpdateAction
    data object UpdateBackground : ConfigUpdateAction
    data object UpdateStyle : ConfigUpdateAction
    data object UpdateBackgroundAlpha : ConfigUpdateAction
    data object UpdatePageSlopSquare : ConfigUpdateAction
    data object ReloadContent : ConfigUpdateAction
    data object RelayoutContent : ConfigUpdateAction
    data object UpdateContent : ConfigUpdateAction
    data object UpdateChapterStyle : ConfigUpdateAction
    data object InvalidateTextPage : ConfigUpdateAction
    data object UpdateLayout : ConfigUpdateAction
    data object RebuildWholeBookPageIndex : ConfigUpdateAction
    data object UpdateWholeBookPageDemand : ConfigUpdateAction
    data object SubmitRenderTask : ConfigUpdateAction
    data object UpdatePageAnim : ConfigUpdateAction
}

private val HEADER_FOOTER_TIP_ACTIONS = setOf(
    ConfigUpdateAction.UpdateStyle,
    ConfigUpdateAction.UpdateContent,
    ConfigUpdateAction.UpdateWholeBookPageDemand,
)

/**
 * Typed config mutations replace direct writes to the legacy ReadBookConfig facade.
 * Each variant carries [actions] that describe which UI updates are needed.
 */
@Immutable
sealed interface ConfigUpdate {
    val actions: Set<ConfigUpdateAction>

    // --- Text style ---
    data class TextSize(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class LetterSpacing(val value: Float) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class LineSpacing(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class ParagraphSpacing(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class ParagraphIndent(val value: String) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TextItalic(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TextBold(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.UpdateContent)
    }
    data class TextColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.InvalidateTextPage)
    }
    data class TextAccentColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.InvalidateTextPage)
    }

    // --- Title style ---
    data class TitleMode(val value: Int) : ConfigUpdate {
        override val actions = setOf(
            ConfigUpdateAction.RebuildWholeBookPageIndex,
            ConfigUpdateAction.ReloadContent,
        )
    }
    data class TitleBold(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.UpdateContent)
    }
    data class TitleSegScaling(val value: Float) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TitleLineSpacingExtra(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TitleLineSpacingSub(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TitleSize(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TitleTopSpacing(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TitleBottomSpacing(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TitleColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.InvalidateTextPage)
    }
    data class TitleColorNight(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.InvalidateTextPage)
    }
    data class TitleFont(val path: String) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TitleSegType(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TitleSegDistance(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class TitleSegFlag(val value: String) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }

    // --- Header / footer tips ---
    data class HeaderMode(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class FooterMode(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class TipHeaderLeft(val value: Int) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class TipHeaderMiddle(val value: Int) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class TipHeaderRight(val value: Int) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class TipFooterLeft(val value: Int) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class TipFooterMiddle(val value: Int) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class TipFooterRight(val value: Int) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class CustomTipHeaderLeft(val value: String) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class CustomTipHeaderMiddle(val value: String) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class CustomTipHeaderRight(val value: String) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class CustomTipFooterLeft(val value: String) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class CustomTipFooterMiddle(val value: String) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class CustomTipFooterRight(val value: String) : ConfigUpdate {
        override val actions = HEADER_FOOTER_TIP_ACTIONS
    }
    data class HeaderFont(val path: String) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class HeaderFontSize(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class FooterFont(val path: String) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class FooterFontSize(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class ApplyHeaderStyle(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class TipHeaderColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class TipHeaderColorNight(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class TipFooterColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class TipFooterColorNight(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class TipDividerColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }

    // --- Layout / style ---
    data class StyleSelect(val index: Int) : ConfigUpdate {
        override val actions = setOf(
            ConfigUpdateAction.UpdateBackground,
            ConfigUpdateAction.UpdateStyle,
            ConfigUpdateAction.RebuildWholeBookPageIndex,
            ConfigUpdateAction.ReloadContent,
            ConfigUpdateAction.UpdateSystemUi,
            ConfigUpdateAction.UpdatePageAnim
        )
    }
    data class ShareLayout(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(
            ConfigUpdateAction.UpdateBackground,
            ConfigUpdateAction.UpdateStyle,
            ConfigUpdateAction.RebuildWholeBookPageIndex,
            ConfigUpdateAction.ReloadContent,
            ConfigUpdateAction.UpdatePageAnim
        )
    }
    data class PageAnim(val value: Int) : ConfigUpdate {
        override val actions = setOf(
            ConfigUpdateAction.UpdateBackground,
            ConfigUpdateAction.UpdatePageAnim,
            ConfigUpdateAction.RebuildWholeBookPageIndex,
            ConfigUpdateAction.ReloadContent
        )
    }

    // --- Menu colors ---
    data class MenuBgColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateSystemUi)
    }
    data class MenuAccentColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }
    data class MenuContainerColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }
    data class MenuBgColorNight(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateSystemUi)
    }
    data class MenuAccentColorNight(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }
    data class MenuContainerColorNight(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }
    data class MenuTextColor(val color: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuTextColorNight(val color: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuColorMode(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateSystemUi)
    }
    data class ReadBarStyle(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateSystemUi)
    }

    // --- Menu bar border ---
    data class BorderWidth(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }
    data class BorderColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }
    data class BorderColorNight(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }

    // --- Shadow ---
    data class TextShadow(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class ShadowRadius(val value: Float) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class ShadowDx(val value: Float) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class ShadowDy(val value: Float) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
    }
    data class ShadowColor(val color: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.InvalidateTextPage)
    }

    // --- Underline ---
    data class Underline(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
    }
    data class DottedLine(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
    }
    data class UnderlineExtend(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
    }
    data class UnderlineHeight(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.UpdateContent)
    }
    data class UnderlinePadding(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.UpdateContent)
    }
    data class DottedBase(val value: Float) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateContent, ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.UpdateLayout)
    }
    data class DottedRatio(val value: Float) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateContent, ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.UpdateLayout)
    }
    data class UnderlineColor(val color: Int) : ConfigUpdate {
        // 对齐兄弟项 Underline/DottedLine：改颜色要重绘已排好的 TextPage，
        // 仅 UpdateStyle（readView.upStyle()）不会重画下划线。
        override val actions = setOf(ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
    }

    // --- Body padding ---
    data class PaddingTop(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateLayout, ConfigUpdateAction.ReloadContent)
    }
    data class PaddingBottom(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateLayout, ConfigUpdateAction.ReloadContent)
    }
    data class PaddingLeft(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateLayout, ConfigUpdateAction.ReloadContent)
    }
    data class PaddingRight(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateLayout, ConfigUpdateAction.ReloadContent)
    }

    // --- Header padding ---
    data class HeaderPaddingTop(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class HeaderPaddingBottom(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class HeaderPaddingLeft(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class HeaderPaddingRight(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class ShowHeaderLine(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }

    // --- Footer padding ---
    data class FooterPaddingTop(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class FooterPaddingBottom(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class FooterPaddingLeft(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class FooterPaddingRight(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class ShowFooterLine(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }

    // --- Background / display ---
    data class BgStr(val value: String) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateBackgroundAlpha, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateSystemUi)
    }
    data class BgStrNight(val value: String) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateBackgroundAlpha, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateSystemUi)
    }
    data class BgStrEInk(val value: String) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateBackgroundAlpha, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateSystemUi)
    }
    data class BgType(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateBackgroundAlpha, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateSystemUi)
    }
    data class BgTypeNight(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateBackgroundAlpha, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateSystemUi)
    }
    data class BgTypeEInk(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateBackgroundAlpha, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateSystemUi)
    }
    data class BgAlpha(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateBackgroundAlpha)
    }
    data class StatusIconDark(val value: Boolean) : ConfigUpdate {
        // 唯一生效路径是 UpdateSystemUi → upSystemUiVisibility() →
        // setLightStatusBar(curStatusIconDark())（ReadBookController:318）。
        // 原本的 ReloadContent 只会重新加载正文，与状态栏图标无关。
        override val actions = setOf(ConfigUpdateAction.UpdateSystemUi)
    }
    data class StyleName(val value: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuIconShowText(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuIconStyle(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }

    data class TitleBarIconStyle(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuIconItemsPerRow(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuIconRowCount(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuBottomCornerRadius(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class FloatingBottomBar(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuTopBarBlurMode(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuBottomBarBlurMode(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuTopBarLiquidGlassButtons(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }

    data class MenuTopBarMergeButtons(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuTopBarTitleCapsule(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuBottomBarLiquidGlassButtons(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuFloatingIconLiquidGlass(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuTopBarBlurSelection(val mode: Int, val style: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuBottomBarBlurStyle(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuBlurRadius(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuBlurAlpha(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuBlurColor(val color: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuBlurColorNight(val color: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuPaletteStyle(val value: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuLensRadius(val value: Float) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MenuCustomIcon(val id: String, val path: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class TitleBarCustomIcon(val id: String, val path: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class TitleBarIconPosition(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class ShowTitleBarIcons(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }

    data class TitleBarCompact(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }

    // --- System UI (also updates AppConfig) ---
    data class HideStatusBar(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateSystemUi, ConfigUpdateAction.UpdateStyle)
    }
    data class HideNavigationBar(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateSystemUi, ConfigUpdateAction.UpdateStyle)
    }

    // --- Display toggles ---
    data class PaddingDisplayCutouts(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.UpdateStyle)
    }
    data class TitleBarMode(val value: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class ShowMenuIcon(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class ReadBodyToLh(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.RebuildWholeBookPageIndex, ConfigUpdateAction.ReloadContent)
    }
    data class DefaultSourceChangeAll(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class TextFullJustify(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.RebuildWholeBookPageIndex, ConfigUpdateAction.ReloadContent)
    }
    data class TextBottomJustify(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.RebuildWholeBookPageIndex, ConfigUpdateAction.ReloadContent)
    }
    data class AdaptSpecialStyle(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.RebuildWholeBookPageIndex, ConfigUpdateAction.ReloadContent)
    }
    data class UseZhLayout(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.RebuildWholeBookPageIndex, ConfigUpdateAction.ReloadContent)
    }
    data class ShowBrightnessView(val value: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }

    data class BrightnessVwPos(val value: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }

    data class BrightnessAuto(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class UseUnderlineGlobal(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.ReloadContent)
    }
    data class ReadSliderMode(val value: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class DoubleHorizontalPage(val value: String) : ConfigUpdate {
        override val actions = setOf(
            ConfigUpdateAction.UpdateLayout,
            ConfigUpdateAction.ReloadContent,
        )
    }
    data class ProgressBarBehavior(val value: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MouseWheelPage(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class VolumeKeyPage(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class VolumeKeyPageOnPlay(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class KeyPageOnLongPress(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class SwipeToAddBookmark(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }

    data class BookmarkBadgeSize(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class SliderVibrator(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class UseNewTocSheet(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class MaxLengthWithNoToc(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class SelectVibrator(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class AutoChangeSource(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class AutoSuggestDayNight(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class SelectText(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class NoAnimScrollPage(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class OptimizeRender(val value: Boolean) : ConfigUpdate {
        override val actions = setOf(
            ConfigUpdateAction.UpdateChapterStyle,
            ConfigUpdateAction.ReloadContent,
            ConfigUpdateAction.SubmitRenderTask,
        )
    }
    data class ClickImgWay(val value: String) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class DisableReturnKey(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class ExpandTextMenu(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class ShowSelectMenuIcon(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }
    data class ShowReadTitleAddition(val value: Boolean) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }

    // --- Auto read ---
    data class AutoReadSpeed(val value: Int) : ConfigUpdate {
        override val actions = emptySet<ConfigUpdateAction>()
    }

    // --- Chinese converter ---
    data class ChineseConverterType(val value: Int) : ConfigUpdate {
        override val actions = setOf(ConfigUpdateAction.RebuildWholeBookPageIndex, ConfigUpdateAction.ReloadContent)
    }
}
