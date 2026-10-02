package io.legado.app.constant

import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.utils.LogUtils
import io.legado.app.utils.toastOnUi
import org.koin.core.context.GlobalContext
import splitties.init.appCtx

object AppLog {

    private val otherGateway by lazy { GlobalContext.get().get<OtherSettingsGateway>() }

    private val mLogs = arrayListOf<Triple<Long, String, Throwable?>>()

    /**
     * 头插、按 `System.currentTimeMillis()` 打时间戳、超上限从尾部丢：
     * 同一毫秒内重复上报的消息会有完全相同的 `(timestamp, message)`，
     * 消费方不能拿这两项当唯一标识（朗读日志页的 key 编号见 `ttsLogKeys`）。
     */
    val logs get() = mLogs.toList()

    @Synchronized
    fun put(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (toast) {
            appCtx.toastOnUi(message)
        }
        if (mLogs.size > 100) {
            mLogs.removeLastOrNull()
        }
        if (throwable == null) {
            LogUtils.d("AppLog", message)
        } else {
            LogUtils.d("AppLog", "$message\n${throwable.stackTraceToString()}")
        }
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            Log.e(stackTrace[3].className, message, throwable)
        }
    }

    @Synchronized
    fun putNotSave(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (toast) {
            appCtx.toastOnUi(message)
        }
        if (mLogs.size > 100) {
            mLogs.removeLastOrNull()
        }
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            Log.e(stackTrace[3].className, message, throwable)
        }
    }

    @Synchronized
    fun clear() {
        mLogs.clear()
    }

    fun putDebug(message: String?, throwable: Throwable? = null) {
        if (otherGateway.currentSettings.recordLog) {
            put(message, throwable)
        }
    }

}