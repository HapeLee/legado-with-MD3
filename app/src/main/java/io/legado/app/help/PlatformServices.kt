package io.legado.app.help

import io.legado.app.core.platform.CookieStore
import io.legado.app.core.platform.KeyValueStore
import io.legado.app.core.platform.SourceRuntime
import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.model.SharedJsScope
import io.legado.app.platform.AndroidPlatformCapabilities
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx

/**
 * 下沉后 BaseSource 仍需的平台能力契约的 app 侧适配实现 + 注册。
 *
 * BaseSource 已下沉 core:data，其方法体通过 KeyValueStore/CookieStore/SourceRuntime
 * 契约访问平台能力。这三者的实现都真的依赖 `:app`（CacheManager / okhttp CookieStore /
 * SharedJsScope），`core:*` 不能反向依赖 `:app`，所以只能在这里适配成契约形状，
 * 并在 [install] 里一次性注入（App.onCreate 调用）。
 *
 * M2 起逐条清退（`Provider` 存在的唯一理由就是实现在 `:app`；实现能搬进共享层就直接删契约）：
 *  - M2-3：`SymmetricCrypto` 改成 `:core:platform` 的 `expect object` 原语，
 *    `SymmetricCryptoProvider` 已删除，不再注入。
 *  - M2-4a：`Logger` 契约已删除——`AppLog` 的环形缓冲与落盘整体下沉，
 *    共享层消费方直接调 `:core:platform` 的 `AppLogStore`，不再经 Provider。
 *  - M2-4b：`SourceRuntime` 收窄到剩下 3 项——`isMainThread` 成平台原语、
 *    `androidId` 成宿主配置项、并发率登记表下沉 `:core:data`，
 *    本适配器只留 `SharedJsScope` 的三个方法。
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

    /** 源运行时：只剩共享 JS 作用域相关能力（其余已下沉或成原语，见类 KDoc）。 */
    private val sourceRuntime = object : SourceRuntime {
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
    }

    /** 注入全部平台能力契约；App.onCreate 在 `super.onCreate()` 后调用。 */
    fun install() {
        io.legado.app.core.platform.KeyValueStoreProvider.install(keyValueStore)
        io.legado.app.core.platform.CookieStoreProvider.install(cookieStore)
        io.legado.app.core.platform.SourceRuntimeProvider.install(sourceRuntime)
        // 剪贴板/轻提示的实现已移到 `io.legado.app.platform.AndroidPlatformCapabilities`：
        // Provider 路径与 di 里的构造注入路径共用同一组工厂。这里沿用 appCtx（全局
        // Application Context），与两个 Context 扩展原本的使用方式一致。
        io.legado.app.core.platform.ClipboardProvider.install(
            AndroidPlatformCapabilities.clipboard(appCtx)
        )
        io.legado.app.core.platform.ToasterProvider.install(
            AndroidPlatformCapabilities.toaster(appCtx)
        )
        // M1-3n：`FilePickerSheet`（已进 `:core:designsystem/commonMain`）用它把扩展名翻译成
        // 系统文件选择器要的 MIME。`MimeTypeMap` 是进程级单例，**不需要 Context**。
        io.legado.app.core.platform.MimeTypeResolverProvider.install(
            AndroidPlatformCapabilities.mimeTypeResolver()
        )
        // M1-3o：`plainTextClipEntry`（已随本切片进 `:core:designsystem/commonMain`）用它把
        // 文本包成平台剪贴板条目。契约类型 `ClipEntry` 是 Compose 的 `expect class`，
        // 共享层没有构造器，装不进 `:core:platform` ⇒ 契约住在 designsystem，但仍在同一处注入。
        io.legado.app.ui.util.PlainTextClipEntryProvider.install(
            AndroidPlatformCapabilities.plainTextClipEntryFactory()
        )
        // M1-3p：`AppContainerBackground`（已进 `:core:designsystem/commonMain`）用它把
        // `.9.png` 解成九宫格 drawable 再交给 Coil。`BitmapFactory.decodeFile` 收绝对路径，
        // **不需要 Context**。desktop 侧不注入 ⇒ Provider 一律返回 null ⇒ 回落成按原路径加载。
        io.legado.app.core.platform.NinePatchLoaderProvider.install(
            AndroidPlatformCapabilities.ninePatchLoader()
        )
        // M1-3r：顶栏（已进 `:core:designsystem/commonMain`）需要两条契约。
        // ① 状态栏 inset：Android **必须**注入 `statusBarsIgnoringVisibility`（忽略可见性、
        //    恒返回真实高度），否则从隐藏了状态栏的界面返回时顶栏会重排；
        //    desktop 不注入 ⇒ 回落 `WindowInsets.statusBars`（恒为零，语义正确）。
        io.legado.app.ui.widget.components.topbar.StatusBarInsetsProvider.install(
            AndroidPlatformCapabilities.topBarStatusBarInsets()
        )
        // ② 液态玻璃：Android 实现留在 `:core:ui`（`topBarLiquidGlass` 是 internal，
        //    app 看不见），这里只注入工厂。未注入 ⇒ 关闭液态玻璃，退化为普通胶囊按钮。
        io.legado.app.ui.widget.components.topbar.LiquidGlassEffectsProvider.install(
            io.legado.app.ui.widget.components.topbar.androidLiquidGlassEffects()
        )
        // M1-3t：`SwitchSettingItem`（已进 `:core:designsystem/commonMain`）的 Miuix 分支要用
        // `top.yukonga.miuix.kmp.preference.SwitchPreference`——它是整个 miuix 里唯一没有
        // desktop 变体的制品（只有 `miuix-preference-android`）⇒ 走契约，Android 实现留在
        // `:core:ui`。desktop 不注入 ⇒ `current` 为 null ⇒ 调方显式落到 Material3 分支
        // （desktop 上 Miuix 引擎本就不可达，见契约 KDoc，不是静默降级）。
        io.legado.app.ui.widget.components.settingItem.MiuixPreferenceRendererProvider.install(
            io.legado.app.ui.widget.components.settingItem.androidMiuixPreferenceRenderer()
        )
    }
}
