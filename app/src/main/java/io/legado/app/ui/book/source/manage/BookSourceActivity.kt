package io.legado.app.ui.book.source.manage

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity

/** Legacy entry point retained for callers outside the Compose navigation graph. */
class BookSourceActivity : BaseComposeActivity() {
    private val importDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { showDialogFragment(ImportBookSourceDialog(it.toString())) }
    }

    @Composable
    override fun Content() {
        BookSourceRouteScreen(
            onBackClick = ::finish,
            onAddSource = { startActivity<BookSourceEditActivity>() },
            onEditSource = { sourceUrl ->
                startActivity<BookSourceEditActivity> {
                    putExtra("sourceUrl", sourceUrl)
                }
            },
            onImportLocal = {
                importDocument.launch(
                    arrayOf(
                        "application/json",
                        "text/plain",
                        "text/*"
                    )
                )
            },
            onImportOnline = { text -> showDialogFragment(ImportBookSourceDialog(text)) },
        )
    }
}
