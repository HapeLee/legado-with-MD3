package io.legado.app.data.repository.manga.engine

import io.legado.app.domain.gateway.MangaInpaintModelGateway
import io.legado.app.domain.gateway.MangaOcrModelGateway
import io.legado.app.domain.gateway.MangaVisionModelGateway
import io.legado.app.domain.model.manga.MangaInpaintEngine
import io.legado.app.domain.model.manga.MangaInpaintModelState
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaOcrModelPhase
import io.legado.app.domain.model.manga.MangaOcrModelState
import io.legado.app.domain.model.manga.MangaVisionModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Model management for products without a local runtime.
 *
 * [MangaOcrModelGateway.refresh] stays silent because the translation screen observes it
 * unconditionally on entry; only actions that would download or delete weights report the missing
 * capability, so ordinary reading and cloud flows never surface an error they cannot act on.
 */
internal class UnavailableMangaOcrModelGateway(private val message: String) : MangaOcrModelGateway {
    private val mutableState = MutableStateFlow(emptyMap<MangaLocalOcrEngine, MangaOcrModelState>())
    override val state: StateFlow<Map<MangaLocalOcrEngine, MangaOcrModelState>> = mutableState.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun download(engine: MangaLocalOcrEngine): Unit = unavailable()

    override suspend fun delete(engine: MangaLocalOcrEngine): Unit = unavailable()

    private fun unavailable(): Nothing = throw MangaLocalEngineUnavailableException(message)
}

/** Inpainting counterpart of [UnavailableMangaOcrModelGateway]. */
internal class UnavailableMangaInpaintModelGateway(private val message: String) : MangaInpaintModelGateway {
    private val mutableState = MutableStateFlow(emptyMap<MangaInpaintEngine, MangaInpaintModelState>())
    override val state: StateFlow<Map<MangaInpaintEngine, MangaInpaintModelState>> = mutableState.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun download(engine: MangaInpaintEngine): Unit = unavailable()

    override suspend fun delete(engine: MangaInpaintEngine): Unit = unavailable()

    private fun unavailable(): Nothing = throw MangaLocalEngineUnavailableException(message)
}

/** 轻量产品没有气泡检测权重：状态恒为 MISSING（排版按文字块分组），下载与删除明确报能力缺失。 */
internal class UnavailableMangaVisionModelGateway(private val message: String) : MangaVisionModelGateway {
    private val mutableState = MutableStateFlow(MangaVisionModelState(MangaOcrModelPhase.MISSING))
    override val state: StateFlow<MangaVisionModelState> = mutableState.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun download(): Unit = unavailable()

    override suspend fun delete(): Unit = unavailable()

    private fun unavailable(): Nothing = throw MangaLocalEngineUnavailableException(message)
}
