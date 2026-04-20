package io.legado.app.ui.rss.favorites

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.rss.read.RssReadRouteScreen

/**
 * 收藏夹
 */
class RssFavoritesActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun Content() {
        var readArgs by remember {
            mutableStateOf<ReadArgs?>(null)
        }

        val currentReadArgs = readArgs
        if (currentReadArgs == null) {
            RssFavoritesScreen(
                onBackClick = { finish() },
                onOpenRead = { title, origin, link, openUrl ->
                    readArgs = ReadArgs(title, origin, link, openUrl)
                }
            )
        } else {
            RssReadRouteScreen(
                title = currentReadArgs.title,
                origin = currentReadArgs.origin,
                link = currentReadArgs.link,
                openUrl = currentReadArgs.openUrl,
                onBackClick = { readArgs = null }
            )
        }
    }

    private data class ReadArgs(
        val title: String?,
        val origin: String,
        val link: String?,
        val openUrl: String?
    )
}
