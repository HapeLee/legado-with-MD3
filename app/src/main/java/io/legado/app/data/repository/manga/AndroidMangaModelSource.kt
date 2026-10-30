package io.legado.app.data.repository.manga

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import io.legado.app.domain.gateway.MangaModelSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only the Android host adapter knows document URIs and ContentResolver. */
class AndroidMangaModelSource(private val application: Application, private val uri: Uri) : MangaModelSource {
    override val name: String = runCatching { application.contentResolver.query(uri,
        arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    override suspend fun transfer(write: (ByteArray, Int) -> Unit) {
        requireNotNull(application.contentResolver.openInputStream(uri)).use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                write(buffer, count)
            }
        }
    }
}
