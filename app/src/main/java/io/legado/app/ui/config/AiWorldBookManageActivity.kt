package io.legado.app.ui.config

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityAiWorldBookManageBinding
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.ai.compose.AiWorldBookImportPayload
import io.legado.app.ui.main.ai.compose.AiWorldBookManageRoute
import io.legado.app.ui.widget.compose.showComposeTextInputDialog
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiWorldBookManageActivity : BaseActivity<ActivityAiWorldBookManageBinding>(
    fullScreen = false,
    imageBg = false
) {

    override val binding by viewBinding(ActivityAiWorldBookManageBinding::inflate)
    private var importPayload by mutableStateOf<AiWorldBookImportPayload?>(null)
    private var importRequestId = 0L
    private val importWorldBook = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            lifecycleScope.launch {
                kotlin.runCatching {
                    uri.readText(this@AiWorldBookManageActivity)
                }.onSuccess(::emitImportPayload)
                    .onFailure {
                        toastOnUi(it.localizedMessage ?: "世界书文件读取失败")
                    }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeRoot.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeRoot.setContent {
            AiWorldBookManageRoute(
                initialTargetType = intent.getStringExtra(EXTRA_TARGET_TYPE).orEmpty(),
                initialTargetKey = intent.getStringExtra(EXTRA_TARGET_KEY).orEmpty(),
                importPayload = importPayload,
                onImportConsumed = { importPayload = null },
                onRequestImportLocal = ::launchImportFile,
                onRequestImportNetwork = ::showImportUrlDialog,
                onBack = ::finish
            )
        }
    }

    private fun launchImportFile() {
        importWorldBook.launch {
            mode = HandleFileContract.FILE
            title = "导入世界书"
            allowExtensions = arrayOf("json")
        }
    }

    private fun showImportUrlDialog() {
        showComposeTextInputDialog(
            title = "网络导入世界书",
            hint = "https://...",
            onPositive = { value ->
                val url = value.trim()
                if (url.isNotEmpty()) importWorldBookFromUrl(url)
            }
        )
    }

    private fun importWorldBookFromUrl(url: String) {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    okHttpClient.newCallResponseBody { url(url) }.use { it.string() }
                }
            }.onSuccess(::emitImportPayload)
                .onFailure {
                    toastOnUi(it.localizedMessage ?: "世界书下载失败")
                }
        }
    }

    private fun emitImportPayload(raw: String) {
        if (raw.isBlank()) {
            toastOnUi("世界书内容为空")
            return
        }
        importPayload = AiWorldBookImportPayload(++importRequestId, raw)
    }

    companion object {
        const val EXTRA_TARGET_TYPE = "targetType"
        const val EXTRA_TARGET_KEY = "targetKey"
    }
}
