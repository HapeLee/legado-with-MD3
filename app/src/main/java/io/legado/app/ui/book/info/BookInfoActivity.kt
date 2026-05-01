package io.legado.app.ui.book.info

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookInfoActivity : BaseComposeActivity(), VariableDialog.Callback {

    private val viewModel: BookInfoViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun Content() {
        BookInfoRouteScreen(
            bookUrl = intent.getStringExtra("bookUrl").orEmpty(),
            viewModel = viewModel,
            onBack = { finishAfterTransition() },
            onFinish = { resultCode, afterTransition ->
                resultCode?.let { setResult(it) }
                if (afterTransition) finishAfterTransition() else finish()
            },
            onOpenSearch = { keyword ->
                startActivity(MainActivity.createSearchIntent(this, key = keyword))
            },
        )
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.setVariable(key, variable)
    }
}
