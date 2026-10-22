package io.legado.app.model.webBook

import com.google.gson.annotations.SerializedName
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.Debug
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 详情页与书源调试共用的关联书籍解析。单个模块失败不影响其他模块。 */
object RelatedBooks {
    suspend fun load(book: Book, source: BookSource): List<Module> = load(
        book = book,
        modulesJson = source.ruleBookInfo?.relatedBooks,
        explore = { url -> WebBook.exploreBookWithResolvedUrl(source, url, 1, book) },
        log = { message, error ->
            // 关联模块是可选内容，不能用 state = -1 提前关闭整个调试会话。
            Debug.log(source.bookSourceUrl, message + (error?.let { "\n${it.stackTraceStr}" } ?: ""))
            if (error != null) AppLog.put("${source.bookSourceName}: $message", error)
        },
    )

    internal suspend fun load(
        book: Book,
        modulesJson: String?,
        explore: suspend (String) -> Pair<String, List<SearchBook>>,
        log: (String, Throwable?) -> Unit,
    ): List<Module> {
        if (modulesJson.isNullOrBlank()) return emptyList()
        log("︾开始解析关联书籍 relatedBooks", null)
        val definitions = GSON.fromJsonArray<Definition>(modulesJson).getOrElse {
            log("关联书籍 relatedBooks 配置解析失败", it)
            return emptyList()
        }
        val modules = coroutineScope {
            definitions.mapIndexed { index, def ->
                async {
                    val label = "关联书籍 relatedBooks[${index + 1}] ${def.title.orEmpty()}"
                    // 仅去掉首尾空白，不能删除 JS 字符串、关键字之间的空格。
                    val url = def.url?.trim()
                    if (url.isNullOrBlank()) {
                        log("$label: url 为空，跳过", IllegalArgumentException("url 不能为空"))
                        return@async null
                    }
                    try {
                        log("$label: 开始请求 $url", null)
                        val (resolvedUrl, books) = explore(url)
                        val filtered = books.filter { it.bookUrl != book.bookUrl }
                        log("$label: 获取 ${books.size} 本，过滤当前书籍后 ${filtered.size} 本", null)
                        Module(def.key ?: def.title.orEmpty(), def.title.orEmpty(), url, resolvedUrl, filtered)
                            .takeIf { filtered.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("$label: 请求或解析失败", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        log("︽关联书籍 relatedBooks 解析完成，显示 ${modules.size} 个模块", null)
        return modules
    }

    data class Module(
        val key: String,
        val title: String,
        val url: String,
        val resolvedUrl: String,
        val books: List<SearchBook>,
    )

    // 书源 JSON 的固定字段名，避免 R8 改名破坏解析。
    private data class Definition(
        @SerializedName("key") val key: String? = null,
        @SerializedName("title") val title: String? = null,
        @SerializedName("url") val url: String? = null,
    )
}
