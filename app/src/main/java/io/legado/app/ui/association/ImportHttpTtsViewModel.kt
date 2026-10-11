package io.legado.app.ui.association

import android.app.Application
import android.util.Base64
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.repository.HttpTtsRepository
import io.legado.app.domain.model.readaloud.HttpTtsCatalogDraft
import io.legado.app.domain.usecase.SyncHttpTtsCatalogUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.readaloud.speech.HttpTtsCatalogParser
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isDataUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isUri
import io.legado.app.utils.readText
import splitties.init.appCtx

class ImportHttpTtsViewModel(
    app: Application,
    private val repository: HttpTtsRepository,
    private val syncHttpTtsCatalog: SyncHttpTtsCatalogUseCase,
) : BaseViewModel(app) {

    val errorLiveData = MutableLiveData<String>()
    val successLiveData = MutableLiveData<Int>()

    val allSources = arrayListOf<HttpTTS>()
    /** 与 [allSources] 同下标：源自带的发言人/情绪目录，没有就是空草稿。 */
    private val catalogDrafts = arrayListOf<HttpTtsCatalogDraft>()
    val checkSources = arrayListOf<HttpTTS?>()
    val selectStatus = arrayListOf<Boolean>()

    val isSelectAll: Boolean
        get() {
            selectStatus.forEach {
                if (!it) {
                    return false
                }
            }
            return true
        }

    val selectCount: Int
        get() {
            var count = 0
            selectStatus.forEach {
                if (it) {
                    count++
                }
            }
            return count
        }

    fun importSelect(finally: () -> Unit) {
        execute {
            val selectSource = arrayListOf<HttpTTS>()
            val selectDrafts = arrayListOf<HttpTtsCatalogDraft>()
            selectStatus.forEachIndexed { index, b ->
                if (b) {
                    selectSource.add(allSources[index])
                    selectDrafts.add(catalogDrafts.getOrElse(index) { HttpTtsCatalogDraft() })
                }
            }
            repository.insert(*selectSource.toTypedArray())
            // 目录落 read_aloud_voices，不写回源行；没带目录的源由 UseCase 自己跳过
            selectSource.forEachIndexed { index, source ->
                syncHttpTtsCatalog(
                    engineId = source.id.toString(),
                    draft = selectDrafts[index],
                    sourceRevision = source.lastUpdateTime,
                )
            }
        }.onFinally {
            finally.invoke()
        }
    }

    fun importSource(text: String) {
        execute {
            importSourceAwait(text.trim())
        }.onError {
            errorLiveData.postValue("ImportError:${it.localizedMessage}")
            AppLog.put("ImportError:${it.localizedMessage}", it)
        }.onSuccess {
            comparisonSource()
        }
    }

    private suspend fun importSourceAwait(text: String) {
        when {
            text.isJsonObject() -> {
                val draft = HttpTtsCatalogParser.parse(text)
                HttpTTS.fromJson(text).getOrThrow().let {
                    allSources.add(it)
                    catalogDrafts.add(draft)
                }
            }
            text.isJsonArray() -> {
                val drafts = HttpTtsCatalogParser.parseList(text)
                HttpTTS.fromJsonArray(text).getOrThrow().let { items ->
                    items.forEachIndexed { index, source ->
                        allSources.add(source)
                        catalogDrafts.add(drafts.getOrElse(index) { HttpTtsCatalogDraft() })
                    }
                }
            }
            text.isDataUrl() -> {
                val data = AppPattern.dataUriRegex.find(text)?.groupValues?.getOrNull(1)
                    ?: throw NoStackTraceException(context.getString(R.string.wrong_format))
                importSourceAwait(Base64.decode(data, Base64.DEFAULT).toString(Charsets.UTF_8))
            }
            text.isAbsUrl() -> {
                importSourceUrl(text)
            }
            text.isUri() -> {
                importSourceAwait(text.toUri().readText(appCtx))
            }
            else -> throw NoStackTraceException(context.getString(R.string.wrong_format))
        }
    }

    private suspend fun importSourceUrl(url: String) {
        okHttpClient.newCallResponseBody {
            if (url.endsWith("#requestWithoutUA")) {
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.decompressed().text().let {
            importSourceAwait(it)
        }
    }

    private fun comparisonSource() {
        execute {
            allSources.forEach {
                val source = repository.findById(it.id)
                checkSources.add(source)
                selectStatus.add(source == null || source.lastUpdateTime < it.lastUpdateTime)
            }
            successLiveData.postValue(allSources.size)
        }
    }

}
