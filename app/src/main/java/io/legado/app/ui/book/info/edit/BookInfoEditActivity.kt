package io.legado.app.ui.book.info.edit

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import io.legado.app.base.AppTheme
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog

class BookInfoEditActivity : BaseComposeActivity(), ChangeCoverDialog.CallBack {

    private val viewModel by viewModels<BookInfoEditViewModel>()

    @Composable
    override fun Content() {
        setContent {
            AppTheme {
                BookInfoEditScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onSave = ::saveData
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("bookUrl")?.let {
            viewModel.loadBook(it)
        }
    }

    private fun saveData() {
        viewModel.saveBook {
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun coverChangeTo(coverUrl: String) {
        // 更新封面 URL
        viewModel.updateCoverUrl(coverUrl)
    }

}