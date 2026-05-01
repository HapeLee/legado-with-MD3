package io.legado.app.ui.about

import android.content.Intent
import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity
import io.legado.app.help.CrashHandler
import io.legado.app.ui.main.MainActivity
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
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
