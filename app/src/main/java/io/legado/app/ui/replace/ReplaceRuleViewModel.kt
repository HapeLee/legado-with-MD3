package io.legado.app.ui.replace

import android.app.Application
import android.text.TextUtils
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import splitties.init.appCtx

/**
 * 替换规则数据修改
 * 修改数据要copy,直接修改会导致界面不刷新
 */
class ReplaceRuleViewModel(application: Application) : BaseViewModel(application) {

    private val _sortMode = MutableStateFlow("desc")
    private val _searchKey = MutableStateFlow<String?>(null)

    val sortMode: StateFlow<String> = _sortMode
    val searchKey: StateFlow<String?> = _searchKey

    fun setSortMode(mode: String) {
        _sortMode.value = mode
    }

    fun setSearchKey(key: String?) {
        _searchKey.value = key
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val rulesFlow = combine(_searchKey, _sortMode) { search, sort ->
        Pair(search, sort)
    }.flatMapLatest { (searchKey, sortMode) ->
        when {
            searchKey.isNullOrEmpty() -> when (sortMode) {
                "asc" -> appDb.replaceRuleDao.flowAllAsc()
                "name_asc" -> appDb.replaceRuleDao.flowAllNameAsc()
                "name_desc" -> appDb.replaceRuleDao.flowAllNameDesc()
                else -> appDb.replaceRuleDao.flowAllDesc()
            }

            searchKey == appCtx.getString(R.string.no_group) -> when (sortMode) {
                "asc" -> appDb.replaceRuleDao.flowNoGroupAsc()
                "name_asc" -> appDb.replaceRuleDao.flowNoGroupNameAsc()
                "name_desc" -> appDb.replaceRuleDao.flowNoGroupNameDesc()
                else -> appDb.replaceRuleDao.flowNoGroupDesc()
            }

            searchKey.startsWith("group:") -> {
                val key = searchKey.substringAfter("group:")
                when (sortMode) {
                    "asc" -> appDb.replaceRuleDao.flowGroupSearchAsc("%$key%")
                    "name_asc" -> appDb.replaceRuleDao.flowGroupSearchNameAsc("%$key%")
                    "name_desc" -> appDb.replaceRuleDao.flowGroupSearchNameDesc("%$key%")
                    else -> appDb.replaceRuleDao.flowGroupSearchDesc("%$key%")
                }
            }

            else -> when (sortMode) {
                "asc" -> appDb.replaceRuleDao.flowSearchAsc("%$searchKey%")
                "name_asc" -> appDb.replaceRuleDao.flowSearchNameAsc("%$searchKey%")
                "name_desc" -> appDb.replaceRuleDao.flowSearchNameDesc("%$searchKey%")
                else -> appDb.replaceRuleDao.flowSearchDesc("%$searchKey%")
            }
        }
    }.flowOn(Dispatchers.IO)

    fun update(vararg rule: ReplaceRule) {
        execute {
            appDb.replaceRuleDao.update(*rule)
        }
    }

    fun delete(rule: ReplaceRule) {
        execute {
            appDb.replaceRuleDao.delete(rule)
        }
    }

    fun toTop(rule: ReplaceRule) {
        execute {
            rule.order = appDb.replaceRuleDao.minOrder - 1
            appDb.replaceRuleDao.update(rule)
        }
    }

    fun topSelect(rules: List<ReplaceRule>) {
        execute {
            var minOrder = appDb.replaceRuleDao.minOrder - rules.size
            rules.forEach {
                it.order = ++minOrder
            }
            appDb.replaceRuleDao.update(*rules.toTypedArray())
        }
    }

    fun toBottom(rule: ReplaceRule) {
        execute {
            rule.order = appDb.replaceRuleDao.maxOrder + 1
            appDb.replaceRuleDao.update(rule)
        }
    }

    fun bottomSelect(rules: List<ReplaceRule>) {
        execute {
            var maxOrder = appDb.replaceRuleDao.maxOrder
            rules.forEach {
                it.order = maxOrder++
            }
            appDb.replaceRuleDao.update(*rules.toTypedArray())
        }
    }

    fun upOrder() {
        execute {
            val rules = appDb.replaceRuleDao.all
            for ((index, rule) in rules.withIndex()) {
                rule.order = index + 1
            }
            appDb.replaceRuleDao.update(*rules.toTypedArray())
        }
    }

    fun enableSelection(rules: List<ReplaceRule>) {
        execute {
            val array = Array(rules.size) {
                rules[it].copy(isEnabled = true)
            }
            appDb.replaceRuleDao.update(*array)
        }
    }

    fun disableSelection(rules: List<ReplaceRule>) {
        execute {
            val array = Array(rules.size) {
                rules[it].copy(isEnabled = false)
            }
            appDb.replaceRuleDao.update(*array)
        }
    }

    fun delSelection(rules: List<ReplaceRule>) {
        execute {
            appDb.replaceRuleDao.delete(*rules.toTypedArray())
        }
    }

    fun addGroup(group: String) {
        execute {
            val sources = appDb.replaceRuleDao.noGroup
            sources.forEach { source ->
                source.group = group
            }
            appDb.replaceRuleDao.update(*sources.toTypedArray())
        }
    }

    fun upGroup(oldGroup: String, newGroup: String?) {
        execute {
            val sources = appDb.replaceRuleDao.getByGroup(oldGroup)
            sources.forEach { source ->
                source.group?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(oldGroup)
                    if (!newGroup.isNullOrEmpty())
                        it.add(newGroup)
                    source.group = TextUtils.join(",", it)
                }
            }
            appDb.replaceRuleDao.update(*sources.toTypedArray())
        }
    }

    fun delGroup(group: String) {
        execute {
            execute {
                val sources = appDb.replaceRuleDao.getByGroup(group)
                sources.forEach { source ->
                    source.group?.splitNotBlank(",")?.toHashSet()?.let {
                        it.remove(group)
                        source.group = TextUtils.join(",", it)
                    }
                }
                appDb.replaceRuleDao.update(*sources.toTypedArray())
            }
        }
    }
}
