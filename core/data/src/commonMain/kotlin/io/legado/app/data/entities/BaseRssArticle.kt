package io.legado.app.data.entities

import io.legado.app.core.platform.JsonCodec
import io.legado.app.data.bigdata.BigDataStoreProvider
import io.legado.app.model.analyzeRule.RuleDataInterface

interface BaseRssArticle : RuleDataInterface {

    var origin: String
    var link: String

    var variable: String?

    override fun putVariable(key: String, value: String?): Boolean {
        if (super.putVariable(key, value)) {
            variable = JsonCodec.toJson(variableMap)
        }
        return true
    }

    override fun putBigVariable(key: String, value: String?) {
        BigDataStoreProvider.current.putRssVariable(origin, link, key, value)
    }

    override fun getBigVariable(key: String): String? {
        return BigDataStoreProvider.current.getRssVariable(origin, link, key)
    }

}