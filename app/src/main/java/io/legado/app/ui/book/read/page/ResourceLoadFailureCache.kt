package io.legado.app.ui.book.read.page

internal class ResourceLoadFailureCache<K> {

    private val failedKeys = HashSet<K>()

    fun <V> load(key: K, loader: () -> V?): V? {
        synchronized(failedKeys) {
            if (key in failedKeys) return null
        }
        return loader().also { result ->
            if (result == null) {
                synchronized(failedKeys) {
                    failedKeys.add(key)
                }
            }
        }
    }
}
