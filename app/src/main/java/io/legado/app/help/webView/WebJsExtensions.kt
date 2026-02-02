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
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.AudioPlay
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.ui.rss.read.RssJsExtensions
import io.legado.app.utils.GSON
import io.legado.app.utils.escapeForJs
import io.legado.app.utils.fromJsonObject
import java.lang.ref.WeakReference
import java.util.UUID

@Suppress("unused")
class WebJsExtensions(
    source: BaseSource, activity: AppCompatActivity,
    private val webView: WebView,
    private val bookType: Int = 0,
    callback: Callback? = null
) : RssJsExtensions(activity, source) {
    private val callbackRef: WeakReference<Callback> = WeakReference(callback)

    interface Callback {
        fun upConfig(config: String)
    }

    @JavascriptInterface
    fun upConfig(config: String) {
        callbackRef.get()?.upConfig(config)
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

    /**
     * 由软件主动注入的js函数调用
     */
    @JavascriptInterface
    fun request(funName: String, jsParam: Array<String>, id: String) {
        val activity = activityRef.get() ?: return
        Coroutine.async(activity.lifecycleScope) {
            when (funName) {
                "run" -> AnalyzeRule(book, getSource()).run {
                    setCoroutineContext(coroutineContext)
                    setChapter(chapter)
                    evalJS(jsParam[0]).toString()
                }
                "ajaxAwait" -> {
                    ajax(jsParam[0], jsParam[1].toIntOrNull()).toString()
                }
                "connectAwait" -> {
                    connect(jsParam[0], jsParam[1], jsParam[2].toIntOrNull())
                }
                "getAwait" -> {
                    get(jsParam[0], jsParam[1], jsParam[2].toIntOrNull())
                }
                "headAwait" -> {
                    head(jsParam[0], jsParam[1], jsParam[2].toIntOrNull())
                }
                "postAwait" -> {
                    post(jsParam[0], jsParam[1], jsParam[2], jsParam[3].toIntOrNull())
                }
                "webViewAwait" -> {
                    webView(jsParam[0], jsParam[1], jsParam[2], jsParam[3].toBoolean()).toString()
                }
                "webViewGetSourceAwait" -> {
                    webViewGetSource(
                        jsParam[0],
                        jsParam[1],
                        jsParam[2],
                        jsParam[3],
                        jsParam[4].toBoolean(),
                        jsParam[5].toLongOrNull() ?: 0
                    ).toString()
                }
                "decryptStrAwait" -> {
                    createSymmetricCrypto(jsParam[0], jsParam[1], jsParam[2]).decryptStr(jsParam[3])
                }
                "encryptBase64Await" -> {
                    createSymmetricCrypto(
                        jsParam[0],
                        jsParam[1],
                        jsParam[2]
                    ).encryptBase64(jsParam[3])
                }
                "encryptHexAwait" -> {
                    createSymmetricCrypto(jsParam[0], jsParam[1], jsParam[2]).encryptHex(jsParam[3])
                }
                "createSignHexAwait" -> {
                    createSign(jsParam[0]).setPublicKey(jsParam[1]).setPrivateKey(jsParam[2])
                        .signHex(jsParam[3])
                }
                "downloadFileAwait" -> {
                    downloadFile(jsParam[0])
                }
                "readTxtFileAwait" -> {
                    readTxtFile(jsParam[0])
                }
                "importScriptAwait" -> {
                    importScript(jsParam[0])
                }
                "getStringAwait" -> AnalyzeRule(book, getSource()).run {
                    setCoroutineContext(coroutineContext)
                    setChapter(chapter)
                    getString(jsParam[0], jsParam[1])
                }
                else -> "error funName"
            }
        }.onSuccess { data ->
            webView.evaluateJavascript(
                "window.$JSBridgeResult('$id', '${data.escapeForJs()}', null);",
                null
            )
        }.onError {
            webView.evaluateJavascript(
                "window.$JSBridgeResult('$id', null, '${it.localizedMessage?.escapeForJs()}');",
                null
            )
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
    fun log(msg: String?): String {
        return super.log(msg).toString()
    }
    @JavascriptInterface
    fun ajax(url: String): String? {
        return super.ajax(url, 9000)
    }
    @JavascriptInterface
    fun ajax(url: String, callTimeout: Int?): String? {
        return super.ajax(url, callTimeout?.toLong())
    }
    @JavascriptInterface
    fun connect(urlStr: String?): String {
        if (urlStr.isNullOrEmpty()) return "error empty url"
        return super.connect(urlStr, null, 9000).toString()
    }
    @JavascriptInterface
    fun connect(urlStr: String, header: String): String {
        return super.connect(urlStr, header, 9000).toString()
    }
    @JavascriptInterface
    fun connect(urlStr: String, header: String, callTimeout: Int?): String {
        return super.connect(urlStr, header, callTimeout?.toLong()).toString()
    }
    @JavascriptInterface
    fun get(urlStr: String, headers: String): String {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(headers).getOrNull() ?: emptyMap()
        return super.get(urlStr, headerMap, 9000).body()
    }
    @JavascriptInterface
    fun get(urlStr: String, headers: String, timeout: Int?): String {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(headers).getOrNull() ?: emptyMap()
        return super.get(urlStr, headerMap, timeout).body()
    }
    @JavascriptInterface
    fun post(urlStr: String, body: String, headers: String): String {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(headers).getOrNull() ?: emptyMap()
        return super.post(urlStr, body, headerMap, 9000).body()
    }
    @JavascriptInterface
    fun post(urlStr: String, body: String, headers: String, timeout: Int?): String {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(headers).getOrNull() ?: emptyMap()
        return super.post(urlStr, body, headerMap, timeout).body()
    }
    @JavascriptInterface
    fun head(urlStr: String, headers: String): String {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(headers).getOrNull() ?: emptyMap()
        return GSON.toJson(super.head(urlStr, headerMap, 9000).headers())
    }
    @JavascriptInterface
    fun head(urlStr: String, headers: String, timeout: Int?): String {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(headers).getOrNull() ?: emptyMap()
        return GSON.toJson(super.head(urlStr, headerMap, timeout).headers())
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

        val JS_INJECTION by lazy {
            """
            const requestId = n => 'req_' + n + '_' + Date.now() + '_' + Math.random().toString(36).slice(-3);
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
                    java?.request("run", [String(jsCode)], id);
                });
            };
            function ajaxAwait(url, callTimeout) {
                return new Promise((resolve, reject) => {
                    const id = requestId("ajaxAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("ajaxAwait", [String(url), String(callTimeout)], id);
                });
            };
            function connectAwait(url, header, callTimeout) {
                return new Promise((resolve, reject) => {
                    const id = requestId("connectAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("connectAwait", [String(url), String(header), String(callTimeout)], id);
                });
            };
            function getAwait(url, header, callTimeout) {
                return new Promise((resolve, reject) => {
                    const id = requestId("getAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("getAwait", [String(url), String(header), String(callTimeout)], id);
                });
            };
            function headAwait(url, header, callTimeout) {
                return new Promise((resolve, reject) => {
                    const id = requestId("headAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("headAwait", [String(url), String(header), String(callTimeout)], id);
                });
            };
            function postAwait(url, body, header, callTimeout) {
                return new Promise((resolve, reject) => {
                    const id = requestId("postAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("postAwait", [String(url), String(body), String(header), String(callTimeout)], id);
                });
            };
            function webViewAwait(html, url, js, cacheFirst) {
                return new Promise((resolve, reject) => {
                    const id = requestId("webViewAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("webViewAwait", [String(html), String(url), String(js), String(cacheFirst)], id);
                });
            };
            function webViewGetSourceAwait(html, url, js, sourceRegex, cacheFirst, delayTime) {
                return new Promise((resolve, reject) => {
                    const id = requestId("webViewGetSourceAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("webViewGetSourceAwait", [String(html), String(url), String(js), String(sourceRegex), String(cacheFirst), String(delayTime)], id);
                });
            }
            function decryptStrAwait(transformation, key, iv, data) {
                return new Promise((resolve, reject) => {
                    const id = requestId("decryptStrAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("decryptStrAwait", [String(transformation), String(key), String(iv), String(data)], id);
                });
            };
            function encryptBase64Await(transformation, key, iv, data) {
                return new Promise((resolve, reject) => {
                    const id = requestId("encryptBase64Await");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("encryptBase64Await", [String(transformation), String(key), String(iv), String(data)], id);
                });
            };
            function encryptHexAwait(transformation, key, iv, data) {
                return new Promise((resolve, reject) => {
                    const id = requestId("encryptHexAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("encryptHexAwait", [String(transformation), String(key), String(iv), String(data)], id);
                });
            };
            function createSignHexAwait(algorithm, publicKey, privateKey, data) {
                return new Promise((resolve, reject) => {
                    const id = requestId("createSignHexAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("createSignHexAwait", [String(algorithm), String(publicKey), String(privateKey), String(data)], id);
                });
            };
            function downloadFileAwait(url) {
                return new Promise((resolve, reject) => {
                    const id = requestId("downloadFileAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("downloadFileAwait", [String(url)], id);
                });
            };
            function readTxtFileAwait(path) {
                return new Promise((resolve, reject) => {
                    const id = requestId("readTxtFileAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("readTxtFileAwait", [String(path)], id);
                });
            };
            function importScriptAwait(url) {
                return new Promise((resolve, reject) => {
                    const id = requestId("importScriptAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("importScriptAwait", [String(url)], id);
                });
            };
            function getStringAwait(ruleStr, mContent) {
                return new Promise((resolve, reject) => {
                    const id = requestId("getStringAwait");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("getStringAwait", [String(ruleStr), String(mContent)], id);
                });
            };
            window.$JSBridgeResult = function(requestId, result, error) {
                if (JSBridgeCallbacks[requestId]) {
                    if (error) {
                        JSBridgeCallbacks[requestId].reject(error);
                    } else {
                        JSBridgeCallbacks[requestId].resolve(result);
                    }
                    delete JSBridgeCallbacks[requestId];
                }
            };""".trimIndent()
        }

        val JS_INJECTION2 by lazy {
            """
            const requestId = n => 'req_' + n + '_' + Date.now() + '_' + Math.random().toString(36).slice(-3);
            const JSBridgeCallbacks = {};
            const java = window.$nameJava;
            delete window.$nameJava;
            function run(jsCode) {
                return new Promise((resolve, reject) => {
                    const id = requestId("run");
                    JSBridgeCallbacks[id] = { resolve, reject };
                    java?.request("run", [String(jsCode)], id);
                });
            };
            window.$JSBridgeResult = function(requestId, result, error) {
                if (JSBridgeCallbacks[requestId]) {
                    if (error) {
                        JSBridgeCallbacks[requestId].reject(error);
                    } else {
                        JSBridgeCallbacks[requestId].resolve(result);
                    }
                    delete JSBridgeCallbacks[requestId];
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