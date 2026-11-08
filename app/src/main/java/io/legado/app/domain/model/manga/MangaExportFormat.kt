package io.legado.app.domain.model.manga

enum class MangaExportFormat(val value: String) {
    Folder("folder"),
    Cbz("cbz");

    companion object {
        fun fromValue(value: String?): MangaExportFormat = entries.firstOrNull { it.value == value } ?: Cbz
    }
}
