package io.legado.app.help.readaloud.cast

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.RegexCastRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * 一条正则角色规则在朗读时真正要用的东西：编译好的正则 + 它把命中的文字变成什么。
 *
 * [voiceId] 与 [soundPath] 二选一非空：前者是「换这个音色念」，后者是「不念，放这段音频」。
 */
data class RegexCastEffect(
    val label: String,
    val pattern: Regex,
    val voiceId: String? = null,
    val soundPath: String? = null,
)

/**
 * 正则角色的读写出口（界面与朗读服务都只经这里，不直连 DAO）。
 *
 * 除 CRUD 外还负责把「池 + 条目」解成朗读能用的东西：只选了池没选条目时，按池内启用的
 * 随机取一条（与背景音乐场景同一口径）；解不出来（音色被停用、音频文件被删）就丢掉这条规则
 * 并记一行日志——朗读不能因为一条坏规则整章失败。
 */
object RegexCastRuleStore {

    suspend fun all(): List<RegexCastRule> = withContext(Dispatchers.IO) {
        appDb.regexCastRuleDao.all()
    }

    suspend fun groups(): List<String> = withContext(Dispatchers.IO) {
        appDb.regexCastRuleDao.allGroups()
    }

    suspend fun save(rule: RegexCastRule) = withContext(Dispatchers.IO) {
        if (rule.id == 0L) {
            appDb.regexCastRuleDao.insert(rule)
        } else {
            appDb.regexCastRuleDao.update(rule.copy(updatedAt = System.currentTimeMillis()))
        }
        Unit
    }

    suspend fun delete(rule: RegexCastRule) = withContext(Dispatchers.IO) {
        appDb.regexCastRuleDao.delete(rule)
    }

    suspend fun setEnabled(rule: RegexCastRule, enabled: Boolean) = withContext(Dispatchers.IO) {
        appDb.regexCastRuleDao.setEnabled(rule.id, enabled)
    }

    suspend fun move(rule: RegexCastRule, up: Boolean) = withContext(Dispatchers.IO) {
        val all = appDb.regexCastRuleDao.all().toMutableList()
        val index = all.indexOfFirst { it.id == rule.id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target < 0 || target >= all.size) return@withContext
        val moved = all.removeAt(index)
        all.add(target, moved)
        appDb.regexCastRuleDao.updateAll(all.mapIndexed { i, r -> r.copy(order = i) })
    }

    /**
     * 这本书现在能用的正则角色：按规则顺序编译并解好音色/音频。
     *
     * 范围判定与官方替换规则同一口径（书名或书源 URL 的子串），交给 DAO 的 SQL 做。
     */
    suspend fun effectsFor(book: Book): List<RegexCastEffect> = withContext(Dispatchers.IO) {
        val rules = appDb.regexCastRuleDao.findEnabledForBook(book.name, book.origin)
        if (rules.isEmpty()) return@withContext emptyList()
        val voicePoolDao = appDb.voicePoolDao
        val bgmPoolDao = appDb.bgmPoolDao
        rules.mapNotNull { rule ->
            val pattern = compile(rule.pattern) ?: return@mapNotNull null
            when (rule.poolKind) {
                RegexCastRule.POOL_BGM -> {
                    val path = resolveTrack(bgmPoolDao, rule)
                    if (path == null) {
                        AppLog.put("正则角色「${rule.name}」没有可用的配乐（池内没有启用的曲目或文件已删），本条跳过")
                        null
                    } else {
                        RegexCastEffect(rule.name, pattern, soundPath = path)
                    }
                }

                else -> {
                    val voiceId = resolveVoice(voicePoolDao, rule)
                    if (voiceId == null) {
                        AppLog.put("正则角色「${rule.name}」没有可用的音色（池内没有启用的条目），本条跳过")
                        null
                    } else {
                        RegexCastEffect(rule.name, pattern, voiceId = voiceId)
                    }
                }
            }
        }
    }

    /** 文本与正则同一张表：先按正则编，编不过（用户填的是带括号的普通文本）就整串当字面量。 */
    fun compile(pattern: String): Regex? {
        if (pattern.isBlank()) return null
        return runCatching { Regex(pattern) }
            .getOrElse { Regex(Regex.escape(pattern)) }
            .takeIf { it.pattern.isNotEmpty() }
    }

    private suspend fun resolveVoice(
        dao: io.legado.app.data.dao.VoicePoolDao,
        rule: RegexCastRule,
    ): String? {
        if (rule.itemId.isNotBlank()) return rule.itemId
        val candidates = dao.getMembers(rule.poolId).filter { it.enabled }.map { it.voiceId }
        return candidates.randomOrNull()
    }

    private suspend fun resolveTrack(
        dao: io.legado.app.data.dao.BgmPoolDao,
        rule: RegexCastRule,
    ): String? {
        val candidates = if (rule.itemId.isNotBlank()) {
            listOfNotNull(dao.getTrack(rule.itemId))
        } else {
            val memberIds = dao.getMembers(rule.poolId).filter { it.enabled }.map { it.trackId }
            memberIds.mapNotNull { dao.getTrack(it) }
        }
        return candidates.filter { it.enabled && it.path.isNotBlank() && File(it.path).exists() }
            .randomOrNull()?.path
    }

    private fun <T> List<T>.randomOrNull(): T? =
        if (isEmpty()) null else get(Random.nextInt(size))

    /** 编辑弹窗要显示的池名/条目名（找不到就显示原 id，别显示空白让人以为没存）。 */
    suspend fun describe(rule: RegexCastRule): Pair<String, String> = withContext(Dispatchers.IO) {
        if (rule.poolKind == RegexCastRule.POOL_BGM) {
            val pool = appDb.bgmPoolDao.getPool(rule.poolId)?.name.orEmpty()
            val item = appDb.bgmPoolDao.getTrack(rule.itemId)?.name.orEmpty()
            pool to item
        } else {
            val pool = appDb.voicePoolDao.getAll().firstOrNull { it.id == rule.poolId }?.name.orEmpty()
            val item = rule.itemId.takeIf { it.isNotBlank() }
                ?.let { appDb.readAloudVoiceDao.getVoice(it)?.displayName }
                .orEmpty()
            pool to item
        }
    }
}
