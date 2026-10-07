package io.legado.app.help.readaloud.cast

import io.legado.app.data.appDb
import io.legado.app.data.entities.CastCharacter
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechPlanItem
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.constant.AppLog
import io.legado.app.ui.config.readConfig.ReadConfig
import org.koin.core.context.GlobalContext

/**
 * 多角色朗读的分配覆盖层：已分配的引号段用那个角色的音色读，其余一律旁白。
 *
 * 归属权威是分配表（`chapter_role_assignments`）注入正文的标记，不是官方规则/AI 分析的结果——
 * 分配表能改到句，朗读就得跟到句。官方链路负责切分，这里只把「谁念哪一段」换成用户的分配。
 * 标记是否送给引擎由朗读服务的 `speechText()` 决定（偏移算完后才去掉，见 [CastMarkers.strip]）。
 *
 * 开关口径：「多角色朗读」决定我们这套分配发不发生效；「多角色分配」决定正文里有没有标记与胶囊。
 * 标记的唯一来源是「多角色分配」注入正文的那份文本，本层只读它、绝不自己补标记——
 * 关掉分配就是没有分配信息可读，整章旁白（引擎侧若认这套标记则另说）。
 */
object CastSpeechOverlay {

    /**
     * 一段已分配对话的朗读范围：章内偏移 [start, end] 之内的句子说这个角色的声音。
     *
     * start 取**开引号**而不是标记：一句对话常被子标点切成多个播放单元，
     * 首单元从引号起算（在标记之前），按标记位置比就会把这句话判成旁白。
     * [ordinal] 是这句对话的分配锚点序号，[effect] 是**只作用于这一句**的变声器预设名。
     */
    internal data class Span(
        val start: Int,
        val end: Int,
        val name: String,
        val pool: String,
        val ordinal: Int = -1,
        val effect: String = "",
    )

