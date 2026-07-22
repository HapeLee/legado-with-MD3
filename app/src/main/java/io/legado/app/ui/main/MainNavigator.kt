package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import androidx.navigation3.runtime.NavKey
import io.legado.app.ui.rss.article.MainRouteRssSort
import io.legado.app.ui.rss.read.MainRouteRssRead
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MainNavigator {

    var backNavigationInProgress = false
        private set
    private val navigationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var backNavigationResetJob: Job? = null

    fun navigateToRoute(backStack: MutableList<NavKey>, route: NavKey) {
        val currentRoute = backStack.lastOrNull()
        if (currentRoute == route) return

        when (route) {
            MainRouteHome -> {
                backStack.clear()
                backStack.add(MainRouteHome)
            }

            MainRouteSettings -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(MainRouteSettings)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(MainRouteSettings)
                }
            }

            MainRouteAiChat -> {
                if (currentRoute == MainRouteSettingsAi || currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(MainRouteSettings)
                    backStack.add(MainRouteSettingsAi)
                    backStack.add(route)
                }
            }

            MainRouteSettingsOther,
            MainRouteSettingsRead,
            MainRouteSettingsCover,
            MainRouteSettingsTheme,
            MainRouteSettingsBackup,
            MainRouteSettingsAi,
            is MainRouteSettingsAiProviderEdit,
            is MainRouteSettingsAiModelEdit,
            MainRouteSettingsAiSummary,
            MainRouteSettingsAiPrompt,
            MainRouteSettingsCustomTheme,
            MainRouteSettingsThemeManage,
            MainRouteSettingsDownloadCache,
            MainRouteSettingsTranslation -> {
                backStack.clear()
                backStack.add(MainRouteHome)
                backStack.add(MainRouteSettings)
                backStack.add(route)
            }

            MainRouteImportLocal,
            MainRouteImportRemote,
            is MainRouteCache,
            MainRouteBookCacheManage,
            is MainRouteReadBook -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteSearchContent -> {
                backStack.add(route)
            }

            is MainRouteSearch -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteExploreShow ||
                    currentRoute is MainRouteSearch
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteBookInfo -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteSearch ||
                    currentRoute is MainRouteExploreShow ||
                    currentRoute is MainRouteBookInfo
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteBookCharacterDetail,
            is MainRouteBookCharacterNetwork,
            is MainRouteBookCharacterList,
            is MainRouteBookVoiceCasting,
            is MainRouteCloudTtsEngines,
            MainRouteTtsCache,
            is MainRouteBookKnowledgeList,
            is MainRouteBookKnowledgeDetail,
            is MainRouteBookEventList,
            is MainRouteBookEventDetail -> {
                if (
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteBookCharacterDetail ||
                    currentRoute is MainRouteBookCharacterNetwork ||
                    currentRoute is MainRouteBookCharacterList ||
                    currentRoute is MainRouteBookVoiceCasting ||
                    currentRoute is MainRouteCloudTtsEngines ||
                    currentRoute == MainRouteTtsCache ||
                    currentRoute is MainRouteBookKnowledgeList ||
                    currentRoute is MainRouteBookKnowledgeDetail ||
                    currentRoute is MainRouteBookEventList ||
                    currentRoute is MainRouteBookEventDetail ||
                    currentRoute is MainRouteReadBook
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteExploreShow -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteSearch ||
                    currentRoute is MainRouteExploreShow
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteRssSort -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteRssSort ||
                    currentRoute is MainRouteRssRead
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteRssRead -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteRssSort ||
                    currentRoute is MainRouteRssRead
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteRssFavorites,
            MainRouteRuleSub -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteHighlightTagRule,
            MainRouteReadRecord -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteAbout -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteReadRecordOverview -> {
                if (currentRoute == MainRouteHome || currentRoute == MainRouteReadRecord) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }
        }
    }

    fun navigateBack(activity: Activity, backStack: MutableList<NavKey>) {
        if (backNavigationInProgress) {
            return
        }
        if (backStack.size > 1) {
            backNavigationInProgress = true
            backStack.removeLastOrNull()
        } else {
            activity.finish()
        }
    }

    fun onBackStackChanged() {
        backNavigationResetJob?.cancel()
        backNavigationResetJob = navigationScope.launch {
            delay(500)
            backNavigationInProgress = false
        }
    }

    fun resolveStartRoute(intent: Intent?): NavKey {
        val route = intent?.getStringExtra(MainIntent.EXTRA_START_ROUTE)
        resolveRssStartRoute(route, intent)?.let { return it }
        return resolveStartRoute(route, intent)
    }

    private fun resolveRssStartRoute(route: String?, intent: Intent?): NavKey? {
        return when (route) {
            MainRouteConst.ROUTE_RSS_SORT -> {
                val sourceUrl = intent?.getStringExtra(MainIntent.EXTRA_RSS_SOURCE_URL)
                if (sourceUrl.isNullOrBlank()) {
                    null
                } else {
                    MainRouteRssSort(
                        sourceUrl = sourceUrl,
                        sortUrl = intent.getStringExtra(MainIntent.EXTRA_RSS_SORT_URL),
                        key = intent.getStringExtra(MainIntent.EXTRA_RSS_KEY)
                    )
                }
            }

            MainRouteConst.ROUTE_RSS_READ -> {
                val origin = intent?.getStringExtra(MainIntent.EXTRA_RSS_READ_ORIGIN)
                if (origin.isNullOrBlank()) {
                    null
                } else {
                    MainRouteRssRead(
                        title = intent.getStringExtra(MainIntent.EXTRA_RSS_READ_TITLE),
                        origin = origin,
                        link = intent.getStringExtra(MainIntent.EXTRA_RSS_READ_LINK),
                        openUrl = intent.getStringExtra(MainIntent.EXTRA_RSS_READ_OPEN_URL)
                    )
                }
            }

            MainRouteConst.ROUTE_RSS_FAVORITES -> MainRouteRssFavorites

            MainRouteConst.ROUTE_RULE_SUB -> MainRouteRuleSub

            else -> null
        }
    }

    private fun resolveStartRoute(route: String?, intent: Intent?): MainRoute {
        return when (route) {
            MainRouteConst.ROUTE_MAIN -> MainRouteHome
            MainRouteConst.ROUTE_SETTINGS -> MainRouteSettings
            MainRouteConst.ROUTE_SETTINGS_OTHER -> MainRouteSettingsOther
            MainRouteConst.ROUTE_SETTINGS_READ -> MainRouteSettingsRead
            MainRouteConst.ROUTE_SETTINGS_COVER -> MainRouteSettingsCover
            MainRouteConst.ROUTE_SETTINGS_THEME -> MainRouteSettingsTheme
            MainRouteConst.ROUTE_SETTINGS_BACKUP -> MainRouteSettingsBackup
            MainRouteConst.ROUTE_SETTINGS_AI -> MainRouteSettingsAi
            MainRouteConst.ROUTE_AI_CHAT -> MainRouteAiChat
            MainRouteConst.ROUTE_SETTINGS_CUSTOM_THEME -> MainRouteSettingsCustomTheme
            MainRouteConst.ROUTE_SETTINGS_DOWNLOAD_CACHE -> MainRouteSettingsDownloadCache
            MainRouteConst.ROUTE_SETTINGS_TRANSLATION -> MainRouteSettingsTranslation
            MainRouteConst.ROUTE_IMPORT_LOCAL -> MainRouteImportLocal
            MainRouteConst.ROUTE_IMPORT_REMOTE -> MainRouteImportRemote
            MainRouteConst.ROUTE_CACHE -> MainRouteCache(
                intent?.getLongExtra(
                    MainIntent.EXTRA_CACHE_GROUP_ID,
                    -1L
                ) ?: -1L
            )

            MainRouteConst.ROUTE_BOOK_CACHE_MANAGE -> MainRouteBookCacheManage
            MainRouteConst.ROUTE_READ_BOOK -> MainRouteReadBook(
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL),
                readAloud = intent?.getBooleanExtra(MainIntent.EXTRA_READ_ALOUD, false) == true,
                inBookshelf = intent?.getBooleanExtra(MainIntent.EXTRA_IN_BOOKSHELF, true) != false,
                chapterChanged = intent?.getBooleanExtra(
                    MainIntent.EXTRA_CHAPTER_CHANGED,
                    false
                ) == true,
            )
            MainRouteConst.ROUTE_SEARCH -> MainRouteSearch(
                key = intent?.getStringExtra(MainIntent.EXTRA_SEARCH_KEY),
                scopeRaw = intent?.getStringExtra(MainIntent.EXTRA_SEARCH_SCOPE)
            )

            MainRouteConst.ROUTE_BOOK_INFO -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookInfo(
                        name = intent.getStringExtra(MainIntent.EXTRA_BOOK_NAME),
                        author = intent.getStringExtra(MainIntent.EXTRA_BOOK_AUTHOR),
                        bookUrl = bookUrl,
                        origin = intent.getStringExtra(MainIntent.EXTRA_BOOK_ORIGIN),
                        coverPath = intent.getStringExtra(MainIntent.EXTRA_BOOK_COVER)
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_CHARACTER_DETAIL -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookCharacterDetail(
                        bookUrl = bookUrl,
                        characterId = intent.getStringExtra(MainIntent.EXTRA_CHARACTER_ID),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_CHARACTER_NETWORK -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookCharacterNetwork(bookUrl = bookUrl)
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_CHARACTER_LIST -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookCharacterList(bookUrl = bookUrl)
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_KNOWLEDGE_LIST -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookKnowledgeList(bookUrl = bookUrl)
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_KNOWLEDGE_DETAIL -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookKnowledgeDetail(
                        bookUrl = bookUrl,
                        entryId = intent.getStringExtra(MainIntent.EXTRA_ENTRY_ID),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_EVENT_LIST -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookEventList(bookUrl = bookUrl)
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_EVENT_DETAIL -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookEventDetail(
                        bookUrl = bookUrl,
                        eventId = intent.getStringExtra(MainIntent.EXTRA_EVENT_ID),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_EXPLORE_SHOW -> intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { sourceUrl ->
                    MainRouteExploreShow(
                        title = intent.getStringExtra(MainIntent.EXTRA_EXPLORE_NAME),
                        sourceUrl = sourceUrl,
                        exploreUrl = intent.getStringExtra(MainIntent.EXTRA_EXPLORE_URL),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_ABOUT -> MainRouteAbout

            else -> MainRouteHome
        }
    }
}
