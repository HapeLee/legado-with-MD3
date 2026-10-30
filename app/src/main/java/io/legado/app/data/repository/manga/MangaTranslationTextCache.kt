package io.legado.app.data.repository.manga

import java.io.File

/** Validated batches survive rendering/network failures; ownership follows the existing clear generation. */
internal class MangaTranslationTextCache(
    private val store: MangaTranslationStore,
    private val record: MangaTranslationStore.Record,
    private val pageScope: String,
) {
    private fun file(key: String) = File(store.pages(record),
        "texts/${MangaTranslationStore.digest(pageScope + "|" + key)}.json")

    fun read(key: String): String? = runCatching {
        val file = file(key)
        if (file.isFile) file.readText() else null
    }.getOrNull()

    fun write(key: String, value: String): Boolean = store.writeText(record, file(key), value)
}
