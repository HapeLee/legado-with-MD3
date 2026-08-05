package io.legado.app.ui.book.changesource

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.SourceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx
import java.util.concurrent.ConcurrentHashMap

object ObservableSourceConfig : SharedPreferences.OnSharedPreferenceChangeListener {

    private val sp = appCtx.getSharedPreferences("SourceConfig", MODE_PRIVATE)
    private val bookScoreFlows = ConcurrentHashMap<String, MutableStateFlow<Int>>()
    private val sourceScoreFlows = ConcurrentHashMap<String, MutableStateFlow<Int>>()

    init {
        sp.registerOnSharedPreferenceChangeListener(this)
    }

    fun bookScoreFlow(searchBook: SearchBook): StateFlow<Int> {
        return bookScoreFlow(searchBook.origin, searchBook.name, searchBook.author)
    }

    fun bookScoreFlow(origin: String, name: String, author: String): StateFlow<Int> {
        val key = bookScoreKey(origin, name, author)
        return bookScoreFlows.getOrPut(key) {
            MutableStateFlow(sp.getInt(key, 0))
        }.asStateFlow()
    }

    fun sourceScoreFlow(origin: String): StateFlow<Int> {
        return sourceScoreFlows.getOrPut(origin) {
            MutableStateFlow(sp.getInt(origin, 0))
        }.asStateFlow()
    }

    fun getBookScore(searchBook: SearchBook): Int {
        return bookScoreFlow(searchBook).value
    }

    fun setBookScore(searchBook: SearchBook, score: Int) {
        setBookScore(searchBook.origin, searchBook.name, searchBook.author, score)
    }

    fun setBookScore(origin: String, name: String, author: String, score: Int) {
        SourceConfig.setBookScore(origin, name, author, score)
        syncBookScore(bookScoreKey(origin, name, author))
        syncSourceScore(origin)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key ?: return
        syncBookScore(key)
        syncSourceScore(key)
    }

    private fun syncBookScore(key: String) {
        bookScoreFlows[key]?.value = sp.getInt(key, 0)
    }

    private fun syncSourceScore(origin: String) {
        sourceScoreFlows[origin]?.value = sp.getInt(origin, 0)
    }

    private fun bookScoreKey(origin: String, name: String, author: String): String {
        return "${origin}_${name}_${author}"
    }
}
