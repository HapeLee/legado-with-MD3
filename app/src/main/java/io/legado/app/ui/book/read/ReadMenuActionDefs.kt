package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.R

/**
 * 阅读菜单按钮注册表：顶栏、浮动图标行、底栏三处共用的唯一事实来源。
 *
 * 一个按钮的图标、文案、点击动作、适用条件只在这里写一遍；
 * 三处 UI 各按自己的配置（顺序 + 开关）从 [buildAllReadMenuActionDefs] 取用，
 * 并按 [ReadMenuActionDef.applicable] 过滤掉当前书籍用不上的按钮。
 * 「更多操作」菜单另有二级菜单/开关类条目，不适用图标模型，
 * 但其 `applicable` 复用 [isReadMenuActionApplicable]，避免两处判定不一致。
 */

/** 一个按钮的图标与文案。 */
internal data class ReadMenuButtonInfo(
    val id: String,
    val icon: ImageVector,
    val label: String,
)

/** 旧版写死在顶栏上、后来才进池的那批按钮 id。 */
internal val ReadBookTopBarOnlyActionIds = listOf(
    "download",
    "refresh",
    "change_source",
    "audio_download",
    "source_custom_button",
    "toc_rule",
    "charset",
)

/** 完整按钮池：顶栏专属那批在前，其余接共享图标池 [ReadBookButtonIds]（去重）。 */
internal val ReadMenuActionIds =
    ReadBookTopBarOnlyActionIds + ReadBookButtonIds.filter { it !in ReadBookTopBarOnlyActionIds }

/** 顶栏最多展示的按钮数，与旧版固定顶栏一致，避免挤压书名与章节名。 */
internal const val MAX_TITLE_BAR_ACTIONS = 6

/** 顶栏按钮的长按二级菜单。 */
internal enum class ReadMenuLongPressMenu {
    ChangeSource,
    Refresh,
}

/**
 * 阅读菜单里一个可配置按钮的运行时定义。
 *
 * 顶栏、浮动图标行、底栏三处共用同一份定义，只有自定义图标路径由各自的配置提供。
 */
internal data class ReadMenuActionDef(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val isActive: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val longPressMenu: ReadMenuLongPressMenu? = null,
    /** 当前这本书是否用得上这个按钮；不适用的在渲染时过滤掉。 */
    val applicable: Boolean = true,
)

/**
 * 按钮在当前这本书上是否适用（不随配置变化，只跟书的类型与书源有关）。
 * 顶栏/浮动图标/底栏的渲染过滤与「更多操作」的 `applicable` 都走这里。
 */
internal fun isReadMenuActionApplicable(id: String, state: ReadBookUiState): Boolean = when (id) {
    "source_custom_button" -> !state.isLocalBook && state.bookSource?.customButton == true
    "change_source", "refresh", "download" -> !state.isLocalBook
    "toc_rule" -> state.isLocalBook && state.isLocalTxt
    "charset" -> state.isLocalBook
    else -> true
}

/**
 * 配置页里给按钮标注的适用范围文案资源；返回 null 表示所有书都适用。
 * 标注只是提示——配置仍然只存一份，靠 [isReadMenuActionApplicable] 决定实际显隐。
 */
internal fun readMenuActionScopeLabelRes(id: String): Int? = when (id) {
    "source_custom_button", "change_source", "refresh", "download" -> R.string.only_online_books
    "toc_rule", "charset" -> R.string.only_local_books
    else -> null
}

/** 顶栏专属按钮的图标与文案。 */
internal fun topBarActionButtonInfos(context: Context): List<ReadMenuButtonInfo> = listOf(
    ReadMenuButtonInfo(
        "download",
        Icons.Default.CloudDownload,
        context.getString(R.string.offline_cache),
    ),
    ReadMenuButtonInfo(
        "refresh",
        Icons.Default.Refresh,
        context.getString(R.string.menu_refresh),
    ),
    ReadMenuButtonInfo(
        "change_source",
        Icons.Default.SwapHoriz,
        context.getString(R.string.change_origin),
    ),
    ReadMenuButtonInfo(
        "audio_download",
        Icons.Default.Download,
        context.getString(R.string.read_aloud_audio_download_entry),
    ),
    ReadMenuButtonInfo(
        "source_custom_button",
        Icons.Default.Extension,
        context.getString(R.string.custom_button),
    ),
    ReadMenuButtonInfo(
        "toc_rule",
        Icons.AutoMirrored.Filled.Toc,
        context.getString(R.string.txt_toc_rule),
    ),
    ReadMenuButtonInfo(
        "charset",
        Icons.Default.Translate,
        context.getString(R.string.set_charset),
    ),
)

