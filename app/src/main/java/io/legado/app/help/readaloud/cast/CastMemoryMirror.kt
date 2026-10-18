package io.legado.app.help.readaloud.cast

import androidx.room.withTransaction
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.utils.GSON
import kotlinx.coroutines.sync.withLock

/**
 * 本书角色记忆 ↔ 官方人物档案 的两个方向。
 *
 * 记忆是 AI 每章滚出来的一表『主名｜别名/身份｜关系｜池』（见
 * `AiCastPresetStore.DEFAULT_CONTRACT` 的 memory 字段约定），人物档案是用户在人物详情页
 * 编辑的那一份。本类让两边互读：AI 归并好的别名进人物页，用户在人物页补的别名回给 AI，
 * 否则每一趟分配都要重新猜一遍同一个人。
 *
 * 方向一（记忆 → 档案）跟着这一趟的记忆走，但**只动 AI 自己写过的那部分**：
 * 凭实际写入记录（[IdentityOwnership]）认出哪些别名、哪一句简介
 * 是模型填的，那些才跟着更新或摘除；凭据里没有的（用户在人物页自己写的）留到最后，
 * 记忆那一侧的条数封顶 [MAX_ALIAS_COUNT]，这一栏才不会随章数无界地涨。池那一栏永远只填空缺：
 * 改了就是换声音。
 * 方向二（档案 → 记忆）整行替换：那一行本来就代表这个人，用户在人物页改的就是最终口径。
 */
object CastMemoryMirror {

    /** 记忆里的一行。[aliases] 是「别名/身份」那一栏拆出来的名单。 */
    data class Line(val name: String, val aliases: List<String>, val relation: String, val pool: String)

