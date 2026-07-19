package io.legado.app.data.repository

internal inline fun <T> T.diffPrefMap(
    transform: (T) -> T,
    toPrefMap: (T) -> Map<String, Any?>,
): Map<String, Any?> {
    val previous = toPrefMap(this)
    return toPrefMap(transform(this)).filter { (key, value) -> previous[key] != value }
}
