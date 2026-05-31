package io.legado.app.api.controller


import android.text.TextUtils
import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject

object RssSourceController {

    val sources: ReturnData
        get() {
            val source = appDb.rssSourceDao.all
            val returnData = ReturnData()
            return if (source.isEmpty()) {
                returnData.setErrorMsg("Source list is empty")
            } else returnData.setData(source)
        }

    fun saveSource(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("Data cannot be empty")
        GSON.fromJsonObject<RssSource>(postData).onFailure {
            returnData.setErrorMsg("Source conversion failed ${it.localizedMessage}")
        }.onSuccess { source ->
            if (TextUtils.isEmpty(source.sourceName) || TextUtils.isEmpty(source.sourceUrl)) {
                returnData.setErrorMsg("Source name and URL cannot be empty")
            } else {
                appDb.rssSourceDao.insert(source)
                returnData.setData("")
            }
        }
        return returnData
    }

    fun saveSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("Data cannot be empty")
        val okSources = arrayListOf<RssSource>()
        val source = GSON.fromJsonArray<RssSource>(postData).getOrNull()
        if (source.isNullOrEmpty()) {
            return ReturnData().setErrorMsg("Source conversion failed")
        }
        for (rssSource in source) {
            if (rssSource.sourceName.isBlank() || rssSource.sourceUrl.isBlank()) {
                continue
            }
            appDb.rssSourceDao.insert(rssSource)
            okSources.add(rssSource)
        }
        return ReturnData().setData(okSources)
    }

    fun getSource(parameters: Map<String, List<String>>): ReturnData {
        val url = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (url.isNullOrEmpty()) {
            return returnData.setErrorMsg("Parameter url cannot be empty, please specify source URL")
        }
        val source = appDb.rssSourceDao.getByKey(url)
            ?: return returnData.setErrorMsg("Source not found, please check source URL")
        return returnData.setData(source)
    }

    fun deleteSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("No data passed")
        GSON.fromJsonArray<RssSource>(postData).onFailure {
            return ReturnData().setErrorMsg("Invalid format")
        }.onSuccess {
            SourceHelp.deleteRssSources(it)
        }
        return ReturnData().setData("Executed"/*okSources*/)
    }
}
