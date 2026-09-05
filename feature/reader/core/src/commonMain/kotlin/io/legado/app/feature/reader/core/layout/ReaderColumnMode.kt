package io.legado.app.feature.reader.core.layout

enum class ReaderColumnMode {
    SINGLE, DOUBLE, LANDSCAPE, LANDSCAPE_OR_TABLET;

    fun columnCount(widthPx: Int, heightPx: Int, isTablet: Boolean, isScroll: Boolean): Int {
        val double = when (this) {
            SINGLE -> false
            DOUBLE -> true // Explicit double-page mode also applies to scroll, as in the View reader.
            LANDSCAPE -> widthPx > heightPx && !isScroll
            LANDSCAPE_OR_TABLET -> (widthPx > heightPx || isTablet) && !isScroll
        }
        return if (double) 2 else 1
    }

    companion object {
        fun fromPreference(value: String): ReaderColumnMode = when (value) {
            "1" -> DOUBLE
            "2" -> LANDSCAPE
            "3" -> LANDSCAPE_OR_TABLET
            else -> SINGLE
        }
    }
}
