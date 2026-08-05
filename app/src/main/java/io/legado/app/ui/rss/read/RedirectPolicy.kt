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
        RedirectPolicy.ALLOW_ALL -> "允许所有跳转"
        RedirectPolicy.ASK_ALWAYS -> "总是询问"
        RedirectPolicy.ASK_CROSS_ORIGIN -> "跨域询问"
        RedirectPolicy.ASK_SAME_DOMAIN_BLOCK_CROSS -> "同域询问，跨域拦截"
        RedirectPolicy.BLOCK_CROSS_ORIGIN -> "拦截跨域"
        RedirectPolicy.BLOCK_ALL -> "拦截所有"
    }
}
