@file:Suppress("DEPRECATION")

package io.legado.app.ui.book.toc

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.replace.ReplaceRuleActivity

/**
 * 目录
 */
class TocActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current

        TocScreen(
            onBackClick = { finish() },
            onChapterClick = { index ->
                val data = Intent().apply {
                    putExtra("index", index)
                }
                setResult(RESULT_OK, data)
                finish()
            },
            onOpenReplaceRule = { editRoute ->
                val intent = ReplaceRuleActivity.startIntent(context, editRoute)
                context.startActivity(intent)
            },
            onBookmarkClick = { index, pos ->
                val data = Intent().apply {
                    putExtra("index", index)
                    putExtra("chapterPos", pos)
                }
                setResult(RESULT_OK, data)
                finish()
            }
        )
    }
}