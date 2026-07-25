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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import splitties.systemservices.notificationManager

class FullBookPageService : BaseService() {
    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, FullBookPageService::class.java))
        }
    }

    private val notificationBuilder by lazy {
        NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_book_has)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.full_book_pagination))
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<FullBookPageService>(IntentAction.stop),
            )
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch {
            FullBookPaginator.state.collectLatest { state ->
                if (!state.isRunning) {
                    stopSelf()
                    return@collectLatest
                }
                notificationBuilder
                    .setContentTitle("${getString(R.string.full_book_pagination)}: ${ReadBook.book?.name.orEmpty()}")
                    .setContentText("${state.completed} / ${state.total}")
                    .setProgress(state.total, state.completed, state.total == 0)
                notificationManager.notify(NotificationId.FullBookPageService, notificationBuilder.build())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == IntentAction.stop) FullBookPaginator.stop()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun startForegroundNotification() {
        startForeground(NotificationId.FullBookPageService, notificationBuilder.build())
    }
}
