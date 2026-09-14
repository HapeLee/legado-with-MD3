@file:Suppress("unused")

package io.legado.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import io.legado.app.BuildConfig
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.core.platform.APP_LOGGER_NAME
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.help.globalExecutor
import org.koin.core.context.GlobalContext
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.time.Duration.Companion.days

@SuppressLint("SimpleDateFormat")
@Suppress("unused")
object LogUtils {
    const val TIME_PATTERN = "yy-MM-dd HH:mm:ss.SSS"
    val logTimeFormat by lazy { SimpleDateFormat(TIME_PATTERN) }

    private val otherSettingsGateway
        get() = GlobalContext.get().get<OtherSettingsGateway>()

    fun init(context: Context) {
        fileHandler = createFileHandler(context)?.also {
            logger.addHandler(it)
        }
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        logger.log(Level.INFO, "$tag $msg")
    }

    inline fun d(tag: String, lazyMsg: () -> String) {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "$tag ${lazyMsg()}")
        }
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        logger.log(Level.WARNING, "$tag $msg")
    }

    /**
     * 宿主日志框架的 logger。
     *
     * 名字取自共享层常量 [APP_LOGGER_NAME]：`:core:platform` 的 `PlatformLog`（供
     * `AppLogStore` 写共享层日志）用的是同一个名字，因此两边写进同一个日志文件。
     * 这两处必须共用常量，不要各自硬编码字符串。
     */
    val logger: Logger by lazy {
        Logger.getLogger(APP_LOGGER_NAME)
    }

    private var fileHandler: FileHandler? = null

    private fun createFileHandler(context: Context): FileHandler? {
        try {
            val root = context.externalCacheDir ?: return null
            val logFolder = FileUtils.createFolderIfNotExist(root, "logs")
            globalExecutor.execute {
                val expiredTime = System.currentTimeMillis() - 7.days.inWholeMilliseconds
                logFolder.listFiles()?.forEach {
                    if (it.lastModified() < expiredTime || it.name.endsWith(".lck")) {
                        it.delete()
                    }
                }
            }
            val date = getCurrentDateStr(TIME_PATTERN)
            val logPath = FileUtils.getPath(root = logFolder, "appLog-$date.txt")
            return AsyncFileHandler(logPath).apply {
                formatter = object : java.util.logging.Formatter() {
                    override fun format(record: LogRecord): String {
                        // 设置文件输出格式
                        return getCurrentDateStr(TIME_PATTERN) + ": " + record.message + "\n"
                    }
                }
                level = if (otherSettingsGateway.currentSettings.recordLog) {
                    Level.INFO
                } else {
                    Level.OFF
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppLog.putNotSave("创建fileHandler出错\n$e", e)
            return null
        }
    }

    fun upLevel() {
        val level = if (otherSettingsGateway.currentSettings.recordLog) {
            Level.INFO
        } else {
            Level.OFF
        }
        fileHandler?.level = level
    }

    /**
     * 获取当前时间
     */
    @SuppressLint("SimpleDateFormat")
    fun getCurrentDateStr(pattern: String): String {
        val date = Date()
        val sdf = SimpleDateFormat(pattern)
        return sdf.format(date)
    }

    fun logDeviceInfo() {
        d("DeviceInfo") {
            buildString {
                kotlin.runCatching {
                    //获取系统信息
                    append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n")
                    append("BRAND=").append(Build.BRAND).append("\n")
                    append("MODEL=").append(Build.MODEL).append("\n")
                    append("SDK_INT=").append(Build.VERSION.SDK_INT).append("\n")
                    append("RELEASE=").append(Build.VERSION.RELEASE).append("\n")
                    val userAgent = try {
                        WebSettings.getDefaultUserAgent(appCtx)
                    } catch (e: Throwable) {
                        e.toString()
                    }
                    append("WebViewUserAgent=").append(userAgent).append("\n")
                    append("packageName=").append(appCtx.packageName).append("\n")
                    append("heapSize=").append(Runtime.getRuntime().maxMemory()).append("\n")
                    //获取app版本信息
                    AppConst.appInfo.let {
                        append("versionName=").append(it.versionName).append("\n")
                        append("versionCode=").append(it.versionCode).append("\n")
                    }
                }
            }
        }
    }

}

fun Throwable.printOnDebug() {
    if (BuildConfig.DEBUG) {
        printStackTrace()
    }
}
