package io.legado.app.ui.book.read

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.legado.app.R
import io.legado.app.help.IntentData
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.searchContent.SearchContentActivity
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.model.ReadBook as ReadBookModel

data class ReadBookViewRefs(
    val root: FrameLayout,
    val readView: ReadView,
    val textMenuPosition: View,
    val cursorLeft: ImageView,
    val cursorRight: ImageView,
    val navigationBar: View,
)

interface ReadBookRouteHost :
    View.OnTouchListener,
    ReadView.CallBack,
    ContentTextView.CallBack {

    val isInMultiWindowModeCompat: Boolean

    fun closeReadBook()

    fun upSystemUiVisibility(
        isInMultiWindow: Boolean,
        toolBarHide: Boolean,
    )
}

/**
 * Outer wrapper for ReadBookScreen — handles system UI state sync
 * and ActivityResult launcher registration.
 */
@Composable
fun ReadBookRouteScreen(
    viewModel: ReadBookViewModel,
    host: ReadBookRouteHost,
    controller: ReadBookController,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readPreferences by viewModel.readPreferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val menuBackdrop = rememberLayerBackdrop()

    // ── ActivityResult Launchers ──────────────────────────────────────

    val tocLauncher = rememberLauncherForActivityResult(TocActivityResult()) { result ->
        result?.let { (index, chapterPos, _) ->
            viewModel.openChapter(index, chapterPos)
        }
    }

    val sourceEditLauncher = rememberLauncherForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.upBookSource()
        }
    }

    val replaceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.replaceRuleChanged()
        }
    }

    val fontFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            it.takePersistablePermissionSafely(context)
            viewModel.setFontFolder(it.toString())
            viewModel.onIntent(ReadBookIntent.DismissSheet)
            viewModel.onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.FontSelect))
        }
    }

    val readStyleImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ReadStyleImageSelected(it)) }
    }

    val readStyleImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ReadStyleConfigImportSelected(it)) }
    }

    val readStyleExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ReadStyleConfigExportSelected(it)) }
    }

    val txtTocRuleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra("tocRegex")?.let { rule ->
                ReadBookModel.book?.let {
                    it.tocUrl = rule
                    viewModel.loadChapterList(it)
                }
            }
        }
    }

    val searchContentLauncher = rememberLauncherForActivityResult(
        StartActivityContract(SearchContentActivity::class.java)
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val key = data.getLongExtra("key", System.currentTimeMillis())
        val index = data.getIntExtra("index", 0)
        val searchResult = IntentData.get<SearchResult>("searchResult$key")
        val searchResultList = IntentData.get<List<SearchResult>>("searchResultList$key")
        if (searchResult != null && searchResultList != null) {
            viewModel.searchContentQuery = searchResult.query
            viewModel.onIntent(ReadBookIntent.SetSearchResults(searchResultList, index))
        }
    }

    val bookInfoLauncher = rememberLauncherForActivityResult(
        StartActivityContract(BookInfoActivity::class.java)
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // Book deleted — finish the reader
            controller.closeReadBook()
        } else {
            ReadBook.loadOrUpContent()
        }
    }

    // ── Register launchers to controller ──────────────────────────────

    LaunchedEffect(controller) {
        controller.registerLaunchers(
            tocLauncher = tocLauncher,
            sourceEditLauncher = sourceEditLauncher,
            replaceLauncher = replaceLauncher,
            fontFolderPicker = fontFolderPicker,
            readStyleImagePicker = readStyleImagePicker,
            readStyleImportPicker = readStyleImportPicker,
            readStyleExportPicker = readStyleExportPicker,
            txtTocRuleLauncher = txtTocRuleLauncher,
            searchContentLauncher = searchContentLauncher,
            bookInfoLauncher = bookInfoLauncher,
        )
    }

    // ── System UI sync ────────────────────────────────────────────────

    LaunchedEffect(state.toolBarHide, state.menuVisible) {
        host.upSystemUiVisibility(host.isInMultiWindowModeCompat, !state.menuVisible)
    }
    // ── View layer + Compose UI ───────────────────────────────────────

    Box(Modifier.fillMaxSize()) {
        ReadBookViewLayer(
            modifier = Modifier.layerBackdrop(menuBackdrop),
            onRefsReady = { controller.onRefsReady(it) },
            onCursorTouch = controller,
            readViewCallBack = controller,
            contentTextViewCallBack = controller,
        )
        ReadBookColorTheme(
            configUpdateTrigger = state.configUpdateTrigger,
            preferences = readPreferences,
        ) {
            ReadBookMenuBar(state = state, onIntent = viewModel::onIntent, backdrop = menuBackdrop)
            ReadBookSearchBar(state = state, onIntent = viewModel::onIntent)
            ReadBookScreen(
                state = state,
                onIntent = viewModel::onIntent,
                onBack = { controller.closeReadBook() },
            )
        }
    }
}

@Composable
private fun ReadBookViewLayer(
    modifier: Modifier = Modifier,
    onRefsReady: (ReadBookViewRefs) -> Unit,
    onCursorTouch: View.OnTouchListener,
    readViewCallBack: ReadView.CallBack,
    contentTextViewCallBack: ContentTextView.CallBack,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                val readView = ReadView(
                    context = context,
                    callBack = readViewCallBack,
                    contentCallBack = contentTextViewCallBack,
                ).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                }
                val textMenuPosition = View(context).apply {
                    id = R.id.text_menu_position
                    visibility = View.INVISIBLE
                    layoutParams = FrameLayout.LayoutParams(0, 0)
                }
                val cursorLeft = ImageView(context).apply {
                    id = R.id.cursor_left
                    contentDescription = context.getString(R.string.select_start)
                    setImageResource(R.drawable.ic_cursor_left)
                    visibility = View.INVISIBLE
                    setOnTouchListener(onCursorTouch)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    )
                }
                val cursorRight = ImageView(context).apply {
                    id = R.id.cursor_right
                    contentDescription = context.getString(R.string.select_end)
                    setImageResource(R.drawable.ic_cursor_right)
                    visibility = View.INVISIBLE
                    setOnTouchListener(onCursorTouch)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    )
                }
                val navigationBar = View(context).apply {
                    id = R.id.navigation_bar
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        0,
                        android.view.Gravity.BOTTOM,
                    )
                }

                addView(readView)
                addView(textMenuPosition)
                addView(cursorLeft)
                addView(cursorRight)
                addView(navigationBar)

                onRefsReady(
                    ReadBookViewRefs(
                        root = this,
                        readView = readView,
                        textMenuPosition = textMenuPosition,
                        cursorLeft = cursorLeft,
                        cursorRight = cursorRight,
                        navigationBar = navigationBar,
                    )
                )
            }
        },
    )
}
