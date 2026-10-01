package io.legado.app.help.readaloud.cast

/**
 * 把一段朗读文字按 [RegexCastEffect] 切开。
 *
 * 纯函数，不碰数据库也不碰引擎：切完的每一块带着「这段用哪个音色念」或「这块文字不念、
 * 改放这段音频」。朗读单元是按章内坐标排的，所以切出来的块必须各自带绝对起点，
 * 中间被吃掉的那段文字就地留一个空洞（后面的单元起点跟着往后挪，区间不重叠就行）。
 *
 * 匹配定位用**抹平版**文字（角色标记等长替换成空格）：只在标记之外找命中，
 * 但切片仍切原文，下标两边一致——与 [io.legado.app.feature.reader.core.cast.CastMarkers]
 * 在分配侧的用法同一口径。
 */
object RegexCastSplitter {

    /**
     * 切出来的一块。[voiceId] 非空 = 这一块换那个音色念；
     * [sound] 非空 = 这一块起播时并行放这些音频（多条以 \n 分隔）。
     */
    data class Part(
        val start: Int,
        val text: String,
        val voiceId: String?,
        val sound: String,
        /** 命中它的那条规则叫什么：只进日志，认得出是哪条规则顶掉了原声。 */
        val label: String = "",
    )

    /** [parts] 为空时，[trailingSound] 是整段文字都被「不念」吃掉后没处挂的音频。 */
    class SplitResult(val parts: List<Part>, val trailingSound: String)

    private class Hit(val start: Int, val end: Int, val rank: Int, val effect: RegexCastEffect)

    fun split(base: Int, raw: String, blanked: String, effects: List<RegexCastEffect>): SplitResult {
        if (raw.isEmpty()) return SplitResult(emptyList(), "")
        if (effects.isEmpty()) return SplitResult(listOf(Part(base, raw, null, "")), "")
        val hits = ArrayList<Hit>()
        effects.forEachIndexed { rank, effect ->
            effect.pattern.findAll(blanked).forEach { match ->
                val from = match.range.first
                val to = match.range.last + 1
                // 空命中（如 `a*` 匹配空串）会把文字切成无穷块，直接不收
                if (to > from) hits += Hit(from, to, rank, effect)
            }
        }
        if (hits.isEmpty()) return SplitResult(listOf(Part(base, raw, null, "")), "")
        // 同一位置只应用排序在前的那条规则；重叠的后面那些整条丢掉
        hits.sortWith(compareBy<Hit> { it.start }.thenBy { it.rank })
        val parts = ArrayList<Part>()
        val pending = ArrayList<Pair<String, Int>>()
        val text = StringBuilder()
        var blockStart = -1
        fun append(from: Int, to: Int) {
            if (to <= from) return
            if (blockStart < 0) blockStart = from
            text.append(raw, from, to)
        }
        /**
         * 收一块。
         *
         * 音效命中的那几个月挂在这一块上，**不另起一块**：多一个朗读单元就是多一次
         * 向 TTS 引擎要音频，而合成一条要三五秒、整条链路是串行的，多要一次就当场断流——
         * 用户听到的「读到匹配处停顿一段时间」就是这个。所以音效只把那几个字从文字里抠掉，
         * 音频按它在单元里的千分位延迟放，近似对准原来那个字的位置。
         */
        fun close() {
            if (blockStart < 0 || text.isEmpty()) return
            val spoken = text.toString()
            val length = spoken.length.coerceAtLeast(1)
            parts += Part(
                start = base + blockStart,
                text = spoken,
                voiceId = null,
                sound = pending.joinToString(SOUND_SEPARATOR) { (path, at) ->
                    val permille = ((at - blockStart) * 1000 / length).coerceIn(0, 1000)
                    if (permille > 0) "$path$OFFSET_SEPARATOR$permille" else path
                },
            )
            pending.clear()
            text.setLength(0)
            blockStart = -1
        }
        var cursor = 0
        hits.forEach { hit ->
            if (hit.start < cursor) return@forEach
            append(cursor, hit.start)
            cursor = hit.end
            val voiceId = hit.effect.voiceId
            if (voiceId != null) {
                // 换音色必须单独成一块：一块只有一个音色
                close()
                parts += Part(base + hit.start, raw.substring(hit.start, hit.end), voiceId, "", hit.effect.label)
            } else {
                hit.effect.soundPath?.let { pending += it to hit.start }
            }
        }
        append(cursor, raw.length)
        close()
        if (parts.isEmpty()) {
            return SplitResult(emptyList(), pending.joinToString(SOUND_SEPARATOR) { it.first })
        }
        if (pending.isNotEmpty()) {
            // 音频落在整段末尾：没有「后面那块」可挂，就挂到前面最后一块上（早半拍响，总比不响好）
            val last = parts.lastIndex
            parts[last] = parts[last].copy(
                sound = mergeSound(parts[last].sound, pending.joinToString(SOUND_SEPARATOR) { it.first })
            )
        }
        return SplitResult(parts, "")
    }

    /** 多条音频在一个朗读单元上一起响。 */
    const val SOUND_SEPARATOR = "\n"

    /** 一条音频的「延迟千分位」分隔符：`路径#350` = 这一单元播到 35% 时响。 */
    const val OFFSET_SEPARATOR = "#"

    fun mergeSound(left: String, right: String): String =
        (left.split(SOUND_SEPARATOR) + right.split(SOUND_SEPARATOR))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(SOUND_SEPARATOR)
}
