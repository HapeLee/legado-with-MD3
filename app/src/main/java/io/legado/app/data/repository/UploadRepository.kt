package io.legado.app.data.repository

import io.legado.app.help.DirectLinkUpload

interface UploadRepository {
    suspend fun upload(
        fileName: String,
        file: Any,
        contentType: String
    ): String
}

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
