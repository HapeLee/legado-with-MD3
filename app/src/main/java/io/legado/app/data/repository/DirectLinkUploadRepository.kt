package io.legado.app.data.repository

import io.legado.app.help.DirectLinkUpload

/**
 * [UploadRepository] 的 Android 实现（直链上传）。
 *
 * 接口已下沉 `:core:data`（见 `UploadRepository.kt`），实现依赖 app 侧的
 * `help.DirectLinkUpload`（okhttp + 配置存储），故留在 `:app`，由 Koin 绑定。
 */
class DirectLinkUploadRepository : UploadRepository {

    override suspend fun upload(
        fileName: String,
        file: Any,
        contentType: String
    ): String {
        return DirectLinkUpload.upLoad(
            fileName = fileName,
            file = file,
            contentType = contentType
        )
    }

}
