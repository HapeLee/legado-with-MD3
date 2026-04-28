package io.legado.app.ui.book.info

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.openFileUri
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import kotlinx.coroutines.flow.collectLatest

@Composable
fun BookInfoRouteScreen(
    bookUrl: String,
    viewModel: BookInfoViewModel,
    onBack: () -> Unit,
    onFinish: (resultCode: Int?, afterTransition: Boolean) -> Unit,
    onOpenSearch: (String) -> Unit,
    onRegisterVariableSetter: (((String, String?) -> Unit)?) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity

    val tocActivityResult = rememberLauncherForActivityResult(TocActivityResult()) {
        viewModel.onTocResult(it)
    }
    val localBookTreeSelect = rememberLauncherForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            OtherConfig.defaultBookTreeUri = treeUri.toString()
        }
    }
    val infoEditResult = rememberLauncherForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            viewModel.onInfoEdited()
        }
    }
    val editSourceResult = rememberLauncherForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) {
        if (it.resultCode != Activity.RESULT_CANCELED) {
            viewModel.onSourceEdited()
        }
    }
    val readBookResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onReaderResult(it.resultCode)
    }

    LaunchedEffect(bookUrl, viewModel) {
        viewModel.initData(bookUrl)
    }

    DisposableEffect(viewModel) {
        onRegisterVariableSetter(viewModel::setVariable)
        onDispose {
            onRegisterVariableSetter(null)
        }
    }

    LaunchedEffect(viewModel, activity) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BookInfoEffect.Finish -> {
                    onFinish(effect.resultCode, effect.afterTransition)
                }

                is BookInfoEffect.OpenBookInfoEdit -> {
                    infoEditResult.launch {
                        putExtra("bookUrl", effect.bookUrl)
                    }
                }

                is BookInfoEffect.OpenReader -> {
                    val cls = when {
                        effect.book.isAudio -> AudioPlayActivity::class.java
                        !effect.book.isLocal && effect.book.isImage && AppConfig.showMangaUi -> {
                            ReadMangaActivity::class.java
                        }

                        else -> ReadBookActivity::class.java
                    }
                    readBookResult.launch(
                        Intent(activity, cls).apply {
                            putExtra("bookUrl", effect.book.bookUrl)
                            putExtra("inBookshelf", effect.inBookshelf)
                            putExtra("chapterChanged", effect.chapterChanged)
                        }
                    )
                }

                is BookInfoEffect.OpenToc -> tocActivityResult.launch(effect.bookUrl)
                is BookInfoEffect.OpenBookSourceEdit -> {
                    editSourceResult.launch {
                        putExtra("sourceUrl", effect.sourceUrl)
                    }
                }

                is BookInfoEffect.OpenSourceLogin -> {
                    activity.startActivity<SourceLoginActivity> {
                        putExtra("type", "bookSource")
                        putExtra("key", effect.sourceUrl)
                    }
                }

                BookInfoEffect.OpenSelectBooksDir -> localBookTreeSelect.launch {
                    title = activity.getString(R.string.select_book_folder)
                }

                is BookInfoEffect.OpenFile -> activity.openFileUri(effect.uri, effect.mimeType)
                is BookInfoEffect.RunSourceCallback -> {
                    runSourceCallback(activity, effect, viewModel, onOpenSearch)
                }

                is BookInfoEffect.ShowVariableDialog -> {
                    activity.showDialogFragment(
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
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

private fun runSourceCallback(
    activity: AppCompatActivity,
    effect: BookInfoEffect.RunSourceCallback,
    viewModel: BookInfoViewModel,
    onOpenSearch: (String) -> Unit,
) {
    SourceCallBack.callBackBtn(
        activity,
        effect.event,
        effect.source,
        effect.book,
        null,
    ) {
        when (val action = effect.action) {
            is BookInfoCallbackAction.Search -> {
                onOpenSearch(action.keyword)
            }

            is BookInfoCallbackAction.ShareText -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, action.text)
                    type = "text/plain"
                }
                activity.startActivity(Intent.createChooser(intent, action.chooserTitle))
            }

            is BookInfoCallbackAction.CopyText -> {
                activity.sendToClip(action.text)
            }

            BookInfoCallbackAction.ClearCache -> {
                viewModel.clearCache()
            }
        }
    }
}
