package io.legado.app.help.readaloud

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.help.ai.AiReadAloudRoleService
import io.legado.app.help.readaloud.speech.SpeechRoute
import io.legado.app.help.readaloud.speech.SpeechRouteSanitizer
import io.legado.app.ui.book.read.page.entities.ReadAloudCue
import io.legado.app.ui.book.read.page.entities.TextChapter

data class ReadAloudSpeechPlan(
    val cues: List<ReadAloudCue>,
    val routes: List<SpeechRoute?>,
    val items: List<ReadAloudSpeechPlanItem>
)

data class ReadAloudSpeechPlanItem(
    val index: Int,
    val cue: ReadAloudCue,
    val route: SpeechRoute?,
    val roleType: String,
    val characterId: Long,
    val characterName: String,
    val avatar: String,
    val emotionName: String,
    val roleLevel: Int = BookCharacter.ROLE_NORMAL,
    val leftSide: Boolean,
    val narrator: Boolean,
    val sourceCueIndex: Int,
    val sourceStart: Int,
    val sourceEnd: Int
)

object ReadAloudSpeechPlanner {

    private const val LOCAL_DIALOGUE_CUE_GAP = 2

    fun planKey(
        bookUrl: String?,
        chapter: TextChapter?,
        cues: List<ReadAloudCue>,
        roleCacheKey: String? = null
    ): String {
        return buildString {
            append(bookUrl.orEmpty())
            append('|')
            append(chapter?.chapter?.index ?: -1)
            append('|')
            append(chapter?.chapter?.url.orEmpty())
            append('|')
            append(cues.size)
            append('|')
            append(cues.firstOrNull()?.key.orEmpty())
            append('|')
            append(cues.lastOrNull()?.key.orEmpty())
            append('|')
            append(cues.sumOf { it.text.length })
            append('|')
            append(roleCacheKey.orEmpty())
        }
    }

    fun build(
        bookUrl: String?,
        chapter: TextChapter,
        baseCues: List<ReadAloudCue>,
        multiRoleEnabled: Boolean,
        roleCacheKey: String? = null
    ): ReadAloudSpeechPlan {
        if (baseCues.isEmpty()) {
            return ReadAloudSpeechPlan(emptyList(), emptyList(), emptyList())
        }
        if (!multiRoleEnabled || bookUrl.isNullOrBlank()) {
            val items = baseCues.mapIndexed { index, cue ->
                ReadAloudSpeechPlanItem(
                    index = index,
                    cue = cue.copy(index = index),
                    route = null,
                    roleType = "narrator",
                    characterId = 0L,
                    characterName = "旁白",
                    avatar = "",
                    emotionName = "",
                    roleLevel = BookCharacter.ROLE_NORMAL,
                    leftSide = false,
                    narrator = true,
                    sourceCueIndex = cue.index,
                    sourceStart = 0,
                    sourceEnd = cue.text.length
                )
            }
            return ReadAloudSpeechPlan(
                cues = items.map { it.cue },
                routes = List(items.size) { null },
                items = items
            )
        }

        val characters = appDb.bookCharacterDao.characters(bookUrl)
        val byId = characters.associateBy { it.id }
        val byName = charactersByNormalizedName(characters)
        val assignedSegmentsByCue = AiReadAloudRoleService.assignedSegmentsByCue(
            bookUrl = bookUrl,
            chapterIndex = chapter.chapter.index,
            cacheKey = roleCacheKey
        )
        val plannedItems = arrayListOf<ReadAloudSpeechPlanItem>()
        baseCues.forEachIndexed { cueIndex, cue ->
            val beforeSize = plannedItems.size
            val segments = completeCueSegments(
                cueIndex = cueIndex,
                cueText = cue.text,
                rawSegments = assignedSegmentsByCue[cueIndex].orEmpty()
            )
            segments.forEach { segment ->
                val text = cue.text.substring(segment.start, segment.end)
                if (text.isEmpty()) return@forEach
                val chapterPosition = cue.chapterPosition + segment.start
                val pageIndex = chapter.getPageIndexByCharIndex(chapterPosition)
                    .takeIf { it >= 0 }
                    ?: cue.pageIndex
                val pageStartPos = (chapterPosition - chapter.getReadLength(pageIndex)).coerceAtLeast(0)
                val character = resolveCharacter(segment, byId, byName)
                val displayName = when {
                    segment.roleType == "narrator" -> "旁白"
                    character != null -> character.displayName()
                    segment.characterName.isNotBlank() -> segment.characterName
                    segment.roleType == "thought" -> "心理"
                    else -> "角色"
                }
                val characterId = character?.id ?: segment.characterId
                val route = routeForSegment(character, segment)
                val plannedCue = ReadAloudCue(
                    index = plannedItems.size,
                    text = text,
                    chapterPosition = chapterPosition,
                    pageIndex = pageIndex,
                    pageStartPos = pageStartPos,
                    key = "${cue.key}:role:${segment.start}:${segment.end}:${text.hashCode()}"
                )
                plannedItems += ReadAloudSpeechPlanItem(
                    index = plannedItems.size,
                    cue = plannedCue,
                    route = route,
                    roleType = segment.roleType,
                    characterId = characterId,
                    characterName = displayName,
                    avatar = character?.avatar.orEmpty(),
                    emotionName = segment.emotionName.ifBlank { route?.emotionName.orEmpty() },
                    roleLevel = character?.roleLevel ?: BookCharacter.ROLE_NORMAL,
                    leftSide = true,
                    narrator = segment.roleType == "narrator" || displayName == "旁白",
                    sourceCueIndex = cueIndex,
                    sourceStart = segment.start,
                    sourceEnd = segment.end
                )
            }
            if (plannedItems.size == beforeSize) {
                val plannedCue = cue.copy(index = plannedItems.size)
                plannedItems += ReadAloudSpeechPlanItem(
                    index = plannedItems.size,
                    cue = plannedCue,
                    route = null,
                    roleType = "narrator",
                    characterId = 0L,
                    characterName = "旁白",
                    avatar = "",
                    emotionName = "",
                    roleLevel = BookCharacter.ROLE_NORMAL,
                    leftSide = false,
                    narrator = true,
                    sourceCueIndex = cueIndex,
                    sourceStart = 0,
                    sourceEnd = cue.text.length
                )
            }
        }
        val displayItems = assignConversationSides(plannedItems)
        return ReadAloudSpeechPlan(
            cues = displayItems.map { it.cue },
            routes = displayItems.map { it.route },
            items = displayItems
        )
    }

