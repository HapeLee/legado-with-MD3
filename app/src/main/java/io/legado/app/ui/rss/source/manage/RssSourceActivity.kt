package io.legado.app.ui.rss.source.manage

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.theme.AppTheme
import io.legado.app.utils.startActivity

class RssSourceActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        AppTheme {
            RssSourceScreen(
                onBackClick = { finish() },
                onEditSource = { source ->
                    startActivity<RssSourceEditActivity> {
                        putExtra("sourceUrl", source.sourceUrl)
                    }
                },
                onAddSource = {
                    startActivity<RssSourceEditActivity>()
                }
            )
        }
    }

}