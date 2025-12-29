package io.legado.app.ui.replace.edit

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import io.legado.app.ui.theme.AppTheme
import io.legado.app.base.BaseComposeActivity

/**
 * 编辑替换规则
 */
class ReplaceEditActivity : BaseComposeActivity() {

    companion object {
        fun startIntent(
            context: Context,
            id: Long = -1,
            pattern: String? = null,
            isRegex: Boolean = false,
            scope: String? = null,
            isScopeTitle: Boolean = false,
            isScopeContent: Boolean = false
        ): Intent = Intent(context, ReplaceEditActivity::class.java).apply {
            putExtra("id", id)
            putExtra("pattern", pattern)
            putExtra("isRegex", isRegex)
            putExtra("scope", scope)
            putExtra("isScopeTitle", isScopeTitle)
            putExtra("isScopeContent", isScopeContent)
        }
    }

    @Composable
    override fun Content() {
        setContent {
            AppTheme {
                ReplaceEditScreen(
                    onBack = { finish() },
                    onSaveSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    onShowHelp = { key -> showHelp(key) }
                )
            }
        }
    }

    private fun showHelp(key: String) {

    }

}
