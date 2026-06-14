package io.legado.app.ui.book.search

import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.BookSearchScope.ScopeSourceItem
import splitties.init.appCtx

/**
 * 搜索范围
 */
@Suppress("unused")
data class SearchScope(private var scope: String) {

    constructor(groups: List<String>) : this(BookSearchScope.encodeGroups(groups))

    constructor(source: BookSource) : this(
        encodeSourceScope(source.bookSourceName, source.bookSourceUrl)
    )

    constructor(source: BookSourcePart) : this(
        encodeSourceScope(source.bookSourceName, source.bookSourceUrl)
    )

    override fun toString(): String {
        return scope
    }

    val stateLiveData = MutableLiveData(scope)

    fun update(scope: String, postValue: Boolean = true) {
        this.scope = scope
        if (postValue) stateLiveData.postValue(scope)
    }

    fun update(groups: List<String>) {
        scope = BookSearchScope.encodeGroups(groups)
        stateLiveData.postValue(scope)
    }

    fun update(source: BookSource) {
        scope = encodeSourceScope(source.bookSourceName, source.bookSourceUrl)
        stateLiveData.postValue(scope)
    }

    fun update(source: BookSourcePart) {
        scope = encodeSourceScope(source.bookSourceName, source.bookSourceUrl)
        stateLiveData.postValue(scope)
    }

    fun updateSources(sources: List<BookSourcePart>) {
        scope = encodeSourceScope(sources)
        stateLiveData.postValue(scope)
    }

    fun isSource(): Boolean {
        return parsedScope().isSource
    }

    val display: String
        get() {
            if (isSource()) {
                val sourceNames = parsedScope().sourceNames
                if (sourceNames.isEmpty()) return appCtx.getString(R.string.all_source)
                return sourceNames.joinToString(",")
            }
            if (scope.isEmpty()) {
                return appCtx.getString(R.string.all_source)
            }
            return parsedScope().groupNames.joinToString(",")
        }

    /**
     * 搜索范围显示
     */
    val displayNames: List<String>
        get() {
            val list = arrayListOf<String>()
            if (isSource()) {
                list.addAll(parsedScope().sourceNames)
            } else {
                list.addAll(parsedScope().groupNames)
            }
            return list
        }

    val sourceUrls: List<String>
        get() = parsedScope().sourceUrls

    fun remove(scope: String) {
        if (isSource()) {
            val sourceItems = sourceItems().filterNot {
                it.name == scope || it.url == scope
            }
            this.scope = BookSearchScope.encodeSources(sourceItems)
        } else {
            this.scope = BookSearchScope.encodeGroups(displayNames.filterNot { it == scope })
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
                sourceItems().forEach { sourceItem ->
                    appDb.bookSourceDao.getBookSourcePart(sourceItem.url)?.let { source ->
                        list.add(source)
                    }
                }
            } else {
                val oldScope = parsedScope().groupNames
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
        return parsedScope().isAll
    }

    private fun parsedScope(): BookSearchScope = BookSearchScope(scope)

    private fun sourceItems(): List<ScopeSourceItem> {
        val parsed = parsedScope()
        return parsed.sourceNames.zip(parsed.sourceUrls) { name, url ->
            ScopeSourceItem(name, url)
        }
    }

    companion object {
        private fun sanitizeSourceName(name: String): String {
            return name.replace(":", "").replace(",", "")
        }

        private fun encodeSourceScope(name: String, url: String): String {
            return BookSearchScope.encodeSources(
                listOf(ScopeSourceItem(sanitizeSourceName(name), url))
            )
        }

        private fun encodeSourceScope(sources: List<BookSourcePart>): String {
            return BookSearchScope.encodeSources(
                sources.map { source ->
                    ScopeSourceItem(
                        name = sanitizeSourceName(source.bookSourceName),
                        url = source.bookSourceUrl
                    )
                }
            )
        }
    }

}