    suspend fun apply(
        bookUrl: String,
        chapterIndex: Int,
        paragraphs: List<CanonicalSpeechParagraph>,
        plan: List<SpeechPlanItem>,
    ): List<SpeechPlanItem> {
        if (plan.isEmpty()) return plan
        // 正则角色独立于多角色分配：它不认引号、不查分配表，命中文字换音色或不念改音效，
        // 「多角色朗读」关着时也生效（规则与生效口径见 RegexCastRuleStore.effectsFor）。
        val effects = runCatching {
            appDb.bookDao.getBook(bookUrl)?.let { RegexCastRuleStore.effectsFor(it) }
        }.getOrNull().orEmpty()
        val multiRole = ReadConfig.useMultiSpeaker
        if (!multiRole && effects.isEmpty()) return plan
        // 段级变声器按引号序号存在分配表里（正文胶囊那一栏设的，只管那一句）
        val effectsOfQuote = if (paragraphs.isEmpty() || !multiRole) {
            emptyMap()
        } else {
            appDb.chapterRoleAssignmentDao.getForChapter(bookUrl, chapterIndex)
                .filter { it.voiceEffect.isNotBlank() }
                .associate { it.quoteOrdinal to it.voiceEffect }
        }
        val spans = if (multiRole) spansOf(paragraphs, effectsOfQuote) else emptyList()
        if (spans.isEmpty() && effects.isEmpty()) {
            // 正文里没有角色标记：要么没开「多角色分配」（标记由它注入），要么这一章还没分配过角色
            AppLog.put(
                "多角色朗读: 第${chapterIndex + 1}章正文里没有角色标记（未开「多角色分配」或本章未分配），" +
                    "整章按旁白读"
            )
            return plan
        }
        val gateway = runCatching { GlobalContext.get().get<ReadAloudVoiceGateway>() }
            .getOrNull() ?: return plan
        val characters = appDb.castCharacterDao.getByBook(bookUrl)
        val voices = gateway.getEnabledVoices().associateBy { it.id }
        val narrator = gateway.bindingVoice(
            bookUrl,
            BookVoiceBinding.SUBJECT_NARRATOR,
            voices,
        )
        // 官方人物页/AI 识别建的角色可能还没选过音色（没进过配音页）：按声音池就地补一次，
        // 否则那句角色台词会退回旁白音。ensureVoice 幂等，选完落库并镜像绑定。
        val picked = HashMap<String, CastCharacter>()
        var voiced = 0
        var regexVoiced = 0
        var regexMuted = 0
        val sample = StringBuilder()
        val result = ArrayList<SpeechPlanItem>()
        // 整段文字都被「不念」吃掉时，音频没有后继单元可挂，先攒着落到下一个朗读单元上
        var carrySound = ""

        /**
         * 一个切块 → 一个朗读单元。音色优先级：正则角色 > 分配表角色 > 旁白——
         * 这条优先级在这里落地，正则侧的选择结果由 [RegexCastSplitter.Part.voiceId] 带过来，
         * 正则侧那条规则自己选的变声器由 [RegexCastSplitter.Part.voiceEffect] 带过来（同一优先级）。
         *
         * [part] 带着切块自己的章内起点，所以被音效吃掉的那段文字在这里就是个空洞：
         * 前后两块各归各的坐标，区间只留缝、不重叠（播放队列不许重叠）。
         */
        suspend fun speechFor(
            item: SpeechPlanItem,
            part: RegexCastSplitter.Part,
            span: Span?,
        ): SpeechPlanItem {
            val delta = part.start - item.segment.chapterPosition
            val spoken = part.text
            val regexVoice = part.voiceId?.let { id ->
                voices[id] ?: narrator.also {
                    AppLog.put("正则角色「${part.label}」的音色 $id 不在启用的音色表里，这段按原声读")
                }
            }
            if (part.voiceId != null && voices.containsKey(part.voiceId)) regexVoiced++
            if (part.sound.isNotEmpty()) regexMuted++
            val character = span?.let { characters.match(it.name, it.pool) }
            // 播放单元里保留标记原文：偏移要按含标记的正文算，去掉标记是送进引擎前的最后一步
            val segment = item.segment.copy(
                text = spoken,
                start = item.segment.start + delta,
                end = item.segment.start + delta + spoken.length,
                chapterPosition = part.start,
            )
            val built = when {
                // 没开多角色朗读：官方解析出来的那个音色就是这句的声音，正则没顶到它就原样留着
                !multiRole -> item.copy(
                    segment = segment,
                    voice = regexVoice ?: item.voice,
                    fallbackVoices = item.fallbackVoices,
                    characterPerformance = item.characterPerformance,
                )

                character == null -> item.copy(
                    segment = segment.copy(
                        roleType = SpeechRoleType.Narrator,
                        characterId = null,
                        characterName = "",
                        voiceEffect = span?.effect.orEmpty(),
                    ),
                    voice = regexVoice ?: narrator,
                    fallbackVoices = emptyList(),
                    characterPerformance = null,
                )

                else -> {
                    val resolved = picked.getOrPut(character.id) {
                        CastVoicePicker.ensureVoice(character)
                    }
                    val characterVoice = voices[resolved.voiceId]
                        ?: gateway.bindingVoice(bookUrl, resolved.id, voices)
                    if (characterVoice == null) {
                        // 角色音丢掉、退回旁白是「换了分配却听不出来」的直接原因：
                        // 音色被停用/删除，或配音页绑的 id 已经不在音色表里
                        AppLog.put(
                            "多角色朗读: 角色「${resolved.name}」的音色 ${resolved.voiceId} " +
                                "不在启用的音色表里，这句按旁白读",
                        )
                    } else {
                        voiced++
                        if (sample.isEmpty()) {
                            sample.append("${resolved.name}=${characterVoice.displayName}")
                                .append('/').append(characterVoice.engineType)
                                .append('/').append(characterVoice.engineId)
                                .append(" speaker=").append(characterVoice.speakerId.ifBlank { "空" })
                                .append(" 旁白=").append(narrator?.displayName ?: "无")
                                .append('/').append(narrator?.engineType ?: "-")
                        }
                    }
                    item.copy(
                        segment = segment.copy(
                            roleType = SpeechRoleType.Character,
                            characterId = resolved.id,
                            characterName = resolved.name,
                            voiceEffect = span.effect,
                        ),
                        voice = regexVoice ?: characterVoice ?: narrator,
                        fallbackVoices = listOfNotNull(
                            narrator?.takeIf { it.id != (regexVoice ?: characterVoice)?.id },
                        ),
                        characterPerformance = item.characterPerformance
                            ?.takeIf { it.characterId == resolved.id },
                    )
                }
            }
            // 音效串整条透传（格式契约在 RegexCastSplitter 底部），下游经
            // ReadAloudPlaybackCue.soundEffect 到 BaseReadAloudService.takeCueSounds 解析。
            // 变声器同理跟着这一单元走：写进 segment.voiceEffect，下游 ReadAloudPlaybackQueue.from
            // 原样搬到 ReadAloudPlaybackCue，朗读服务两处都读它（音高/语速 + 会话混响/金属感）。
            // 优先级与音色一致：正则角色 > 分配表那一段（span.effect）。
            // 音色没顶上（退回原声）时连变声一起丢，否则「按原声读」的日志就成了原声加混响。
            val regexEffect = part.voiceEffect.takeIf {
                it.isNotBlank() && part.voiceId != null && voices.containsKey(part.voiceId)
            }
            return built.copy(
                soundEffect = part.sound,
                segment = regexEffect?.let { built.segment.copy(voiceEffect = it) } ?: built.segment,
            )
        }

        plan.forEach { item ->
            piecesOf(item, spans).forEach { piece ->
                val parts = if (effects.isEmpty()) {
                    listOf(RegexCastSplitter.Part(piece.start, piece.text, null, ""))
                } else {
                    RegexCastSplitter
                        .split(piece.start, piece.text, CastMarkers.blank(piece.text), effects)
                        .let { split ->
                            carrySound = RegexCastSplitter.mergeSound(carrySound, split.trailingSound)
                            split.parts
                        }
                }
                parts.forEach { part -> result += speechFor(item, part, piece.span) }
            }
        }
        if (carrySound.isNotEmpty() && result.isNotEmpty()) {
            // 章末只剩音效、后面没有单元可挂：并到最后一个朗读单元上，随它起播响
            val last = result.lastIndex
            result[last] = result[last].copy(
                soundEffect = RegexCastSplitter.mergeSound(result[last].soundEffect, carrySound)
            )
        }
        // 只剩空白的段不进队列（见 dropSilentUnits）
        val (kept, blankDropped) = dropSilentUnits(result)
        AppLog.put(
            "多角色朗读: 本章 ${plan.size} 个朗读单元，$voiced 段用角色音" +
                if (effects.isEmpty()) {
                    ""
                } else {
                    "；正则角色顶音色 $regexVoiced 段、吞字放音效 $regexMuted 段"
                } +
                "；丢掉 $blankDropped 个空白单元（无声占位）" +
                "；$sample"
        )
        return kept
    }

