package io.legado.app.feature.mangatranslation

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
data class MangaModelItem(val model: MangaImageModel, val status: MangaModelStatus, val bytes: Long, val total: Long, val error: String?)
@Stable
data class MangaModelsUiState(val items: ImmutableList<MangaModelItem> = persistentListOf())
sealed interface MangaModelsIntent {
    data class Download(val model: MangaImageModel) : MangaModelsIntent
    data class Import(val model: MangaImageModel, val locator: String) : MangaModelsIntent
    data class Delete(val model: MangaImageModel) : MangaModelsIntent
    data class Cancel(val model: MangaImageModel) : MangaModelsIntent
}

class MangaModelsViewModel(private val gateway: MangaImageModelGateway) : ViewModel() {
    private val mutableState = MutableStateFlow(MangaModelsUiState())
    val state = mutableState.asStateFlow()
    private val jobs = mutableMapOf<MangaImageModel, Job>()
    init {
        viewModelScope.launch { gateway.states.collect { states ->
            mutableState.value = MangaModelsUiState(MangaImageModel.entries.map { model ->
                val s = states.getValue(model)
                MangaModelItem(model, s.status, s.bytes, s.total, s.error)
            }.toImmutableList())
        } }
    }
    fun onIntent(intent: MangaModelsIntent) {
        val model = when (intent) {
            is MangaModelsIntent.Download -> intent.model
            is MangaModelsIntent.Import -> intent.model
            is MangaModelsIntent.Delete -> intent.model
            is MangaModelsIntent.Cancel -> intent.model
        }
        val previous = jobs[model]
        if (intent is MangaModelsIntent.Cancel) { previous?.cancel(); return }
        if (intent !is MangaModelsIntent.Delete && previous?.isActive == true) return
        if (intent is MangaModelsIntent.Delete) previous?.cancel()
        jobs[model] = viewModelScope.launch {
            previous?.join()
            when (intent) {
                is MangaModelsIntent.Download -> gateway.download(model)
                is MangaModelsIntent.Import -> gateway.import(model, intent.locator)
                is MangaModelsIntent.Delete -> gateway.delete(model)
                is MangaModelsIntent.Cancel -> Unit
            }
        }
    }
}
