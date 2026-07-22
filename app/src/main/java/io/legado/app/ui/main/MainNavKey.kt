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
data object MainRouteSettingsCoverAlbums : MainRoute

@Serializable
data object MainRouteSettingsTheme : MainRoute

@Serializable
data object MainRouteSettingsBackup : MainRoute

@Serializable
data object MainRouteSettingsAi : MainRoute

@Serializable
data object MainRouteAiChat : MainRoute

@Serializable
data class MainRouteSettingsAiProviderEdit(
    val providerId: String? = null
) : MainRoute

@Serializable
data class MainRouteSettingsAiModelEdit(
    val providerId: String? = null,
    val modelProfileId: String? = null
) : MainRoute

@Serializable
data object MainRouteSettingsAiSummary : MainRoute

@Serializable
data object MainRouteSettingsAiPrompt : MainRoute

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
data class MainRouteBookCharacterDetail(
    val bookUrl: String,
    val characterId: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookCharacterNetwork(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteBookCharacterList(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteBookVoiceCasting(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteCloudTtsEngines(val bookUrl: String? = null) : MainRoute

@Serializable
data object MainRouteTtsCache : MainRoute

@Serializable
data class MainRouteBookKnowledgeList(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteBookKnowledgeDetail(
    val bookUrl: String,
    val entryId: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookEventList(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteBookEventDetail(
    val bookUrl: String,
    val eventId: String? = null,
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
data object MainRouteHighlightTagRule : MainRoute

@Serializable
data object MainRouteAbout : MainRoute

object MainRouteConst {
    const val ROUTE_MAIN = "main"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SETTINGS_OTHER = "settings/other"
    const val ROUTE_SETTINGS_READ = "settings/read"
    const val ROUTE_SETTINGS_COVER = "settings/cover"
    const val ROUTE_SETTINGS_COVER_ALBUMS = "settings/cover/albums"
    const val ROUTE_SETTINGS_THEME = "settings/theme"
    const val ROUTE_SETTINGS_BACKUP = "settings/backup"
    const val ROUTE_SETTINGS_AI = "settings/ai"
    const val ROUTE_SETTINGS_AI_SUMMARY = "settings/ai/summary"
    const val ROUTE_SETTINGS_AI_PROMPT = "settings/ai/prompt"
    const val ROUTE_AI_CHAT = "ai/chat"
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
    const val ROUTE_BOOK_CHARACTER_DETAIL = "book/character/detail"
    const val ROUTE_BOOK_CHARACTER_NETWORK = "book/character/network"
    const val ROUTE_BOOK_CHARACTER_LIST = "book/character/list"
    const val ROUTE_BOOK_KNOWLEDGE_LIST = "book/knowledge/list"
    const val ROUTE_BOOK_KNOWLEDGE_DETAIL = "book/knowledge/detail"
    const val ROUTE_BOOK_EVENT_LIST = "book/event/list"
    const val ROUTE_BOOK_EVENT_DETAIL = "book/event/detail"
    const val ROUTE_EXPLORE_SHOW = "explore/show"
    const val ROUTE_RSS_SORT = "rss/sort"
    const val ROUTE_RSS_READ = "rss/read"
    const val ROUTE_RSS_FAVORITES = "rss/favorites"
    const val ROUTE_RULE_SUB = "rss/rule_sub"
    const val ROUTE_READ_RECORD = "read_record"
    const val ROUTE_READ_RECORD_OVERVIEW = "read_record_overview"
    const val ROUTE_ABOUT = "about"
    const val ROUTE_TTS_CACHE = "tts_cache"
}