    /**
     * 丢掉「念不出东西」的朗读单元，返回（留下的单元，丢掉的个数）。
     *
     * 这种单元送进引擎只会得到一段无声音频——`res/raw/silent_sound.mp3` 长 1224 毫秒
     * （17 帧），听感就是段与段之间、以及正则命中前凭空顿一下。正文里的空行、
     * 命中正好落在段首缩进之后，都会造出这种单元。
     * 判空白用 [CastMarkers.strip]：只挂着角色标记的段同样念不出东西，而朗读服务那一步
     * 还会再去掉不可念字符，所以这里丢掉的必然也是它那边的静音。
     * 单元身上挂的音效不丢：并给下一个单元（章末则并回最后一个）。
     */
    internal fun dropSilentUnits(
        items: List<SpeechPlanItem>,
    ): Pair<List<SpeechPlanItem>, Int> {
        val kept = ArrayList<SpeechPlanItem>(items.size)
        var blankSound = ""
        var dropped = 0
        items.forEach { item ->
            if (CastMarkers.strip(item.segment.text).isBlank()) {
                blankSound = RegexCastSplitter.mergeSound(blankSound, item.soundEffect)
                dropped++
                return@forEach
            }
            kept += if (blankSound.isEmpty()) {
                item
            } else {
                item.copy(soundEffect = RegexCastSplitter.mergeSound(blankSound, item.soundEffect))
            }
            blankSound = ""
        }
        if (blankSound.isNotEmpty() && kept.isNotEmpty()) {
            val last = kept.lastIndex
            kept[last] = kept[last].copy(
                soundEffect = RegexCastSplitter.mergeSound(kept[last].soundEffect, blankSound)
            )
        }
        return kept to dropped
    }

