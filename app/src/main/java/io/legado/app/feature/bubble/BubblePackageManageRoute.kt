package io.legado.app.feature.bubble

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@Composable
fun BubblePackageManageRoute(
    onBackClick: () -> Unit,
    viewModel: BubblePackageManageViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    // 导入前先落到缓存文件：包解析只需要一个普通 File。
                    val target = File(
                        context.cacheDir,
                        "bubble_import_${System.currentTimeMillis()}.zip"
                    )
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("open stream failed")
                    target
                }
            }.getOrNull() ?: return@launch
            viewModel.importZip(file)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BubblePackageManageEffect.Message -> context.toastOnUi(effect.textRes)
                is BubblePackageManageEffect.ShareFile ->
                    context.share(effect.file, "application/zip")
                BubblePackageManageEffect.PickImportFile ->
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            }
        }
    }

    BubblePackageManageScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}
