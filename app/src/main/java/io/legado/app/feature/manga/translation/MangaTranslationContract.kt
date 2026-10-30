package io.legado.app.feature.manga.translation

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.manga.MangaCloudOcrConfig
import io.legado.app.domain.model.manga.MangaCloudOcrProvider
import io.legado.app.domain.model.manga.MangaInpaintEngine
import io.legado.app.domain.model.manga.MangaInpaintModelState
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaOcrModelState
import io.legado.app.domain.model.manga.MangaTranslationAiProgress
import io.legado.app.domain.model.manga.MangaTranslationGlobalSettings
import io.legado.app.domain.model.manga.MangaTranslationPage
import io.legado.app.domain.model.manga.MangaTranslationProgress
import io.legado.app.domain.model.manga.MangaTranslationSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Stable
data class MangaTranslationModelUi(val id: String, val name: String, val imageEditing: Boolean, val text: Boolean = true, val vision: Boolean = false)

@Stable
data class MangaTranslatedImageUi(val path: String, val revision: String, val canRecompose: Boolean = false)

@Stable
data class MangaTranslationUiState(
    val open: Boolean = false,
    val loaded: Boolean = false,
    val settings: MangaTranslationSettings = MangaTranslationSettings(),
    val globalSettings: MangaTranslationGlobalSettings = MangaTranslationGlobalSettings(),
    val globalMode: Boolean = false,
    val useGlobalSettings: Boolean = false,
    val models: ImmutableList<MangaTranslationModelUi> = persistentListOf(),
    val images: ImmutableMap<String, MangaTranslatedImageUi> = persistentMapOf(),
    val busy: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val progress: MangaTranslationProgress = MangaTranslationProgress(),
    val ai: MangaTranslationAiProgress? = null,
    val diagnosticPath: String? = null,
    val elapsedSeconds: Int = 0,
    val phaseStartedAtSeconds: Int = 0,
    val error: String? = null,
    val glossaryDraft: String = "",
    val currentPageAvailable: Boolean = false,
    val currentPageKey: String? = null,
    val chapterPages: Int = 0,
    val chapterPageKeys: ImmutableList<String> = persistentListOf(),
    val ocrModels: ImmutableMap<MangaLocalOcrEngine, MangaOcrModelState> = persistentMapOf(),
    val ocrOperation: MangaLocalOcrEngine? = null,
    val inpaintModels: ImmutableMap<MangaInpaintEngine, MangaInpaintModelState> = persistentMapOf(),
    val inpaintOperation: MangaInpaintEngine? = null,
    val cloudProvider: MangaCloudOcrProvider? = null,
    val cloudDraft: MangaCloudOcrConfig = MangaCloudOcrConfig(),
    val cloudLoading: Boolean = false,
    val cloudSaving: Boolean = false,
    val cloudSaved: Boolean = false,
) {
    val chapterTranslated: Boolean get() = chapterPageKeys.isNotEmpty() && chapterPageKeys.all { it in images }
    val currentPageCanRecompose: Boolean get() = images[currentPageKey]?.canRecompose == true
}

sealed interface MangaTranslationIntent {
    data object BindGlobal : MangaTranslationIntent
    data class SetUseGlobalSettings(val enabled: Boolean) : MangaTranslationIntent
    data class Bind(val bookUrl: String, val pages: List<MangaTranslationPage>, val currentKey: String?, val active: Boolean,
        val loadedPages: List<MangaTranslationPage> = emptyList(), val sourcePages: List<MangaTranslationPage> = pages) : MangaTranslationIntent
    data object Open : MangaTranslationIntent
    data object Close : MangaTranslationIntent
    data class ChangeSettings(val transform: (MangaTranslationSettings) -> MangaTranslationSettings) : MangaTranslationIntent
    data class ChangeGlobalSettings(val transform: (MangaTranslationGlobalSettings) -> MangaTranslationGlobalSettings) : MangaTranslationIntent
    data class EditGlossary(val text: String) : MangaTranslationIntent
    data object SaveGlossary : MangaTranslationIntent
    data class Translate(val chapter: Boolean = false, val force: Boolean = false, val page: MangaTranslationPage? = null) : MangaTranslationIntent
    data class Recompose(val chapter: Boolean = false, val page: MangaTranslationPage? = null) : MangaTranslationIntent
    data object Cancel : MangaTranslationIntent
    data object Deactivate : MangaTranslationIntent
    data class ClearPage(val page: MangaTranslationPage) : MangaTranslationIntent
    data object Clear : MangaTranslationIntent
    data class DownloadOcr(val engine: MangaLocalOcrEngine) : MangaTranslationIntent
    data class CancelOcrDownload(val engine: MangaLocalOcrEngine) : MangaTranslationIntent
    data class DeleteOcr(val engine: MangaLocalOcrEngine) : MangaTranslationIntent
    data class DownloadInpaint(val engine: MangaInpaintEngine) : MangaTranslationIntent
    data class CancelInpaintDownload(val engine: MangaInpaintEngine) : MangaTranslationIntent
    data class DeleteInpaint(val engine: MangaInpaintEngine) : MangaTranslationIntent
    data class EditCloudOcr(val transform: (MangaCloudOcrConfig) -> MangaCloudOcrConfig) : MangaTranslationIntent
    data object SaveCloudOcr : MangaTranslationIntent
}
