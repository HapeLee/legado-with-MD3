package io.legado.app.ui.main

fun bookCoverSharedElementKey(bookUrl: String, sourceId: String? = null): String {
    val source = sourceId?.takeIf { it.isNotBlank() } ?: return "book-cover:$bookUrl"
    return "book-cover:$source:$bookUrl"
}
