package io.legado.app.ui.rss.favorites

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

/**
 * 收藏夹
 */
class RssFavoritesActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun Content() {
        RssFavoritesScreen(
            onBackClick = { finish() }
        )
    }
}
