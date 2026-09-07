package io.legado.app.core.platform

/**
 * 日志契约（P4-e BaseSource 下沉第 4 刀）。
 *
 * 取代 app 侧 `constant.AppLog` 在 commonMain 值对象里的用法，让下沉后的 entity
 * 能记录调试/错误日志而不依赖 Android 日志栈。
 *
 * BaseSource 只需 [debug]（替代 `log()`/`AppLog.putDebug`）和 [error]（替代
 * `AppLog.put(msg, throwable)`）两个方法，按真实调用方收敛
 * （AGENTS.md「无调用方抽象」）。
 *
 * **为什么是 interface + composition root 注入**：实现依赖 `:app` 的日志框架，
 * `core:*` 不能反向依赖 `:app`。
 */
interface Logger {

    /** 记录一条调试日志。 */
    fun debug(msg: String)

    /** 记录一条错误日志（含可选异常堆栈）。 */
    fun error(msg: String, throwable: Throwable? = null)
}

/**
 * [Logger] 的注入点。模式同 [KeyValueStoreProvider]。
 */
object LoggerProvider {

    @Volatile
    private var delegate: Logger? = null

    fun install(logger: Logger) {
        delegate = logger
    }

    fun uninstall() {
        delegate = null
    }

    val isInstalled: Boolean get() = delegate != null

    val current: Logger
        get() = delegate ?: error(
            "Logger 未安装：请在应用 composition root 调用 " +
                "LoggerProvider.install(...) 注入平台实现。"
        )
}

/** 丢弃所有日志的实现：用于测试与不想输出日志的场景。 */
class NoOpLogger : Logger {
    override fun debug(msg: String) {}
    override fun error(msg: String, throwable: Throwable?) {}
}
