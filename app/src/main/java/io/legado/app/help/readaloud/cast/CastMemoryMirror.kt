package io.legado.app.help.readaloud.cast

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray

/**
 * 本书角色记忆 ↔ 官方人物档案 的两个方向。
 *
 * 记忆是 AI 每章滚出来的一表『主名｜别名/身份｜关系｜池』（见
 * `AiCastPresetStore.DEFAULT_CONTRACT` 的 memory 字段约定），人物档案是用户在人物详情页
 * 编辑的那一份。本类让两边互读：AI 归并好的别名进人物页，用户在人物页补的别名回给 AI，
 * 否则每一趟分配都要重新猜一遍同一个人。
 *
 * 方向一（记忆 → 档案）**只填空缺、只并别名**：记忆会被 AI 每章整段重写，
 * 拿它覆盖用户手写的简介/性格，等于用户在人物页写的东西每读一章就被抹一次。
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

    /** 角色换了声音池 → 本书记忆里那一行的池跟着换（AI 下一趟才会按新池填它）。 */
    suspend fun syncCharacterPool(bookUrl: String, name: String, pool: String) {
        if (bookUrl.isBlank()) return
        val row = appDb.bookCastMemoryDao.get(bookUrl) ?: return
        val next = replaceLinePool(row.memory, name, pool) ?: return
        appDb.bookCastMemoryDao.upsert(
            row.copy(memory = next, updatedAt = System.currentTimeMillis()),
        )
    }

    /** 记忆 → 档案：补空缺的简介与池，并把别名并进去（不覆盖用户已经写下的那一份）。 */
    suspend fun applyMemoryToProfiles(bookUrl: String, memory: String) {
        if (bookUrl.isBlank() || memory.isBlank()) return
        parse(memory).forEach { line ->
            val profile = appDb.bookKnowledgeDao.getCharacterProfile(bookUrl, line.name)
                ?.takeIf { it.bookUrl == bookUrl && it.name == line.name }
                ?: return@forEach
            val aliases = GSON.fromJsonArray<String>(profile.aliasesJson).getOrNull().orEmpty()
            val merged = (aliases + line.aliases).map(String::trim).filter(String::isNotBlank).distinct()
            val next = profile.copy(
                aliasesJson = GSON.toJson(merged),
                summary = profile.summary.ifBlank { line.relation },
                voiceAgeBand = VoicePoolStore.poolNameOrEmpty(profile.voiceAgeBand)
                    .ifBlank { line.pool }
                    .takeIf { it.isNotBlank() } ?: profile.voiceAgeBand,
                updatedAt = System.currentTimeMillis(),
            )
            if (next != profile) appDb.bookKnowledgeDao.upsertCharacterProfile(next)
        }
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
     * 位置式比对只在**用户自己改的这一次**有意义：整段记忆是 AI 每章重写的，行数与顺序都可能变，
     * 所以 AI 回写那条路（[applyMemoryToProfiles]）不走这里，这里也只在行数一致、且这一行除主名
     * 或池栏以外一个字没动时才认作改名/改池，其余一律当作没改。
     */
    fun diffUserEdits(before: String, after: String): UserEdits {
        val old = parse(before)
        val next = parse(after)
        if (old.size != next.size) return UserEdits()
        val renames = ArrayList<Pair<String, String>>()
        val pools = ArrayList<Pair<String, String>>()
        old.zip(next).forEach { (from, to) ->
            when {
                from.name != to.name && from.aliases == to.aliases && from.relation == to.relation ->
                    renames += from.name to to.name

                from.name == to.name && from.pool != to.pool ->
                    pools += from.name to to.pool
            }
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
            castRowsNamed(bookUrl, name).forEach { row ->
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
    suspend fun applyProfileToMemory(bookUrl: String, profile: BookCharacterProfile) {
        if (bookUrl.isBlank() || profile.name.isBlank()) return
        val row = appDb.bookCastMemoryDao.get(bookUrl) ?: return
        val aliases = GSON.fromJsonArray<String>(profile.aliasesJson).getOrNull().orEmpty()
        val pool = VoicePoolStore.poolNameOrEmpty(profile.voiceAgeBand)
        val next = replaceLine(
            row.memory,
            Line(profile.name, aliases, profile.summary, pool),
        )
        if (next == row.memory) return
        appDb.bookCastMemoryDao.upsert(row.copy(memory = next, updatedAt = System.currentTimeMillis()))
    }
}
