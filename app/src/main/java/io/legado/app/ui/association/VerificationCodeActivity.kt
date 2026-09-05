package io.legado.app.ui.association

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.legado.app.base.BaseComposeActivity
import io.legado.app.help.source.SourceVerificationHelp

/** Retains the legacy Intent contract while the dialog content is Compose-owned. */
class VerificationCodeActivity : BaseComposeActivity(transparent = true) {

    private val viewModel by viewModels<VerificationCodeViewModel>()
    private val imageUrl get() = intent.getStringExtra("imageUrl")
    private val sourceOrigin get() = intent.getStringExtra("sourceOrigin").orEmpty()
    private val sourceName get() = intent.getStringExtra("sourceName").orEmpty()

    @Composable
    override fun Content() {
        val url = imageUrl
        if (url == null) {
            LaunchedEffect(Unit) { finish() }
            return
        }
        LaunchedEffect(Unit) { viewModel.initData(intent.extras ?: return@LaunchedEffect) }
        VerificationCodeDialog(
            imageUrl = url,
            sourceOrigin = sourceOrigin,
            sourceName = sourceName,
            onSubmit = { code ->
                SourceVerificationHelp.setResult(sourceOrigin, code)
                finish()
            },
            onDisableSource = { viewModel.disableSource(::finish) },
            onDeleteSource = { viewModel.deleteSource(::finish) },
            onDismissRequest = ::finish,
        )
    }

    override fun onDestroy() {
        SourceVerificationHelp.checkResult(sourceOrigin)
        super.onDestroy()
    }
}
