package io.legado.app.ui.main

import android.content.Context
import android.content.Intent
import io.legado.app.ui.config.ConfigTag

object MainIntent {
    const val EXTRA_START_ROUTE = "startRoute"
    const val EXTRA_CACHE_GROUP_ID = "extra_cache_group_id"
    const val EXTRA_SEARCH_KEY = "extra_search_key"
    const val EXTRA_SEARCH_SCOPE = "extra_search_scope"
    const val EXTRA_BOOK_NAME = "name"
    const val EXTRA_BOOK_AUTHOR = "author"
    const val EXTRA_BOOK_URL = "bookUrl"
    const val EXTRA_EXPLORE_NAME = "exploreName"
    const val EXTRA_SOURCE_URL = "sourceUrl"
    const val EXTRA_EXPLORE_URL = "exploreUrl"

    const val EXTRA_RSS_SOURCE_URL = "extra_rss_source_url"
    const val EXTRA_RSS_SORT_URL = "extra_rss_sort_url"
    const val EXTRA_RSS_KEY = "extra_rss_key"

    const val EXTRA_RSS_READ_TITLE = "extra_rss_read_title"
    const val EXTRA_RSS_READ_ORIGIN = "extra_rss_read_origin"
    const val EXTRA_RSS_READ_LINK = "extra_rss_read_link"
    const val EXTRA_RSS_READ_OPEN_URL = "extra_rss_read_open_url"

    fun createLauncherIntent(context: Context): Intent {
        val launcherComponent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        return if (launcherComponent != null) {
            Intent().setComponent(launcherComponent)
        } else {
            Intent(context, MainActivity::class.java)
        }
    }

    fun createHomeIntent(context: Context): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_MAIN)
        }
    }

    fun createIntent(context: Context, configTag: String? = null): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, routeForConfigTag(configTag))
        }
    }

    fun createRssSortIntent(
        context: Context,
        sourceUrl: String,
        sortUrl: String? = null,
        key: String? = null
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_RSS_SORT)
            putExtra(EXTRA_RSS_SOURCE_URL, sourceUrl)
            putExtra(EXTRA_RSS_SORT_URL, sortUrl)
            putExtra(EXTRA_RSS_KEY, key)
        }
    }

    fun createRssReadIntent(
        context: Context,
        title: String? = null,
        origin: String,
        link: String? = null,
        openUrl: String? = null
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_RSS_READ)
            putExtra(EXTRA_RSS_READ_TITLE, title)
            putExtra(EXTRA_RSS_READ_ORIGIN, origin)
            putExtra(EXTRA_RSS_READ_LINK, link)
            putExtra(EXTRA_RSS_READ_OPEN_URL, openUrl)
        }
    }

    fun createBookshelfManageScreenIntent(
        context: Context,
        groupId: Long = -1L
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_CACHE)
            putExtra(EXTRA_CACHE_GROUP_ID, groupId)
        }
    }

    fun createCacheIntent(
        context: Context,
        groupId: Long = -1L
    ): Intent = createBookshelfManageScreenIntent(context, groupId)

    fun createBookCacheManageIntent(context: Context): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_CACHE_MANAGE)
        }
    }

    fun createSearchIntent(
        context: Context,
        key: String? = null,
        scopeRaw: String? = null
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_SEARCH)
            putExtra(EXTRA_SEARCH_KEY, key)
            putExtra(EXTRA_SEARCH_SCOPE, scopeRaw)
        }
    }

    fun createBookInfoIntent(
        context: Context,
        name: String? = null,
        author: String? = null,
        bookUrl: String
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_INFO)
            putExtra(EXTRA_BOOK_NAME, name)
            putExtra(EXTRA_BOOK_AUTHOR, author)
            putExtra(EXTRA_BOOK_URL, bookUrl)
        }
    }

    fun createExploreShowIntent(
        context: Context,
        exploreName: String? = null,
        sourceUrl: String,
        exploreUrl: String? = null
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_EXPLORE_SHOW)
            putExtra(EXTRA_EXPLORE_NAME, exploreName)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
            putExtra(EXTRA_EXPLORE_URL, exploreUrl)
        }
    }

    private fun routeForConfigTag(configTag: String?): String {
        return when (configTag) {
            ConfigTag.OTHER_CONFIG -> MainRouteConst.ROUTE_SETTINGS_OTHER
            ConfigTag.READ_CONFIG -> MainRouteConst.ROUTE_SETTINGS_READ
            ConfigTag.COVER_CONFIG -> MainRouteConst.ROUTE_SETTINGS_COVER
            ConfigTag.THEME_CONFIG -> MainRouteConst.ROUTE_SETTINGS_THEME
            ConfigTag.BACKUP_CONFIG -> MainRouteConst.ROUTE_SETTINGS_BACKUP
            ConfigTag.DOWNLOAD_CACHE_CONFIG -> MainRouteConst.ROUTE_SETTINGS_DOWNLOAD_CACHE
            else -> MainRouteConst.ROUTE_SETTINGS
        }
    }
}
