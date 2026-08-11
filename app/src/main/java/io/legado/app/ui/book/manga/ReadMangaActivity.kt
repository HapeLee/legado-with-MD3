package io.legado.app.ui.book.manga

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.info.READER_RESULT_DELETED
import io.legado.app.ui.book.read.sheet.ReaderBookSheetRoute
import io.legado.app.ui.book.read.sheet.ReaderBookSheetTab
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.openUrl
import io.legado.app.utils.toggleSystemBar
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Android compatibility host for the Compose manga reader.
 *
 * The reader surface and interaction state live in [MangaReaderScreen] and
 * [MangaReaderViewModel]. This activity only owns Android activity results,
 * window state and external activity navigation.
 */
class ReadMangaActivity : BaseComposeActivity(imageBg = false) {

    private val readerViewModel by viewModel<MangaReaderViewModel>()
    private val networkChangedListener by lazy { NetworkChangedListener(this) }

    private var isRestoredFromSavedState = false
    private var justInitialized = false

    private val sourceEditActivity =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                readerViewModel.onIntent(MangaReaderIntent.RefreshBookSource)
            }
        }

    private val bookInfoActivity =
        registerForActivityResult(StartActivityContract(BookInfoActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                setResult(READER_RESULT_DELETED)
                finish()
            } else {
                readerViewModel.onIntent(MangaReaderIntent.ReloadContent)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        isRestoredFromSavedState = savedInstanceState != null
        super.onCreate(savedInstanceState)
        toggleSystemBar(false)
        justInitialized = true
        initializeReader(intent)
    }

    @Composable
    override fun Content() {
        val state by readerViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            readerViewModel.effects.collectLatest(::handleEffect)
        }
        MangaReaderScreen(state = state, onIntent = readerViewModel::onIntent)
        if (state.activeSheet == MangaReaderSheet.Catalog && state.bookUrl.isNotEmpty()) {
            ReaderBookSheetRoute(
                show = true,
                bookUrl = state.bookUrl,
                initialTab = ReaderBookSheetTab.Toc,
                onDismissRequest = { readerViewModel.onIntent(MangaReaderIntent.DismissSheet) },
                onChapterClick = { chapterIndex, pageIndex ->
                    readerViewModel.onIntent(MangaReaderIntent.DismissSheet)
                    readerViewModel.onIntent(MangaReaderIntent.OpenChapter(chapterIndex, pageIndex))
                },
                onOpenFullBookInfo = {
                    readerViewModel.onIntent(MangaReaderIntent.DismissSheet)
                    openBookInfoActivity()
                },
            )
        }
    }

    private fun handleEffect(effect: MangaReaderEffect) {
        when (effect) {
            is MangaReaderEffect.Finish -> {
                if (effect.bookshelfChanged) setResult(RESULT_OK)
                finishReader()
            }
            MangaReaderEffect.OpenBookInfo -> openBookInfoActivity()
            is MangaReaderEffect.OpenChapterUrl -> openCurrentChapterUrl(effect.externalBrowser)
            is MangaReaderEffect.OpenSourceLogin -> startActivity(
                MainActivity.createSourceLoginIntent(
                    this,
                    SourceLoginType.BookSource,
                    effect.sourceUrl,
                )
            )
            is MangaReaderEffect.OpenSourceEdit -> sourceEditActivity.launch(
                MainActivity.createBookSourceEditIntent(this, effect.sourceUrl)
            )
            is MangaReaderEffect.OpenPaymentUrl -> startActivity(
                MainActivity.createWebViewIntent(
                    this,
                    getString(R.string.chapter_pay),
                    effect.url,
                    effect.sourceOrigin,
                    effect.sourceName,
                    effect.sourceType,
                )
            )
            is MangaReaderEffect.SetWindowBrightness -> {
                if (effect.auto) resetWindowToSystemBrightness()
                else updateWindowBrightness(effect.brightness)
            }
            is MangaReaderEffect.SetSystemBarsVisible -> toggleSystemBar(effect.visible)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initializeReader(intent)
    }

    private fun initializeReader(androidIntent: Intent) {
        readerViewModel.onIntent(
            MangaReaderIntent.Initialize(
                bookUrl = androidIntent.getStringExtra("bookUrl"),
                inBookshelf = androidIntent.getBooleanExtra("inBookshelf", true),
                chapterChanged = androidIntent.getBooleanExtra("chapterChanged", false),
            )
        )
    }

    override fun onResume() {
        super.onResume()
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            if (NetworkUtils.isAvailable() && !justInitialized) {
                readerViewModel.onIntent(MangaReaderIntent.NetworkAvailable)
            }
        }
        justInitialized = false
        readerViewModel.onIntent(MangaReaderIntent.ResumeSession)
    }

    override fun onPause() {
        readerViewModel.onIntent(MangaReaderIntent.PauseSession)
        networkChangedListener.unRegister()
        super.onPause()
    }

    private fun finishReader() {
        if (readerViewModel.uiState.value.inBookshelf && !isRestoredFromSavedState) supportFinishAfterTransition()
        else finish()
    }

    private fun openBookInfoActivity() {
        readerViewModel.uiState.value.let {
            if (it.bookUrl.isEmpty()) return
            bookInfoActivity.launch {
                putExtra("name", it.bookName)
                putExtra("author", it.bookAuthor)
                putExtra("bookUrl", it.bookUrl)
            }
        }
    }

    private fun openCurrentChapterUrl(externalBrowser: Boolean) {
        val state = readerViewModel.uiState.value
        val chapterUrl = state.chapterUrl ?: return
        if (externalBrowser) {
            openUrl(chapterUrl)
            return
        }
        startActivity(
            MainActivity.createWebViewIntent(
                this,
                state.chapterName,
                chapterUrl,
                state.sourceUrl,
                state.sourceName,
                state.sourceType,
            )
        )
    }

    private fun resetWindowToSystemBrightness() {
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun updateWindowBrightness(brightness: Int) {
        window.attributes = window.attributes.apply {
            screenBrightness = (brightness / 255f).coerceIn(0f, 1f)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val settings = readerViewModel.uiState.value.settings
        if (!settings.volumeKeyPage) return super.onKeyDown(keyCode, event)
        val reverse = settings.reverseVolumeKeyPage
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (reverse) 1 else -1
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverse) -1 else 1
            else -> return super.onKeyDown(keyCode, event)
        }
        readerViewModel.onIntent(MangaReaderIntent.PageStep(direction))
        return true
    }
}
