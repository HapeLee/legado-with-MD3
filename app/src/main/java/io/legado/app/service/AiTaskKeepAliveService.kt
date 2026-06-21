package io.legado.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.NotificationId
import io.legado.app.help.ai.AiTaskKeepAlive
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.activityPendingIntent
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager

class AiTaskKeepAliveService : BaseService() {

    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:AiTaskKeepAliveService")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopSelf()
                return super.onStartCommand(intent, flags, startId)
            }
            ACTION_REFRESH -> {
                syncWakeLock()
                upNotification()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun startForegroundNotification() {
        syncWakeLock()
        startForeground(NotificationId.AiTaskService, createNotification().build())
    }

    private fun upNotification() {
        syncWakeLock()
        notificationManager.notify(NotificationId.AiTaskService, createNotification().build())
    }

    private fun syncWakeLock() {
        if (AiTaskKeepAlive.activeCount > 0) {
            if (!wakeLock.isHeld) {
                wakeLock.acquire()
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private fun createNotification(): NotificationCompat.Builder {
        val count = AiTaskKeepAlive.activeCount.coerceAtLeast(1)
        val tasks = AiTaskKeepAlive.activeTaskSnapshot()
        val text = if (count > 1) {
            tasks.take(3)
                .joinToString("\n") { "${it.title}：${it.displayText}" }
                .ifBlank { "还有 $count 个 AI 任务运行中" }
        } else {
            AiTaskKeepAlive.content
        }
        val title = if (count > 1) "AI任务处理中 · $count 个" else AiTaskKeepAlive.title
        return NotificationCompat.Builder(this, AppConst.channelIdAiTask)
            .setSmallIcon(R.drawable.ic_web_service_noti)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(activityPendingIntent<MainActivity>("aiTask"))
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
        notificationManager.cancel(NotificationId.AiTaskService)
    }

    companion object {
        const val ACTION_REFRESH = "io.legado.app.action.AI_TASK_REFRESH"
        const val ACTION_STOP = "io.legado.app.action.AI_TASK_STOP"
    }
}
