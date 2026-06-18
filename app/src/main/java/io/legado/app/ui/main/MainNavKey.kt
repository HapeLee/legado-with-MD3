package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoute : NavKey

@Serializable
data object MainRouteHome : MainRoute

@Serializable
data object MainRouteSettings : MainRoute

@Serializable
data object MainRouteSettingsOther : MainRoute

@Serializable
data object MainRouteSettingsRead : MainRoute

@Serializable
data object MainRouteSettingsCover : MainRoute

@Serializable
data object MainRouteSettingsTheme : MainRoute

@Serializable
data object MainRouteSettingsBackup : MainRoute

@Serializable
data object MainRouteSettingsCustomTheme : MainRoute

@Serializable
data object MainRouteSettingsThemeManage : MainRoute

@Serializable
data object MainRouteSettingsLabConfig : MainRoute

@Serializable
data object MainRouteSettingsDownloadCache : MainRoute

@Serializable
data object MainRouteSettingsTranslation : MainRoute

@Serializable
data object MainRouteImportLocal : MainRoute

@Serializable
data object MainRouteImportRemote : MainRoute

@Serializable
data object MainRouteReadRecord : MainRoute

@Serializable
data object MainRouteReadRecordOverview : MainRoute

@Serializable
data class MainRouteCache(val groupId: Long) : MainRoute

@Serializable
data object MainRouteBookCacheManage : MainRoute

@Serializable
data class MainRouteReadBook(
    val bookUrl: String? = null,
    val readAloud: Boolean = false,
    val inBookshelf: Boolean = true,
    val chapterChanged: Boolean = false,
) : MainRoute

@Serializable
data class MainRouteSearch(
    val key: String?,
    val scopeRaw: String? = null
) : MainRoute

@Serializable
data class MainRouteBookInfo(
    val name: String?,
    val author: String?,
    val bookUrl: String,
    val origin: String? = null,
    val coverPath: String? = null,
    val sharedCoverKey: String? = null,
) : MainRoute

@Serializable
data object MainRouteRssFavorites : MainRoute

@Serializable
data object MainRouteRuleSub : MainRoute

@Serializable
data class MainRouteExploreShow(
    val title: String?,
    val sourceUrl: String,
    val exploreUrl: String?,
) : MainRoute

@Serializable
data class MainRouteSearchContent(
    val bookUrl: String,
    val searchWord: String? = null,
    val searchResultIndex: Int = 0,
) : MainRoute

@Serializable
data object MainRouteAbout : MainRoute

// ========== AI 模块路由 ==========
@Serializable
data object MainRouteAiConsole : MainRoute

@Serializable
data object MainRouteAiChat : MainRoute

@Serializable
data object MainRouteAiImage : MainRoute

@Serializable
data object MainRouteAiVideo : MainRoute

@Serializable
data object MainRouteAiVision : MainRoute

@Serializable
data object MainRouteAiTextTools : MainRoute

@Serializable
data object MainRouteAiSource : MainRoute

@Serializable
data object MainRouteAiBookshelf : MainRoute

@Serializable
data object MainRouteAiContentTools : MainRoute

@Serializable
data object MainRouteAiArt : MainRoute

@Serializable
data object MainRouteAiSourceAdvanced : MainRoute

@Serializable
data object MainRouteAiRecommend : MainRoute

@Serializable
data object MainRouteAiArchive : MainRoute

@Serializable
data object MainRouteAiSettings : MainRoute

object MainRouteConst {
    const val ROUTE_MAIN = "main"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SETTINGS_OTHER = "settings/other"
    const val ROUTE_SETTINGS_READ = "settings/read"
    const val ROUTE_SETTINGS_COVER = "settings/cover"
    const val ROUTE_SETTINGS_THEME = "settings/theme"
    const val ROUTE_SETTINGS_BACKUP = "settings/backup"
    const val ROUTE_SETTINGS_CUSTOM_THEME = "settings/custom_theme"
    const val ROUTE_SETTINGS_LAB_CONFIG = "settings/lab_config"
    const val ROUTE_SETTINGS_DOWNLOAD_CACHE = "settings/download_cache"
    const val ROUTE_SETTINGS_TRANSLATION = "settings/translation"
    const val ROUTE_IMPORT_LOCAL = "import/local"
    const val ROUTE_IMPORT_REMOTE = "import/remote"
    const val ROUTE_CACHE = "cache"
    const val ROUTE_BOOK_CACHE_MANAGE = "book/cache/manage"
    const val ROUTE_READ_BOOK = "book/read"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_SEARCH_CONTENT = "book/searchContent"
    const val ROUTE_BOOK_INFO = "book/info"
    const val ROUTE_EXPLORE_SHOW = "explore/show"
    const val ROUTE_RSS_SORT = "rss/sort"
    const val ROUTE_RSS_READ = "rss/read"
    const val ROUTE_RSS_FAVORITES = "rss/favorites"
    const val ROUTE_RULE_SUB = "rss/rule_sub"
    const val ROUTE_READ_RECORD = "read_record"
    const val ROUTE_READ_RECORD_OVERVIEW = "read_record_overview"
    const val ROUTE_ABOUT = "about"
    const val ROUTE_AI_CONSOLE = "ai/console"
    const val ROUTE_AI_CHAT = "ai/chat"
    const val ROUTE_AI_IMAGE = "ai/image"
    const val ROUTE_AI_VIDEO = "ai/video"
    const val ROUTE_AI_VISION = "ai/vision"
    const val ROUTE_AI_TEXT_TOOLS = "ai/text_tools"
    const val ROUTE_AI_SOURCE = "ai/book_source"
    const val ROUTE_AI_BOOKSHELF = "ai/bookshelf"
    const val ROUTE_AI_CONTENT_TOOLS = "ai/content_tools"
    const val ROUTE_AI_ART = "ai/art"
    const val ROUTE_AI_SOURCE_ADVANCED = "ai/book_source/advanced"
    const val ROUTE_AI_RECOMMEND = "ai/recommend"
    const val ROUTE_AI_ARCHIVE = "ai/archive"
    const val ROUTE_AI_SETTINGS = "ai/settings"
}
