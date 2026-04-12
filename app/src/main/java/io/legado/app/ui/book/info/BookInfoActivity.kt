package io.legado.app.ui.book.info

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.help.config.AppConfig
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.openFileUri
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookInfoActivity : BaseComposeActivity(), VariableDialog.Callback {

    private val viewModel: BookInfoViewModel by viewModel()

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        viewModel.onTocResult(it)
    }
    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
        }
    }
    private val infoEditResult = registerForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.onInfoEdited()
        }
    }
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) {
        if (it.resultCode != RESULT_CANCELED) {
            viewModel.onSourceEdited()
        }
    }
    private val readBookResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onReaderResult(it.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initData(intent)
    }

    @Composable
    override fun Content() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value

        LaunchedEffect(Unit) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is BookInfoEffect.Finish -> {
                        effect.resultCode?.let { setResult(it) }
                        if (effect.afterTransition) finishAfterTransition() else finish()
                    }

                    is BookInfoEffect.OpenBookInfoEdit -> {
                        infoEditResult.launch {
                            putExtra("bookUrl", effect.bookUrl)
                        }
                    }

                    is BookInfoEffect.OpenReader -> startReadActivity(
                        book = effect.book,
                        inBookshelf = effect.inBookshelf,
                        chapterChanged = effect.chapterChanged,
                    )
                    is BookInfoEffect.OpenToc -> tocActivityResult.launch(effect.bookUrl)
                    is BookInfoEffect.OpenBookSourceEdit -> {
                        editSourceResult.launch {
                            putExtra("sourceUrl", effect.sourceUrl)
                        }
                    }

                    is BookInfoEffect.OpenSourceLogin -> {
                        startActivity<SourceLoginActivity> {
                            putExtra("type", "bookSource")
                            putExtra("key", effect.sourceUrl)
                        }
                    }

                    BookInfoEffect.OpenSelectBooksDir -> localBookTreeSelect.launch {
                        title = getString(R.string.select_book_folder)
                    }

                    is BookInfoEffect.OpenFile -> openFileUri(effect.uri, effect.mimeType)
                    is BookInfoEffect.RunSourceCallback -> runSourceCallback(effect)
                    is BookInfoEffect.ShowVariableDialog -> {
                        showDialogFragment(
                            VariableDialog(
                                effect.title,
                                effect.key,
                                effect.variable,
                                effect.comment,
                            )
                        )
                    }
                }
            }
        }

        BookInfoScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onBack = { finishAfterTransition() },
        )
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.setVariable(key, variable)
    }

    private fun runSourceCallback(effect: BookInfoEffect.RunSourceCallback) {
        SourceCallBack.callBackBtn(
            this,
            effect.event,
            effect.source,
            effect.book,
            null,
        ) {
            when (val action = effect.action) {
                is BookInfoCallbackAction.Search -> {
                    startActivity<SearchActivity> {
                        putExtra("key", action.keyword)
                    }
                }

                is BookInfoCallbackAction.ShareText -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, action.text)
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(intent, action.chooserTitle))
                }

                is BookInfoCallbackAction.CopyText -> {
                    sendToClip(action.text)
                }

                BookInfoCallbackAction.ClearCache -> {
                    viewModel.clearCache()
                }
            }
        }
    }

    private fun startReadActivity(
        book: io.legado.app.data.entities.Book,
        inBookshelf: Boolean,
        chapterChanged: Boolean,
    ) {
        val cls = when {
            book.isAudio -> AudioPlayActivity::class.java
            !book.isLocal && book.isImage && AppConfig.showMangaUi -> ReadMangaActivity::class.java
            else -> ReadBookActivity::class.java
        }
        readBookResult.launch(
            Intent(this, cls).apply {
                putExtra("bookUrl", book.bookUrl)
                putExtra("inBookshelf", inBookshelf)
                putExtra("chapterChanged", chapterChanged)
            }
        )
    }
}
