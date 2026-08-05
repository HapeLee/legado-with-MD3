package io.legado.app.ui.dict

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class DictActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        DictSheet(
            show = true,
            word = intent.getStringExtra(EXTRA_WORD).orEmpty(),
            onDismissRequest = { finish() },
        )
    }

    companion object {
        private const val EXTRA_WORD = "word"

        fun startIntent(context: Context, word: String): Intent {
            return Intent(context, DictActivity::class.java)
                .putExtra(EXTRA_WORD, word)
                .apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
        }
    }
}
