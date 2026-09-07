package io.legado.app.data.entities.rule

/**
 * 发现分类
 */
data class ExploreKind(
    val title: String = "",
    val url: String? = null,
    val type: String = Type.url,
    val action: String? = null,
    val chars: Array<String?>? = null,
    val default: String? = null,
    var viewName: String? = null,
    val style: FlexChildStyle? = null
) {

    @Suppress("ConstPropertyName")
    object Type {
        const val url = "url"
        const val text = "text"
        const val button = "button"
        const val toggle = "toggle"
        const val select = "select"
    }

    fun style(): FlexChildStyle {
        return style ?: FlexChildStyle.defaultStyle
    }

    override fun equals(other: Any?): Boolean {
        if (other is ExploreKind) {
            return other.title == title
                && other.type == type
                && other.url == url
                && other.action == action
                && other.default == default
        }
        return false
    }

    override fun hashCode(): Int {
        var result = title.hashCode() + type.hashCode()
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (action?.hashCode() ?: 0)
        result = 31 * result + (default?.hashCode() ?: 0)
        return result
    }

}
