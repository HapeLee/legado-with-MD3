package io.legado.app.feature.bookexport

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.manga.MangaExportFormat

@Stable
data class BookExportOptions(
    val exportUseReplace: Boolean = true,
    val enableCustomExport: Boolean = false,
    val exportNoChapterName: Boolean = false,
    val exportToWebDav: Boolean = false,
    val exportPictureFile: Boolean = false,
    val parallelExportBook: Boolean = false,
    val exportType: Int = 0,
    val exportCharset: String = "UTF-8",
    val bookExportFileName: String? = null,
    val episodeExportFileName: String = "",
    val mangaExportFormat: MangaExportFormat = MangaExportFormat.Cbz,
) {
    val isCustomEpubExportEnabled: Boolean
        get() = enableCustomExport && exportType == 1

    fun usesCustomEpubExport(isManga: Boolean): Boolean = !isManga && isCustomEpubExportEnabled

    fun exportTypeFor(isManga: Boolean): String =
        if (isManga) mangaExportFormat.value else if (exportType == 1) "epub" else "txt"
}
