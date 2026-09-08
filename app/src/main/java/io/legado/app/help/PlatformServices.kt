package io.legado.app.help

import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.CookieStore
import io.legado.app.core.platform.KeyValueStore
import io.legado.app.core.platform.Logger
import io.legado.app.core.platform.SourceRuntime
import io.legado.app.core.platform.SymmetricCrypto
import io.legado.app.core.platform.Toaster
import io.legado.app.data.json.GsonImportJsonEditor
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.help.ConcurrentRateLimiter.Companion.updateConcurrentRate
import io.legado.app.help.crypto.SymmetricCryptoAndroid
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.getClipText
import io.legado.app.utils.isMainThread
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx

/**
 * 下沉后 BaseSource 依赖的 5 个平台能力契约的 app 侧适配实现 + 注册。
 *
 * BaseSource 已下沉 core:data，其方法体通过 KeyValueStore/CookieStore/SymmetricCrypto/
 * Logger/SourceRuntime 五个契约访问平台能力。这里把 app 侧的单例/工具适配成契约形状，
 * 并在 [install] 里一次性注入（App.onCreate 调用）。
 */
object PlatformServices {

    /** 键值存储：委托 app 侧 `CacheManager`。 */
    private val keyValueStore = object : KeyValueStore {
        override fun get(key: String): String? = CacheManager.get(key)
        override fun put(key: String, value: String, saveTime: Int) {
            CacheManager.put(key, value, saveTime)
        }

        override fun delete(key: String) {
            CacheManager.delete(key)
        }
    }

    /** Cookie 存储：委托 app 侧 `help.http.CookieStore`。 */
    private val cookieStore = object : CookieStore {
        override fun replaceCookie(url: String, cookie: String) {
            io.legado.app.help.http.CookieStore.replaceCookie(url, cookie)
        }

        override fun removeCookie(url: String) {
            io.legado.app.help.http.CookieStore.removeCookie(url)
        }
    }

    /** 对称加解密：委托 app 侧 `SymmetricCryptoAndroid`。 */
    private val symmetricCrypto = object : SymmetricCrypto {
        override fun encryptBase64(algorithm: String, key: ByteArray, data: String): String =
            SymmetricCryptoAndroid(algorithm, key).encryptBase64(data)

        override fun decryptStr(algorithm: String, key: ByteArray, data: String): String =
            SymmetricCryptoAndroid(algorithm, key).decryptStr(data)
    }

    /** 日志：委托 app 侧 `AppLog`。 */
    private val logger = object : Logger {
        override fun debug(msg: String) {
            AppLog.putDebug(msg)
        }

        override fun error(msg: String, throwable: Throwable?) {
            AppLog.put(msg, throwable)
        }
    }

    /** 源运行时：聚合 app 侧分散的源生命周期能力。 */
    private val sourceRuntime = object : SourceRuntime {
        override fun isMainThread(): Boolean = io.legado.app.utils.isMainThread

        override fun getShareScope(jsLib: String?): io.legado.app.core.platform.JsScope? =
            SharedJsScope.getScope(jsLib, null)

        override fun removeJsLib(jsLib: String?) {
            SharedJsScope.remove(jsLib)
        }

        override fun clearExploreKindsCache(source: Any) {
            if (source is BookSource) {
                runBlocking { source.clearExploreKindsCache() }
            }
        }

        override fun updateConcurrentRate(key: String, value: String) {
            ConcurrentRateLimiter.updateConcurrentRate(key, value)
        }

        override fun androidId(): String = AppConst.androidId
    }

    /**
     * 剪贴板：委托 app 侧 `Context.getClipText()` / `Context.sendToClip()`。
     * 用 `appCtx`（splitties 全局 Context）——与两个扩展原本的使用方式一致。
     */
    private val clipboard = object : Clipboard {
        override fun getText(): String? = appCtx.getClipText()

        override fun setText(text: String) {
            appCtx.sendToClip(text)
        }
    }

    /** 轻提示：委托 app 侧 `Context.toastOnUi()` / `Context.longToastOnUi()`。 */
    private val toaster = object : Toaster {
        override fun toast(message: String) {
            appCtx.toastOnUi(message)
        }

        override fun longToast(message: String) {
            appCtx.longToastOnUi(message)
        }
    }

    /** 导入对象的按字段编辑：委托 `:core:data` 的 Gson 实现（与 `GSON` 同模块）。 */
    private val importJsonEditor = GsonImportJsonEditor()

    /** 注入全部平台能力契约；App.onCreate 在 `super.onCreate()` 后调用。 */
    fun install() {
        io.legado.app.core.platform.KeyValueStoreProvider.install(keyValueStore)
        io.legado.app.core.platform.CookieStoreProvider.install(cookieStore)
        io.legado.app.core.platform.SymmetricCryptoProvider.install(symmetricCrypto)
        io.legado.app.core.platform.LoggerProvider.install(logger)
        io.legado.app.core.platform.SourceRuntimeProvider.install(sourceRuntime)
        io.legado.app.core.platform.ClipboardProvider.install(clipboard)
        io.legado.app.core.platform.ToasterProvider.install(toaster)
        io.legado.app.core.platform.ImportJsonEditorProvider.install(importJsonEditor)
    }
}
