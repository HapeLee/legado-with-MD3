package io.legado.app.ui.login

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.HttpTtsRepository
import io.legado.app.data.repository.RssRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.help.http.CookieStore
import io.legado.app.model.AudioPlay
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isAbsUrl
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SourceLoginViewModel(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository,
    private val rssRepository: RssRepository,
    private val httpTtsRepository: HttpTtsRepository,
    private val searchRepository: SearchRepository,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourceLoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SourceLoginEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    internal var source: BaseSource? = null
        private set
    private var sourceType = SourceLoginType.BookSource
    private var book: Book? = null
    private var chapter: BookChapter? = null
    private var rows: List<RowUi> = emptyList()
    private var loginInfo = mutableMapOf<String, String>()
    private var hasChanges = false
    private var submitted = false
    private var jsExtensions: SourceLoginJsExtensions? = null

    fun onIntent(intent: SourceLoginIntent) {
        when (intent) {
            is SourceLoginIntent.Initialize -> initialize(intent)
            is SourceLoginIntent.ValueChanged -> {
                hasChanges = true
                loginInfo[intent.key] = intent.value
                _uiState.update { state ->
                    state.copy(values = (state.values + (intent.key to intent.value)).toImmutableMap())
                }
            }

            is SourceLoginIntent.ValueCommitted -> runRowAction(intent.key, false)
            is SourceLoginIntent.RunAction -> runRowAction(intent.key, intent.longClick)
            is SourceLoginIntent.WebProgressChanged ->
                _uiState.update { it.copy(webProgress = intent.progress) }

            is SourceLoginIntent.WebPageStarted -> saveCookie(intent.url)
            is SourceLoginIntent.WebPageFinished -> {
                saveCookie(intent.url)
                if (_uiState.value.checkingCookie) _effects.tryEmit(SourceLoginEffect.Finish)
            }

            SourceLoginIntent.Confirm -> confirm()
            SourceLoginIntent.ShowLoginHeader -> {
                val header = source?.getLoginHeader().orEmpty()
                _uiState.update { it.copy(activeSheet = SourceLoginSheet.LoginHeader(header)) }
            }

            SourceLoginIntent.DeleteLoginHeader -> source?.removeLoginHeader()
            SourceLoginIntent.ShowLog ->
                _uiState.update { it.copy(activeSheet = SourceLoginSheet.Log) }

            is SourceLoginIntent.CopyLoginHeader ->
                _effects.tryEmit(SourceLoginEffect.CopyText(intent.content))

            SourceLoginIntent.DismissSheet ->
                _uiState.update {
                    it.copy(activeSheet = if (it.mode == SourceLoginMode.Form) SourceLoginSheet.Form else null)
                }

            SourceLoginIntent.Back -> finish(saveDraft = true)
        }
    }

    fun attachJsExtensions(extensions: SourceLoginJsExtensions) {
        jsExtensions = extensions
    }

    fun updateFromJs(data: Map<String, Any?>?) {
        if (data == null) {
            loginInfo = rows.associate { it.name to it.default.orEmpty() }.toMutableMap()
        } else {
            data.forEach { (key, value) -> loginInfo[key] = value?.toString().orEmpty() }
        }
        hasChanges = true
        _uiState.update { it.copy(values = loginInfo.toImmutableMap()) }
    }

    fun rebuildFromJs() {
        viewModelScope.launch { buildForm() }
    }

    private fun initialize(intent: SourceLoginIntent.Initialize) {
        if (source != null || !_uiState.value.loading) return
        sourceType = intent.type
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { loadSource(intent) }
            }.onSuccess { loaded ->
                source = loaded
                if (loaded == null) {
                    _effects.tryEmit(SourceLoginEffect.ShowMessage("未找到书源"))
                    _effects.tryEmit(SourceLoginEffect.Finish)
                    return@onSuccess
                }
                loginInfo = loaded.getLoginInfoMap()
                val headers = withContext(Dispatchers.IO) {
                    runScriptWithContext {
                        loaded.getHeaderMap(
                            downloadCacheSettingsGateway.currentSettings.userAgent,
                            true
                        )
                    }
                }
                val loginUrl = loaded.loginUrl?.let {
                    io.legado.app.utils.NetworkUtils.getAbsoluteURL(loaded.getKey(), it)
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        title = application.getString(R.string.login_source, loaded.getTag()),
                        mode = if (loaded.loginUi.isNullOrBlank()) SourceLoginMode.Web else SourceLoginMode.Form,
                        webUrl = loginUrl,
                        headers = headers.toImmutableMap(),
                        values = loginInfo.toImmutableMap(),
                        activeSheet = if (loaded.loginUi.isNullOrBlank()) null else SourceLoginSheet.Form,
                    )
                }
                if (!loaded.loginUi.isNullOrBlank()) buildForm()
            }.onFailure { error ->
                AppLog.put("登录 UI 初始化失败\n$error", error, true)
                _effects.tryEmit(
                    SourceLoginEffect.ShowMessage(
                        error.localizedMessage ?: "登录初始化失败"
                    )
                )
                _effects.tryEmit(SourceLoginEffect.Finish)
            }
        }
    }

    private suspend fun loadSource(intent: SourceLoginIntent.Initialize): BaseSource? {
        return when (intent.type) {
            SourceLoginType.ReadingBook -> {
                book = ReadBook.book?.also {
                    chapter = bookRepository.getChapter(it.bookUrl, ReadBook.durChapterIndex)
                }
                ReadBook.bookSource
            }

            SourceLoginType.AudioBook -> {
                book = AudioPlay.book
                chapter = AudioPlay.durChapter
                AudioPlay.bookSource
            }

            SourceLoginType.BookSource -> intent.sourceKey?.let {
                bookSourceRepository.getBookSource(
                    it
                )
            }

            SourceLoginType.RssSource -> intent.sourceKey?.let { rssRepository.getByKey(it) }
            SourceLoginType.HttpTts -> intent.sourceKey?.toLongOrNull()
                ?.let { httpTtsRepository.findById(it) }
        }.also {
            if (book == null) {
                book = intent.bookUrl?.let { url ->
                    bookRepository.getBook(url) ?: searchRepository.getSearchBook(url)?.toBook()
                }
            }
        }
    }

    private suspend fun buildForm() {
        val currentSource = source ?: return
        val loginUi = currentSource.loginUi.orEmpty()
        val json = if (loginUi.startsWith("@js:")) {
            evaluate(loginUi.substring(4))
        } else if (loginUi.startsWith("<js>")) {
            evaluate(loginUi.substring(4, loginUi.lastIndexOf("<")))
        } else loginUi
        rows = GSON.fromJsonArray<RowUi>(json).getOrElse {
            AppLog.put("${currentSource.getTag()} loginUi parse error", it)
            emptyList()
        }
        val resolved = rows.map { row ->
            val title = resolveTitle(row)
            val options = row.chars?.filterNotNull().orEmpty()
            if (row.type in setOf(
                    RowUi.Type.select,
                    RowUi.Type.toggle
                ) && loginInfo[row.name].isNullOrEmpty()
            ) {
                loginInfo[row.name] = row.default ?: options.firstOrNull().orEmpty()
                hasChanges = true
            }
            row.toUi(title)
        }
        _uiState.update {
            it.copy(rows = resolved.toImmutableList(), values = loginInfo.toImmutableMap())
        }
    }

    private suspend fun resolveTitle(row: RowUi): String {
        val viewName = row.viewName ?: return row.name
        if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
            return viewName.substring(1, viewName.lastIndex)
        }
        return evaluate(viewName).takeUnless { it.isNullOrEmpty() } ?: row.name
    }

    private fun runRowAction(key: String, longClick: Boolean) {
        val row = rows.firstOrNull { it.name == key } ?: return
        val action = row.action ?: return
        if (action.isAbsUrl()) {
            _effects.tryEmit(SourceLoginEffect.OpenExternalUrl(action))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val loginJs = source?.getLoginJs().orEmpty()
                runScriptWithContext {
                    source?.evalJS("$loginJs\n$action") {
                        put("java", jsExtensions)
                        put("result", loginInfo.toMutableMap())
                        put("book", book)
                        put("chapter", chapter)
                        put("isLongClick", longClick)
                    }
                }
            }.onFailure { AppLog.put("LoginUI Button $key JavaScript error", it) }
        }
    }

    private suspend fun evaluate(script: String): String? = withContext(Dispatchers.IO) {
        val currentSource = source ?: return@withContext null
        runCatching {
            runScriptWithContext {
                currentSource.evalJS("${currentSource.getLoginJs().orEmpty()}\n$script") {
                    put("result", loginInfo.toMutableMap())
                    put("book", book)
                    put("chapter", chapter)
                }.toString()
            }
        }.onFailure {
            AppLog.put("${currentSource.getTag()} loginUi err:${it.localizedMessage}", it)
        }.getOrNull()
    }

    private fun confirm() {
        if (_uiState.value.mode == SourceLoginMode.Web) {
            _uiState.update { it.copy(checkingCookie = true) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val currentSource = source ?: return@launch
            runCatching {
                if (loginInfo.isEmpty()) currentSource.removeLoginInfo()
                else if (currentSource.putLoginInfo(GSON.toJson(loginInfo))) {
                    runScriptWithContext { currentSource.login() }
                }
            }.onSuccess {
                submitted = true
                _effects.tryEmit(SourceLoginEffect.ShowMessage(application.getString(R.string.success)))
                _effects.tryEmit(SourceLoginEffect.Finish)
            }.onFailure {
                AppLog.put("登录出错\n${it.localizedMessage}", it)
                _effects.tryEmit(SourceLoginEffect.ShowMessage("登录出错\n${it.localizedMessage}"))
            }
        }
    }

    private fun finish(saveDraft: Boolean) {
        if (saveDraft && hasChanges && !submitted) {
            source?.let { currentSource ->
                if (loginInfo.isEmpty()) currentSource.removeLoginInfo()
                else currentSource.putLoginInfo(GSON.toJson(loginInfo))
            }
        }
        _effects.tryEmit(SourceLoginEffect.Finish)
    }

    private fun saveCookie(url: String) {
        source?.let {
            CookieStore.setCookie(
                it.getKey(),
                CookieManager.getInstance().getCookie(url)
            )
        }
    }
}

private fun RowUi.toUi(title: String): LoginRowUi {
    val layoutUi = LoginRowLayoutUi(
        flexGrow = style().layout_flexGrow,
        basisPercent = style().layout_flexBasisPercent,
        wrapBefore = style().layout_wrapBefore,
        justify = style().layout_justifySelf,
    )
    return when (type) {
        RowUi.Type.password -> LoginRowUi.Text(name, title, action, layoutUi, password = true)
        RowUi.Type.select -> LoginRowUi.Select(
            name, title, action, layoutUi, chars?.filterNotNull().orEmpty().toImmutableList()
        )

        RowUi.Type.button -> LoginRowUi.Button(name, title, action, layoutUi)
        RowUi.Type.toggle -> LoginRowUi.Toggle(
            name,
            title,
            action,
            layoutUi,
            chars?.filterNotNull().orEmpty().toImmutableList(),
            style().layout_justifySelf != "right",
        )

        else -> LoginRowUi.Text(name, title, action, layoutUi, password = false)
    }
}
