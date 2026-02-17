package io.legado.app.ui.book.toc.rule

//import io.legado.app.lib.theme.primaryColor
import android.content.Intent
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.theme.AppTheme

class TxtTocRuleActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val initialRule = intent.getStringExtra("tocRegex")

        AppTheme {
            TxtRuleScreen(
                initialRule = initialRule,
                onPickRule = { rule ->
                    val data = Intent().apply {
                        putExtra("tocRegex", rule)
                    }
                    setResult(RESULT_OK, data)
                    finish()
                },
                onBackClick = { finish() }
            )
        }
    }

}
