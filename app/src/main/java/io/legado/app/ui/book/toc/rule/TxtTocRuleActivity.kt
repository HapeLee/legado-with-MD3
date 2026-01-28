package io.legado.app.ui.book.toc.rule

//import io.legado.app.lib.theme.primaryColor
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.theme.AppTheme

class TxtTocRuleActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        AppTheme {
            TxtRuleScreen(onBackClick = { finish() })
        }
    }

}
