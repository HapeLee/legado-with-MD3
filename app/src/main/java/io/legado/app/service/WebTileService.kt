package io.legado.app.service

import android.app.Dialog
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.WindowManager.BadTokenException
import androidx.annotation.RequiresApi
import io.legado.app.R
import io.legado.app.constant.IntentAction
import io.legado.app.ui.main.MainIntent
import io.legado.app.utils.printOnDebug


/**
 * web服务快捷开关
 */
@RequiresApi(Build.VERSION_CODES.N)
class WebTileService : TileService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                IntentAction.start -> qsTile?.run {
                    state = Tile.STATE_ACTIVE
                    updateTile()
                }

                IntentAction.stop -> qsTile?.run {
                    state = Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        } catch (e: Exception) {
            e.printOnDebug()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.run {
            state = if (WebService.isRun) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (WebService.isRun) {
            WebService.stop(this)
        } else if (!WebService.hasLocalNetworkPermission(this)) {
            // TileService 无法发起运行时权限申请，转到宿主 Activity 申请并补启服务
            openInApp()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val dialog = Dialog(this, R.style.AppTheme_Transparent)
                dialog.setOnShowListener {
                    try {
                        WebService.startForeground(this)
                    } catch (e: ForegroundServiceStartNotAllowedException) {
                        e.printStackTrace()
                    }
                    dialog.dismiss()
                }
                try {
                    showDialog(dialog)
                } catch (e: BadTokenException) {
                    e.printStackTrace()
                }
            } else {
                WebService.start(this)
            }
        }
    }

    private fun openInApp() {
        // 本地网络权限只在 Android 17 (API 37) 及以上才会缺失，此处必然满足 API 34 要求；
        // 低版本不做处理，避免调用已废弃且对 targetSdk 34+ 抛异常的 Intent 重载。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                0,
                MainIntent.createWebServiceLocalNetworkIntent(this),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

}