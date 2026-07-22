package io.legado.app.ui.book.read

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.help.IntentHelp
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.sheet.TextSelectMenuConfigSheet
import io.legado.app.ui.book.searchContent.SearchContentResult
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


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

    fun previewBrightness(value: Int)

    fun upSystemUiVisibility(
        isInMultiWindow: Boolean,
        toolBarHide: Boolean,
    )
}

/**
 * Narrow interface for hardware input delegation from Activity.
 * MainActivity holds this instead of the full bridge/controller.
 */
interface ReadBookInputHandler {
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean
    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean
    fun mouseWheelPage(direction: PageDirection)
    fun handleKeyPage(direction: PageDirection, longPress: Boolean = false)
    fun toggleMenu()
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
    onEffectsReady: () -> Unit = {},
    onOpenSearch: (word: String?, bookUrl: String) -> Unit = { _, _ -> },
    onOpenVoiceCasting: (bookUrl: String) -> Unit = {},
    onOpenTtsEnginesAndVoices: () -> Unit = {},
    onOpenTtsCache: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readPreferences by viewModel.readPreferences.collectAsStateWithLifecycle()
    val textMenuState by controller.textMenuState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkTheme = LocalAppUiConfiguration.current.isDarkTheme
    val effectsReady = remember(viewModel) { CompletableDeferred<Unit>() }
    val menuBackdrop = rememberLayerBackdrop()
    val menuHazeState = remember { HazeState() }
    val useMenuHazeSource = state.menuConfig.readMenuTopBarBlurMode == ReadMenuBlurMode.Haze ||
            state.menuConfig.readMenuBottomBarBlurMode == ReadMenuBlurMode.Haze ||
            (
                    !state.menuConfig.readMenuFloatingBottomBar &&
                            state.menuConfig.readMenuBottomBarBlurMode == ReadMenuBlurMode.LiquidGlass
                    )

    DisposableEffect(controller) {
        onDispose {
            controller.clearAppThemeOverride()
        }
    }

    LaunchedEffect(state.menuVisible) {
        controller.onMenuVisibilityChanged(state.menuVisible)
    }

    LaunchedEffect(isDarkTheme, readPreferences.eyeProtectionAutoNight) {
        viewModel.onIntent(ReadBookIntent.SyncEyeProtectionForTheme(isDarkTheme))
    }

