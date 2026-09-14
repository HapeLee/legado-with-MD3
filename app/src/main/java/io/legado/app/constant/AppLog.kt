package io.legado.app.constant

import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.core.platform.AppLogStore
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

/**
 * 应用日志门面（M2-4a 收窄为平台侧薄适配层）。
 *
 * 背景：迁移前本对象自己持有环形缓冲 + 落盘（`LogUtils.d`）+ 轻提示 + Logcat；`BaseSource`
 * 下沉 `:core:data` 后只能靠 `LoggerProvider`（service locator）调用它，成了 M2 要清退的债。
 *
 * 现在**环形缓冲与落盘整体搬进共享层** `:core:platform` 的 [AppLogStore]（纯 Kotlin：
 * `ArrayList` + `Triple`，落盘只依赖一个 logger 名字），共享层消费方（`BaseSource` 等）
 * 直接调用 [AppLogStore]；`Logger` / `LoggerProvider` 契约已删除。
 *
 * 本对象保留**签名不变**的薄壳（全仓 106 个调用方零改动），只承载两件真的需要 `:app` 的事：
 *  1. **轻提示**：`toast = true` 时 `appCtx.toastOnUi(message)`——要 `Context`。
 *  2. **debug 构建下的 Logcat 直投**：`android.util.Log.e`，tag 取调用方的调用方类名
 *     （`stackTrace[3]`）。它必须在 `AppLog.put` 内**直接**取栈，帧深度才与迁移前一致；
 *     抽成辅助函数或挪进共享层都会让 tag 漂移。
 */
object AppLog {

    /** 最近的内存日志（新的在前），转发 [AppLogStore.logs]，日志界面零改动。 */
    val logs get() = AppLogStore.logs

    fun put(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (toast) {
            appCtx.toastOnUi(message)
        }
        AppLogStore.put(message, throwable)
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            Log.e(stackTrace[3].className, message, throwable)
        }
    }

    fun putNotSave(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (toast) {
            appCtx.toastOnUi(message)
        }
        AppLogStore.putNotSave(message, throwable)
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            Log.e(stackTrace[3].className, message, throwable)
        }
    }

    fun clear() {
        AppLogStore.clear()
    }

    /**
     * 调试日志：仅在宿主开启「记录日志」时记录。
     *
     * 门控已随环形缓冲下沉（[AppLogStore.putDebug] 读 `:core:platform` 的 `LogSettings.recordLog`，
     * 由 `:app` 在 `App.onCreate` 与设置变化时同步）。
     */
    fun putDebug(message: String?, throwable: Throwable? = null) {
        AppLogStore.putDebug(message, throwable)
    }
}
