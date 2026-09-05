package io.legado.app.feature.reader.core.model

object ReaderImageCachePolicy {
    fun key(element: ReaderElement.Image): String =
        "${element.source}|${element.bounds.width.toInt()}x${element.bounds.height.toInt()}"

    fun belongsToSource(key: String, source: String): Boolean = key.startsWith("$source|")

    fun withGeneration(key: String, generation: Long): String = "$key|generation=$generation"
}
