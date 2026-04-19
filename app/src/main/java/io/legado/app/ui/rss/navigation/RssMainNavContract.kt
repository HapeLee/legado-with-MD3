package io.legado.app.ui.rss.navigation

import android.content.Context
import android.content.Intent
import androidx.navigation3.runtime.NavKey
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.rss.article.MainRouteRssSort
import io.legado.app.ui.rss.read.MainRouteRssRead

object RssMainNavContract {

    private const val ROUTE_RSS_SORT = "rss/sort"
    private const val ROUTE_RSS_READ = "rss/read"

    private const val EXTRA_RSS_SOURCE_URL = "extra_rss_source_url"
    private const val EXTRA_RSS_SORT_URL = "extra_rss_sort_url"
    private const val EXTRA_RSS_KEY = "extra_rss_key"

    private const val EXTRA_RSS_READ_TITLE = "extra_rss_read_title"
    private const val EXTRA_RSS_READ_ORIGIN = "extra_rss_read_origin"
    private const val EXTRA_RSS_READ_LINK = "extra_rss_read_link"
    private const val EXTRA_RSS_READ_OPEN_URL = "extra_rss_read_open_url"

    fun createRssSortIntent(
        context: Context,
        sourceUrl: String,
        sortUrl: String? = null,
        key: String? = null
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_ROUTE, ROUTE_RSS_SORT)
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
        return Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_ROUTE, ROUTE_RSS_READ)
            putExtra(EXTRA_RSS_READ_TITLE, title)
            putExtra(EXTRA_RSS_READ_ORIGIN, origin)
            putExtra(EXTRA_RSS_READ_LINK, link)
            putExtra(EXTRA_RSS_READ_OPEN_URL, openUrl)
        }
    }

    fun resolveStartRoute(route: String?, intent: Intent?): NavKey? {
        return when (route) {
            ROUTE_RSS_SORT -> {
                val sourceUrl = intent?.getStringExtra(EXTRA_RSS_SOURCE_URL)
                if (sourceUrl.isNullOrBlank()) {
                    null
                } else {
                    MainRouteRssSort(
                        sourceUrl = sourceUrl,
                        sortUrl = intent.getStringExtra(EXTRA_RSS_SORT_URL),
                        key = intent.getStringExtra(EXTRA_RSS_KEY)
                    )
                }
            }

            ROUTE_RSS_READ -> {
                val origin = intent?.getStringExtra(EXTRA_RSS_READ_ORIGIN)
                if (origin.isNullOrBlank()) {
                    null
                } else {
                    MainRouteRssRead(
                        title = intent.getStringExtra(EXTRA_RSS_READ_TITLE),
                        origin = origin,
                        link = intent.getStringExtra(EXTRA_RSS_READ_LINK),
                        openUrl = intent.getStringExtra(EXTRA_RSS_READ_OPEN_URL)
                    )
                }
            }

            else -> null
        }
    }
}
