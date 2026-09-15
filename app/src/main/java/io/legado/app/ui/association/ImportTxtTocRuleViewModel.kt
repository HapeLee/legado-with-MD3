package io.legado.app.ui.association

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.data.rules.toDomain
import io.legado.app.domain.rules.TxtTocRule
import io.legado.app.domain.rules.TxtTocRuleRepository
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isUri
import io.legado.app.utils.parseTxtTocRule
import io.legado.app.utils.parseTxtTocRules
import io.legado.app.utils.readText
import splitties.init.appCtx

class ImportTxtTocRuleViewModel(
    app: Application,
    private val repository: TxtTocRuleRepository,
) : BaseViewModel(app) {

    val errorLiveData = MutableLiveData<String>()
    val successLiveData = MutableLiveData<Int>()

    val allSources = arrayListOf<TxtTocRule>()
    val checkSources = arrayListOf<TxtTocRule?>()
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
            val selectSource = arrayListOf<TxtTocRule>()
            selectStatus.forEachIndexed { index, b ->
                if (b) {
                    selectSource.add(allSources[index])
                }
            }
            repository.insert(*selectSource.toTypedArray())
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

    /**
     * M3-4：规则的公开类型全部换成领域模型（`io.legado.app.domain.rules.TxtTocRule`），
     * 而 JSON 的解析仍在**实体**上完成——旧版本备份里 `chapterRule` 的键名是 `rule`，那条
     * 键名提升注册在实体类型上（`:core:data/androidMain` 的 `txtTocRuleJsonDeserializer`），
     * 共享层的 `JsonCodec` 不含它。所以这里走 `parseTxtTocRules` / `parseTxtTocRule`
     * （`io.legado.app.utils`，与 `GSON` 门面同包），再在边界上 `toDomain()`。
     *
     * ⚠️ 不要图省事改回 `JsonCodec.fromJsonObject<TxtTocRule>`：那会让老用户导入旧备份时
     * `rule` 键被静默丢掉（规则名还在、章节正则变空串），而且没有任何用例会红。
     *
     * 与 `feature:txttocrules` 的 `TxtTocRuleImportCompat` 是同一份实现的两个入口：那边服务
     * CMP Feature 的导入对话框（共享层），这边服务本老路径（URL / URI / 纯文本多形态）。
     * 前四片也是这个形态（见 `ImportReplaceRuleViewModel` 直接用 `ReplaceAnalyzer`）。
     */
    private suspend fun importSourceAwait(text: String) {
        when {
            text.isJsonObject() -> {
                parseTxtTocRule(text).toDomain().let {
                    allSources.add(it)
                }
            }
            text.isJsonArray() -> parseTxtTocRules(text)
                .let { items ->
                    allSources.addAll(items.map { it.toDomain() })
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
                selectStatus.add(source == null || it != source)
            }
            successLiveData.postValue(allSources.size)
        }
    }

}
