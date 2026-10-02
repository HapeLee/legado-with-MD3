package io.legado.app.help.readaloud.cast

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.dao.BgmPoolDao
import io.legado.app.data.dao.VoicePoolDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.RegexCastGroup
import io.legado.app.data.entities.RegexCastRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.random.Random

/**
 * 一条正则角色规则在朗读时真正要用的东西：编译好的正则 + 它把命中的文字变成什么。
 *
 * [voiceId] 与 [soundPath] 二选一非空：前者是「换这个音色念」，后者是「不念，放这段音频」。
 * [soundPath] 会被 [RegexCastSplitter] 编成 `路径#千分位` 格式的音频串（见
 * [RegexCastSplitter.SOUND_SEPARATOR] / [RegexCastSplitter.OFFSET_SEPARATOR]），
 * 解析端在 BaseReadAloudService.takeCueSounds。
 * [voiceEffect] 是这条规则的变声器预设名（来自 `regex_cast_rules.voiceEffect`，空 = 不变声），
 * 只在 [voiceId] 那一支有值：它跟着切块经 [RegexCastSplitter.Part] 落到朗读单元的
 * `ChapterSpeechSegment.voiceEffect`，与正文胶囊那一份同一条通道，消费方是
 * [io.legado.app.help.readaloud.effect.VoiceEffectStore.ofSpeech]。
 */
data class RegexCastEffect(
    val label: String,
    val pattern: Regex,
    val voiceId: String? = null,
    val soundPath: String? = null,
    val voiceEffect: String = "",
)

/**
 * 正则角色的读写出口（界面与朗读服务都只经这里，不直连 DAO）。
 *
 * 除 CRUD 外还负责两件事：
 * 1. 把「分组树 + 规则」拉平成界面那套 [CastPoolRow] / [CastGroupRow]，与角色声音池、
 *    背景音乐池共用同一套树逻辑（[CastPoolTree]）和同一个列表部件；
 * 2. 朗读侧的解析——把「池 + 条目」解成音色 id 或音频路径。只选了池没选条目时按池内启用的
 *    随机取一条（与背景音乐场景同一口径）；解不出来（音色停用、音频文件被删）就丢掉这条规则
 *    并记一行日志，朗读不能因为一条坏规则整章失败。
 */
object RegexCastRuleStore {

    /** 分组显示路径的分隔符；与角色声音池 / 背景音乐池列表里的同一条口径，三处要一致。 */
    private const val GROUP_SEPARATOR = "/"

    // ---------- 列表数据 ----------

    /** 分组树（DFS 拉平，根层在前、子组紧跟其父），与声音池那套同一算法。 */
    suspend fun listGroups(): List<CastGroupRow> = withContext(Dispatchers.IO) {
        val all = appDb.regexCastRuleDao.getGroups()
        // 父组不存在的孤儿按根层处理，避免整棵子树从列表上消失
        val ids = all.mapTo(mutableSetOf()) { it.id }
        val byParent = all.groupBy { if (it.parentId in ids) it.parentId else "" }
            .mapValues { (_, rows) -> rows.sortedWith(compareBy({ it.order }, { it.name })) }
        val rows = ArrayList<CastGroupRow>(all.size)

        fun walk(group: RegexCastGroup, depth: Int, parentPath: String, parentUsable: Boolean) {
            val path = if (parentPath.isEmpty()) {
                group.name
            } else {
                "$parentPath$GROUP_SEPARATOR${group.name}"
            }
            val usable = parentUsable && group.enabled
            rows += CastGroupRow(
                group.id, group.name, group.parentId, path, depth,
                group.order, group.enabled, usable,
            )
            byParent[group.id].orEmpty().forEach { walk(it, depth + 1, path, usable) }
        }
        byParent[""].orEmpty().forEach { walk(it, 0, "", true) }
        rows
    }

    /**
     * 规则 → 列表行。一条规则就是一个 [CastPoolRow]（[subtitle] 放规则摘要，
     * 界面按 hasMembers=false 的那条绘制分支走它，见 CastPoolWidgets 的 CastPoolRow）。
     * [summaryOf] 由调用方拼那行中文摘要（Store 不认识资源串）。
     */
    suspend fun poolRows(summaryOf: (RegexCastRule) -> String): List<CastPoolRow> =
        withContext(Dispatchers.IO) {
            val groups = listGroups()
            val pathById = groups.associate { it.id to it.path }
            val usableById = groups.associate { it.id to it.usable }
            appDb.regexCastRuleDao.all().map { rule ->
                CastPoolRow(
                    id = rule.id.toString(),
                    name = rule.name.ifBlank { rule.pattern },
                    groupId = rule.groupId,
                    groupName = pathById[rule.groupId].orEmpty(),
                    order = rule.order,
                    enabled = rule.enabled,
                    groupEnabled = usableById[rule.groupId] ?: true,
                    isDefault = false,
                    total = 0,
                    enabledCount = 0,
                    subtitle = summaryOf(rule),
                )
            }
        }

