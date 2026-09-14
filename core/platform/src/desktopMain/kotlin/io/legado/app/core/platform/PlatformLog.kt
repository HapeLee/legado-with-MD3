package io.legado.app.core.platform

import java.util.logging.Level
import java.util.logging.Logger

/**
 * [PlatformLog] 的 JVM 实现：写宿主同名 logger（[APP_LOGGER_NAME]）。
 *
 * androidMain 与 desktopMain 都是 JVM，故实现相同（同 [RuleDataStorage] / [SymmetricCrypto] 先例）。
 * `Logger.getLogger` 是按名字返回进程级单例，所以这里拿到的一定是宿主挂过 `FileHandler`
 * 的那一个——共享层与 `:app` 因此写进同一个日志文件。
 */
actual object PlatformLog {

    private val logger: Logger by lazy { Logger.getLogger(APP_LOGGER_NAME) }

    actual fun info(tag: String, msg: String) {
        logger.log(Level.INFO, "$tag $msg")
    }
}
