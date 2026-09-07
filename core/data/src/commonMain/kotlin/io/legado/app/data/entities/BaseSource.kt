package io.legado.app.data.entities

import io.legado.app.core.platform.CookieStoreProvider
import io.legado.app.core.platform.JsBindings
import io.legado.app.core.platform.JsEngine
import io.legado.app.core.platform.JsonCodec
import io.legado.app.core.platform.KeyValueStoreProvider
import io.legado.app.core.platform.LoggerProvider
import io.legado.app.core.platform.SourceRuntimeProvider
import io.legado.app.core.platform.SymmetricCryptoProvider
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.utils.has
import kotlinx.coroutines.runBlocking

/**
 * 可在js里调用,source.xxx()
 */
@Suppress("unused")
interface BaseSource {
    /**
     * 并发率
     */
    var concurrentRate: String?

    /**
     * 登录地址
     */
    var loginUrl: String?

    /**
     * 登录UI
     */
    var loginUi: String?

    /**
     * 请求头
     */
    var header: String?

    /**
     * 启用cookieJar
     */
    var enabledCookieJar: Boolean?

    /**
     * js库
     */
    var jsLib: String?

    fun getTag(): String

    fun getKey(): String

    fun getSource(): BaseSource? {
        return this
    }

    fun getLoginJs(): String? {
        val loginJs = loginUrl
        return when {
            loginJs == null -> null
            loginJs.startsWith("@js:") -> loginJs.substring(4)
            loginJs.startsWith("<js>") -> loginJs.substring(4, loginJs.lastIndexOf("<"))
            else -> loginJs
        }
    }

    /**
     * 调用login函数 实现登录请求
     */
    fun login() {
        val loginJs = getLoginJs()
        if (!loginJs.isNullOrBlank()) {
            val js = """$loginJs
                if(typeof login=='function'){
                    login.apply(this);
                } else {
                    throw('Function login not implements!!!')
                }
            """.trimIndent()
            evalJS(js)
        }
    }

    /**
     * 解析header规则
     */
    fun getHeaderMap(userAgent: String, hasLoginHeader: Boolean = false) = HashMap<String, String>().apply {
        header?.let {
            try {
                val json = when {
                    it.startsWith("@js:", true) -> evalJS(it.substring(4)).toString()
                    it.startsWith("<js>", true) -> evalJS(
                        it.substring(4, it.lastIndexOf("<"))
                    ).toString()

                    else -> it
                }
                JsonCodec.decodeStringMapStrict(json)?.let { map ->
                    putAll(map)
                } ?: JsonCodec.decodeStringMap(json)?.let { map ->
                    LoggerProvider.current.debug("请求头规则 JSON 格式不规范，请改为规范格式")
                    putAll(map)
                }
            } catch (e: Exception) {
                LoggerProvider.current.error("执行请求头规则出错\n$e", e)
            }
        }
        if (!has("User-Agent", true)) {
            put("User-Agent", userAgent)
        }
        if (hasLoginHeader) {
            getLoginHeaderMap()?.let {
                putAll(it)
            }
        }
    }

    /**
     * 获取用于登录的头部信息
     */
    fun getLoginHeader(): String? {
        return KeyValueStoreProvider.current.get("loginHeader_${getKey()}")
    }

    fun getLoginHeaderMap(): Map<String, String>? {
        val cache = getLoginHeader() ?: return null
        return JsonCodec.decodeStringMap(cache)
    }

    /**
     * 保存登录头部信息,map格式,访问时自动添加
     */
    fun putLoginHeader(header: String) {
        val headerMap = JsonCodec.decodeStringMap(header)
        val cookie = headerMap?.get("Cookie") ?: headerMap?.get("cookie")
        cookie?.let {
            CookieStoreProvider.current.replaceCookie(getKey(), it)
        }
        KeyValueStoreProvider.current.put("loginHeader_${getKey()}", header)
    }

    fun removeLoginHeader() {
        KeyValueStoreProvider.current.delete("loginHeader_${getKey()}")
        CookieStoreProvider.current.removeCookie(getKey())
    }

    /**
     * 获取用户信息,可以用来登录
     * 用户信息采用aes加密存储
     */
    fun getLoginInfo(): String? {
        try {
            val key = SourceRuntimeProvider.current.androidId().encodeToByteArray(0, 16)
            val cache = KeyValueStoreProvider.current.get("userInfo_${getKey()}") ?: return null
            return SymmetricCryptoProvider.current.decryptStr("AES", key, cache)
        } catch (e: Exception) {
            LoggerProvider.current.error("获取登陆信息出错", e)
            return null
        }
    }

    private fun configureScriptBindings(): JsBindings.() -> Unit = {
        put("result", mutableMapOf<String, String>())
        put("book", null)
        put("chapter", null)
    }

