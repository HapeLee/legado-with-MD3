package io.legado.app.ui.replace

import android.app.Application
import android.text.TextUtils
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
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
import kotlinx.coroutines.flow.map
import splitties.init.appCtx

/**
 * 替换规则数据修改
 * 修改数据要copy,直接修改会导致界面不刷新
 */
class ReplaceRuleViewModel(application: Application) : BaseViewModel(application) {

    private val _sortMode = MutableStateFlow(context.getPrefString(PreferKey.replaceSortMode, "desc") ?: "desc")
    private val _searchKey = MutableStateFlow<String?>(null)

    val sortMode: StateFlow<String> = _sortMode
    val searchKey: StateFlow<String?> = _searchKey

    fun setSortMode(mode: String) {
        _sortMode.value = mode
        context.putPrefString(PreferKey.replaceSortMode, mode)
    }

    fun setSearchKey(key: String?) {
        _searchKey.value = key
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val rulesFlow = combine(_searchKey, _sortMode) { search, sort ->
        Pair(search, sort)
    }.flatMapLatest { (searchKey, sortMode) ->
        // 先获取基础数据
        val baseFlow = when {
            searchKey.isNullOrEmpty() -> appDb.replaceRuleDao.flowAll()
            searchKey == appCtx.getString(R.string.no_group) -> appDb.replaceRuleDao.flowNoGroup()
            searchKey.startsWith("group:") -> {
                val key = searchKey.substringAfter("group:")
                appDb.replaceRuleDao.flowGroupSearch("%$key%")
            }
            else -> appDb.replaceRuleDao.flowSearch("%$searchKey%")
        }

        baseFlow.map { rules ->
            val comparator = when (sortMode) {
                "asc" -> Comparator<ReplaceRule> { a, b ->
                    when {
                        // 置顶优先
                        a.order == -1 && b.order != -1 -> -1
                        a.order != -1 && b.order == -1 -> 1
                        // 置底最后
                        a.order == -2 && b.order != -2 -> 1
                        a.order != -2 && b.order == -2 -> -1
                        // 普通规则按order排序
                        else -> a.order.compareTo(b.order)
                    }
                }
                "desc" -> Comparator<ReplaceRule> { a, b ->
                    when {
                        a.order == -1 && b.order != -1 -> -1
                        a.order != -1 && b.order == -1 -> 1
                        a.order == -2 && b.order != -2 -> 1
                        a.order != -2 && b.order == -2 -> -1
                        else -> b.order.compareTo(a.order)
                    }
                }
                "name_asc" -> Comparator<ReplaceRule> { a, b ->
                    when {
                        a.order == -1 && b.order != -1 -> -1
                        a.order != -1 && b.order == -1 -> 1
                        a.order == -2 && b.order != -2 -> 1
                        a.order != -2 && b.order == -2 -> -1
                        else -> a.name.lowercase().compareTo(b.name.lowercase())
                    }
                }
                "name_desc" -> Comparator<ReplaceRule> { a, b ->
                    when {
                        a.order == -1 && b.order != -1 -> -1
                        a.order != -1 && b.order == -1 -> 1
                        a.order == -2 && b.order != -2 -> 1
                        a.order != -2 && b.order == -2 -> -1
                        else -> b.name.lowercase().compareTo(a.name.lowercase())
                    }
                }
                else -> null
            }

            if (comparator != null) rules.sortedWith(comparator) else rules
        }
    }.flowOn(Dispatchers.Default)

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
            rule.order = -1
            appDb.replaceRuleDao.update(rule)
        }
    }

    fun topSelect(rules: List<ReplaceRule>) {
        execute {
            rules.forEach {
                it.order = -1
            }
            appDb.replaceRuleDao.update(*rules.toTypedArray())
        }
    }

    fun toBottom(rule: ReplaceRule) {
        execute {
            rule.order = -2
            appDb.replaceRuleDao.update(rule)
        }
    }

    fun bottomSelect(rules: List<ReplaceRule>) {
        execute {
            rules.forEach {
                it.order = -2
            }
            appDb.replaceRuleDao.update(*rules.toTypedArray())
        }
    }

    fun upOrder() {
        execute {
            // 重置所有非特殊排序的规则
            val rules = appDb.replaceRuleDao.all
            var normalOrder = 1
            rules.forEach { rule ->
                if (rule.order >= 0) { // 只重置普通排序的规则
                    rule.order = normalOrder++
                }
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
