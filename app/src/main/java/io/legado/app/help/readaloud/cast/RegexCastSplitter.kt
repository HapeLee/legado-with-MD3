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
     * 切出来的一块。[voiceId] 非空 = 这一块换那个音色念，优先级（正则角色 > 分配表角色 > 旁白）
     * 在 [CastSpeechOverlay] 的 speechFor 里落地；[sound] 非空 = 这一块起播时并行放这些音频
     * （多条以 [SOUND_SEPARATOR] 分隔，格式契约见文件底部），落到
     * [io.legado.app.domain.model.readaloud.SpeechPlanItem.soundEffect]，解析端是
     * BaseReadAloudService.takeCueSounds。
     */
    data class Part(
        val start: Int,
        val text: String,
        val voiceId: String?,
        val sound: String,
        /** 命中它的那条规则叫什么：只进日志，认得出是哪条规则顶掉了原声。 */
        val label: String = "",
        /**
         * 命中它的那条规则的变声器预设名（[RegexCastEffect.voiceEffect]），空 = 不变声。
         * 生产方是 [split]，消费方是 [CastSpeechOverlay] 的 speechFor：它把这一份写进朗读单元的
         * `ChapterSpeechSegment.voiceEffect`，朗读服务再按名字取预设套音高/语速与混响。
         */
        val voiceEffect: String = "",
    )

    /** [parts] 为空时（整段文字都被「不念」吃掉），[trailingSound] 是音频串，由调用方挂到下一个朗读单元。 */
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
         * 音效只把命中的那几个字从文字里抠掉、把音频挂在这一块上，**不另起一块**：
         * 一个朗读单元对应一次向 TTS 引擎要音频的请求，整条合成链路是串行的，
         * 多一个单元就多一次串行等待。音频按命中字符在单元内的占比（千分位）延迟起播。
         *
         * 只剩空白的块（正文每段开头的两个全角空格、命中正好在段首时最常见）**不成块**：
         * 它送进引擎只会得到一段无声音频，听感就是命中前先空一下。留着的音效不丢，
         * 跟着 [pending] 挂到后面那一块上（整段都没有实文时走 [trailingSound]）。
         */
        fun close() {
            if (blockStart < 0) return
            val spoken = text.toString()
            if (spoken.isBlank()) {
                text.setLength(0)
                blockStart = -1
                return
            }
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
                // 换音色必须单独成一块：一块只有一个音色（变声器同理，一块只套一个预设）
                close()
                parts += Part(
                    base + hit.start,
                    raw.substring(hit.start, hit.end),
                    voiceId,
                    "",
                    hit.effect.label,
                    hit.effect.voiceEffect,
                )
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
            // 音频落在整段末尾、没有「后面那块」可挂：并到最后一块上，随它起播响
            val last = parts.lastIndex
            parts[last] = parts[last].copy(
                sound = mergeSound(parts[last].sound, pending.joinToString(SOUND_SEPARATOR) { it.first })
            )
        }
        return SplitResult(parts, "")
    }

    /**
     * 音效串的格式（本文件是唯一定义处）：多条音频挂在同一个朗读单元上以 [SOUND_SEPARATOR]
     * 分隔，每条可带 [OFFSET_SEPARATOR] 后缀的延迟千分位——`路径#350` = 该单元播到 35% 时响。
     * 生产方是 [split]，解析方是 BaseReadAloudService.takeCueSounds，两边必须用同一对分隔符。
     */
    const val SOUND_SEPARATOR = "\n"

    /** 一条音频的「延迟千分位」分隔符；解析端按行内第一个 `#` 拆分，两边口径要一致。 */
    const val OFFSET_SEPARATOR = "#"

    /** 两段音效串并成一段（去重、保持 [SOUND_SEPARATOR] 格式）。 */
    fun mergeSound(left: String, right: String): String =
        (left.split(SOUND_SEPARATOR) + right.split(SOUND_SEPARATOR))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(SOUND_SEPARATOR)
}