    LaunchedEffect(viewModel, controller, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.readAloudProgress.collect { chapterStart ->
                chapterStart?.let(controller::updateReadAloudProgress)
            }
        }
    }

    // ── ActivityResult Launchers ──────────────────────────────────────

    val tocLauncher = rememberLauncherForActivityResult(TocActivityResult()) { result ->
        result?.let { (index, chapterPos, _) ->
            viewModel.onIntent(ReadBookIntent.OpenChapterResult(index, chapterPos))
        }
    }

    val sourceEditLauncher = rememberLauncherForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onIntent(ReadBookIntent.SourceEditResult)
        }
    }

    val replaceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onIntent(ReadBookIntent.ReplaceRuleResult)
        }
    }

    val fontFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            it.takePersistablePermissionSafely(context)
            viewModel.onIntent(ReadBookIntent.FontFolderSelected(it))
        }
    }

    val booksDirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            it.takePersistablePermissionSafely(context)
            viewModel.onIntent(ReadBookIntent.BooksDirSelected(it))
        }
    }

    val readStyleImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ReadStyleImageSelected(it)) }
    }

    var pendingReadStyleImageIsNight by remember { mutableStateOf(false) }
    val readStyleImagePickerForMode = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.onIntent(ReadBookIntent.ReadStyleImageSelectedForMode(it, pendingReadStyleImageIsNight))
        }
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

    var pendingMenuCustomIconId by remember { mutableStateOf<String?>(null) }
    val menuCustomIconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val id = pendingMenuCustomIconId
        pendingMenuCustomIconId = null
        if (id != null && uri != null) {
            viewModel.onIntent(ReadBookIntent.SaveMenuCustomIcon(id, uri))
        }
    }

    var pendingTitleBarCustomIconId by remember { mutableStateOf<String?>(null) }
    val titleBarCustomIconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val id = pendingTitleBarCustomIconId
        pendingTitleBarCustomIconId = null
        if (id != null && uri != null) {
            viewModel.onIntent(ReadBookIntent.SaveTitleBarCustomIcon(id, uri))
        }
    }

    val txtTocRuleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra("tocRegex")?.let { rule ->
                viewModel.onIntent(ReadBookIntent.TocRegexResult(rule))
            }
        }
    }

    val importHttpTtsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ImportHttpTtsFileSelected(it)) }
    }

    val exportHttpTtsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ExportHttpTtsToFile(it)) }
    }

    val importHighlightRulePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.HighlightRuleImportFileSelected(it)) }
    }

    val exportHighlightRulePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ExportHighlightRulesToFile(it)) }
    }

    val bookInfoLauncher = rememberLauncherForActivityResult(
        StartActivityContract(BookInfoActivity::class.java)
    ) { result ->
        viewModel.onIntent(ReadBookIntent.BookInfoResult(result.resultCode == android.app.Activity.RESULT_OK))
    }

    AutoSuggestDayNightObserver(
        viewModel = viewModel,
        autoSuggestDayNight = readPreferences.autoSuggestDayNight,
        lifecycleOwner = lifecycleOwner,
    )

    // ── Effect collection: route handles launcher effects, rest goes to bridge ──

    LaunchedEffect(viewModel) {
        launch {
            viewModel.effects
                .onSubscription {
                    effectsReady.complete(Unit)
                    onEffectsReady()
                }
                .collect { effect ->
                    try {
                        when (effect) {
                            // Launcher-dependent effects — handled directly by route
                            is ReadBookEffect.OpenChapterList -> {
                                tocLauncher.launch(effect.bookUrl)
                            }
                            is ReadBookEffect.OpenSourceEdit -> {
                                sourceEditLauncher.launch { putExtra("sourceUrl", effect.sourceUrl) }
                            }
                            is ReadBookEffect.OpenBookInfo -> {
                                bookInfoLauncher.launch {
                                    putExtra("name", effect.name)
                                    putExtra("author", effect.author)
                                    putExtra("bookUrl", effect.bookUrl)
                                }
                            }
                            is ReadBookEffect.ShowLogin -> {
                                context.startActivity(
                                    Intent(context, SourceLoginActivity::class.java).apply {
                                        putExtra("bookType", BookType.text)
                                    }
                                )
                            }
                            is ReadBookEffect.OpenHttpTtsLogin -> {
                                context.startActivity(
                                    Intent(context, SourceLoginActivity::class.java).apply {
                                        putExtra("type", "httpTts")
                                        putExtra("key", effect.engineId.toString())
                                    }
                                )
                            }
                            is ReadBookEffect.OpenWebView -> {
                                context.startActivity(
                                    Intent(context, WebViewActivity::class.java).apply {
                                        putExtra("title", effect.title)
                                        putExtra("url", effect.url)
                                        putExtra("sourceOrigin", effect.sourceOrigin)
                                        putExtra("sourceName", effect.sourceName)
                                        effect.sourceType?.let { putExtra("sourceType", it) }
                                        effect.html?.let { putExtra("html", it) }
                                    }
                                )
                            }
                            is ReadBookEffect.RunSourceCustomButton -> {
                                (context as? AppCompatActivity)?.let { activity ->
                                    SourceCallBack.callBackBtn(
                                        activity,
                                        effect.event,
                                        effect.source,
                                        effect.book,
                                        effect.chapter,
                                        BookType.text,
                                    )
                                }
                            }
                            is ReadBookEffect.OpenSearchActivity -> {
                                onOpenSearch(effect.word, effect.bookUrl)
                            }
                            is ReadBookEffect.OpenBookVoiceCasting -> {
                                onOpenVoiceCasting(effect.bookUrl)
                            }
                            ReadBookEffect.OpenTtsEnginesAndVoices -> onOpenTtsEnginesAndVoices()
                            ReadBookEffect.OpenTtsCache -> onOpenTtsCache()
                            is ReadBookEffect.MenuSettingReplace -> {
                                replaceLauncher.launch(Intent(context, ReplaceRuleActivity::class.java))
                            }
                            is ReadBookEffect.TextActionReplace -> {
                                val scopes = arrayListOf<String>()
                                effect.bookName?.let { scopes.add(it) }
                                effect.bookSourceUrl?.let { scopes.add(it) }
                                val text = effect.text.lineSequence().map { it.trim() }.joinToString("\n")
                                val editRoute = ReplaceEditRoute(
                                    id = -1, pattern = text,
                                    scope = scopes.joinToString(";"),
                                    isScopeTitle = false, isScopeContent = true,
                                )
                                replaceLauncher.launch(ReplaceRuleActivity.startIntent(context, editRoute))
                            }
                            is ReadBookEffect.OpenReplaceEditor -> {
                                val editRoute = ReplaceEditRoute(id = effect.id, pattern = effect.pattern)
                                replaceLauncher.launch(ReplaceRuleActivity.startIntent(context, editRoute))
                            }
                            is ReadBookEffect.MenuTocRegex -> {
                                val intent = Intent(
                                    context,
                                    io.legado.app.ui.book.toc.rule.preview.TxtTocRulePreviewActivity::class.java
                                )
                                intent.putExtra("bookUrl", effect.bookUrl)
                                intent.putExtra("tocRegex", effect.tocRegex)
                                txtTocRuleLauncher.launch(intent)
                            }
                            is ReadBookEffect.OpenFontFolderPicker -> {
                                fontFolderPicker.launch(null)
                            }
                            is ReadBookEffect.OpenBooksDirPicker -> {
                                booksDirPicker.launch(null)
                            }
                            is ReadBookEffect.OpenReadStyleImagePicker -> {
                                readStyleImagePicker.launch("image/*")
                            }
                            is ReadBookEffect.OpenReadStyleImagePickerForMode -> {
                                pendingReadStyleImageIsNight = effect.isNight
                                readStyleImagePickerForMode.launch("image/*")
                            }
                            is ReadBookEffect.OpenReadStyleImport -> {
                                readStyleImportPicker.launch(
                                    arrayOf("application/zip", "application/octet-stream", "*/*")
                                )
                            }
                            is ReadBookEffect.OpenReadStyleExport -> {
                                readStyleExportPicker.launch(effect.fileName)
                            }
                            is ReadBookEffect.OpenMenuCustomIconPicker -> {
                                pendingMenuCustomIconId = effect.id
                                menuCustomIconPicker.launch("image/*")
                            }
                            is ReadBookEffect.OpenTitleBarCustomIconPicker -> {
                                pendingTitleBarCustomIconId = effect.id
                                titleBarCustomIconPicker.launch("image/*")
                            }
                            is ReadBookEffect.OpenSystemTtsSettings -> {
                                IntentHelp.openTTSSetting()
                            }
                            is ReadBookEffect.TtsCacheCleared -> {
                                context.toastOnUi(effect.message)
                            }
                            is ReadBookEffect.OpenHttpTtsImportPicker -> {
                                importHttpTtsPicker.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/plain"
                                    )
                                )
                            }

                            is ReadBookEffect.OpenHttpTtsExportPicker -> {
                                exportHttpTtsPicker.launch("httpTTS.json")
                            }

                            is ReadBookEffect.OpenHighlightRuleImportPicker -> {
                                importHighlightRulePicker.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/plain"
                                    )
                                )
                            }

                            is ReadBookEffect.OpenHighlightRuleExportPicker -> {
                                exportHighlightRulePicker.launch("highlightRule.json")
                            }

                            // All other effects — delegate to bridge (View/Window/Activity operations)
                            else -> controller.handleEffect(effect)
                        }
                    } catch (e: Exception) {
                        AppLog.put("ReadBook effect处理异常: ${effect::class.simpleName}", e)
                    }
                }
        }
    }

    // ── System UI sync ────────────────────────────────────────────────

    LaunchedEffect(state.menuVisible) {
        host.upSystemUiVisibility(host.isInMultiWindowModeCompat, !state.menuVisible)
    }

    // ── Search result collection (from Navigation3 search route) ──────

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            SearchContentResult.results.collect { result ->
                effectsReady.await()
                if (result.bookUrl != ReadBook.book?.bookUrl) {
                    SearchContentResult.resetReplayCache()
                    return@collect
                }
                when (result) {
                    is SearchContentResult.Clear -> {
                        viewModel.onIntent(ReadBookIntent.SetSearchResults(emptyList(), 0, ""))
                        viewModel.onIntent(ReadBookIntent.ExitSearch)
                    }

                    is SearchContentResult.Result -> {
                        viewModel.onIntent(
                            ReadBookIntent.SetSearchResults(
                                result.searchResults,
                                result.index,
                                result.query
                            )
                        )
                        result.searchResults.getOrNull(result.index)?.let { searchResult ->
                            viewModel.onIntent(
                                ReadBookIntent.NavigateToSearchResult(searchResult, result.index)
                            )
                        }
                    }
                }
                SearchContentResult.resetReplayCache()
            }
        }
    }

    // ── View layer + Compose UI ───────────────────────────────────────

    var showSelectMenuConfigSheet by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
    ) {
        key(controller) {
            ReadBookViewLayer(
                modifier = Modifier
                    .then(if (useMenuHazeSource) Modifier.hazeSource(menuHazeState) else Modifier)
                    .layerBackdrop(menuBackdrop),
                onRefsReady = { controller.onRefsReady(it) },
                onCursorTouch = controller,
                readViewCallBack = controller,
                contentTextViewCallBack = controller,
                isDarkTheme = isDarkTheme,
                onThemeChanged = controller::onAppThemeChanged,
            )
        }
        ReadBookColorTheme(
            styleConfig = state.styleConfig,
            preferences = readPreferences,
            isDarkTheme = isDarkTheme,
        ) {
            ReadBookMenuBar(
                state = state,
                preferences = readPreferences,
                onIntent = viewModel::onIntent,
                onBrightnessPreview = host::previewBrightness,
                backdrop = menuBackdrop,
                hazeState = if (useMenuHazeSource) menuHazeState else null,
            )
            ReadBookSearchBar(state = state, onIntent = viewModel::onIntent)
            AnimatedVisibility(
                visible = state.isReadAloudRunning &&
                    state.showReadAloudCapsule &&
                        !state.menuVisible,
                enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.88f),
                exit = fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.88f),
            ) {
                ReadAloudCapsule(
                    book = state.book,
                    isPaused = state.isReadAloudPaused,
                    offsetXDp = state.readAloudCapsuleOffsetX,
                    offsetYDp = state.readAloudCapsuleOffsetY,
                    progress = state.readAloudChapterPosition.toFloat() /
                        state.readAloudChapterLength.coerceAtLeast(1),
                    autoCollapse = state.capsuleAutoCollapse,
                    onPositionChanged = { x, y ->
                        viewModel.onIntent(ReadBookIntent.SetReadAloudCapsulePosition(x, y))
                    },
                    onTogglePause = {
                        viewModel.onIntent(ReadBookIntent.ReadAloudTogglePause)
                    },
                    onStop = { viewModel.onIntent(ReadBookIntent.ReadAloudStop) },
                    onOpenPlayer = { viewModel.onIntent(ReadBookIntent.OpenReadAloudPlayer) },
                )
            }
            ReadBookScreen(
                state = state,
                preferences = readPreferences,
                onIntent = viewModel::onIntent,
                onBack = { controller.closeReadBook() },
                onOpenTextSelectMenuConfig = {
                    viewModel.onIntent(ReadBookIntent.DismissSheet)
                    showSelectMenuConfigSheet = true
                },
            )
            TextActionSelectionMenu(
                menuState = textMenuState,
                expandTextMenu = readPreferences.expandTextMenu,
                onDismiss = { controller.dismissTextActionMenu() },
                onItemClick = { item -> controller.onTextMenuItemClick(item) },
                onOpenManage = {
                    controller.dismissTextActionMenu()
                    controller.refs?.readView?.cancelSelect()
                    showSelectMenuConfigSheet = true
                }
            )
            var configItems by remember { mutableStateOf<List<ActionMenuItem>>(emptyList()) }
            LaunchedEffect(showSelectMenuConfigSheet) {
                if (showSelectMenuConfigSheet) {
                    configItems = controller.getActionMenuItems()
                } else {
                    configItems = emptyList()
                }
            }
            TextSelectMenuConfigSheet(
                show = showSelectMenuConfigSheet,
                items = configItems,
                expandTextMenu = readPreferences.expandTextMenu,
                showSelectMenuIcon = readPreferences.showSelectMenuIcon,
                onExpandTextMenuChange = {
                    viewModel.onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ExpandTextMenu(it)))
                },
                onShowSelectMenuIconChange = {
                    viewModel.onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowSelectMenuIcon(it)))
                    controller.refreshActionMenuItems()
                },
                onDismissRequest = { showSelectMenuConfigSheet = false },
                onSaved = { items -> controller.saveMenuConfig(items) }
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
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            onThemeChanged(isDarkTheme)
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
        update = {
            onThemeChanged(isDarkTheme)
        },
    )
}

@Composable
private fun AutoSuggestDayNightObserver(
    viewModel: ReadBookViewModel,
    autoSuggestDayNight: Boolean,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val context = LocalContext.current
    LaunchedEffect(autoSuggestDayNight) {
        if (!autoSuggestDayNight) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
            if (sensorManager != null && lightSensor != null) {
                while (isActive) {
                    if (!viewModel.isDayNightSwitchCoolingDown()) {
                        val finalLux = AtomicReference<Float?>(null)
                        val listener = object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent?) {
                                event?.values?.firstOrNull()?.let { lux ->
                                    finalLux.set(lux)
                                }
                            }

                            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                        }
                        try {
                            sensorManager.registerListener(
                                listener,
                                lightSensor,
                                SensorManager.SENSOR_DELAY_NORMAL
                            )
                            delay(1.seconds)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLog.put("lightSensor收集异常", e)
                        } finally {
                            sensorManager.unregisterListener(listener)
                        }

                        finalLux.get()?.let { lux ->
                            viewModel.onIntent(ReadBookIntent.CheckSwitchDayNight(lux))
                        }

                    }

                    delay(15.minutes)
                }
            }
        }
    }
}
