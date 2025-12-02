package io.legado.app.ui.book.bookmark

import android.os.Bundle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

/**
 * 所有书签
 */
class AllBookmarkActivity : BaseComposeActivity() {
    @Composable
    override fun Content() {
        MaterialTheme {
            AllBookmarkScreen(
                onBack = { finish() }
            )
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }
}