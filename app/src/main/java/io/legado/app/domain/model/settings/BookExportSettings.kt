package io.legado.app.domain.model.settings

import io.legado.app.domain.model.manga.MangaExportFormat

data class BookExportSettings(
    val bookExportFileName: String? = null,
    val episodeExportFileName: String = "",
    val exportCharset: String = "UTF-8",
    val exportUseReplace: Boolean = true,
    val exportToWebDav: Boolean = false,
    val exportNoChapterName: Boolean = false,
    val enableCustomExport: Boolean = false,
    val exportType: Int = 0,
    val exportPictureFile: Boolean = false,
    val parallelExportBook: Boolean = false,
    val mangaExportFormat: MangaExportFormat = MangaExportFormat.Cbz,
)
