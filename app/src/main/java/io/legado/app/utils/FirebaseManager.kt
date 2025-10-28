package io.legado.app.utils

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import io.legado.app.help.config.AppConfig

object FirebaseManager {

    var isEnabled: Boolean
        get() = AppConfig.firebaseEnable
        private set(value) {
            AppConfig.firebaseEnable = value
        }

    fun init(context: Context) {
        applyState(context, isEnabled)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        applyState(context, enabled)
    }

    private fun applyState(context: Context, enabled: Boolean) {
        if (enabled) {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(true)
        } else {
            try {
                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false)
                    FirebaseApp.getInstance().delete()
                }
            } catch (_: Exception) {
                // 忽略异常
            }
        }
        isEnabled = enabled
    }
}