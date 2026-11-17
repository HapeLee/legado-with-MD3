package io.legado.app.service

// ——————【新增引用】——————
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.startService
import io.legado.app.utils.stopService
import io.legado.app.utils.toastOnUi
import io.legado.app.web.KtorServer
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import splitties.init.appCtx
import splitties.systemservices.powerManager
import splitties.systemservices.wifiManager

class WebService : BaseService() {

    private val otherSettingsGateway by inject<OtherSettingsGateway>()

    companion object {
        var isRun = false
        var hostAddress = ""

        fun start(context: Context) {
            context.startService<WebService>()
        }

        fun startForeground(context: Context) {
            val intent = Intent(context, WebService::class.java)
            context.startForegroundServiceCompat(intent)
        }

        fun stop(context: Context) {
            context.stopService<WebService>()
        }

        fun serve() {
            appCtx.startService<WebService> {
                action = "serve"
            }
        }
    }

    private val useWakeLock = appCtx.getPrefBoolean(PreferKey.webServiceWakeLock, false)
    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:WebService")
            .apply {
                setReferenceCounted(false)
            }
    }
    private val wifiLock by lazy {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "legado:AudioPlayService")
            ?.apply {
                setReferenceCounted(false)
            }
    }
    private var ktorServer: KtorServer? = null
    /**
     * Web 服务是用户主动开启的常驻本地服务器，用 specialUse 而不是 dataSync：
     * Android 15 起 dataSync 受「任意 24 小时累计 6 小时」配额限制，配额耗尽后
     * startForeground 会抛 ForegroundServiceStartNotAllowedException。
     * API 34 起才有该类型常量，更低版本沿用清单声明（无类型时长限制）。
     */
    private val specialUseType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    private var notificationList = mutableListOf(appCtx.getString(R.string.service_starting))
    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        // 系统拒绝进入前台（如 dataSync 配额耗尽）时服务已被停止，不再做后续初始化。
        if (foregroundStartRejected) return
        if (useWakeLock) {
            wakeLock.acquire()
            wifiLock?.acquire()
        }
        isRun = true
        upTile(true)
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            val addressList = NetworkUtils.getLocalIPAddress()
            notificationList.clear()
            if (addressList.any()) {
                notificationList.addAll(addressList.map { address ->
                    getString(
                        R.string.http_ip,
                        address.hostAddress,
                        getPort()
                    )
                })
                hostAddress = notificationList.first()
            } else {
                hostAddress = getString(R.string.network_connection_unavailable)
                notificationList.add(hostAddress)
            }
            startForegroundNotification()
            postEvent(EventBus.WEB_SERVICE, hostAddress)
            FlowEventBus.post(EventBus.WEB_SERVICE, hostAddress)
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (foregroundStartRejected) {
            // 无法进入前台时不再启动 Ktor，避免起来了又被系统回收。
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            IntentAction.stop -> {
                // ——————【修改开始】通知栏点击停止时，也记录关闭状态——————
                lifecycleScope.launch {
                    otherSettingsGateway.update { it.copy(webServiceAutoStart = false) }
                }
                stopSelf()
                // ——————【修改结束】——————
            }
            "copyHostAddress" -> sendToClip(hostAddress)
            "serve" -> if (useWakeLock) {
                wakeLock.acquire()
                wifiLock?.acquire()
            }

            else -> upWebServer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        networkChangedListener.unRegister()
        isRun = false
        ktorServer?.stop()
        postEvent(EventBus.WEB_SERVICE, "")
        FlowEventBus.post(EventBus.WEB_SERVICE, "")
        upTile(false)
    }

    private fun upWebServer() {
        ktorServer?.stop()
        val addressList = NetworkUtils.getLocalIPAddress()
        if (addressList.any()) {
            val port = getPort()
            ktorServer = KtorServer(port)
            try {
                ktorServer?.start()
                ktorServer?.startWebSocket(port + 1)
                notificationList.clear()
                notificationList.addAll(addressList.map { address ->
                    getString(
                        R.string.http_ip,
                        address.hostAddress,
                        getPort()
                    )
                })
                hostAddress = notificationList.first()
                isRun = true
                postEvent(EventBus.WEB_SERVICE, hostAddress)
                FlowEventBus.post(EventBus.WEB_SERVICE, hostAddress)
                startForegroundNotification()
            } catch (e: Exception) {
                ktorServer?.stop()
                ktorServer = null
                toastOnUi(e.localizedMessage ?: "")
                e.printOnDebug()
                stopSelf()
            }
        } else {
            toastOnUi("web service cant start, no ip address")
            stopSelf()
        }
    }

    private fun getPort(): Int {
        var port = getPrefInt(PreferKey.webPort, 1122)
        if (port > 65530 || port < 1024) {
            port = 1122
        }
        return port
    }

    /**
     * 更新通知。同时是前台服务的唯一发布入口：首次调用进入前台，之后只更新通知内容。
     *
     * 本方法会被网络变化回调（ConnectivityManager 的 CallbackHandler 线程）和启动流程调用，
     * 因此不能把“刷新通知”实现成再次 startForeground()：Android 15+ 对受限前台类型
     * （dataSync / mediaProcessing）有 24 小时累计运行配额，配额耗尽后会抛
     * ForegroundServiceStartNotAllowedException，异常落在系统回调线程上会直接终止进程。
     * 本服务已改用 specialUse，仍按此约束编码以免将来回退类型时复发。
     */
    override fun startForegroundNotification() {
        val notification = buildNotification()
        if (postForegroundNotification(NotificationId.WebService, notification, specialUseType)) return
        // 系统拒绝进入前台（如后台启动受限）。无法以前台服务存活，主动退出并把状态回传给 UI。
        hostAddress = ""
        postEvent(EventBus.WEB_SERVICE, "")
        FlowEventBus.post(EventBus.WEB_SERVICE, "")
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdWeb)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_web_service_noti)
            .setOngoing(true)
            .setContentTitle(getString(R.string.web_service))
            .setContentText(notificationList.joinToString("\n"))
            .setContentIntent(
                servicePendingIntent<WebService>("copyHostAddress")
            )
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            servicePendingIntent<WebService>(IntentAction.stop)
        )
        return builder.build()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun upTile(active: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            kotlin.runCatching {
                startService<WebTileService> {
                    action = if (active) {
                        IntentAction.start
                    } else {
                        IntentAction.stop
                    }
                }
            }

        }
    }
}