    /** 全部规则（列表按分组树渲染，这里只给原始行）。 */
    suspend fun all(): List<RegexCastRule> = withContext(Dispatchers.IO) {
        appDb.regexCastRuleDao.all()
    }

    // ---------- 规则增删改 ----------

    suspend fun save(rule: RegexCastRule) = withContext(Dispatchers.IO) {
        // 变声器名按预设表的口径存：去空白、限长 24（与 chapter_role_assignments.voiceEffect 一致），
        // 预设是精确按名字匹配的，多一个空格就查不到、听起来像「设了没生效」
        val normalized = rule.copy(voiceEffect = rule.voiceEffect.trim().take(24))
        if (normalized.id == 0L) {
            appDb.regexCastRuleDao.insert(normalized)
        } else {
            appDb.regexCastRuleDao.update(normalized.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun delete(rule: RegexCastRule) = withContext(Dispatchers.IO) {
        appDb.regexCastRuleDao.delete(rule)
    }

    suspend fun setEnabled(rule: RegexCastRule, enabled: Boolean) = withContext(Dispatchers.IO) {
        appDb.regexCastRuleDao.setEnabled(rule.id, enabled)
    }

    /**
     * 拖动结束后的整表回写。两组 slot 来自 [io.legado.app.ui.book.readaloud.cast.CastPoolTree.savePlan]
     * （UI 侧入口是 RegexCastRuleIntent.MoveItem / SaveSortOrder），内容是 `id to (父级 id, 顺序)`。
     * 只认这次列表里出现的行，收起的子树原样不动。
     */
    suspend fun saveSlots(
        ruleSlots: List<Pair<String, Pair<String, Int>>>,
        groupSlots: List<Pair<String, Pair<String, Int>>>,
    ) = withContext(Dispatchers.IO) {
        val dao = appDb.regexCastRuleDao
        val rules = dao.all().associateBy { it.id.toString() }
        dao.updateRules(
            ruleSlots.mapNotNull { (id, slot) ->
                rules[id]?.copy(groupId = slot.first, order = slot.second)
            }
        )
        val groups = dao.getGroups().associateBy { it.id }
        dao.updateGroups(
            groupSlots.mapNotNull { (id, slot) ->
                groups[id]?.copy(parentId = slot.first, order = slot.second)
            }
        )
    }

    // ---------- 分组增删改 ----------

    /** 建组：同一父级下同名不重复建，已存在就返回它的 id。 */
    suspend fun createGroup(name: String, parentId: String): String? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        val dao = appDb.regexCastRuleDao
        dao.getGroupByName(parentId, trimmed)?.id ?: run {
            val id = UUID.randomUUID().toString()
            dao.insertGroup(RegexCastGroup(id = id, name = trimmed, parentId = parentId))
            id
        }
    }

    suspend fun renameGroup(id: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val dao = appDb.regexCastRuleDao
        val group = dao.getGroup(id) ?: return@withContext false
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == group.name) return@withContext false
        // 同级重名会撞 [io.legado.app.data.entities.RegexCastGroup] 上的 (parentId, name) 唯一索引，先挡掉
        if (dao.getGroupByName(group.parentId, trimmed) != null) return@withContext false
        dao.updateGroups(listOf(group.copy(name = trimmed, updatedAt = System.currentTimeMillis())))
        true
    }

    suspend fun setGroupEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        appDb.regexCastRuleDao.setGroupEnabled(id, enabled)
    }

    /** 移动分组（拖进别的组 = 改父级）。拖进自己或自己的子树会成环，判非法。 */
    suspend fun moveGroupToParent(id: String, parentId: String): Boolean = withContext(Dispatchers.IO) {
        val dao = appDb.regexCastRuleDao
        val group = dao.getGroup(id) ?: return@withContext false
        if (id == parentId) return@withContext false
        if (isDescendant(parentId, id)) return@withContext false
        dao.updateGroups(listOf(group.copy(parentId = parentId, updatedAt = System.currentTimeMillis())))
        true
    }

    /**
     * 删组：组内规则与直属子组一起回到被删组的父级，**规则本身不删**——
     * 删组是整理目录，不承担清数据的语义。
     */
    suspend fun deleteGroup(id: String) = withContext(Dispatchers.IO) {
        val dao = appDb.regexCastRuleDao
        val group = dao.getGroup(id) ?: return@withContext
        dao.moveRulesOutOfGroup(from = id, to = group.parentId)
        dao.moveChildGroupsOutOfGroup(from = id, to = group.parentId)
        dao.deleteGroupById(id)
    }

    private suspend fun isDescendant(candidateId: String, ancestorId: String): Boolean {
        val dao = appDb.regexCastRuleDao
        var cursor: String? = candidateId
        var guard = 0
        while (!cursor.isNullOrEmpty() && guard++ < 32) {
            if (cursor == ancestorId) return true
            cursor = dao.getGroup(cursor)?.parentId
        }
        return false
    }