/** 共享图标池（[ReadBookButtonIds]）的图标与文案。 */
internal fun readMenuButtonInfos(context: Context): List<ReadMenuButtonInfo> = listOf(
    ReadMenuButtonInfo("search", Icons.Default.Search, context.getString(R.string.search_content)),
    ReadMenuButtonInfo(
        "auto_page",
        Icons.Default.PlayArrow,
        context.getString(R.string.auto_next_page),
    ),
    ReadMenuButtonInfo(
        "catalog",
        Icons.AutoMirrored.Filled.List,
        context.getString(R.string.chapter_list),
    ),
    ReadMenuButtonInfo(
        "read_aloud",
        Icons.Default.RecordVoiceOver,
        context.getString(R.string.read_aloud),
    ),
    ReadMenuButtonInfo("setting", Icons.Default.Settings, context.getString(R.string.setting)),
    ReadMenuButtonInfo("addBookmark", Icons.Default.Bookmark, context.getString(R.string.bookmark)),
    ReadMenuButtonInfo(
        "theme",
        Icons.Default.Brightness6,
        context.getString(R.string.day_night_switch),
    ),
    ReadMenuButtonInfo(
        "eye_protection",
        Icons.Default.Visibility,
        context.getString(R.string.eye_protection),
    ),
    ReadMenuButtonInfo(
        "prev_chapter",
        Icons.Default.SkipPrevious,
        context.getString(R.string.previous_chapter),
    ),
    ReadMenuButtonInfo(
        "next_chapter",
        Icons.Default.SkipNext,
        context.getString(R.string.next_chapter),
    ),
    ReadMenuButtonInfo(
        "replace",
        Icons.Default.FindReplace,
        context.getString(R.string.text_processing),
    ),
    ReadMenuButtonInfo(
        "replace_badge",
        Icons.Default.FindReplace,
        context.getString(R.string.text_processing),
    ),
    ReadMenuButtonInfo(
        "translate",
        Icons.Default.Translate,
        context.getString(R.string.translate),
    ),
    ReadMenuButtonInfo(
        "refresh_current",
        Icons.Default.Refresh,
        context.getString(R.string.menu_refresh_dur),
    ),
    ReadMenuButtonInfo(
        "ai_summary",
        Icons.Default.AutoAwesome,
        context.getString(R.string.ai_chapter_summary),
    ),
    ReadMenuButtonInfo(
        "ai_rewrite",
        Icons.Default.Edit,
        context.getString(R.string.ai_text_rewrite),
    ),
    ReadMenuButtonInfo(
        "multi_role_read",
        Icons.Default.Groups,
        context.getString(R.string.use_multi_speaker),
    ),
    ReadMenuButtonInfo(
        "multi_role_cast",
        Icons.Default.Face,
        context.getString(R.string.multi_role_cast),
    ),
    ReadMenuButtonInfo(
        "cast_table",
        Icons.Default.TableRows,
        context.getString(R.string.book_voice_casting),
    ),
    ReadMenuButtonInfo(
        "ai_cast",
        Icons.Default.SmartToy,
        context.getString(R.string.ai_cast_roles),
    ),
    ReadMenuButtonInfo(
        "more_actions",
        Icons.Default.MoreVert,
        context.getString(R.string.more_actions),
    ),
)

/**
 * 构造顶栏专属动作（旧版写死在顶栏上的那批）。它们同样对底栏/浮动图标开放，
 * 靠 [ReadMenuActionDef.applicable] 决定在当前书上是否真的显示。
 */
internal fun buildTopBarActionDefs(
    context: Context,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
): Map<String, ReadMenuActionDef> {
    val infoMap = topBarActionButtonInfos(context).associateBy { it.id }
    fun def(
        id: String,
        onLongClick: (() -> Unit)? = null,
        longPressMenu: ReadMenuLongPressMenu? = null,
        onClick: () -> Unit,
    ): ReadMenuActionDef {
        val info = infoMap.getValue(id)
        return ReadMenuActionDef(
            id = info.id,
            icon = info.icon,
            label = info.label,
            onClick = onClick,
            onLongClick = onLongClick,
            longPressMenu = longPressMenu,
            applicable = isReadMenuActionApplicable(info.id, state),
        )
    }
    return listOf(
        def("download") {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download))
        },
        def(
            "refresh",
            longPressMenu = ReadMenuLongPressMenu.Refresh,
        ) {
            onIntent(ReadBookIntent.MenuRefreshDur)
        },
        def(
            "change_source",
            longPressMenu = ReadMenuLongPressMenu.ChangeSource,
        ) {
            onIntent(ReadBookIntent.MenuChangeSource)
        },
        def("audio_download") {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AudioDownload))
        },
        def(
            "source_custom_button",
            onLongClick = { onIntent(ReadBookIntent.SourceCustomButton(true)) },
        ) {
            onIntent(ReadBookIntent.SourceCustomButton(false))
        },
        def("toc_rule") {
            onIntent(ReadBookIntent.MenuTocRegex)
        },
        def("charset") {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset))
        },
    ).associateBy { it.id }
}

/**
 * 完整动作表：共享图标池（[ReadBookButtonIds]）+ 顶栏专属那批。
 * 顶栏、浮动图标行、底栏三处都从这里按 id 取，行为保持一致。
 */
