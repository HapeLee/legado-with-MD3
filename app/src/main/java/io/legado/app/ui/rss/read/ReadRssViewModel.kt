package io.legado.app.ui.rss.read

import android.app.Application
import android.content.Intent
import android.util.Base64
import android.webkit.URLUtil
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.TTS
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.rss.Rss
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext

data class ReadRssArgs(
    val title: String? = null,
    val origin: String,
    val link: String? = null,
    val openUrl: String? = null
)

class ReadRssViewModel(application: Application) : BaseViewModel(application) {
    var rssSource: RssSource? = null
    var rssArticle: RssArticle? = null
    var tts: TTS? = null
    var headerMap: Map<String, String> = emptyMap()

    private val _contentState = MutableStateFlow<String?>(null)
    val contentState: StateFlow<String?> = _contentState.asStateFlow()

    private val _urlState = MutableStateFlow<AnalyzeUrl?>(null)
    val urlState: StateFlow<AnalyzeUrl?> = _urlState.asStateFlow()

    private val _isSpeakingState = MutableStateFlow(false)
    val isSpeakingState: StateFlow<Boolean> = _isSpeakingState.asStateFlow()

    private val _rssStarState = MutableStateFlow<RssStar?>(null)
    val rssStarState: StateFlow<RssStar?> = _rssStarState.asStateFlow()

    fun initData(intent: Intent) {
        val origin = intent.getStringExtra("origin") ?: return
        initData(
            ReadRssArgs(
                title = intent.getStringExtra("title"),
                origin = origin,
                link = intent.getStringExtra("link"),
                openUrl = intent.getStringExtra("openUrl")
            )
        )
    }

    fun initData(args: ReadRssArgs) {
        execute {
            rssSource = appDb.rssSourceDao.getByKey(args.origin)
            headerMap = runScriptWithContext {
                rssSource?.getHeaderMap() ?: emptyMap()
            }

            val link = args.link
            if (!link.isNullOrBlank()) {
                _rssStarState.value = appDb.rssStarDao.get(args.origin, link)
                rssArticle = _rssStarState.value?.toRssArticle() ?: appDb.rssArticleDao.getByLink(args.origin, link)
                val article = rssArticle ?: return@execute
                if (!article.description.isNullOrBlank()) {
                    _contentState.value = article.description!!
                } else {
                    rssSource?.let {
                        val ruleContent = it.ruleContent
                        if (!ruleContent.isNullOrBlank()) {
                            loadContent(article, ruleContent)
                        } else {
                            loadUrl(article.link, article.origin)
                        }
                    } ?: loadUrl(article.link, article.origin)
                }
                return@execute
            }

            val openUrl = args.openUrl
            if (!openUrl.isNullOrBlank()) {
                loadUrl(openUrl, args.origin)
                return@execute
            }

            val ruleContent = rssSource?.ruleContent
            if (ruleContent.isNullOrBlank()) {
                loadUrl(args.origin, args.origin)
            } else {
                val article = RssArticle().apply {
                    origin = args.origin
                    this.link = args.origin
                    title = rssSource!!.sourceName
                }
                rssArticle = article
                loadContent(article, ruleContent)
            }
        }
    }

    private suspend fun loadUrl(url: String, baseUrl: String) {
        val analyzeUrl = AnalyzeUrl(
            mUrl = url,
            baseUrl = baseUrl,
            source = rssSource,
            coroutineContext = coroutineContext,
            hasLoginHeader = false
        )
        _urlState.value = analyzeUrl
    }

    private fun loadContent(rssArticle: RssArticle, ruleContent: String) {
        val source = rssSource ?: return
        Rss.getContent(viewModelScope, rssArticle, ruleContent, source)
            .onSuccess(IO) { body ->
                rssArticle.description = body
                appDb.rssArticleDao.insert(rssArticle)
                _rssStarState.value?.let {
                    it.description = body
                    appDb.rssStarDao.insert(it)
                }
                _contentState.value = body
            }.onError {
                _contentState.value = "加载正文失败\n${it.stackTraceToString()}"
            }
    }

    fun refresh(finish: () -> Unit) {
        rssArticle?.let { article ->
            rssSource?.let {
                val ruleContent = it.ruleContent
                if (!ruleContent.isNullOrBlank()) {
                    loadContent(article, ruleContent)
                } else {
                    finish.invoke()
                }
            } ?: let {
                appCtx.toastOnUi("订阅源不存在")
                finish.invoke()
            }
        } ?: finish.invoke()
    }

    fun addFavorite() {
        execute {
            _rssStarState.value ?: rssArticle?.toStar()?.let {
                appDb.rssStarDao.insert(it)
                _rssStarState.value = it
            }
        }
    }

    fun updateFavorite(title: String?, group: String?) {
        rssArticle?.let { article ->
            if (!title.isNullOrBlank()) {
                article.title = title
            }
            group?.let {
                article.group = it
            }
        }
        execute {
            rssArticle?.toStar()?.let {
                appDb.rssStarDao.update(it)
                _rssStarState.value = it
            }
        }
    }

    fun delFavorite() {
        execute {
            _rssStarState.value?.let {
                appDb.rssStarDao.delete(it.origin, it.link)
                _rssStarState.value = null
            }
        }
    }

    fun saveImage(webPic: String?) {
        webPic ?: return
        execute {
            val byteArray = webData2bitmap(webPic) ?: throw NoStackTraceException("NULL")
            val success = ImageSaveUtils.saveImageToGallery(
                context,
                byteArray,
                folderName = "Legado"
            )
            if (!success) throw NoStackTraceException("保存到相册失败")
        }.onError {
            context.toastOnUi("保存图片失败: ${it.localizedMessage}")
        }.onSuccess {
            context.toastOnUi("已保存到相册")
        }
    }

    private suspend fun webData2bitmap(data: String): ByteArray? {
        return if (URLUtil.isValidUrl(data)) {
            okHttpClient.newCallResponseBody {
                url(data)
            }.bytes()
        } else {
            Base64.decode(data.split(",").toTypedArray()[1], Base64.DEFAULT)
        }
    }

    fun clHtml(content: String): String {
        return when {
            !rssSource?.style.isNullOrEmpty() -> {
                """
                    <style>
                        ${rssSource?.style}
                    </style>
                    $content
                """.trimIndent()
            }

            content.contains("<style>".toRegex()) -> {
                content
            }

            else -> {
                """
                    <style>
                        img{max-width:100% !important; width:auto; height:auto;}
                        video{object-fit:fill; max-width:100% !important; width:auto; height:auto;}
                        body{word-wrap:break-word; height:auto;max-width: 100%; width:auto;}
                    </style>
                    $content
                """.trimIndent()
            }
        }
    }

    @Synchronized
    fun readAloud(text: String) {
        if (tts == null) {
            tts = TTS().apply {
                setSpeakStateListener(object : TTS.SpeakStateListener {
                    override fun onStart() {
                        _isSpeakingState.value = true
                    }

                    override fun onDone() {
                        _isSpeakingState.value = false
                    }
                })
            }
        }
        tts?.speak(text)
    }

    fun stopReadAloud() {
        tts?.stop()
        _isSpeakingState.value = false
    }

    fun updateRssSourceRedirectPolicy(sourceUrl: String, redirectPolicy: String) {
        execute {
            appDb.rssSourceDao.updateRedirectPolicy(sourceUrl, redirectPolicy)
            rssSource?.redirectPolicy = redirectPolicy
        }.onError {
            appCtx.toastOnUi("保存失败: ${it.localizedMessage}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.clearTts()
    }
}
