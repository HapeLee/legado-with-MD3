package io.legado.app.domain.gateway

import io.legado.app.domain.model.manga.MangaVisionModelState
import kotlinx.coroutines.flow.StateFlow

/**
 * 气泡检测权重（RT-DETR）的管理入口。
 *
 * 这些权重不属于任何 OCR 引擎：本地排版分组与 AI 锚点都用它，ML Kit 与 Paddle 两条路径共享。
 * 缺失时只损失排版分组质量（降级为按文字块分组），因此 [refresh] 必须静默，下载/删除才报错。
 */
interface MangaVisionModelGateway {
    val state: StateFlow<MangaVisionModelState>
    suspend fun refresh()
    suspend fun download()
    suspend fun delete()
}
