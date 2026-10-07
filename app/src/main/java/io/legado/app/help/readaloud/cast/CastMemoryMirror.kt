package io.legado.app.help.readaloud.cast

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
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
