package io.legado.app.exception

import io.legado.app.R
import splitties.init.appCtx
import java.io.FileNotFoundException

internal class LocalMangaDirectoryUnavailableException(cause: Exception) :
    FileNotFoundException(appCtx.getString(R.string.local_manga_directory_unavailable)) {
    init {
        initCause(cause)
    }
}
