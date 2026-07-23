package io.legado.app.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.model.FullBookPaginator
import io.legado.app.model.ReadBook
import io.legado.app.utils.servicePendingIntent
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

        fun update(context: Context, progress: Int, total: Int) {
            val intent = Intent(context, FullBookPageService::class.java)
            intent.action = "update"
            intent.putExtra("progress", progress)
            intent.putExtra("total", total)
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
                servicePendingIntent<FullBookPageService>(IntentAction.stop)
            )
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    override fun onCreate() {
        super.onCreate()
        isRun = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.start -> {
                updateNotification(0, 0)
            }
            IntentAction.stop -> {
                stopSelf()
            }
            "update" -> {
                val progress = intent.getIntExtra("progress", 0)
                val total = intent.getIntExtra("total", 0)
                updateNotification(progress, total)
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