    /**
     * 把一个朗读单元按「谁在说」切成连续片段。
     *
     * 「整段」「整页」划分下一个单元就是一整段，旁白和已分配台词混在里面：只按单元起点判断
     * 归属（早先的做法）会让整段被判给旁白，表现就是划分方式一改整章角色音全丢。
     * 这里在台词边界上切开，每块各用各的声音；句级划分下台词本来就自成一个单元，
     * 切完还是一块，行为不变。同角色的相邻片段合并，不在台词中间制造停顿。
     */
    internal fun piecesOf(item: SpeechPlanItem, spans: List<Span>): List<Piece> {
        val raw = item.segment.text
        // 抹平版只用来判「这块除了标记还剩不剩下可念的字」，等长替换保证下标两边一致
        val blanked = CastMarkers.blank(raw)
        val base = item.segment.chapterPosition
        val end = base + raw.length
        val own = spans.filter { it.start < end && it.end > base }.sortedBy { it.start }
        if (own.isEmpty()) return listOf(Piece(base, raw, null))
        val cuts = buildList {
            add(base)
            own.forEach {
                add(it.start.coerceIn(base, end))
                add(it.end.coerceIn(base, end))
            }
            add(end)
        }.distinct().sorted()
        val pieces = ArrayList<Piece>()
        for (index in cuts.indices.drop(1)) {
            val from = cuts[index - 1]
            val to = cuts[index]
            if (to <= from) continue
            // 取起点最靠后的那条：嵌套引号都分配过时，念的是里层那个角色
            val span = own.filter { it.start <= from && from < it.end }.maxByOrNull { it.start }
            val slice = raw.substring(from - base, to - base)
            val tail = pieces.lastIndex
            if (tail >= 0 && pieces[tail].span === span) {
                pieces[tail] = Piece(pieces[tail].start, pieces[tail].text + slice, span)
            } else {
                pieces.add(Piece(from, slice, span))
            }
        }
        // 只剩标点/空白（含只有标记）的碎片不值得单独成一句
        return pieces.filter {
            blanked.substring(it.start - base, it.start - base + it.text.length).isNotBlank()
        }.ifEmpty { listOf(Piece(base, raw, null)) }
    }

    /** 单元内一段同一说话人的范围：章内绝对起点 [start] + 该范围的文字（标记原样带着）。 */
    internal data class Piece(
        val start: Int,
        val text: String,
        val span: Span?,
    )

    /**
     * 从带标记的正文里量出每段已分配对话的范围。
     *
     * 标记只在开引号后出现一次，而一句对话常被标点切成多段（默认划分方式就切在
     * 「。！？…」，段与段之间还可能是不同的朗读单元），所以范围必须按引号配对算，
     * 且**跨单元共用一个引号栈**：闭引号落在后面的单元里也要能收口，否则对话后半句
     * 会掉回旁白。只有首段带标记，其余段靠落在这对引号之间认领。
     * 收尾引号缺失（漏引号、章末未完）时读到该单元末尾。
     */
    /** 栈里的一对未闭合引号：期望的闭引号、开引号的章内绝对位置、引号内标记的坐标、锚点序号。 */
    private class Open(
        val close: Char,
        val at: Int,
        val marker: Pair<Int, Int>?,
        val ordinal: Int,
    )

