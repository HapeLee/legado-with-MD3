package io.legado.app.ui.book.search

import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.splitNotBlank
import splitties.init.appCtx

/**
 * 搜索范围
 */
@Suppress("unused")
data class SearchScope(private var scope: String) {

    constructor(groups: List<String>) : this(groups.joinToString(","))

    constructor(source: BookSource) : this(
        encodeSourceToken(source.bookSourceName, source.bookSourceUrl)
    )

    constructor(source: BookSourcePart) : this(
        encodeSourceToken(source.bookSourceName, source.bookSourceUrl)
    )

    override fun toString(): String {
        return scope
    }

    val stateLiveData = MutableLiveData(scope)

    fun update(scope: String, postValue: Boolean = true) {
        this.scope = scope
        if (postValue) stateLiveData.postValue(scope)
        save()
    }

    fun update(groups: List<String>) {
        scope = groups.joinToString(",")
        stateLiveData.postValue(scope)
        save()
    }

    fun update(source: BookSource) {
        scope = encodeSourceToken(source.bookSourceName, source.bookSourceUrl)
        stateLiveData.postValue(scope)
        save()
    }

    fun update(source: BookSourcePart) {
        scope = encodeSourceToken(source.bookSourceName, source.bookSourceUrl)
        stateLiveData.postValue(scope)
        save()
    }

    fun updateSources(sources: List<BookSourcePart>) {
        scope = encodeSourceScope(sources)
        stateLiveData.postValue(scope)
        save()
    }

    fun isSource(): Boolean {
        val items = scopeItems()
        if (items.isEmpty()) return false
        return parseSourceItems(items).size == items.size
    }

    val display: String
        get() {
            if (isSource()) {
                val sourceNames = parseSourceItems().map { it.name }
                if (sourceNames.isEmpty()) return appCtx.getString(R.string.all_source)
                return sourceNames.joinToString(",")
            }
            if (scope.isEmpty()) {
                return appCtx.getString(R.string.all_source)
            }
            return scope
        }

    /**
     * 搜索范围显示
     */
    val displayNames: List<String>
        get() {
            val list = arrayListOf<String>()
            if (isSource()) {
                parseSourceItems().forEach {
                    list.add(it.name)
                }
            } else {
                scopeItems().forEach {
                    list.add(it)
                }
            }
            return list
        }

    val sourceUrls: List<String>
        get() = parseSourceItems().map { it.url }

    fun remove(scope: String) {
        if (isSource()) {
            val sourceItems = parseSourceItems().filterNot {
                it.name == scope || it.url == scope
            }
            this.scope = sourceItems.joinToString(",") { "${it.name}::${it.url}" }
        } else {
            val stringBuilder = StringBuilder()
            scopeItems().forEach {
                if (it != scope) {
                    if (stringBuilder.isNotEmpty()) {
                        stringBuilder.append(",")
                    }
                    stringBuilder.append(it)
                }
            }
            this.scope = stringBuilder.toString()
        }
        stateLiveData.postValue(this.scope)
    }

    /**
     * 搜索范围书源
     */
    fun getBookSourceParts(): List<BookSourcePart> {
        val list = hashSetOf<BookSourcePart>()
        if (scope.isEmpty()) {
            list.addAll(appDb.bookSourceDao.allEnabledPart)
        } else {
            if (isSource()) {
                parseSourceItems().forEach { sourceItem ->
                    appDb.bookSourceDao.getBookSourcePart(sourceItem.url)?.let { source ->
                        list.add(source)
                    }
                }
            } else {
                val oldScope = scopeItems()
                val newScope = oldScope.filter {
                    val bookSources = appDb.bookSourceDao.getEnabledPartByGroup(it)
                    list.addAll(bookSources)
                    bookSources.isNotEmpty()
                }
                if (oldScope.size != newScope.size) {
                    update(newScope)
                    stateLiveData.postValue(scope)
                }
            }
            if (list.isEmpty()) {
                scope = ""
                appDb.bookSourceDao.allEnabledPart.let {
                    if (it.isNotEmpty()) {
                        stateLiveData.postValue(scope)
                        list.addAll(it)
                    }
                }
            }
        }
        return list.sortedBy { it.customOrder }
    }

    fun isAll(): Boolean {
        return scope.isEmpty()
    }

    fun save() {
        AppConfig.searchScope = scope
        if (isAll() || isSource() || scope.contains(",")) {
            AppConfig.searchGroup = ""
        } else {
            AppConfig.searchGroup = scope
        }
    }

    private fun scopeItems(): List<String> = scope.splitNotBlank(",").toList()

    private fun parseSourceItems(
        items: List<String> = scopeItems()
    ): List<ScopeSourceItem> {
        return items.mapNotNull { item ->
            val splitIndex = item.indexOf("::")
            if (splitIndex <= 0 || splitIndex >= item.lastIndex) {
                null
            } else {
                ScopeSourceItem(
                    name = item.substring(0, splitIndex),
                    url = item.substring(splitIndex + 2)
                )
            }
        }
    }

    private data class ScopeSourceItem(
        val name: String,
        val url: String,
    )

    companion object {
        private fun sanitizeSourceName(name: String): String {
            return name.replace(":", "").replace(",", "")
        }

        private fun encodeSourceToken(name: String, url: String): String {
            return "${sanitizeSourceName(name)}::${url}"
        }

        private fun encodeSourceScope(sources: List<BookSourcePart>): String {
            return sources.joinToString(",") { source ->
                encodeSourceToken(source.bookSourceName, source.bookSourceUrl)
            }
        }
    }

}
