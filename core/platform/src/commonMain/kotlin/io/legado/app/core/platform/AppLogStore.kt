package io.legado.app.core.platform

/**
 * 应用日志的共享实现（M2-4a）：内存环形缓冲 + 落盘。
 *
 * 迁移前这段逻辑住在 `:app` 的 `constant.AppLog` 里，`:core:data` 的 `BaseSource` 只能通过
 * `LoggerProvider` 这个 service locator 绕过去（M2 系列要清退的最后一批债之一）。
 * 环形缓冲本身是**纯 Kotlin**（`ArrayList` + `Triple` + `systemTimeMillis()`），
 * 落盘只要一个 logger 名字（[PlatformLog]），所以它能整体搬进共享层；
 * 只有两件真的属于宿主的事留在 `:app` 的 `AppLog` 薄适配层里：
 *
 *  - **轻提示**（`toast = true` 时 `appCtx.toastOnUi`）——要 `Context`；
 *  - **debug 构建下的 Logcat 直投**（`android.util.Log.e` + 取调用方类名当 tag）——要
 *    `android.util.Log`，且必须在 `AppLog.put` 这一层取栈（帧深度才能和迁移前一致）。
 *
 * 因此 `:app` 的 `AppLog` 保留原签名（106 个调用方零改动），把「记录 + 落盘 + 缓冲」
 * 委托到这里；`BaseSource` 等共享层消费方直接调用本对象，不再经 Provider。
 *
 * **线程安全**：日志会被任意线程写入，而 `add` / `removeLastOrNull` / `clear` 作用在同一个
 * `ArrayList` 上，迁移前用 `@Synchronized` 保护。`@Synchronized` / `synchronized` 是 JVM-only，
 * 这里改用平台原语 [synchronizedOn]（androidMain / desktopMain 都委托对象监视器），
 * 语义与迁移前一致。
 */
object AppLogStore {

    /** 互斥对象：只保护 [entries]。 */
    private val lock = Any()

    private val entries = arrayListOf<Triple<Long, String, Throwable?>>()

    /**
     * 最近日志，新的在前。
     *
     * 结构对齐迁移前的 `AppLog.logs`（`List<Triple<Long, String, Throwable?>>`），
     * 日志界面因此零改动。与迁移前一样不加锁：`ArrayList.toList()` 的拷贝读一次 `size`
     * 再复制数组，与并发 `add` / `clear` 交错不会越界。
     */
    val logs: List<Triple<Long, String, Throwable?>>
        get() = entries.toList()

    /**
     * 记录一条日志并落盘。对齐迁移前 `AppLog.put(message, throwable)` 里除轻提示与
     * Logcat 之外的全部行为：截断到 100 条、写 `"AppLog"` 日志、插入缓冲头部。
     */
    fun put(message: String?, throwable: Throwable? = null) {
        message ?: return
        synchronizedOn(lock) {
            if (entries.size > 100) {
                entries.removeLastOrNull()
            }
            if (throwable == null) {
                PlatformLog.info("AppLog", message)
            } else {
                PlatformLog.info("AppLog", "$message\n${throwable.stackTraceToString()}")
            }
            entries.add(0, Triple(systemTimeMillis(), message, throwable))
        }
    }

    /**
     * 只进内存缓冲、不落盘。对齐迁移前 `AppLog.putNotSave`（除轻提示与 Logcat 之外的部分）。
     */
    fun putNotSave(message: String?, throwable: Throwable? = null) {
        message ?: return
        synchronizedOn(lock) {
            if (entries.size > 100) {
                entries.removeLastOrNull()
            }
            entries.add(0, Triple(systemTimeMillis(), message, throwable))
        }
    }

    /**
     * 调试日志：仅在宿主开启「记录日志」时记录。对齐迁移前 `AppLog.putDebug`
     * （迁移前读 `OtherSettingsGateway.currentSettings.recordLog`，现在读 host 同步过来的
     * [LogSettings.recordLog]，见其 KDoc）。
     */
    fun putDebug(message: String?, throwable: Throwable? = null) {
        if (LogSettings.recordLog) {
            put(message, throwable)
        }
    }

    /** 清空内存缓冲。对齐迁移前 `AppLog.clear`。 */
    fun clear() {
        synchronizedOn(lock) {
            entries.clear()
        }
    }
}
