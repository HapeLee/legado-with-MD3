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
    const val EXTRA_BOOK_ORIGIN = "origin"
    const val EXTRA_BOOK_COVER = "coverPath"
    const val EXTRA_CHARACTER_ID = "characterId"
    const val EXTRA_ENTRY_ID = "entryId"
    const val EXTRA_EVENT_ID = "eventId"
    const val EXTRA_READ_ALOUD = "readAloud"
    const val EXTRA_IN_BOOKSHELF = "inBookshelf"
    const val EXTRA_CHAPTER_CHANGED = "chapterChanged"
    const val EXTRA_EXPLORE_NAME = "exploreName"
    const val EXTRA_SOURCE_URL = "sourceUrl"
    const val EXTRA_BOOK_SOURCE_IMPORT = "bookSourceImport"
    const val EXTRA_SOURCE_LOGIN_TYPE = "source_login_type"
    const val EXTRA_SOURCE_LOGIN_KEY = "source_login_key"
    const val EXTRA_EXPLORE_URL = "exploreUrl"
    const val EXTRA_WEB_VIEW_TITLE = "title"
    const val EXTRA_WEB_VIEW_URL = "url"
    const val EXTRA_WEB_VIEW_SOURCE_ORIGIN = "sourceOrigin"
    const val EXTRA_WEB_VIEW_SOURCE_NAME = "sourceName"
    const val EXTRA_WEB_VIEW_SOURCE_TYPE = "sourceType"
    const val EXTRA_WEB_VIEW_VERIFICATION = "sourceVerificationEnable"
    const val EXTRA_WEB_VIEW_REFETCH = "refetchAfterSuccess"
    const val EXTRA_WEB_VIEW_HTML = "html"

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

    fun createSourceLoginIntent(
        context: Context,
        type: io.legado.app.ui.login.SourceLoginType,
        sourceKey: String? = null,
        bookUrl: String? = null,
    ): Intent = createLauncherIntent(context).apply {
        // NEW_TASK: 支持从 Application context（如 RssJsExtensions）启动；
        // SINGLE_TOP: MainActivity 已在栈顶时复用现有实例走 onNewIntent，直接把登录路由
        // 压进 nav3 back stack，避免 standard launchMode 下新建 MainActivity 先回主页面。
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_SOURCE_LOGIN)
        putExtra(EXTRA_SOURCE_LOGIN_TYPE, type.name)
        putExtra(EXTRA_SOURCE_LOGIN_KEY, sourceKey)
        putExtra(EXTRA_BOOK_URL, bookUrl)
    }

    fun createWebViewIntent(
        context: Context,
        title: String? = null,
        url: String,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int? = null,
        sourceVerificationEnable: Boolean = false,
        refetchAfterSuccess: Boolean = true,
        html: String? = null,
    ): Intent = createLauncherIntent(context).apply {
        // NEW_TASK: 支持从 Application context（如 SourceVerificationHelp）启动；
        // SINGLE_TOP: MainActivity 已在栈顶时复用现有实例走 onNewIntent，直接把路由压进 nav3 back stack。
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_WEB_VIEW)
        putExtra(EXTRA_WEB_VIEW_TITLE, title)
        putExtra(EXTRA_WEB_VIEW_URL, url)
        putExtra(EXTRA_WEB_VIEW_SOURCE_ORIGIN, sourceOrigin)
        putExtra(EXTRA_WEB_VIEW_SOURCE_NAME, sourceName)
        sourceType?.let { putExtra(EXTRA_WEB_VIEW_SOURCE_TYPE, it) }
        putExtra(EXTRA_WEB_VIEW_VERIFICATION, sourceVerificationEnable)
        putExtra(EXTRA_WEB_VIEW_REFETCH, refetchAfterSuccess)
        putExtra(EXTRA_WEB_VIEW_HTML, html)
    }

    fun createBookSourceManageIntent(
        context: Context,
        importSource: String? = null,
    ): Intent =
        createLauncherIntent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_SOURCE_MANAGE)
            putExtra(EXTRA_BOOK_SOURCE_IMPORT, importSource)
        }

    fun createBookSourceEditIntent(context: Context, sourceUrl: String? = null): Intent =
        createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_SOURCE_EDIT)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
        }

    fun createRssSourceManageIntent(context: Context): Intent =
        createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_RSS_SOURCE_MANAGE)
        }

    fun createRssSourceEditIntent(context: Context, sourceUrl: String? = null): Intent =
        createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_RSS_SOURCE_EDIT)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
        }

    fun createBookSourceDebugIntent(context: Context, sourceUrl: String?): Intent =
        createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_SOURCE_DEBUG)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
        }

    fun createRssSourceDebugIntent(context: Context, sourceUrl: String?): Intent =
        createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_RSS_SOURCE_DEBUG)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
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

    fun createReadBookIntent(
        context: Context,
        bookUrl: String? = null,
        readAloud: Boolean = false,
        inBookshelf: Boolean = true,
        chapterChanged: Boolean = false,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_READ_BOOK)
            bookUrl?.let { putExtra(EXTRA_BOOK_URL, it) }
            putExtra(EXTRA_READ_ALOUD, readAloud)
            putExtra(EXTRA_IN_BOOKSHELF, inBookshelf)
            putExtra(EXTRA_CHAPTER_CHANGED, chapterChanged)
        }
    }

    fun createReadMangaIntent(
        context: Context,
        bookUrl: String? = null,
        inBookshelf: Boolean = true,
        chapterChanged: Boolean = false,
    ): Intent = createLauncherIntent(context).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_READ_MANGA)
        bookUrl?.let { putExtra(EXTRA_BOOK_URL, it) }
        putExtra(EXTRA_IN_BOOKSHELF, inBookshelf)
        putExtra(EXTRA_CHAPTER_CHANGED, chapterChanged)
    }

    fun createAudioPlayIntent(
        context: Context,
        bookUrl: String? = null,
        inBookshelf: Boolean = true,
    ): Intent = createLauncherIntent(context).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_AUDIO_PLAY)
        bookUrl?.let { putExtra(EXTRA_BOOK_URL, it) }
        putExtra(EXTRA_IN_BOOKSHELF, inBookshelf)
    }

    fun createSearchIntent(
        context: Context,
        key: String? = null,
        scopeRaw: String? = null
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_SEARCH)
            putExtra(EXTRA_SEARCH_KEY, key)
            scopeRaw?.takeIf { it.isNotBlank() }?.let {
                putExtra(EXTRA_SEARCH_SCOPE, it)
            }
        }
    }

    fun createBookInfoIntent(
        context: Context,
        name: String? = null,
        author: String? = null,
        bookUrl: String,
        origin: String? = null,
        coverPath: String? = null
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_INFO)
            putExtra(EXTRA_BOOK_NAME, name)
            putExtra(EXTRA_BOOK_AUTHOR, author)
            putExtra(EXTRA_BOOK_URL, bookUrl)
            putExtra(EXTRA_BOOK_ORIGIN, origin)
            putExtra(EXTRA_BOOK_COVER, coverPath)
        }
    }

    fun createBookCharacterDetailIntent(
        context: Context,
        bookUrl: String,
        characterId: String? = null,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_CHARACTER_DETAIL)
            putExtra(EXTRA_BOOK_URL, bookUrl)
            putExtra(EXTRA_CHARACTER_ID, characterId)
        }
    }

    fun createBookCharacterNetworkIntent(
        context: Context,
        bookUrl: String,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_CHARACTER_NETWORK)
            putExtra(EXTRA_BOOK_URL, bookUrl)
        }
    }

    fun createBookCharacterListIntent(
        context: Context,
        bookUrl: String,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_CHARACTER_LIST)
            putExtra(EXTRA_BOOK_URL, bookUrl)
        }
    }

    fun createBookKnowledgeListIntent(
        context: Context,
        bookUrl: String,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_KNOWLEDGE_LIST)
            putExtra(EXTRA_BOOK_URL, bookUrl)
        }
    }

    fun createBookKnowledgeDetailIntent(
        context: Context,
        bookUrl: String,
        entryId: String? = null,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_KNOWLEDGE_DETAIL)
            putExtra(EXTRA_BOOK_URL, bookUrl)
            entryId?.let { putExtra(EXTRA_ENTRY_ID, it) }
        }
    }

    fun createBookEventListIntent(
        context: Context,
        bookUrl: String,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_EVENT_LIST)
            putExtra(EXTRA_BOOK_URL, bookUrl)
        }
    }

    fun createBookEventDetailIntent(
        context: Context,
        bookUrl: String,
        eventId: String? = null,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_EVENT_DETAIL)
            putExtra(EXTRA_BOOK_URL, bookUrl)
            eventId?.let { putExtra(EXTRA_EVENT_ID, it) }
        }
    }

    fun createExploreShowIntent(
        context: Context,
        exploreName: String? = null,
        sourceUrl: String,
        exploreUrl: String? = null,
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
