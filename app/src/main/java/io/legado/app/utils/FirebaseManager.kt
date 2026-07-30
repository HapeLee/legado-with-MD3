package io.legado.app.utils

import android.content.Context
import io.legado.app.help.config.AppConfig

object FirebaseManager {

    val isEnabled: Boolean
        get() = AppConfig.firebaseEnable

    fun init(context: Context) {
        applyState(context, isEnabled)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        applyState(context, enabled)
    }

    private fun applyState(context: Context, enabled: Boolean) {
        if (enabled) {

        } else {
            try {

            } catch (_: Exception) {
                // 忽略异常
            }
        }
    }
}
