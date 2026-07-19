package io.legado.app.ui.book.info.edit

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.main.MainActivity

class BookInfoEditActivity : BaseComposeActivity(), ChangeCoverDialog.CallBack {

    private val viewModel by viewModels<BookInfoEditViewModel>()

    @Composable
    override fun Content() {
        BookInfoEditScreen(
            viewModel = viewModel,
            onBack = { finish() },
            onSave = {
                viewModel.save {
                    setResult(RESULT_OK)
                    finish()
                }
            },
            onOpenCharacterList = { bookUrl ->
                startActivity(MainActivity.createBookCharacterListIntent(this, bookUrl))
            },
            onOpenCharacterNetwork = { bookUrl ->
                startActivity(MainActivity.createBookCharacterNetworkIntent(this, bookUrl))
            },
            onOpenKnowledgeList = { bookUrl ->
                startActivity(MainActivity.createBookKnowledgeListIntent(this, bookUrl))
            },
            onOpenEventList = { bookUrl ->
                startActivity(MainActivity.createBookEventListIntent(this, bookUrl))
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("bookUrl")?.let {
            viewModel.loadBook(it)
        }
    }

    override fun coverChangeTo(coverUrl: String) {
        // 更新封面 URL
        viewModel.onCoverUrlChange(coverUrl)
    }

}
