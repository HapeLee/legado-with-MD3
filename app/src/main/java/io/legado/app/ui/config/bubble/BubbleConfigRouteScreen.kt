package io.legado.app.ui.config.bubble

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.help.config.BubblePackageManager
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun BubbleConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: BubbleConfigViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingExport by remember { mutableStateOf<BubblePackageManager.Entry?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.onIntent(BubbleConfigIntent.Import(it.toString())) }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val entry = pendingExport
        pendingExport = null
        if (uri != null && entry != null) {
            viewModel.onIntent(BubbleConfigIntent.Export(entry, uri.toString()))
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collectLatest { effect ->
            val message = when (effect) {
                is BubbleConfigEffect.Message -> effect.text
                is BubbleConfigEffect.Exported -> effect.message
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    BubbleConfigScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
        onImportZip = { types ->
            importLauncher.launch(types)
        },
        onExportZip = { entry ->
            pendingExport = entry
            exportLauncher.launch("${entry.dirName}.zip")
        },
    )
}
