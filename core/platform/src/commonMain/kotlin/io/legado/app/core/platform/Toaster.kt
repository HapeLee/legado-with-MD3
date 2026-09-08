package io.legado.app.core.platform

/**
 * 轻提示契约（P4 规则 VM 去 app 直连第 2 刀）。
 *
 * 取代规则类 ViewModel 里的 `context.toastOnUi(...)`（`utils/ToastUtils.kt` 的 `Context` 扩展）。
 * 两个方法对应 app 侧既有的一对：`toastOnUi`（短）与 `longToastOnUi`（长），
 * 不合并成一个带 `duration` 参数的方法——那样会把 Android `Toast.LENGTH_*` 常量语义带进契约。
 *
 * 实现负责切主线程（对齐 `toastOnUi` 内部 `runOnUI`）并吞掉平台异常。
 */
interface Toaster {

    /** 短提示。对齐 `Context.toastOnUi(message)`。 */
    fun toast(message: String)

    /** 长提示。对齐 `Context.longToastOnUi(message)`。 */
    fun longToast(message: String)
}

/**
 * [Toaster] 的注入点。模式同 [LoggerProvider]。
 */
object ToasterProvider {

    @Volatile
    private var delegate: Toaster? = null

    fun install(toaster: Toaster) {
        delegate = toaster
    }

    fun uninstall() {
        delegate = null
    }

    val isInstalled: Boolean get() = delegate != null

    val current: Toaster
        get() = delegate ?: error(
            "Toaster 未安装：请在应用 composition root 调用 " +
                "ToasterProvider.install(...) 注入平台实现。"
        )
}