    internal fun spansOf(
        paragraphs: List<CanonicalSpeechParagraph>,
        effectsOfQuote: Map<Int, String> = emptyMap(),
    ): List<Span> {
        val markers = paragraphs.map { CastMarkers.findMarkers(it.text) }
        val claimed = markers.map { BooleanArray(it.size) }
        val spans = ArrayList<Span>()
        // 与注入/胶囊同一套锚点计数：段级变声器存在分配表里，靠引号序号才认领得到
        val tracker = CastMarkers.CastQuoteTracker()
        // 栈顶 = 未闭合的引号（见 [Open]）
        val stack = ArrayList<Open>()
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val text = paragraph.text
            if (text.isEmpty()) return@forEachIndexed
            val base = paragraph.chapterPosition
            val own = markers[paragraphIndex]
            for ((offset, ch) in text.withIndex()) {
                val isAnchor = tracker.feed(ch)
                val top = stack.lastOrNull()
                if (top != null && ch == top.close) {
                    stack.removeAt(stack.size - 1)
                    val located = top.marker
                    if (located != null) {
                        val (markerParagraph, markerIndex) = located
                        claimed[markerParagraph][markerIndex] = true
                        val marker = markers[markerParagraph][markerIndex]
                        spans += Span(
                            start = top.at,
                            // 闭引号算进台词里：整段划分会正好在闭引号处切开范围，
                            // 不含它就等于把句尾那个引号丢给下一个说话人
                            end = base + offset + 1,
                            name = marker.name,
                            pool = marker.voicePoolLabel,
                            ordinal = top.ordinal,
                            effect = effectsOfQuote[top.ordinal].orEmpty(),
                        )
                    }
                    continue
                }
                for ((open, close) in CastMarkers.QuotePairs) {
                    if (ch == open) {
                        stack.add(
                            Open(
                                close = close,
                                at = base + offset,
                                marker = own.indexOfFirst { m -> m.start == offset + 1 }
                                    .takeIf { it >= 0 }
                                    ?.let { paragraphIndex to it },
                                ordinal = if (isAnchor) tracker.lastCastOrdinal else -1,
                            ),
                        )
                        break
                    }
                }
            }
        }
        // 到章末仍没配到闭引号的：从开引号读到它所在单元的末尾。
        stack.forEach { open ->
            val located = open.marker
            if (located != null) {
                val (markerParagraph, markerIndex) = located
                if (!claimed[markerParagraph][markerIndex]) {
                    claimed[markerParagraph][markerIndex] = true
                    val marker = markers[markerParagraph][markerIndex]
                    val text = paragraphs[markerParagraph].text
                    spans += Span(
                        start = open.at,
                        end = paragraphs[markerParagraph].chapterPosition + text.length,
                        name = marker.name,
                        pool = marker.voicePoolLabel,
                        ordinal = open.ordinal,
                        effect = effectsOfQuote[open.ordinal].orEmpty(),
                    )
                }
            }
        }
        markers.forEachIndexed { paragraphIndex, own ->
            val base = paragraphs[paragraphIndex].chapterPosition
            own.forEachIndexed { index, marker ->
                if (!claimed[paragraphIndex][index]) {
                    spans += Span(
                        base + marker.start,
                        base + paragraphs[paragraphIndex].text.length,
                        marker.name,
                        marker.voicePoolLabel,
                    )
                }
            }
        }
        return spans
    }

    private fun List<CastCharacter>.match(name: String, pool: String): CastCharacter? =
        firstOrNull { it.name == name && it.poolLabel == pool }
            ?: firstOrNull { it.name == name }

    /** 配音页给某个主体绑定的音色；旁白的 subjectId 就是它自己。 */
    private suspend fun ReadAloudVoiceGateway.bindingVoice(
        bookUrl: String,
        subjectId: String,
        voices: Map<String, ReadAloudVoice>,
    ): ReadAloudVoice? {
        val subjectType = if (subjectId == BookVoiceBinding.SUBJECT_NARRATOR) {
            BookVoiceBinding.SUBJECT_NARRATOR
        } else {
            BookVoiceBinding.SUBJECT_CHARACTER
        }
        return getBinding(bookUrl, subjectType, subjectId)?.voiceId?.let(voices::get)
    }
}
