package io.legado.app.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.model.FullBookPaginator
import io.legado.app.model.ReadBook
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.launch
import splitties.systemservices.notificationManager

class FullBookPageService : BaseService() {

    companion object {
        var isRun = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FullBookPageService::class.java)
            intent.action = IntentAction.start
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FullBookPageService::class.java)
            intent.action = IntentAction.stop
            context.startService(intent)
        }
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_book_has)
            .setOngoing(true)
            .setContentTitle(getString(R.string.full_book_pagination))
            .setContentText(getString(R.string.full_book_paginating))
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<FullBookPageService>(IntentAction.stop),
            )
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    override fun onCreate() {
        super.onCreate()
        isRun = true
        lifecycleScope.launch {
            FullBookPaginator.state.collect { state ->
                if (state.isRunning) {
                    updateNotification(state.progress, state.total)
                } else {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.stop -> {
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRun = false
        FullBookPaginator.stop()
    }

    override fun startForegroundNotification() {
        startForeground(NotificationId.FullBookPageService, notificationBuilder.build())
    }

    fun updateNotification(progress: Int, total: Int) {
        val bookName = ReadBook.book?.name ?: ""
        notificationBuilder.setContentTitle("${getString(R.string.full_book_pagination)}: $bookName")
        if (total > 0) {
            notificationBuilder.setProgress(total, progress, false)
            notificationBuilder.setContentText("$progress / $total")
        } else {
            notificationBuilder.setProgress(0, 0, true)
            notificationBuilder.setContentText(getString(R.string.full_book_paginating))
        }
        notificationManager.notify(NotificationId.FullBookPageService, notificationBuilder.build())
    }
}
