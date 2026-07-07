package io.legado.app.ui.book.toc.rule.preview

import android.content.Intent
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity

class TxtTocRulePreviewActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val bookUrl = intent.getStringExtra("bookUrl") ?: ""
        val currentTocRegex = intent.getStringExtra("tocRegex")

        TxtTocRulePreviewRouteScreen(
            bookUrl = bookUrl,
            currentTocRegex = currentTocRegex,
            onBack = { finish() },
            onApplyRule = { rule ->
                val data = Intent().apply {
                    putExtra("tocRegex", rule)
                }
                setResult(RESULT_OK, data)
                finish()
            },
            onOpenManagePage = {
                startActivity(Intent(this, TxtTocRuleActivity::class.java))
            },
        )
    }
}
