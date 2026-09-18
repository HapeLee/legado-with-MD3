package io.legado.app.ui.about

import android.content.Intent
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.help.CrashHandler
import io.legado.app.ui.main.MainIntent
import io.legado.app.utils.sendToClip

class CrashReportActivity : BaseComposeActivity() {

    private val crashText: String by lazy {
        CrashHandler.readCrashLog(
            intent.getStringExtra(CrashHandler.EXTRA_CRASH_FILE_NAME)
        ).orEmpty()
    }

    @Composable
    override fun Content() {
        CrashReportScreen(
            errorText = crashText,
            onCopy = { sendToClip(crashText) },
            onRestart = { restartApp() },
            onClose = { finish() }
        )
    }

    private fun restartApp() {
        // 图标切换后 MainActivity 组件处于禁用态，须解析当前启用的 launcher 组件
        startActivity(
            MainIntent.createLauncherIntent(this)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
