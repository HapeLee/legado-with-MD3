package io.legado.app.domain.model

import java.io.InputStream

/**
 * 封面图片的延迟 IO 入口。`openStream` 依赖 `java.io.InputStream`（平台 IO），
 * 故保留在 app 侧，不与纯数据的 [CoverAlbum]/[CoverAlbumImage]/[CoverAlbumSelection]
 * 一起下沉 core:model。
 */
data class CoverAlbumImageInput(
    val displayName: String,
    val openStream: () -> InputStream,
)
