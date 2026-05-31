package io.legado.app.api.controller


import android.text.TextUtils
import io.legado.app.api.ReturnData
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject

object BookSourceController {

    val sources: ReturnData
        get() {
            val bookSources = appDb.bookSourceDao.all
            val returnData = ReturnData()
            return if (bookSources.isEmpty()) {
                returnData.setErrorMsg("Device source list is empty")
            } else returnData.setData(bookSources)
        }

    fun saveSource(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("Data cannot be empty")
        val bookSource = GSON.fromJsonObject<BookSource>(postData).getOrNull()
        if (bookSource != null) {
            if (TextUtils.isEmpty(bookSource.bookSourceName) || TextUtils.isEmpty(bookSource.bookSourceUrl)) {
                returnData.setErrorMsg("Source name and URL cannot be empty")
            } else {
                appDb.bookSourceDao.insert(bookSource)
                returnData.setData("")
            }
        } else {
            returnData.setErrorMsg("Source conversion failed")
        }
        return returnData
    }

    fun saveSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("Data is empty")
        val okSources = arrayListOf<BookSource>()
        val bookSources = GSON.fromJsonArray<BookSource>(postData).getOrNull()
        if (bookSources.isNullOrEmpty()) {
            return ReturnData().setErrorMsg("Source conversion failed")
        }
        bookSources.forEach { bookSource ->
            if (bookSource.bookSourceName.isNotBlank()
                && bookSource.bookSourceUrl.isNotBlank()
            ) {
                appDb.bookSourceDao.insert(bookSource)
                okSources.add(bookSource)
            }
        }
        return ReturnData().setData(okSources)
    }

    fun getSource(parameters: Map<String, List<String>>): ReturnData {
        val url = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (url.isNullOrEmpty()) {
            return returnData.setErrorMsg("Parameter url cannot be empty, please specify source URL")
        }
        val bookSource = appDb.bookSourceDao.getBookSource(url)
            ?: return returnData.setErrorMsg("Source not found, please check source URL")
        return returnData.setData(bookSource)
    }

    fun deleteSources(postData: String?): ReturnData {
        kotlin.runCatching {
            GSON.fromJsonArray<BookSource>(postData).getOrThrow().let {
                SourceHelp.deleteBookSources(it)
            }
        }.onFailure {
            return ReturnData().setErrorMsg(it.localizedMessage ?: "Data format error")
        }
        return ReturnData().setData("Executed"/*okSources*/)
    }
}
