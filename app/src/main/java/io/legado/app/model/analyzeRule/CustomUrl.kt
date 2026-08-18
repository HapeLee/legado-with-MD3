package io.legado.app.model.analyzeRule

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

@Suppress("unused")
class CustomUrl(url: String) {

    private val mUrl: String
    private val attribute = hashMapOf<String, Any>()

    init {
        val urlMatch = AnalyzeUrl.paramPattern.find(url)
        mUrl = if (urlMatch != null) {
            val attr = url.substring(urlMatch.range.last + 1)
            GSON.fromJsonObject<Map<String, Any>>(attr).getOrNull()?.let {
                attribute.putAll(it)
            }
            url.substring(0, urlMatch.range.first)
        } else {
            url
        }
    }

    fun putAttribute(key: String, value: Any?): CustomUrl {
        if (value == null) {
            attribute.remove(key)
        } else {
            attribute[key] = value
        }
        return this
    }

    fun getUrl(): String {
        return mUrl
    }

    fun getAttr(): Map<String, Any> {
        return attribute
    }

    override fun toString(): String {
        if (attribute.isEmpty()) {
            return mUrl
        }
        return mUrl + "," + GSON.toJson(attribute)
    }

}