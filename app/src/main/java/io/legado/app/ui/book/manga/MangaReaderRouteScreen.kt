package io.legado.app.ui.book.manga

import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.ui.book.read.sheet.ReaderBookSheetRoute
import io.legado.app.ui.book.read.sheet.ReaderBookSheetTab
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.openUrl
import io.legado.app.utils.share
import io.legado.app.utils.toggleSystemBar
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MangaReaderRouteScreen(
    bookUrl: String?,
    inBookshelf: Boolean,
    chapterChanged: Boolean,
    viewModel: MangaReaderViewModel,
    restoreSystemBarsVisible: Boolean,
    onFinish: (bookshelfChanged: Boolean) -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String) -> Unit,
    onOpenSourceLogin: (sourceUrl: String) -> Unit,
    onOpenSourceEdit: (sourceUrl: String) -> Unit,
    onOpenWebView: (
        title: String?,
        url: String,
        sourceOrigin: String?,
        sourceName: String?,
        sourceType: Int?,
    ) -> Unit,
) {
    val activity = LocalActivity.current as MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val networkChangedListener = remember(activity) { NetworkChangedListener(activity) }

    LaunchedEffect(viewModel, bookUrl, inBookshelf, chapterChanged) {
        viewModel.onIntent(
            MangaReaderIntent.Initialize(bookUrl, inBookshelf, chapterChanged)
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            val currentState = viewModel.uiState.value
            when (effect) {
                is MangaReaderEffect.Finish -> onFinish(effect.bookshelfChanged)
                MangaReaderEffect.OpenBookInfo -> {
                    if (currentState.bookUrl.isNotEmpty()) {
                        onOpenBookInfo(
                            currentState.bookName,
                            currentState.bookAuthor,
                            currentState.bookUrl,
                        )
                    }
                }
                is MangaReaderEffect.OpenChapterUrl -> {
                    val chapterUrl = currentState.chapterUrl ?: return@collectLatest
                    if (effect.externalBrowser) activity.openUrl(chapterUrl)
                    else onOpenWebView(
                        currentState.chapterName,
                        chapterUrl,
                        currentState.sourceUrl,
                        currentState.sourceName,
                        currentState.sourceType,
                    )
                }
                is MangaReaderEffect.OpenSourceLogin -> onOpenSourceLogin(effect.sourceUrl)
                is MangaReaderEffect.OpenSourceEdit -> onOpenSourceEdit(effect.sourceUrl)
                is MangaReaderEffect.OpenPaymentUrl -> onOpenWebView(
                    activity.getString(io.legado.app.R.string.chapter_pay),
                    effect.url,
                    effect.sourceOrigin,
                    effect.sourceName,
                    effect.sourceType,
                )
                is MangaReaderEffect.SetWindowBrightness -> {
                    activity.window.attributes = activity.window.attributes.apply {
                        screenBrightness = if (effect.auto) {
                            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        } else {
                            (effect.brightness / 255f).coerceIn(0f, 1f)
                        }
                    }
                }
                is MangaReaderEffect.SetSystemBarsVisible -> activity.toggleSystemBar(effect.visible)
                is MangaReaderEffect.ShareImage -> activity.share(
                    java.io.File(effect.filePath),
                    "image/jpeg"
                )

                is MangaReaderEffect.CopyImage -> {
                    val file = java.io.File(effect.filePath)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        activity, io.legado.app.constant.AppConst.authority, file,
                    )
                    activity.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(
                            android.content.ClipData.newUri(activity.contentResolver, "manga", uri)
                        )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel, networkChangedListener) {
        var hasResumed = false
        fun resumeSession() {
            networkChangedListener.register()
            networkChangedListener.onNetworkChanged = {
                if (NetworkUtils.isAvailable()) {
                    viewModel.onIntent(MangaReaderIntent.NetworkAvailable)
                }
            }
            viewModel.onIntent(MangaReaderIntent.ResumeSession)
            if (hasResumed) viewModel.onIntent(MangaReaderIntent.ReloadContent)
            hasResumed = true
        }

        fun pauseSession() {
            viewModel.onIntent(MangaReaderIntent.PauseSession)
            networkChangedListener.unRegister()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumeSession()
                Lifecycle.Event.ON_PAUSE -> pauseSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            resumeSession()
        }
        onDispose {
            pauseSession()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(activity, viewModel, restoreSystemBarsVisible) {
        val originalBrightness = activity.window.attributes.screenBrightness
        activity.activeMangaKeyHandler = fun(keyCode: Int): Boolean {
            val settings = viewModel.uiState.value.settings
            return if (!settings.volumeKeyPage) false
            else {
                val direction = when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> if (settings.reverseVolumeKeyPage) 1 else -1
                    KeyEvent.KEYCODE_VOLUME_DOWN -> if (settings.reverseVolumeKeyPage) -1 else 1
                    else -> return false
                }
                viewModel.onIntent(MangaReaderIntent.PageStep(direction))
                true
            }
        }
        activity.toggleSystemBar(false)
        onDispose {
            activity.activeMangaKeyHandler = null
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = originalBrightness
            }
            activity.toggleSystemBar(restoreSystemBarsVisible)
        }
    }

    val menuHazeState = remember { HazeState() }
    val useMenuHaze = state.settings.menuBottomBarBlur ||
            (!state.settings.menuBottomBarFloating &&
                    state.settings.menuBottomBarLiquidGlass &&
                    state.settingsCategory != null)
    MangaReaderScreen(
        state = state,
        onIntent = viewModel::onIntent,
        hazeState = if (useMenuHaze) menuHazeState else null,
    )
    if (state.activeSheet == MangaReaderSheet.Catalog && state.bookUrl.isNotEmpty()) {
        ReaderBookSheetRoute(
            show = true,
            bookUrl = state.bookUrl,
            initialTab = ReaderBookSheetTab.Toc,
            currentChapterIndex = state.pendingChapterIndex ?: state.chapterIndex,
            onDismissRequest = { viewModel.onIntent(MangaReaderIntent.DismissSheet) },
            onChapterClick = { chapterIndex, pageIndex ->
                viewModel.onIntent(MangaReaderIntent.DismissSheet)
                viewModel.onIntent(MangaReaderIntent.OpenChapter(chapterIndex, pageIndex))
            },
            onOpenFullBookInfo = {
                viewModel.onIntent(MangaReaderIntent.DismissSheet)
                onOpenBookInfo(state.bookName, state.bookAuthor, state.bookUrl)
            },
        )
    }
}
