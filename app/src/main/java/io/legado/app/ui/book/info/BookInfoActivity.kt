package io.legado.app.ui.book.info

import android.os.Bundle
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.startActivity
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
            onOpenReader = { bookUrl, inBookshelf, chapterChanged ->
                startActivity(
                    MainActivity.createReadBookIntent(
                        context = this,
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                    )
                )
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath ->
                startActivity<BookInfoActivity> {
                    putExtra("bookUrl", bookUrl)
                    putExtra("name", name)
                    putExtra("author", author)
                    putExtra("origin", origin)
                    putExtra("coverPath", coverPath)
                }
            },
            onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                startActivity(
                    MainActivity.createExploreShowIntent(this, title, sourceUrl, exploreUrl)
                )
            },
        )
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.setVariable(key, variable)
    }
}
