package io.legado.app.domain.model.manga

import androidx.compose.runtime.Stable

/** 气泡检测权重的安装状态；阶段语义与 [MangaOcrModelPhase] 一致。 */
@Stable
data class MangaVisionModelState(
    val phase: MangaOcrModelPhase = MangaOcrModelPhase.CHECKING,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
)
