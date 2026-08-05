package io.legado.app.help.webView

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.CacheManager
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.AudioPlay
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.ui.rss.read.RssJsExtensions
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJsonObject
import java.lang.ref.WeakReference
import java.util.UUID

@Suppress("unused")
class WebJsExtensions(
    source: BaseSource,
    activity: AppCompatActivity?,
    webView: WebView,
    bookType: Int = 0,
    callback: Callback? = null
) : RssJsExtensions(activity, source) {
    private val callbackRef: WeakReference<Callback> = WeakReference(callback)
    private val webViewRef: WeakReference<WebView?> = WeakReference(webView)

    interface Callback {
        fun upConfig(config: String)
        fun onNavigateToArticles(sortUrl: String? = null)
    }

    @JavascriptInterface
    fun upConfig(config: String) {
        callbackRef.get()?.upConfig(config)
    }

    @JavascriptInterface
    fun navigateToArticles() {
        callbackRef.get()?.onNavigateToArticles(null)
    }

    @JavascriptInterface
    fun navigateToArticles(sortUrl: String?) {
        callbackRef.get()?.onNavigateToArticles(sortUrl)
    }

    private val bookAndChapter by lazy {
        var book: Book? = null
        var chapter: BookChapter? = null
        when (bookType) {
            BookType.text -> {
                book = ReadBook.book?.also {
                    chapter = appDb.bookChapterDao.getChapter(
                        it.bookUrl,
                        ReadBook.durChapterIndex
                    )
                }
            }

            BookType.audio -> {
                book = AudioPlay.book
                chapter = AudioPlay.durChapter
            }
        }
        Pair(book, chapter)
    }
    private val book: Book? get() = bookAndChapter.first
    private val chapter: BookChapter? get() = bookAndChapter.second

    val analyzeRule by lazy {
        AnalyzeRule(book, source = getSource()).setChapter(chapter)
    }

    @JavascriptInterface
    fun request(funName: String, jsParam: Array<String?>, id: String) {
        val activity = activityRef.get() ?: return
        Coroutine.async(activity.lifecycleScope) {
            val params = Array(6) { i -> jsParam.getOrNull(i) }
            val p0 = params[0]
            val p1 = params[1]
            val p2 = params[2]
            val p3 = params[3]
            val p4 = params[4]
            val p5 = params[5]
            when (funName) {
                "run" -> {
                    analyzeRule.setCoroutineContext(coroutineContext)
                        .evalJS(
                            p0 ?: throw NoStackTraceException("error null")
                        ).toString()
                }
                "ajaxAwait" -> {
                    ajax(
                        p0 ?: throw NoStackTraceException("error url null"),
                        p1?.toLongOrNull()
                    ).toString()
                }
                "connectAwait" -> {
                    connect(
                        p0 ?: throw NoStackTraceException("error url null"),
                        p1,
                        p2?.toIntOrNull()
                    )
                }
                "getAwait" -> {
                    get(
                        p0 ?: throw NoStackTraceException("error url null"),
                        p1 ?: throw NoStackTraceException("error header null"),
                        p2?.toIntOrNull()
                    )
                }
                "headAwait" -> {
                    head(
                        p0 ?: throw NoStackTraceException("error url null"),
                        p1 ?: throw NoStackTraceException("error header null"),
                        p2?.toIntOrNull()
                    )
                }
                "postAwait" -> {
                    post(
                        p0 ?: throw NoStackTraceException("error url null"),
                        p1 ?: throw NoStackTraceException("error body null"),
                        p2 ?: throw NoStackTraceException("error header null"),
                        p3?.toIntOrNull()
                    )
                }
                "webViewAwait" -> {
                    webView(
                        p0,
                        p1,
                        p2,
                        p3.toBoolean()
                    ).toString()
                }
                "webViewGetSourceAwait" -> {
                    webViewGetSource(
                        p0,
                        p1,
                        p2,
                        p3 ?: throw NoStackTraceException("error sourceRegex null"),
                        p4.toBoolean(),
                        p5?.toLongOrNull() ?: 0
                    ).toString()
                }
                "decryptStrAwait" -> {
                    createSymmetricCrypto(
                        p0 ?: throw NoStackTraceException("error transformation null"),
                        p1 ?: throw NoStackTraceException("error key null"),
                        p2
                    ).decryptStr(p3 ?: throw NoStackTraceException("error data null"))
                }
                "encryptBase64Await" -> {
                    createSymmetricCrypto(
                        p0 ?: throw NoStackTraceException("error transformation null"),
                        p1 ?: throw NoStackTraceException("error key null"),
                        p2
                    ).encryptBase64(p3 ?: throw NoStackTraceException("error data null"))
                }
                "encryptHexAwait" -> {
                    createSymmetricCrypto(
                        p0 ?: throw NoStackTraceException("error transformation null"),
                        p1 ?: throw NoStackTraceException("error key null"),
                        p2
                    ).encryptHex(p3 ?: throw NoStackTraceException("error data null"))
                }
                "createSignHexAwait" -> {
                    createSign(p0 ?: throw NoStackTraceException("error algorithm null"))
                        .setPublicKey(p1 ?: throw NoStackTraceException("error publicKey null"))
                        .setPrivateKey(p2 ?: throw NoStackTraceException("error privateKey null"))
                        .signHex(p3 ?: throw NoStackTraceException("error data null"))
                }
                "downloadFileAwait" -> {
                    downloadFile(p0 ?: throw NoStackTraceException("error url null"))
                }
                "readTxtFileAwait" -> {
                    readTxtFile(p0 ?: throw NoStackTraceException("error path null"))
                }
                "importScriptAwait" -> {
                    importScript(p0 ?: throw NoStackTraceException("error path null"))
                }
                "getStringAwait" -> {
                    analyzeRule.setCoroutineContext(coroutineContext)
                        .getString(p0, p1)
                }
                else -> throw NoStackTraceException("error funName")
            }
        }.onSuccess { data ->
            CacheManager.putMemory(id, data)
            webViewRef.get()?.evaluateJavascript("window.$JSBridgeResult('$id', true);", null)
        }.onError {
            CacheManager.putMemory(id, it.localizedMessage ?: "err")
            webViewRef.get()?.evaluateJavascript("window.$JSBridgeResult('$id', false);", null)
        }
    }

    @JavascriptInterface
    fun toast(msg: String?) {
        super.toast(msg)
    }

    @JavascriptInterface
    fun longToast(msg: String?) {
        super.longToast(msg)
    }

    @JavascriptInterface
    override fun open(name: String, url: String?, title: String?, origin: String?) {
        when (name) {
            "sort" -> {
                val sortUrl = if (!url.isNullOrBlank()) {
                    if (url.isJsonObject()) url else title?.let { "{\"$it\":\"$url\"}" } ?: url
                } else null
                callbackRef.get()?.onNavigateToArticles(sortUrl)
            }
            else -> super.open(name, url, title, origin)
        }
    }

    @JavascriptInterface
    fun log(msg: String?): String {
        return super.log(msg).toString()
    }

    @JavascriptInterface
    @JvmOverloads
    fun ajax(url: String, callTimeout: Int = 9000): String? {
        return super.ajax(url, callTimeout.toLong())
    }

    @JavascriptInterface
    @JvmOverloads
    fun connect(urlStr: String, header: String? = null, callTimeout: Int? = 9000): String {
        return super.connect(urlStr, header, callTimeout?.toLong()).toString()
    }

    @JavascriptInterface
    @JvmOverloads
    fun get(urlStr: String, header: String, timeout: Int? = 9000): String {
        val headers = GSON.fromJsonObject<Map<String, String>>(header).getOrNull() ?: emptyMap()
        return super.get(urlStr, headers, timeout).body()
    }

    @JavascriptInterface
    @JvmOverloads
    fun post(urlStr: String, body: String, header: String, timeout: Int? = 9000): String {
        val headers = GSON.fromJsonObject<Map<String, String>>(header).getOrNull() ?: emptyMap()
        return super.post(urlStr, body, headers, timeout).body()
    }

    @JavascriptInterface
    @JvmOverloads
    fun head(urlStr: String, header: String, timeout: Int? = 9000): String {
        val headers = GSON.fromJsonObject<Map<String, String>>(header).getOrNull() ?: emptyMap()
        return GSON.toJson(super.head(urlStr, headers, timeout).headers())
    }

    @JavascriptInterface
    fun getStringList(rule: String?, mContent: String? = null, isUrl: Boolean = false): List<String>? {
        return super.getStringList(rule, mContent, isUrl)
    }

    @JavascriptInterface
    fun getString(ruleStr: String?, mContent: String? = null, isUrl: Boolean = false): String {
        return super.getString(ruleStr, mContent, isUrl)
    }

    companion object {
        private fun getRandomLetter(): Char {
            val letters = "abcdefghijklmnopqrstuvwxyz"
            return letters.random()
        }

        val uuid by lazy {
            UUID.randomUUID().toString().replace('-', getRandomLetter()).chunked(6)
        }
        val uuid2 by lazy {
            UUID.randomUUID().toString().replace('-', getRandomLetter()).chunked(6)
        }
        val nameUrl by lazy { "https://" + uuid[0] + ".com/" + uuid2[0] + ".js" }
        val nameJava by lazy { getRandomLetter() + uuid[1] + uuid2[1] }
        val nameCache by lazy { getRandomLetter() + uuid[2] + uuid2[2] }
        val nameSource by lazy { getRandomLetter() + uuid[3] + uuid2[3] }
        val nameBasic by lazy { getRandomLetter() + uuid[4] + uuid2[4] }
        val JSBridgeResult by lazy { getRandomLetter() + uuid[5] + uuid2[5] }
        val JS_URL by lazy {
            "<script src=\"$nameUrl\"></script>"
        }

        val getInjectionString = "try{var cache=$nameCache,source=$nameSource,java=$nameJava;}catch(e){}"

        val JS_INJECTION by lazy {
            """
            const requestId = n => 'req_' + n + '_' + Date.now() + '_' + Math.random().toString(36).slice(-3);
            const params = a => a.map(p => p != null && typeof p.toString === 'function' ? p.toString() : null);
            const JSBridgeCallbacks = {};
            const java = window.$nameJava;
            delete window.$nameJava;
            const source = window.$nameSource;
            delete window.$nameSource;
            const cache = window.$nameCache;
            delete window.$nameCache;
            function run(jsCode) {
                return new Promise((resolve, reject) => {
                    const id = requestId("run");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("run", [String(jsCode)], id);
                });
            };
            function ajaxAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("ajaxAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("ajaxAwait", params(args), id);
                });
            };
            function connectAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("connectAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("connectAwait", params(args), id);
                });
            };
            function getAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("getAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("getAwait", params(args), id);
                });
            };
            function headAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("headAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("headAwait", params(args), id);
                });
            };
            function postAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("postAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("postAwait", params(args), id);
                });
            };
            function webViewAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("webViewAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("webViewAwait", params(args), id);
                });
            };
            function webViewGetSourceAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("webViewGetSourceAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("webViewGetSourceAwait", params(args), id);
                });
            }
            function decryptStrAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("decryptStrAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("decryptStrAwait", params(args), id);
                });
            };
            function encryptBase64Await(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("encryptBase64Await");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("encryptBase64Await", params(args), id);
                });
            };
            function encryptHexAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("encryptHexAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("encryptHexAwait", params(args), id);
                });
            };
            function createSignHexAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("createSignHexAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("createSignHexAwait", params(args), id);
                });
            };
            function downloadFileAwait(url) {
                return new Promise((resolve, reject) => {
                    const id = requestId("downloadFileAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("downloadFileAwait", [String(url)], id);
                });
            };
            function readTxtFileAwait(path) {
                return new Promise((resolve, reject) => {
                    const id = requestId("readTxtFileAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("readTxtFileAwait", [String(path)], id);
                });
            };
            function importScriptAwait(url) {
                return new Promise((resolve, reject) => {
                    const id = requestId("importScriptAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("importScriptAwait", [String(url)], id);
                });
            };
            function getStringAwait(...args) {
                return new Promise((resolve, reject) => {
                    const id = requestId("getStringAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("getStringAwait", params(args), id);
                });
            };
            window.$JSBridgeResult = function(id, success) {
                const callBack = JSBridgeCallbacks[id];
                if (callBack) {
                    const result = cache.getFromMemory(id);
                    if (success) {
                        callBack.resolve(result);
                    } else {
                        callBack.reject(result);
                    }
                    delete JSBridgeCallbacks[id];
                }
            };""".trimIndent()
        }

        val JS_INJECTION2 by lazy {
            """
            const requestId = n => 'req_' + n + '_' + Date.now() + '_' + Math.random().toString(36).slice(-3);
            const JSBridgeCallbacks = {};
            const java = window.$nameJava;
            delete window.$nameJava;
            const cache = window.$nameCache;
            delete window.$nameCache;
            function run(jsCode) {
                return new Promise((resolve, reject) => {
                    const id = requestId("run");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java.request("run", [String(jsCode)], id);
                });
            };
            window.$JSBridgeResult = function(id, success) {
                const callBack = JSBridgeCallbacks[id];
                if (callBack) {
                    const result = cache.getFromMemory(id);
                    if (success) {
                        callBack.resolve(result);
                    } else {
                        callBack.reject(result);
                    }
                    delete JSBridgeCallbacks[id];
                }
            };""".trimIndent()
        }

        val basicJs by lazy {
            """
            (function() {
            if (screen.orientation) {
                screen.orientation.lock = function(orientation) {
                    return new Promise((resolve, reject) => {
                        window.$nameBasic?.lockOrientation(orientation);
                        resolve()
                    });
                };
                screen.orientation.unlock = function() {
                    return new Promise((resolve, reject) => {
                        window.$nameBasic?.lockOrientation('unlock');
                        resolve()
                    });
                };
            };
            window.close = function() {
                window.$nameBasic?.onCloseRequested();
            };
            })();""".trimIndent()
        }
    }
}
