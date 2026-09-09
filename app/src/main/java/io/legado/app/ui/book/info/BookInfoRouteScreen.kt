package io.legado.app.ui.book.info

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.externalFiles
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.openFileUri
import io.legado.app.utils.sendToClip
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import splitties.init.appCtx

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookInfoRouteScreen(
    bookUrl: String,
    name: String? = null,
    author: String? = null,
    origin: String? = null,
    coverPath: String? = null,
    viewModel: BookInfoViewModel,
    onBack: () -> Unit,
    onFinish: (resultCode: Int?, afterTransition: Boolean) -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenBookSourceEdit: (String) -> Unit,
    onOpenSourceLogin: (String) -> Unit,
    onOpenReader: (bookUrl: String, inBookshelf: Boolean, chapterChanged: Boolean) -> Unit = { _, _, _ -> },
    onOpenMangaReader: (bookUrl: String, inBookshelf: Boolean, chapterChanged: Boolean) -> Unit = { _, _, _ -> },
    onOpenAudioPlay: (bookUrl: String, inBookshelf: Boolean) -> Unit = { _, _ -> },
    onNavigateToBookInfo: (name: String?, author: String?, bookUrl: String, origin: String?, coverPath: String?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit = { _, _, _ -> },
    onOpenCharacterDetail: (bookUrl: String, characterId: String?) -> Unit = { _, _ -> },
    onOpenCharacterNetwork: (bookUrl: String) -> Unit = {},
    onOpenCharacterList: (bookUrl: String) -> Unit = {},
    onOpenKnowledgeList: (bookUrl: String) -> Unit = {},
    onOpenEventList: (bookUrl: String) -> Unit = {},
    /** 打开目录页（picker）：回传选中章节/取消的活由 host 层挂结果通道承担。 */
    onOpenToc: (bookUrl: String) -> Unit = {},
    /** 打开书籍信息编辑页：保存成功后要刷新本书，`onInfoEdited` 由 host 层的结果通道触发。 */
    onOpenBookInfoEdit: (bookUrl: String) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showMangaUi by rememberUpdatedState(uiState.showMangaUi)
    var showSelectBooksDirSheet by remember { mutableStateOf(false) }


    val localBookTreeSelect =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (RealPathUtil.getTreePath(uri)?.startsWith(appCtx.externalFiles.parent!!) == true) {
                return@rememberLauncherForActivityResult
            }
            if (uri.isContentScheme()) {
                uri.takePersistablePermissionSafely(activity)
            }
            viewModel.onIntent(BookInfoIntent.SetDefaultBookTreeUri(uri.toString()))
        }
    LaunchedEffect(bookUrl, name, author, origin, coverPath, viewModel) {
        viewModel.initData(
            bookUrl = bookUrl,
            name = name,
            author = author,
            origin = origin,
            coverPath = coverPath
        )
    }


    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshShelfState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel, activity) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BookInfoEffect.ShowMessage -> context.toastOnUi(effect.message)
                is BookInfoEffect.Finish -> {
                    onFinish(effect.resultCode, effect.afterTransition)
                }

                is BookInfoEffect.OpenBookInfoEdit -> {
                    onOpenBookInfoEdit(effect.bookUrl)
                }

                is BookInfoEffect.OpenReader -> {
                    when {
                        effect.book.isAudio -> onOpenAudioPlay(
                            effect.book.bookUrl,
                            effect.inBookshelf
                        )

                        !effect.book.isLocal && effect.book.isImage && showMangaUi -> {
                            onOpenMangaReader(
                                effect.book.bookUrl,
                                effect.inBookshelf,
                                effect.chapterChanged,
                            )
                        }
                        else -> {
                        onOpenReader(
                            effect.book.bookUrl,
                            effect.inBookshelf,
                            effect.chapterChanged,
                        )
                        }
                    }
                }

                is BookInfoEffect.OpenToc -> {
                    // 目录页在返回栈内：压栈后由同栈结果通道回传选中/取消。
                    onOpenToc(effect.bookUrl)
                }
                is BookInfoEffect.OpenBookSourceEdit -> {
                    onOpenBookSourceEdit(effect.sourceUrl)
                }

                is BookInfoEffect.OpenSourceLogin -> {
                    onOpenSourceLogin(effect.sourceUrl)
                }

                BookInfoEffect.OpenSelectBooksDir -> showSelectBooksDirSheet = true

                is BookInfoEffect.OpenFile -> activity.openFileUri(effect.uri, effect.mimeType)
                is BookInfoEffect.RunSourceCallback -> {
                    runSourceCallback(activity, effect, viewModel, onOpenSearch)
                }

                is BookInfoEffect.RunIntroJs -> {
                    runIntroJs(activity, effect)
                }


                is BookInfoEffect.NavigateToBookInfo -> {
                    onNavigateToBookInfo(effect.name, effect.author, effect.bookUrl, effect.origin, effect.coverPath)
                }

                is BookInfoEffect.NavigateToExploreShow -> {
                    onNavigateToExploreShow(effect.title, effect.sourceUrl, effect.exploreUrl)
                }

                is BookInfoEffect.OpenCharacterDetail -> {
                    onOpenCharacterDetail(effect.bookUrl, effect.characterId)
                }

                is BookInfoEffect.OpenCharacterNetwork -> {
                    onOpenCharacterNetwork(effect.bookUrl)
                }

                is BookInfoEffect.OpenKnowledgeList -> {
                    onOpenKnowledgeList(effect.bookUrl)
                }

                is BookInfoEffect.OpenCharacterList -> {
                    onOpenCharacterList(effect.bookUrl)
                }

                is BookInfoEffect.OpenEventList -> {
                    onOpenEventList(effect.bookUrl)
                }
            }
        }
    }

    FilePickerSheet(
        show = showSelectBooksDirSheet,
        onDismissRequest = { showSelectBooksDirSheet = false },
        title = stringResource(R.string.select_book_folder),
        onSelectSysDir = {
            showSelectBooksDirSheet = false
            localBookTreeSelect.launch(null)
        },
    )
    BookInfoScreen(
        state = uiState,
        groups = viewModel.allGroups
            .collectAsStateWithLifecycle(persistentListOf<BookGroup>()).value,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedCoverKey = sharedCoverKey,
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

            BookInfoCallbackAction.None -> Unit
        }
    }
}

private fun runIntroJs(activity: AppCompatActivity, effect: BookInfoEffect.RunIntroJs) {
    val source = effect.source ?: return
    activity.lifecycleScope.launch(IO) {
        try {
            val java = SourceLoginJsExtensions(activity, source)
            runScriptWithContext {
                source.evalJS(effect.click) {
                    put("result", null)
                    put("java", java)
                    put("book", effect.book)
                }
            }
        } catch (e: Throwable) {
            AppLog.put("${source.bookSourceName}: ${e.localizedMessage}", e)
            activity.toastOnUi("${effect.name} click error\n${e.localizedMessage}")
        }
    }
}
