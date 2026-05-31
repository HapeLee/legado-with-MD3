package io.legado.app.ui.rss.read

enum class RedirectPolicy {
    ALLOW_ALL,
    ASK_ALWAYS,
    ASK_CROSS_ORIGIN,
    BLOCK_CROSS_ORIGIN,
    BLOCK_ALL,
    ASK_SAME_DOMAIN_BLOCK_CROSS;

    companion object {
        fun fromString(value: String?): RedirectPolicy {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: ALLOW_ALL
        }
    }
}

fun RedirectPolicy.title(): String {
    return when (this) {
        RedirectPolicy.ALLOW_ALL -> "Allow all redirects"
        RedirectPolicy.ASK_ALWAYS -> "Always ask"
        RedirectPolicy.ASK_CROSS_ORIGIN -> "Ask on cross-origin"
        RedirectPolicy.ASK_SAME_DOMAIN_BLOCK_CROSS -> "Ask same-domain, block cross-origin"
        RedirectPolicy.BLOCK_CROSS_ORIGIN -> "Block cross-origin"
        RedirectPolicy.BLOCK_ALL -> "Block all"
    }
}