internal fun buildAllReadMenuActionDefs(
    context: Context,
    state: ReadBookUiState,
    eyeProtectionActive: Boolean,
    onIntent: (ReadBookIntent) -> Unit,
): Map<String, ReadMenuActionDef> =
    buildReadMenuActionDefs(context, state, eyeProtectionActive, onIntent) +
            buildTopBarActionDefs(context, state, onIntent)

/** 构造共享图标池（[ReadBookButtonIds]）的动作定义。 */
internal fun buildReadMenuActionDefs(
    context: Context,
    state: ReadBookUiState,
    eyeProtectionActive: Boolean,
    onIntent: (ReadBookIntent) -> Unit,
): Map<String, ReadMenuActionDef> {
    val infoMap = readMenuButtonInfos(context).associateBy { it.id }
    fun def(
        id: String,
        isActive: Boolean = false,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit,
    ): ReadMenuActionDef {
        val info = infoMap.getValue(id)
        return ReadMenuActionDef(
            id = info.id,
            icon = info.icon,
            label = info.label,
            isActive = isActive,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
    return listOf(
        def("search") {
            onIntent(ReadBookIntent.OpenSearch(null))
        },
        def("catalog") {
            onIntent(ReadBookIntent.OpenChapterList)
        },
        def(
            "read_aloud",
            isActive = state.isReadAloudRunning,
            onLongClick = {
                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadAloud))
            },
        ) {
            if (state.isReadAloudRunning) {
                onIntent(ReadBookIntent.ReadAloudAction)
            } else {
                onIntent(ReadBookIntent.ToggleReadAloud)
                onIntent(ReadBookIntent.HideMenu)
            }
        },
        def("setting") {
            onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadStyle))
        },
        def("addBookmark") {
            onIntent(ReadBookIntent.AddBookmark)
        },
        def("theme") {
            onIntent(ReadBookIntent.ToggleDayNight)
        },
        def(
            "eye_protection",
            isActive = eyeProtectionActive,
            onLongClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.EyeProtection)) },
        ) {
            onIntent(ReadBookIntent.ToggleEyeProtection)
        },
        def("prev_chapter") {
            onIntent(ReadBookIntent.PrevChapter)
        },
        def("next_chapter") {
            onIntent(ReadBookIntent.NextChapter)
        },
        def("replace") {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TextProcessing))
        },
        def("replace_badge") {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TextProcessing))
        },
        def("auto_page", isActive = state.isAutoPage) {
            if (state.isAutoPage) {
                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.AutoRead))
            } else {
                onIntent(ReadBookIntent.ToggleAutoPage)
                onIntent(ReadBookIntent.HideMenu)
            }
        },
        def("translate", isActive = state.translationMode) {
            onIntent(ReadBookIntent.ToggleTranslation)
        },
        def("refresh_current") {
            onIntent(ReadBookIntent.RefreshCurrentChapter)
        },
        def("ai_summary") {
            onIntent(ReadBookIntent.OpenChapterSummary)
        },
        def("ai_rewrite") {
            onIntent(ReadBookIntent.OpenAiCurrentChapterRewrite)
        },
        def("multi_role_read", isActive = state.useMultiSpeaker) {
            onIntent(ReadBookIntent.SetUseMultiSpeaker(!state.useMultiSpeaker))
        },
        def("multi_role_cast", isActive = state.multiRoleCast) {
            onIntent(ReadBookIntent.SetMultiRoleCast(!state.multiRoleCast))
        },
        def("cast_table") {
            onIntent(ReadBookIntent.OpenBookVoiceCasting)
        },
        def("ai_cast") {
            onIntent(ReadBookIntent.OpenAiCastDialog)
        },
        def("more_actions") {
            onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.MoreActions))
        },
    ).associateBy { it.id }
}

/**
 * 解析当前书籍的顶栏按钮：配置顺序 + 适用条件 + [MAX_TITLE_BAR_ACTIONS] 截断。
 * 「精简顶部按钮」打开时退回只显示更多菜单，与旧行为一致。
 */
internal fun loadTitleBarActions(
    context: Context,
    state: ReadBookUiState,
    eyeProtectionActive: Boolean,
    onIntent: (ReadBookIntent) -> Unit,
): List<ReadMenuActionDef> {
    val defs = buildAllReadMenuActionDefs(
        context = context,
        state = state,
        eyeProtectionActive = eyeProtectionActive,
        onIntent = onIntent,
    )
    val moreDef = defs.getValue("more_actions")
    if (state.menuConfig.titleBarCompact) return listOf(moreDef)

    val actions = state.menuConfig.titleBarActions
        .asSequence()
        .filter { it.enabled }
        .mapNotNull { defs[it.id] }
        .filter { it.applicable }
        .take(MAX_TITLE_BAR_ACTIONS)
        .toList()
    // 一个都不剩时至少保留更多菜单，避免出现没有任何入口的顶栏
    return actions.ifEmpty { listOf(moreDef) }
}