    fun parse(memory: String): List<Line> = memory.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val parts = line.split('｜', '|').map { it.trim() }
            Line(
                name = parts.firstOrNull().orEmpty(),
                aliases = parts.getOrNull(1).orEmpty().split('、', '，', ',')
                    .map { it.trim() }.filter { it.isNotEmpty() },
                relation = parts.getOrNull(2).orEmpty(),
                pool = parts.getOrNull(3).orEmpty(),
            )
        }
        .filter { it.name.isNotEmpty() }
        .toList()

    fun render(line: Line): String = buildString {
        append(line.name)
        append('｜').append(line.aliases.joinToString("、"))
        append('｜').append(line.relation)
        if (line.pool.isNotBlank()) append('｜').append(line.pool)
    }

    private fun splitAliases(raw: Iterable<String>): List<String> = raw
        .flatMap { it.split('、', '，', ',', '/', '；', ';') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    /**
     * 别名拆到「一个叫得出口的称呼」：`、，,/；` 都算分隔，重复的合掉。
     *
     * 模型把身份用 `/` 串成一串（`男主/埃诺克/埃诺克小哥`），而称呼归并靠逐字相等
     * （[canonicalCastName]），整串谁都匹配不上：拆平后每一截才都是可用的钥匙。
     * 四种不是称呼的一并丢掉：只在这一章成立的（`本章…`）、模型自己标不确定的（带问号）、
     * 回显输入键名的（`已有角色`、`别名…`）与空串。
     */
    fun aliasWords(raw: Iterable<String>): List<String> = splitAliases(raw)
        .filter {
            !it.contains("本章") && !it.contains('？') && !it.contains('?') &&
                it != "已有角色" && !it.startsWith("别名")
        }

    /**
     * 把 [memory] 里主名等于 [line.name] 的那一行**原地**换成 [line]；没有就追加到末尾。
     *
     * 原地而不是删了再加：这一表的顺序就是用户在悬浮窗里看到的顺序，
     * 每次在人物页改个人就把他挪到最底下，等于改一次乱一次。
     */
    fun replaceLine(memory: String, line: Line): String {
        val rendered = render(line)
        val lines = memory.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val at = lines.indexOfFirst { it.split('｜', '|').first().trim() == line.name }
        if (at >= 0) lines[at] = rendered else lines += rendered
        return lines.joinToString("\n")
    }

    /**
     * 只把记忆里 [name] 那一行的**池**那一栏换成 [pool]，别名与关系一个字都不动。
     *
     * 不能整行替换（[replaceLine]）：这一行是 AI 逐章滚出来的，用户在配音页或正文胶囊里
     * 改一次池就把 AI 写下的关系抹没了。
     * 返回 null = 这本书还没有记忆、没有这个主名的行，或者池本来就是这样。
     */
    fun replaceLinePool(memory: String, name: String, pool: String): String? {
        val mainName = name.trim()
        if (memory.isBlank() || mainName.isEmpty()) return null
        val lines = memory.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val at = lines.indexOfFirst { it.split('｜', '|').first().trim() == mainName }
        if (at < 0) return null
        val fields = lines[at].split('｜', '|').map { it.trim() }.toMutableList()
        if (fields.getOrNull(3).orEmpty() == pool) return null
        while (fields.size < 4) fields += ""
        fields[3] = pool
        if (pool.isBlank() && fields.size == 4) fields.removeAt(3)
        val rendered = fields.joinToString("｜")
        if (rendered == lines[at]) return null
        lines[at] = rendered
        return lines.joinToString("\n")
    }

    /**
     * 把记忆里 [from] 那一行的主名换成 [to]、池换成 [pool]，别名与关系照旧。
     *
     * 两个入口共用：配音页/正文胶囊改了角色行（那里主名是用户填的，[from] 是改前的名字），
     * 以及本类 [applyUserEdits] 之外的一切反向同步。
     *
     * - 已经有 [to] 那一行（改名前就分裂过，或根本没改名）：只动它的池，别去改别的行。
     * - 两边都查不到：这本书还没有这一行，返回 null，让 AI 下一趟自己写。
     * - 别名栏里恰好等于新名的那一个要摘掉：AI 常把全名挂在别名上
     *   （`星菲｜李星菲、…`），用户把主名改成 `李星菲` 后留着它就是同一个人两种写法，
     *   下一趟分配又会照别名建出第二个角色。
     */
    fun renameLinePool(memory: String, from: String, to: String, pool: String): String? {
        val target = to.trim()
        if (memory.isBlank() || target.isEmpty()) return null
        val lines = memory.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        fun mainNameOf(line: String) = line.split('｜', '|').first().trim()
        var at = lines.indexOfFirst { mainNameOf(it) == target }
        val origin = from.trim()
        if (at < 0 && origin.isNotEmpty() && origin != target) {
            at = lines.indexOfFirst { mainNameOf(it) == origin }
        }
        if (at < 0) return null
        val fields = lines[at].split('｜', '|').map { it.trim() }.toMutableList()
        while (fields.size < 4) fields += ""
        fields[0] = target
        fields[1] = fields[1].split('、', '，', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != target }
            .joinToString("、")
        fields[3] = pool
        if (pool.isBlank() && fields.size == 4) fields.removeAt(3)
        val rendered = fields.joinToString("｜")
        if (rendered == lines[at]) return null
        lines[at] = rendered
        return lines.joinToString("\n")
    }

    /**
     * 角色行改了主名或声音池 → 本书记忆里那一行跟着改（AI 下一趟才会按新名新池填这个人）。
     *
     * 只按新名去找那一行是找不到的：记忆按主名认人，于是它停留在旧名上，
     * 下一趟 AI 照旧名再建一个角色（用户看到的「同名角色分裂」「要删两次」）。
     */
    suspend fun syncCharacterRow(
        bookUrl: String,
        previousName: String,
        name: String,
        pool: String,
    ) = AiCastPresetStore.memoryMutex.withLock {
        if (bookUrl.isBlank()) return@withLock
        appDb.bookKnowledgeDao.getCharacterProfiles(bookUrl, 500)
            .filter { it.name == previousName || it.name == name }
            .forEach { AiCastPresetStore.markProfileEdited(bookUrl, it.id, identityEdited = false) }
        val row = appDb.bookCastMemoryDao.get(bookUrl) ?: return@withLock
        val next = renameLinePool(row.memory, previousName, name, pool) ?: return@withLock
        appDb.bookCastMemoryDao.upsert(
            row.copy(memory = next, updatedAt = System.currentTimeMillis()),
        )
    }

    /**
     * 把记忆里每一行的池栏按**配音行的当前值**重刷一遍。
     *
     * 整段记忆是 AI 每章重写的，它对池的猜测（主角写成女青年之类）会盖掉用户实际选的那一份，
     * 于是「记忆显示 A、配音与详情显示 B」长期不一致。池的权威来源是配音行 —— 用户在那里
     * 选过、也是发音链路读的那一个；记忆里查不到对应行的名字（还没导成角色）保持 AI 写的原样。
     */
    suspend fun reconcilePoolsWithCast(bookUrl: String, memory: String): String {
        if (bookUrl.isBlank() || memory.isBlank()) return memory
        val poolByName = appDb.castCharacterDao.getByBook(bookUrl).associate { it.name to it.poolLabel }
        if (poolByName.isEmpty()) return memory
        return memory.lineSequence().joinToString("\n") { line ->
            val name = line.split('｜', '|').firstOrNull()?.trim().orEmpty()
            val pool = poolByName[name] ?: return@joinToString line
            replaceLinePool(line, name, pool) ?: line
        }
    }

    /**
     * 记忆 → 档案。返回实际写入的 AI 所有权，调用方在数据库事务成功后保存。
     * 回显用户字段不取得所有权；未出现在本次记忆里的档案仍保留原来的来源记录。
     */
    internal suspend fun applyMemoryToProfiles(
        bookUrl: String,
        memory: String,
        aiLedger: Map<String, IdentityOwnership>,
        fromAi: Boolean,
    ): Map<String, IdentityOwnership> {
        if (bookUrl.isBlank() || memory.isBlank()) return aiLedger
        val ledger = aiLedger.toMutableMap()
        // 整本取一次按主名索引：一表二十几行、每行现查一次库的话，写一次记忆就是二十几次读
        val byName = appDb.bookKnowledgeDao.getCharacterProfiles(bookUrl, 500)
            .filter { it.bookUrl == bookUrl }
            .associateBy { it.name }
        parse(memory).forEach { line ->
            val profile = byName[line.name] ?: return@forEach
            val owned = ledger[profile.id]
            val previous = owned?.takeIf { fromAi }?.let {
                Line(profile.name, it.aliases, it.summary, "")
            }
            val merged = mergeIdentity(line, profile.aliasNames(), profile.summary, previous)
            val next = profile.copy(
                aliasesJson = GSON.toJson(merged.aliases),
                summary = merged.summary,
                voiceAgeBand = VoicePoolStore.poolNameOrEmpty(profile.voiceAgeBand)
                    .ifBlank { line.pool }
                    .takeIf { it.isNotBlank() } ?: profile.voiceAgeBand,
                updatedAt = System.currentTimeMillis(),
            )
            if (next != profile) appDb.bookKnowledgeDao.upsertCharacterProfile(next)
            ledger[profile.id] = if (fromAi) {
                IdentityOwnership(
                    profile.id, merged.aiAliases, merged.aiSummary,
                    userEdited = owned?.userEdited == true,
                    aiCreated = owned?.aiCreated == true,
                )
            } else {
                IdentityOwnership(profile.id, userEdited = true, aiCreated = owned?.aiCreated == true)
            }
        }
        return ledger
    }

    /** 字段名是 prefs 的持久化格式，Release 混淆后也必须稳定。 */
    data class IdentityOwnership(
        @SerializedName("profileId") val profileId: String,
        @SerializedName("aliases") val aliases: List<String> = emptyList(),
        @SerializedName("summary") val summary: String = "",
        @SerializedName("userEdited") val userEdited: Boolean = false,
        @SerializedName("aiCreated") val aiCreated: Boolean = false,
    )

    /** 缺少可靠来源、用户保存过、或含 AI 没写过的字段时都不自动停用。 */
    private fun untouchedProfile(profile: BookCharacterProfile, owned: IdentityOwnership?): Boolean =
        owned != null && owned.aiCreated && !owned.userEdited &&
            profile.avatarUri == null && profile.role.isBlank() && profile.personality.isBlank() &&
            profile.voiceGender == BookCharacterProfile.VOICE_GENDER_UNKNOWN &&
            (profile.tagsJson.isBlank() || profile.tagsJson == "[]") &&
            (profile.summary.isBlank() || profile.summary == owned.summary) &&
            splitAliases(profile.aliasNames()).all { it in owned.aliases }

    /** 并出来的别名栏与简介栏。 */
    data class MergedIdentity(
        val aliases: List<String>,
        val summary: String,
        val aiAliases: List<String>,
        val aiSummary: String,
    )

    /**
     * 一趟记忆的某一行落到档案上时，别名与简介各自该怎么并。
     *
     * 别名两半：**记忆这一趟列出的**（模型每章能一直往里加的那一半，封顶 [MAX_ALIAS_COUNT]）
     * 排在前面，**凭据 [ledger] 里从来没有的**（用户在人物详情页自己补的）全留、跟在后面。
     * 上一次是 AI 写的、这一趟记忆没再列的那些摘掉。两半都先拆平 `/` 等分隔符；
     * 仅 AI 输入过滤占位内容，用户保存的文字不套用模型清洗规则。
     * 这些称谓是称呼归并的匹配钥匙（[canonicalCastName] 逐字相等），砍干净了同一个人会被另建档。
     * 简介：档案里空着，或者那一栏就是上一次 AI 写的那一句 → 跟着这一趟的记忆走；
     * 其余情况那是用户自己写的，留着（记忆没给新句子时也不动）。
     */
    fun mergeIdentity(line: Line, stored: List<String>, storedSummary: String, ledger: Line?): MergedIdentity {
        val ledgerAliases = aliasWords(ledger?.aliases.orEmpty()).toSet()
        val userAliases = splitAliases(stored).filterNot { it in ledgerAliases }
        val memoryAliases = aliasWords(line.aliases).take(MAX_ALIAS_COUNT)
        val aliases = (memoryAliases + userAliases).distinct()
        val ownsSummary = storedSummary.isBlank() || storedSummary == ledger?.relation
        val summary = if (ownsSummary) {
            line.relation.ifBlank { storedSummary }
        } else {
            storedSummary
        }
        return MergedIdentity(
            aliases, summary,
            aiAliases = memoryAliases.filterNot { it in userAliases },
            aiSummary = if (ownsSummary) summary else "",
        )
    }

    /** 用户在记忆编辑器里改出来的差异：换主名 = 改名，主名没动而池栏动了 = 改池。 */
    data class UserEdits(
        val renames: List<Pair<String, String>> = emptyList(),
        val poolChanges: List<Pair<String, String>> = emptyList(),
    ) {
        val isEmpty: Boolean get() = renames.isEmpty() && poolChanges.isEmpty()
    }

    /**
     * 比对用户这一次编辑前后的两份记忆。
     *
     * 按**身份指纹**（别名栏 + 关系栏）配对，不按行号：用户在表里加一行、删一行，
     * 或 AI 在中间补了行，行号配对就会把「另一个人」认成改名，一次保存毁掉整本角色表。
     * 只有某条新行的指纹在旧表里**唯一命中**一条时才认作同一个人，再各自独立地判断
     * 改名与改池（用户经常在同一行里两件一起改）。指纹本身被改动（动了别名或关系）就不推断。
     */
    fun diffUserEdits(before: String, after: String): UserEdits {
        val old = parse(before)
        val next = parse(after)
        val byFingerprint = old.groupBy { it.aliases to it.relation }
        val used = HashSet<String>()
        val renames = ArrayList<Pair<String, String>>()
        val pools = ArrayList<Pair<String, String>>()
        for (to in next) {
            val matches = byFingerprint[to.aliases to to.relation].orEmpty()
                .filter { it.name !in used }
            val from = matches.singleOrNull() ?: continue
            used += from.name
            if (from.name != to.name) renames += from.name to to.name
            if (from.pool != to.pool) pools += to.name to to.pool
        }
        return UserEdits(renames, pools)
    }

    /**
     * 把记忆里改过的主名与池写回配音角色与官方档案：走 [BookCastStore.updateCharacter]
     * 那一个漏斗（分配表、两套 id 的音色绑定、正文胶囊重排、池回记忆都在里面），
     * 不在这里另写一份，否则又是一处「接不上」。
     *
     * 目标名字已被别人占着时**拒绝**这一条并计数返回：档案按 (bookUrl, name) 唯一，
     * 硬写会把占着那个名字的人整条替换成新 id，旧 id 的角色行与音色绑定全成孤儿
     * ——表现为「删了两次才删掉，另一个人的状态和详情都没了」。
     */
    suspend fun applyUserEdits(bookUrl: String, edits: UserEdits): Int {
        if (bookUrl.isBlank() || edits.isEmpty) return 0
        var refused = 0
        for ((from, to) in edits.renames) {
            val target = to.trim()
            val rows = castRowsNamed(bookUrl, from)
            val profile = profileNamed(bookUrl, from)
            if (target.isEmpty() || !CastMarkers.isValidName(target) ||
                (target != from && (castRowsNamed(bookUrl, target).isNotEmpty() ||
                        profileNamed(bookUrl, target) != null))
            ) {
                refused++
                continue
            }
            if (rows.isEmpty()) {
                // 只有档案、还没导成配音角色的人：改名直接落在档案上
                profile?.takeIf { it.name != target }?.let {
                    appDb.bookKnowledgeDao.upsertCharacterProfile(
                        it.copy(name = target, updatedAt = System.currentTimeMillis()),
                    )
                }
                continue
            }
            rows.forEach { row ->
                BookCastStore.updateCharacter(
                    bookUrl, row.id, target, row.poolLabel, row.voiceId, row.voiceEffect,
                )
            }
        }
        for ((name, pool) in edits.poolChanges) {
            val rows = castRowsNamed(bookUrl, name)
            if (rows.isEmpty()) {
                // 只有档案、还没导成配音角色的人：池直接落在档案那一栏（voiceAgeBand 存的就是池名）
                val target = pool.trim().take(12)
                profileNamed(bookUrl, name)?.takeIf {
                    VoicePoolStore.poolNameOrEmpty(it.voiceAgeBand) != target
                }?.let {
                    appDb.bookKnowledgeDao.upsertCharacterProfile(
                        it.copy(voiceAgeBand = target, updatedAt = System.currentTimeMillis()),
                    )
                }
                continue
            }
            rows.forEach { row ->
                if (row.poolLabel != pool.trim().take(12)) {
                    BookCastStore.updateCharacter(
                        bookUrl, row.id, row.name, pool, row.voiceId, row.voiceEffect,
                    )
                }
            }
        }
        return refused
    }

    private suspend fun castRowsNamed(bookUrl: String, name: String) =
        appDb.castCharacterDao.getByBook(bookUrl).filter { it.name == name }

    /** 只认名字完全相等的那条：`getCharacterProfile` 连 aliasesJson 一起 LIKE，会把「记着这个别名的别人」捞出来。 */
    private suspend fun profileNamed(bookUrl: String, name: String) =
        appDb.bookKnowledgeDao.getCharacterProfiles(bookUrl, 500)
            .firstOrNull { it.bookUrl == bookUrl && it.name == name }

    /** 档案 → 记忆：用户在人物详情页存过的内容就是这一行的最终口径，整行替换。 */
    suspend fun applyProfileToMemory(
        bookUrl: String,
        profile: BookCharacterProfile,
    ) = AiCastPresetStore.memoryMutex.withLock {
        if (bookUrl.isBlank() || profile.name.isBlank()) return@withLock
        AiCastPresetStore.markProfileEdited(bookUrl, profile.id, identityEdited = true)
        val row = appDb.bookCastMemoryDao.get(bookUrl) ?: return@withLock
        val aliases = profile.aliasNames()
        val pool = VoicePoolStore.poolNameOrEmpty(profile.voiceAgeBand)
        val next = replaceLine(
            row.memory,
            Line(profile.name, aliases, profile.summary, pool),
        )
        if (next == row.memory) return@withLock
        appDb.bookCastMemoryDao.upsert(row.copy(memory = next, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 一次性群杂的写法：编号路人（`新生甲`/`工作人员丙`）、复数集体（`…们`）、方位称谓（`旁边XX`）。
     *
     * 每一章都换一批编号，给他们留一行没有可延续的信息，只会让这张表每章都长几行，
     * 把有名有姓的角色挤出模型的 1200 字预算。长度限定 2..6：单个天干字与一整句话都不是称谓。
     * 别处（配音角色、人物档案）照常留着，池与音色都不受影响。
     */
    fun isThrowawayExtra(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length !in 2..6) return false
        if (trimmed.endsWith('们')) return true
        if (trimmed.startsWith("旁边") || trimmed.startsWith("其他") || trimmed.startsWith("周围")) {
            return true
        }
        return trimmed.last() in HEAVENLY_STEMS && trimmed.dropLast(1) in EXTRA_TITLES
    }

    /**
     * 把 [memory] 里一次性群杂那几行去掉，别的行一个字不动、顺序不变。
     *
     * 另一条线认它：主名被别人的别名/身份栏原样记着，说明它是某个有名字的人的另一种写法，
     * 那种行留着（合并身份靠的就是它）。
     */
    fun dropThrowawayExtras(memory: String): String {
        val rows = memory.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val fields = line.split('｜', '|').map { it.trim() }
            Triple(line, fields.first(), fields.getOrElse(1) { "" })
        }.toList()
        // 只有留得下来的那些行才算数：群杂行里提到某个编号，不代表那是有名字的人的另一种写法
        val keepers = rows.filterNot { isThrowawayExtra(it.second) }
        return rows.filter { (_, name, _) ->
            !isThrowawayExtra(name) || keepers.any { name in it.third }
        }.joinToString("\n") { it.first }
    }

    /**
     * 一趟 AI 分配跑完，把人物列表收回「这一趟的记忆里有 + 真分配过台词」那一批人。
     *
     * 动一条要三条同时成立：本趟记忆的主名与别名都查不到、本书从没分配到一句台词、
     * 档案上没有用户手过的痕迹（[untouchedProfile]）。说过话的人一个都不动 ——
     * 他的胶囊与音色还挂在配音链路上。
     *
     * 停用而不是删档案：官方人物页的简介、关系与事记要留着，[CastProfileMirror.ensure]
     * 之后照名字把他放回配音链路。但 `cast_characters` 那一行必须一起删——
     * [CastProfileMirror.backfillAll] 会照着角色行把档案的状态改回启用，只改状态等于没改。
     * 有锁定音色、变声器、气泡或手动排序的角色不参与自动清理。
     */
    suspend fun pruneStaleProfiles(
        bookUrl: String,
        memory: String,
    ): Int = AiCastPresetStore.memoryMutex.withLock {
        if (bookUrl.isBlank() || memory.isBlank()) return@withLock 0
        appDb.withTransaction {
            val known = HashSet<String>()
            parse(memory).forEach { line ->
                known += line.name
                known += aliasWords(line.aliases)
            }
            val refs = appDb.chapterRoleAssignmentDao.getCastRefsForBook(bookUrl)
            val spokenIds = refs.map { it.characterId }.toSet()
            val spokenNames = refs.map { it.characterName }.toSet()
            val ledger = AiCastPresetStore.identityOwnership(bookUrl)
            val rows = appDb.castCharacterDao.getByBook(bookUrl)
            val lockedIds = appDb.readAloudVoiceDao.getBindings(bookUrl)
                .filter { it.subjectType == BookVoiceBinding.SUBJECT_CHARACTER && it.locked }
                .mapTo(HashSet()) { it.subjectId }
            val stale = ArrayList<BookCharacterProfile>()
            appDb.bookKnowledgeDao.getCharacterProfiles(bookUrl, 500).forEach { profile ->
                if (profile.status != BookCharacterProfile.STATUS_ACTIVE) return@forEach
                if (profile.id in spokenIds || profile.name in spokenNames) return@forEach
                if (profile.name in known || profile.aliasNames().any { it in known }) return@forEach
                if (!untouchedProfile(profile, ledger[profile.id])) return@forEach
                val castRows = rows.filter { it.id == profile.id || it.name == profile.name }
                if (profile.id in lockedIds || castRows.any {
                        it.id in lockedIds || it.voiceEffect.isNotBlank() ||
                            it.bubbleRuleJson.isNotBlank() || it.sortOrder != 0
                    }) return@forEach
                stale += profile
            }
            if (stale.isEmpty()) return@withTransaction 0
            stale.forEach { profile ->
                // 同名不同 id 的那一份也要删：档案与角色行同 id 是建档约定，
                // 但只说过话的另一套 id 会让 backfillAll 把刚停用的人又改回启用
                rows.filter { it.id == profile.id || (it.name == profile.name && it.id !in spokenIds) }
                    .forEach { appDb.castCharacterDao.delete(it.id) }
                appDb.bookKnowledgeDao.upsertCharacterProfile(
                    profile.copy(
                        status = BookCharacterProfile.STATUS_DISABLED,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            AppLog.put(
                "AI 分配角色：${stale.size} 个没再出场的人物退出配音（" +
                    stale.take(8).joinToString("、") { it.name } + "）",
            )
            stale.size
        }
    }
}

/** 天干：编号路人的写法（甲/乙/丙/丁…），模型每章重新发一遍号。 */
private val HEAVENLY_STEMS = setOf('甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸')

/** 只认明确的群杂称谓；未收录的叫法保留，不能拿真实姓名的末字当编号。 */
private val EXTRA_TITLES = setOf(
    "路人", "新生", "学生", "同学", "考生", "考官", "工作人员", "工作员", "职员",
    "员工", "服务员", "侍者", "侍女", "守卫", "卫兵", "士兵", "军官", "警察",
    "村民", "居民", "群众", "观众", "客人", "旅客", "乘客", "商人", "伙计",
    "男人", "女人", "男子", "女子", "男生", "女生", "少年", "少女", "老者", "老人",
)

/** 记忆那一侧每人的别名上限（用户在人物页自己补的那些不受这一条约束，见 [mergeIdentity]）。 */
private const val MAX_ALIAS_COUNT = 12
