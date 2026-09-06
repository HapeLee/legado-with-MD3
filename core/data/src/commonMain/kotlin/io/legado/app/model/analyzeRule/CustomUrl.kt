package io.legado.app.model.analyzeRule

import io.legado.app.constant.AppPattern
import io.legado.app.core.platform.JsonCodec

@Suppress("unused")
class CustomUrl(url: String) {

    private val mUrl: String
    private val attribute = hashMapOf<String, Any>()

    init {
        val urlMatch = AppPattern.urlParamPattern.find(url)
        mUrl = if (urlMatch != null) {
            val attr = url.substring(urlMatch.range.last + 1)
            JsonCodec.decodeAnyMap(attr)?.let {
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
        return mUrl + "," + JsonCodec.toJson(attribute)
    }

}
