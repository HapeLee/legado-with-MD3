package io.legado.app.domain.model.manga

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * 擦字实现分两类：
 * - [BUILTIN]：内置的纯像素绘制（`MangaPageComposer`），不加载任何推理运行时，`core`／`full` 都有；
 * - [MI_GAN]／[LAMA]：ONNX 模型擦字，权重按需下载，只有带运行时的 `full` 可用。
 *
 * 组成 SMART 的其余步骤可能另有依赖（漫画增强版的气泡检测、Paddle OCR、长图分段的 ML Kit），
 * 那些属于各自能力，不归这个枚举表达。
 */
@Serializable
enum class MangaInpaintEngine {
    /** 旧存档里写作 `LOCAL`；改名后仍要能读回，否则整个设置面板会解析失败。 */
    @JsonNames("LOCAL")
    BUILTIN,
    MI_GAN, LAMA;

    /** 是否由 ONNX 模型擦字；[BUILTIN] 不碰任何运行时。[downloadable] 只是它面向模型管理的一面。 */
    val usesModel: Boolean get() = this != BUILTIN
    val downloadable: Boolean get() = usesModel
    /** Bump when weights, tensor contract or preprocessing change. */
    val cacheIdentity: String get() = "$name-v1"
}

enum class MangaInpaintModelPhase { CHECKING, MISSING, DOWNLOADING, READY, FAILED }

@Stable
data class MangaInpaintModelState(
    val phase: MangaInpaintModelPhase = MangaInpaintModelPhase.CHECKING,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
)