    // ---------- 朗读侧解析 ----------

    /** 池名与条目名（列表摘要与编辑弹窗都反查一次，一次取全比逐条查库便宜）。 */
    suspend fun names(): Pair<Map<String, String>, Map<String, String>> = withContext(Dispatchers.IO) {
        val pools = (appDb.voicePoolDao.getAll().map { it.id to it.name } +
            appDb.bgmPoolDao.getPools().map { it.id to it.name }).toMap()
        val items = appDb.readAloudVoiceDao.getVoices().associate { it.id to it.displayName } +
            appDb.bgmPoolDao.getAll().associate { it.id to it.name }
        pools to items
    }

    /**
     * 这本书有没有还活着的正则角色。
     *
     * 消费方是 BaseReadAloudService.buildSpeechPlan：关掉「多角色朗读」时朗读侧默认不生成
     * 播放计划，而正则角色必须落在计划上；这一问答 true 就照样生成计划（所有段都用默认音色）。
     */
    suspend fun hasRulesFor(bookUrl: String): Boolean = withContext(Dispatchers.IO) {
        val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext false
        appDb.regexCastRuleDao.findEnabledForBook(book.name, book.origin).isNotEmpty()
    }

    /**
     * 这本书现在能用的正则角色：按规则顺序编译、跳过被分组停用的、解好音色/音频。
     *
     * 分组链上任何一层停用，那一组里的规则整体不生效（与声音池候选同一口径）。
     * 消费方：CastSpeechOverlay.apply 把它交给 [RegexCastSplitter.split]——
     * [RegexCastRule.pattern] 在这里编一次，切分时匹配的是等长抹平版正文
     * （口径见 [io.legado.app.feature.reader.core.cast.CastMarkers.blank]）。
     */
    suspend fun effectsFor(book: Book): List<RegexCastEffect> = withContext(Dispatchers.IO) {
        val rules = appDb.regexCastRuleDao.findEnabledForBook(book.name, book.origin)
        if (rules.isEmpty()) return@withContext emptyList()
        val disabledGroups = disabledGroupIds()
        val voicePoolDao = appDb.voicePoolDao
        val bgmPoolDao = appDb.bgmPoolDao
        rules.mapNotNull { rule ->
            if (rule.groupId.isNotEmpty() && rule.groupId in disabledGroups) return@mapNotNull null
            val pattern = compile(rule.pattern, rule.useRegex) ?: return@mapNotNull null
            when (rule.poolKind) {
                RegexCastRule.POOL_BGM -> {
                    val path = resolveTrack(bgmPoolDao, rule)
                    if (path == null) {
                        AppLog.put(
                            "正则角色「${rule.name}」没有可用的配乐" +
                                "（池内没有启用的曲目或文件已删），本条跳过"
                        )
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
                        RegexCastEffect(rule.name, pattern, voiceId = voiceId, voiceEffect = rule.voiceEffect)
                    }
                }
            }
        }
    }

    /** 自己停用或祖先停用的分组 id（拉平好的树里 `usable=false` 的那些）。 */
    private suspend fun disabledGroupIds(): Set<String> =
        listGroups().filterNot { it.usable }.map { it.id }.toSet()

    /**
     * 按 [RegexCastRule.useRegex] 编译一条规则的匹配串。
     *
     * 关掉正则时整串走 [Regex.escape]，括号、点、星号都只是普通字符。开正则时按正则编，
     * 编不过（用户填的是带裸括号的普通文本）退回字面量，不让一条写坏的正则拖垮整本书的切分。
     *
     * 调用方只有 [effectsFor]：产物 [RegexCastEffect.pattern] 交给
     * [RegexCastSplitter.split] 在等长抹平版正文上匹配。
     */
    fun compile(pattern: String, useRegex: Boolean): Regex? {
        if (pattern.isBlank()) return null
        return runCatching { if (useRegex) Regex(pattern) else Regex(Regex.escape(pattern)) }
            .getOrElse { Regex(Regex.escape(pattern)) }
            .takeIf { it.pattern.isNotEmpty() }
    }

    private suspend fun resolveVoice(dao: VoicePoolDao, rule: RegexCastRule): String? {
        if (rule.itemId.isNotBlank()) return rule.itemId
        return dao.getMembers(rule.poolId).filter { it.enabled }.map { it.voiceId }.randomOrNull()
    }

    private suspend fun resolveTrack(dao: BgmPoolDao, rule: RegexCastRule): String? {
        val candidates = if (rule.itemId.isNotBlank()) {
            listOfNotNull(dao.getTrack(rule.itemId))
        } else {
            dao.getMembers(rule.poolId).filter { it.enabled }.mapNotNull { dao.getTrack(it.trackId) }
        }
        return candidates.filter { it.enabled && it.path.isNotBlank() && File(it.path).exists() }
            .randomOrNull()?.path
    }

    private fun <T> List<T>.randomOrNull(): T? =
        if (isEmpty()) null else get(Random.nextInt(size))
}
