package io.legado.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.domain.gateway.BookSourceCheckGateway
import io.legado.app.domain.usecase.StartBookSourceCheckUseCase
import io.legado.app.help.IntentData
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.startForegroundServiceCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager

class BookSourceCheckService : BaseService() {

    companion object {
        private const val EXTRA_REQUEST_KEY = "bookSourceCheckRequestKey"

        fun start(context: Context, ids: Set<String>, keyword: String) {
            val requestKey = IntentData.put(CheckRequest(ids, keyword))
            val intent = Intent(context, BookSourceCheckService::class.java).apply {
                action = IntentAction.start
                putExtra(EXTRA_REQUEST_KEY, requestKey)
            }
            context.startForegroundServiceCompat(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BookSourceCheckService::class.java).apply {
                    action = IntentAction.stop
                }
            )
        }
    }

    private data class CheckRequest(
        val ids: Set<String>,
        val keyword: String,
    )

    private val checkGateway by inject<BookSourceCheckGateway>()
    private val startBookSourceCheck by inject<StartBookSourceCheckUseCase>()
    private var checkJob: Job? = null
    private var finishedNotificationShown = false
    private var wakeLockAcquired = false

    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "legado:BookSourceCheckService",
        ).apply { setReferenceCounted(false) }
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, AppConst.channelIdBookSourceCheck)
            .setSmallIcon(R.drawable.ic_network_check)
            .setContentTitle(getString(R.string.check_book_source))
            .setContentIntent(
                activityPendingIntent(
                    MainActivity.createBookSourceManageIntent(this),
                    "bookSourceCheck",
                )
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<BookSourceCheckService>(IntentAction.stop),
            )
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch {
            checkGateway.state.collectLatest { state ->
                if (state.isRunning) {
                    notificationBuilder
                        .setContentText(
                            getString(
                                R.string.progress_show,
                                state.currentSourceName,
                                state.completed,
                                state.total,
                            )
                        )
                        .setProgress(state.total, state.completed, false)
                    notificationManager.notify(
                        NotificationId.BookSourceCheckService,
                        notificationBuilder.build(),
                    )
                }
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            IntentAction.start -> {
                val request = IntentData.get<CheckRequest>(
                    intent.getStringExtra(EXTRA_REQUEST_KEY)
                )
                if (request == null) {
                    stopWithoutResultNotification()
                } else if (checkJob?.isActive != true && !checkGateway.state.value.isRunning) {
                    finishedNotificationShown = false
                    wakeLock.acquire()
                    wakeLockAcquired = true
                    checkJob = lifecycleScope.launch {
                        try {
                            startBookSourceCheck(request.ids, request.keyword)
                        } finally {
                            showFinishedNotification()
                            releaseWakeLock()
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                    }
                }
            }

            IntentAction.stop -> {
                if (checkJob?.isActive == true) checkJob?.cancel()
                else stopWithoutResultNotification()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        if (!finishedNotificationShown) {
            notificationManager.cancel(NotificationId.BookSourceCheckService)
        }
        super.onDestroy()
    }

    override fun startForegroundNotification() {
        notificationBuilder
            .setContentText(getString(R.string.service_starting))
            .setProgress(0, 0, true)
        startForeground(
            NotificationId.BookSourceCheckService,
            notificationBuilder.build(),
        )
    }

    private fun showFinishedNotification() {
        val state = checkGateway.state.value
        val content = if (state.cancelledCount > 0) {
            getString(
                R.string.book_source_check_cancelled,
                state.succeededCount,
                state.failedCount,
                state.cancelledCount,
            )
        } else {
            getString(
                R.string.book_source_check_completed,
                state.succeededCount,
                state.failedCount,
            )
        }
        notificationBuilder
            .clearActions()
            .setContentText(content)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
        notificationManager.notify(
            NotificationId.BookSourceCheckService,
            notificationBuilder.build(),
        )
        finishedNotificationShown = true
    }

    private fun stopWithoutResultNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NotificationId.BookSourceCheckService)
        stopSelf()
    }

    private fun releaseWakeLock() {
        if (wakeLockAcquired && wakeLock.isHeld) wakeLock.release()
        wakeLockAcquired = false
    }
}