    private fun resolveCharacter(
        segment: AiReadAloudRoleService.Segment,
        byId: Map<Long, BookCharacter>,
        byName: Map<String, BookCharacter>
    ): BookCharacter? {
        return when {
            segment.characterId > 0L -> byId[segment.characterId]
            segment.characterName.isNotBlank() -> byName[characterNameKey(segment.characterName)]
            else -> null
        }
    }

    private fun charactersByNormalizedName(characters: List<BookCharacter>): Map<String, BookCharacter> {
        val result = linkedMapOf<String, BookCharacter>()
        characters.forEach { character ->
            listOf(character.name, character.displayName())
                .map(::characterNameKey)
                .filter { it.isNotBlank() }
                .forEach { key -> result.putIfAbsent(key, character) }
        }
        return result
    }

    private fun characterNameKey(value: String): String {
        return value.trim()
            .replace(Regex("\\s+"), "")
            .trim('《', '》', '“', '”', '"', '\'', '：', ':')
            .lowercase()
    }

    private fun routeForSegment(
        character: BookCharacter?,
        segment: AiReadAloudRoleService.Segment
    ): SpeechRoute? {
        val route = SpeechRouteSanitizer.validOrNull(SpeechRoute.fromJson(character?.speechRouteJson))
            ?: return null
        if (!route.isConfigured) return null
        val emotionName = segment.emotionName.trim()
        val emotionTag = segment.emotionTag.trim()
        return if (emotionName.isNotBlank() || emotionTag.isNotBlank()) {
            route.copy(emotionName = emotionName, emotionTag = emotionTag)
        } else {
            route
        }
    }

