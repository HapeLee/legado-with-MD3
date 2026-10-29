package io.legado.app.feature.mangatranslation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.gateway.*
import org.koin.compose.viewmodel.koinViewModel

/** Embedded route owns only the system picker; IO and state stay behind the gateway. */
@Composable
fun MangaModelsRoute() {
    val viewModel = koinViewModel<MangaModelsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var importing by rememberSaveable { mutableStateOf<MangaImageModel?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importing?.let { model -> if (uri != null) viewModel.onIntent(MangaModelsIntent.Import(model, uri.toString())) }
        importing = null
    }
    MangaModelsContent(state, viewModel::onIntent) { model ->
        importing = model
        picker.launch(arrayOf("application/octet-stream", "application/onnx", "*/*"))
    }
}

@Composable
private fun MangaModelsContent(state: MangaModelsUiState, onIntent: (MangaModelsIntent) -> Unit, onImport: (MangaImageModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.manga_models_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.manga_models_notice), style = MaterialTheme.typography.bodySmall)
        state.items.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val title = when (item.model) {
                        MangaImageModel.CTD -> R.string.manga_models_ctd
                        MangaImageModel.BUBBLE -> R.string.manga_models_bubble
                        MangaImageModel.AOT -> R.string.manga_models_aot
                    }
                    Text(stringResource(title))
                    Text(stringResource(when (item.status) {
                        MangaModelStatus.MISSING -> R.string.manga_models_missing
                        MangaModelStatus.INSTALLING -> R.string.manga_models_installing
                        MangaModelStatus.VERIFYING -> R.string.manga_models_verifying
                        MangaModelStatus.READY -> R.string.manga_models_ready
                        MangaModelStatus.DELETING -> R.string.manga_models_deleting
                        MangaModelStatus.FAILED -> R.string.manga_models_failed
                    }), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.manga_models_bytes, item.bytes / 1024), style = MaterialTheme.typography.bodySmall)
                    item.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (item.model == MangaImageModel.AOT) Text(stringResource(R.string.manga_models_aot_pending), style = MaterialTheme.typography.bodySmall)
                    run {
                        val busy = item.status in setOf(MangaModelStatus.INSTALLING, MangaModelStatus.VERIFYING, MangaModelStatus.DELETING)
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!busy) {
                                if (item.model != MangaImageModel.AOT) TextButton(onClick = { onIntent(MangaModelsIntent.Download(item.model)) }) { Text(stringResource(R.string.manga_models_download)) }
                                TextButton(onClick = { onImport(item.model) }) { Text(stringResource(R.string.manga_models_import)) }
                            }
                            if (busy && item.status != MangaModelStatus.DELETING) TextButton(onClick = { onIntent(MangaModelsIntent.Cancel(item.model)) }) { Text(stringResource(R.string.cancel)) }
                            if (item.bytes > 0 && item.status != MangaModelStatus.DELETING) TextButton(onClick = { onIntent(MangaModelsIntent.Delete(item.model)) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
}
