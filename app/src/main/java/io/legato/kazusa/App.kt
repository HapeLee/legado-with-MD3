package io.legato.kazusa

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.graphics.scale
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.jeremyliao.liveeventbus.LiveEventBus
import com.jeremyliao.liveeventbus.logger.DefaultLogger
import com.script.rhino.ReadOnlyJavaObject
import com.script.rhino.RhinoScriptEngine
import com.script.rhino.RhinoWrapFactory
import io.legato.kazusa.constant.AppConst.channelIdDownload
import io.legato.kazusa.constant.AppConst.channelIdReadAloud
import io.legato.kazusa.constant.AppConst.channelIdWeb
import io.legato.kazusa.constant.PreferKey
import io.legato.kazusa.data.appDb
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookChapter
import io.legato.kazusa.data.entities.BookSource
import io.legato.kazusa.data.entities.HttpTTS
import io.legato.kazusa.data.entities.RssSource
import io.legato.kazusa.data.entities.rule.BookInfoRule
import io.legato.kazusa.data.entities.rule.ContentRule
import io.legato.kazusa.data.entities.rule.ExploreRule
import io.legato.kazusa.data.entities.rule.SearchRule
import io.legato.kazusa.help.AppFreezeMonitor
import io.legato.kazusa.help.AppWebDav
import io.legato.kazusa.help.CrashHandler
import io.legato.kazusa.help.DefaultData
import io.legato.kazusa.help.LifecycleHelp
import io.legato.kazusa.help.RuleBigDataHelp
import io.legato.kazusa.help.book.BookHelp
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.config.ThemeConfig.applyDayNightInit
import io.legato.kazusa.help.coroutine.Coroutine
import io.legato.kazusa.help.http.Cronet
import io.legato.kazusa.help.http.ObsoleteUrlFactory
import io.legato.kazusa.help.http.okHttpClient
import io.legato.kazusa.help.rhino.NativeBaseSource
import io.legato.kazusa.help.source.SourceHelp
import io.legato.kazusa.help.storage.Backup
import io.legato.kazusa.lib.theme.primaryColor
import io.legato.kazusa.model.BookCover
import io.legato.kazusa.utils.ChineseUtils
import io.legato.kazusa.utils.FirebaseManager
import io.legato.kazusa.utils.LogUtils
import io.legato.kazusa.utils.defaultSharedPreferences
import io.legato.kazusa.utils.getPrefBoolean
import io.legato.kazusa.utils.getPrefString
import io.legato.kazusa.utils.isDebuggable
import kotlinx.coroutines.launch
import org.chromium.base.ThreadUtils
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class App : Application() {

    private lateinit var oldConfig: Configuration

    override fun onCreate() {

        if (getPrefString("app_theme", "0") == "12") {
            if (AppConfig.customMode == "accent")
                setTheme(R.style.ThemeOverlay_WhiteBackground)

            val colorImagePath = getPrefString(PreferKey.colorImage)
            if (!colorImagePath.isNullOrBlank()) {
                val file = File(colorImagePath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        val colorAccuracy = true
                        val targetWidth = if (colorAccuracy) (bitmap.width / 4).coerceAtMost(256) else 16
                        val targetHeight = if (colorAccuracy) (bitmap.height / 4).coerceAtMost(256) else 16
                        val scaledBitmap = bitmap.scale(targetWidth, targetHeight, false)

                        val options = DynamicColorsOptions.Builder()
                            .setContentBasedSource(scaledBitmap)
                            .build()

                        DynamicColors.applyToActivitiesIfAvailable(this, options)
                        bitmap.recycle()
                    }
                }
            }else{
                DynamicColors.applyToActivitiesIfAvailable(
                    this,
                    DynamicColorsOptions.Builder()
                        .setContentBasedSource(this.primaryColor)
                        .build()
                )
            }
        }

        super.onCreate()
        FirebaseManager.init(this)
        CrashHandler(this)
        if (isDebuggable) {
            ThreadUtils.setThreadAssertsDisabledForTesting(true)
        }
        oldConfig = Configuration(resources.configuration)
        applyDayNightInit(this)
        registerActivityLifecycleCallbacks(LifecycleHelp)
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(AppConfig)
        Coroutine.async {
            LogUtils.init(this@App)
            LogUtils.d("App", "onCreate")
            LogUtils.logDeviceInfo()
            //预下载Cronet so
            Cronet.preDownload()
            createNotificationChannels()
            LiveEventBus.config()
                .lifecycleObserverAlwaysActive(true)
                .autoClear(false)
                .enableLogger(BuildConfig.DEBUG || AppConfig.recordLog)
                .setLogger(EventLogger())
            DefaultData.upVersion()
            AppFreezeMonitor.init(this@App)
            URL.setURLStreamHandlerFactory(ObsoleteUrlFactory(okHttpClient))
            launch { installGmsTlsProvider(appCtx) }
            initRhino()
            //初始化封面
            BookCover.toString()
            //清除过期数据
            appDb.cacheDao.clearDeadline(System.currentTimeMillis())
            if (getPrefBoolean(PreferKey.autoClearExpired, true)) {
                val clearTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                appDb.searchBookDao.clearExpired(clearTime)
            }
            RuleBigDataHelp.clearInvalid()
            BookHelp.clearInvalidCache()
            Backup.clearCache()
            //初始化简繁转换引擎
            when (AppConfig.chineseConverterType) {
                1 -> {
                    ChineseUtils.fixT2sDict()
                    ChineseUtils.preLoad(true, TransType.TRADITIONAL_TO_SIMPLE)
                }

                2 -> ChineseUtils.preLoad(true, TransType.SIMPLE_TO_TRADITIONAL)
            }
            //调整排序序号
            SourceHelp.adjustSortNumber()
            //同步阅读记录
            if (AppConfig.syncBookProgress) {
                AppWebDav.downloadAllBookProgress()
            }
        }
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        val diff = newConfig.diff(oldConfig)
//        if ((diff and ActivityInfo.CONFIG_UI_MODE) != 0) {
//            applyDayNight(this)
//        }
//        oldConfig = Configuration(newConfig)
//    }

    /**
     * 尝试在安装了GMS的设备上(GMS或者MicroG)使用GMS内置的Conscrypt
     * 作为首选JCE提供程序，而使Okhttp在低版本Android上
     * 能够启用TLSv1.3
     * https://f-droid.org/zh_Hans/2020/05/29/android-updates-and-tls-connections.html
     * https://developer.android.google.cn/reference/javax/net/ssl/SSLSocket
     *
     * @param context
     * @return
     */
    private fun installGmsTlsProvider(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return
        }
        try {
            val gmsPackageName = "com.google.android.gms"
            val appInfo = packageManager.getApplicationInfo(gmsPackageName, 0)
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                return
            }
            val gms = context.createPackageContext(
                gmsPackageName,
                CONTEXT_INCLUDE_CODE or CONTEXT_IGNORE_SECURITY
            )
            gms.classLoader
                .loadClass("com.google.android.gms.common.security.ProviderInstallerImpl")
                .getMethod("insertProvider", Context::class.java)
                .invoke(null, gms)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建通知ID
     */
    private fun createNotificationChannels() {
        val downloadChannel = NotificationChannel(
            channelIdDownload,
            getString(R.string.action_download),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val readAloudChannel = NotificationChannel(
            channelIdReadAloud,
            getString(R.string.read_aloud),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val webChannel = NotificationChannel(
            channelIdWeb,
            getString(R.string.web_service),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        //向notification manager 提交channel
        notificationManager.createNotificationChannels(
            listOf(
                downloadChannel,
                readAloudChannel,
                webChannel
            )
        )
    }

    private fun initRhino() {
        RhinoScriptEngine
        RhinoWrapFactory.register(BookSource::class.java, NativeBaseSource.factory)
        RhinoWrapFactory.register(RssSource::class.java, NativeBaseSource.factory)
        RhinoWrapFactory.register(HttpTTS::class.java, NativeBaseSource.factory)
        RhinoWrapFactory.register(ExploreRule::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(SearchRule::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(BookInfoRule::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(ContentRule::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(BookChapter::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(Book.ReadConfig::class.java, ReadOnlyJavaObject.factory)
    }

    class EventLogger : DefaultLogger() {

        override fun log(level: Level, msg: String) {
            super.log(level, msg)
            LogUtils.d(TAG, msg)
        }

        override fun log(level: Level, msg: String, th: Throwable?) {
            super.log(level, msg, th)
            LogUtils.d(TAG, "$msg\n${th?.stackTraceToString()}")
        }

        companion object {
            private const val TAG = "[LiveEventBus]"
        }
    }

    companion object {
        init {
            if (BuildConfig.DEBUG) {
                System.setProperty("kotlinx.coroutines.debug", "on")
            }
        }
    }

}
