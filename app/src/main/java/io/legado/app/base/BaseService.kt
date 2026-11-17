package io.legado.app.base

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.CallSuper
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.koin.java.KoinJavaComponent.get
import kotlin.coroutines.CoroutineContext

abstract class BaseService : LifecycleService() {

    private val simpleName = this::class.simpleName.toString()
    @Volatile
    private var isForeground = false

    /**
     * 系统拒绝以前台服务方式运行。典型场景：targetSdk 35+ 的 dataSync / mediaProcessing
     * 在任意 24 小时内累计运行已满 6 小时，或当前不允许从后台启动前台服务。
     * 一旦置位，该服务实例不能再进入前台。
     */
    @Volatile
    protected var foregroundStartRejected = false
        private set

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    fun <T> execute(
        scope: CoroutineScope = lifecycleScope,
        context: CoroutineContext = Dispatchers.IO,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        executeContext: CoroutineContext = Dispatchers.Main,
        semaphore: Semaphore? = null,
        block: suspend CoroutineScope.() -> T
    ) = Coroutine.async(scope, context, start, executeContext, semaphore, block)

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        if (!enterForeground()) return
        LifecycleHelp.onServiceCreate(this)
        val localPreferencesRepository: SettingsRepository = get(SettingsRepository::class.java)
        val checked: Boolean = runBlocking {
            localPreferencesRepository.getPreference(
                LocalPreferencesKeys.PERMISSION_CHECKED, false
            ).first()
        }
        if (!checked) {
            runBlocking {
                localPreferencesRepository.updatePreference(
                    LocalPreferencesKeys.PERMISSION_CHECKED, true
                )
            }
            checkPermission()
        }
    }

    @CallSuper
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtils.d(simpleName) {
            "onStartCommand $intent ${intent?.toUri(0)}"
        }
        if (!enterForeground()) return START_NOT_STICKY
        return super.onStartCommand(intent, flags, startId)
    }

    @CallSuper
    override fun onTaskRemoved(rootIntent: Intent?) {
        LogUtils.d(simpleName, "onTaskRemoved")
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        isForeground = false
        LifecycleHelp.onServiceDestroy(this)
    }

    @CallSuper
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        // dataSync / mediaProcessing 的 24 小时运行配额在此时被系统判定用尽，
        // 必须立即退出，否则系统会以 "did not stop within its timeout" 终止进程。
        AppLog.put("前台服务运行时长配额耗尽，服务将退出($simpleName) startId:$startId fgsType:$fgsType")
        stopSelf()
    }

    /**
     * 开启前台服务并发送通知
     */
    open fun startForegroundNotification() {

    }

    /**
     * 进入前台。失败（系统限制、通知配置错误）时停止服务并返回 false，不向调用方抛异常：
     * 调用方可能是 ConnectivityManager 等系统回调线程，异常会直接终止进程。
     */
    private fun enterForeground(): Boolean {
        if (isForeground) return true
        if (foregroundStartRejected) {
            stopSelf()
            return false
        }
        try {
            startForegroundNotification()
            // 兼容仍直接调用 startForeground() 的子类
            isForeground = true
        } catch (e: Exception) {
            onForegroundStartFailed(e)
        }
        if (foregroundStartRejected) {
            // 未能进入前台的后台服务无法存活，系统会在超时后以
            // "did not then call Service.startForeground()" 终止进程。
            stopSelf()
            return false
        }
        return isForeground
    }

    /**
     * 安全地进入前台：系统拒绝时返回 false 而不是抛出。
     *
     * [foregroundServiceType] 为 0 时沿用清单声明的类型。
     */
    protected fun startForegroundSafely(
        id: Int,
        notification: Notification,
        foregroundServiceType: Int = 0
    ): Boolean {
        return try {
            if (foregroundServiceType != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, id, notification, foregroundServiceType)
            } else {
                startForeground(id, notification)
            }
            isForeground = true
            true
        } catch (e: Exception) {
            onForegroundStartFailed(e)
            false
        }
    }

    /**
     * 发布前台通知：尚未在前台时进入前台，已在前台时只更新通知内容。
     *
     * 服务已在运行时不能再调用 [startForeground]：Android 15 起同一 UID 的 dataSync /
     * mediaProcessing 共享「任意 24 小时累计 6 小时」配额，配额耗尽后重复调用会抛
     * [ForegroundServiceStartNotAllowedException]。
     */
    protected fun postForegroundNotification(
        id: Int,
        notification: Notification,
        foregroundServiceType: Int = 0
    ): Boolean {
        if (isForeground) {
            notificationManager.notify(id, notification)
            return true
        }
        return startForegroundSafely(id, notification, foregroundServiceType)
    }

    private fun onForegroundStartFailed(e: Exception) {
        foregroundStartRejected = true
        val limitedBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            e is ForegroundServiceStartNotAllowedException
        AppLog.put(
            if (limitedBySystem) {
                "系统拒绝启动前台服务($simpleName)：该类型在 24 小时内累计运行已满上限" +
                    "或当前不允许后台启动，服务将退出"
            } else {
                "前台服务启动失败，服务将退出($simpleName)"
            },
            e,
            true
        )
    }

    /**
     * 检测通知权限和后台权限
     */
    private fun checkPermission() {
        PermissionsCompat.Builder()
            .addPermissions(Permissions.POST_NOTIFICATIONS)
            .rationale(R.string.notification_permission_rationale)
            .request()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionsCompat.Builder()
                .addPermissions(Permissions.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .rationale(R.string.ignore_battery_permission_rationale)
                .request()
        }
    }
}
