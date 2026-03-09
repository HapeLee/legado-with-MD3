package io.legado.app.ui.rss.subscription

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

/**
 * 规则订阅界面
 */
class RuleSubActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun Content() {
        RuleSubScreen(
            onBackClick = { finish() }
        )
    }

}