    fun getLoginInfoMap(): MutableMap<String, String> {
        val json = getLoginInfo() ?: if (loginUi.isNullOrBlank()) {
            return mutableMapOf()
        } else {
            val loginUiJson = loginUi?.let {
                when {
                    it.startsWith("@js:") -> evalJS(
                        "${getLoginJs() ?: ""}\n${it.substring(4)}",
                        configureScriptBindings()
                    ).toString()

                    it.startsWith("<js>") -> evalJS(
                        "${getLoginJs() ?: ""}\n${it.substring(4, it.lastIndexOf("<"))}",
                        configureScriptBindings()
                    ).toString()

                    else -> it
                }
            }
            val longinInfo = JsonCodec.decodeList(loginUiJson, RowUi::class)
                ?.filter { it.type != "button" }
                ?.associate { it.name to (it.default ?: "") }
                ?.takeIf { it.isNotEmpty() }?.also {
                    putLoginInfo(JsonCodec.toJson(it))
                }
            return longinInfo?.toMutableMap() ?: mutableMapOf()
        }
        return JsonCodec.decodeAnyMap(json)?.filterValues { it is String }
            ?.mapValues { it.value as String }?.toMutableMap() ?: mutableMapOf()
    }

    /**
     * 保存用户信息,aes加密
     */
    fun putLoginInfo(info: String): Boolean {
        return try {
            val key = SourceRuntimeProvider.current.androidId().encodeToByteArray(0, 16)
            val encodeStr = SymmetricCryptoProvider.current.encryptBase64("AES", key, info)
            KeyValueStoreProvider.current.put("userInfo_${getKey()}", encodeStr)
            true
        } catch (e: Exception) {
            LoggerProvider.current.error("保存登陆信息出错", e)
            false
        }
    }

    fun removeLoginInfo() {
        KeyValueStoreProvider.current.delete("userInfo_${getKey()}")
    }

    /**
     * 设置自定义变量
     * @param variable 变量内容
     */
    fun setVariable(variable: String?) {
        if (variable != null) {
            KeyValueStoreProvider.current.put("sourceVariable_${getKey()}", variable)
        } else {
            KeyValueStoreProvider.current.delete("sourceVariable_${getKey()}")
        }
    }

    /**
     * 设置自定义变量
     * 新,统一为put名称存变量
     */
    fun putVariable(variable: String?) {
        if (variable != null) {
            KeyValueStoreProvider.current.put("sourceVariable_${getKey()}", variable)
        } else {
            KeyValueStoreProvider.current.delete("sourceVariable_${getKey()}")
        }
    }

    /**
     * 获取自定义变量
     */
    fun getVariable(): String {
        getTemporaryVariable()?.let {
            return it
        }
        return KeyValueStoreProvider.current.get("sourceVariable_${getKey()}") ?: ""
    }

    fun setTemporaryVariable(variable: String?) {
    }

    fun getTemporaryVariable(): String? {
        return null
    }

    /**
     * 保存数据
     */
    fun put(key: String, value: String): String {
        KeyValueStoreProvider.current.put("v_${getKey()}_${key}", value)
        return value
    }

    /**
     * 获取保存的数据
     */
    fun get(key: String): String {
        return KeyValueStoreProvider.current.get("v_${getKey()}_${key}") ?: ""
    }

    /**
     * 刷新发现
     */
    fun refreshExplore() {
        if (SourceRuntimeProvider.current.isMainThread()) {
            error("refreshExplore must be called on a background thread")
        }
        runBlocking {
            SourceRuntimeProvider.current.clearExploreKindsCache(this@BaseSource)
        }
    }

    /**
     * 刷新JSLib
     */
    fun refreshJSLib() {
        if (SourceRuntimeProvider.current.isMainThread()) {
            error("refreshJSLib must be called on a background thread")
        }
        runBlocking {
            SourceRuntimeProvider.current.removeJsLib(jsLib)
        }
    }

    /**
     * 设置并发率
     */
    fun putConcurrent(value: String) {
        SourceRuntimeProvider.current.updateConcurrentRate(getKey(), value)
    }

    /**
     * 执行JS
     */
    @Throws(Exception::class)
    fun evalJS(jsStr: String, bindingsConfig: JsBindings.() -> Unit = {}): Any? {
        val bindings = JsBindings()
        bindings["java"] = this
        bindings["source"] = this
        bindings["baseUrl"] = getKey()
        bindings["cookie"] = CookieStoreProvider.current
        bindings["cache"] = KeyValueStoreProvider.current
        bindings.apply(bindingsConfig)
        val sharedScope = SourceRuntimeProvider.current.getShareScope(jsLib)
        val scope = JsEngine.getRuntimeScope(bindings, sharedScope)
        return JsEngine.eval(jsStr, scope)
    }
}