    private fun assignConversationSides(
        items: List<ReadAloudSpeechPlanItem>
    ): List<ReadAloudSpeechPlanItem> {
        if (items.none { !it.narrator }) return items
        val result = items.toMutableList()
        val block = mutableListOf<Int>()
        var lastSpeechCue = -1

        fun flushBlock() {
            if (block.isEmpty()) return
            val speechItems = block.map { result[it] }
            val rightRoleLevel = when {
                speechItems.any { it.roleLevel == BookCharacter.ROLE_MAIN } -> BookCharacter.ROLE_MAIN
                speechItems.any { it.roleLevel == BookCharacter.ROLE_IMPORTANT } -> BookCharacter.ROLE_IMPORTANT
                else -> BookCharacter.ROLE_NORMAL
            }
            val rightSpeakerByLevel = mutableMapOf<Int, String>()
            val sideBySpeaker = mutableMapOf<String, Boolean>()
            block.forEach { index ->
                val item = result[index]
                val speakerKey = item.speakerKey()
                val leftSide = sideBySpeaker.getOrPut(speakerKey) {
                    val roleLevel = item.roleLevel.coerceIn(
                        BookCharacter.ROLE_NORMAL,
                        BookCharacter.ROLE_MAIN
                    )
                    if (roleLevel != rightRoleLevel) {
                        true
                    } else {
                        val rightSpeaker = rightSpeakerByLevel[roleLevel]
                        if (rightSpeaker == null || rightSpeaker == speakerKey) {
                            rightSpeakerByLevel[roleLevel] = speakerKey
                            false
                        } else {
                            true
                        }
                    }
                }
                result[index] = item.copy(leftSide = leftSide)
            }
            block.clear()
        }

        items.forEachIndexed { index, item ->
            if (item.narrator) return@forEachIndexed
            if (block.isNotEmpty() && item.sourceCueIndex - lastSpeechCue > LOCAL_DIALOGUE_CUE_GAP) {
                flushBlock()
            }
            block += index
            lastSpeechCue = item.sourceCueIndex
        }
        flushBlock()
        return result
    }

    private fun ReadAloudSpeechPlanItem.speakerKey(): String {
        return if (characterId > 0L) {
            "id:$characterId"
        } else {
            "name:$roleType:$characterName"
        }
    }

    private fun completeCueSegments(
        cueIndex: Int,
        cueText: String,
        rawSegments: List<AiReadAloudRoleService.Segment>
    ): List<AiReadAloudRoleService.Segment> {
        if (cueText.isEmpty()) return emptyList()
        val normalized = rawSegments
            .mapNotNull { segment ->
                val start = segment.start.coerceIn(0, cueText.length)
                val end = segment.end.coerceIn(start, cueText.length)
                if (start >= end) null else segment.copy(start = start, end = end)
            }
            .sortedWith(compareBy<AiReadAloudRoleService.Segment> { it.start }.thenBy { it.end })
        val result = mutableListOf<AiReadAloudRoleService.Segment>()
        var cursor = 0
        normalized.forEach { segment ->
            if (segment.end <= cursor) return@forEach
            if (segment.start > cursor) {
                result += narratorSegment(cueIndex, cursor, segment.start)
            }
            val start = segment.start.coerceAtLeast(cursor)
            if (start < segment.end) {
                result += segment.copy(start = start)
                cursor = segment.end
            }
        }
        if (cursor < cueText.length) {
            result += narratorSegment(cueIndex, cursor, cueText.length)
        }
        return mergeAdjacentSegments(result)
    }

    private fun narratorSegment(
        cueIndex: Int,
        start: Int,
        end: Int
    ): AiReadAloudRoleService.Segment {
        return AiReadAloudRoleService.Segment(
            paragraphIndex = cueIndex,
            start = start,
            end = end,
            roleType = "narrator",
            characterName = "旁白",
            confidence = 0.5
        )
    }

    private fun mergeAdjacentSegments(
        segments: List<AiReadAloudRoleService.Segment>
    ): List<AiReadAloudRoleService.Segment> {
        if (segments.size <= 1) return segments
        val result = mutableListOf<AiReadAloudRoleService.Segment>()
        segments.forEach { segment ->
            val last = result.lastOrNull()
            if (last != null &&
                last.end == segment.start &&
                last.roleType == segment.roleType &&
                last.characterId == segment.characterId &&
                last.characterName == segment.characterName &&
                last.emotionName == segment.emotionName &&
                last.emotionTag == segment.emotionTag
            ) {
                result[result.lastIndex] = last.copy(end = segment.end)
            } else {
                result += segment
            }
        }
        return result
    }
}
