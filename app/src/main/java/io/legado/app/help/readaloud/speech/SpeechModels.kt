package io.legado.app.help.readaloud.speech

import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.HttpTTS
import io.legado.app.help.character.BookCharacterProfileMeta
import org.json.JSONArray
import org.json.JSONObject

data class SpeechSpeaker(
    val speakerName: String = "",
    val toneID: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val tags: List<String> = emptyList()
) {
    val valid: Boolean
        get() = speakerName.isNotBlank() && toneID.isNotBlank()
}

data class SpeechEmotion(
    val emotionName: String = "",
    val emotionTag: String = "",
    val groupId: String = "",
    val groupName: String = ""
) {
    val valid: Boolean
        get() = emotionName.isNotBlank() && emotionTag.isNotBlank()
}

data class SpeechCatalogGroup<T>(
    val groupId: String = "",
    val groupName: String = "",
    val items: List<T> = emptyList()
)

data class SpeechRoute(
    val engineType: String = ENGINE_DEFAULT,
    val engineValue: String = "",
    val speakerName: String = "",
    val toneID: String = "",
    val emotionName: String = "",
    val emotionTag: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val source: String = ""
) {
    val isConfigured: Boolean
        get() = engineValue.isNotBlank() || toneID.isNotBlank() || speakerName.isNotBlank()

    fun withEmotion(emotion: SpeechEmotion?): SpeechRoute {
        return if (emotion?.valid == true) {
            copy(emotionName = emotion.emotionName, emotionTag = emotion.emotionTag)
        } else {
            this
        }
    }

    fun toJson(): String {
        if (!isConfigured && emotionName.isBlank() && emotionTag.isBlank()) return ""
        return JSONObject().apply {
            put("engineType", engineType)
            put("engineValue", engineValue)
            put("speakerName", speakerName)
            put("toneID", toneID)
            put("emotionName", emotionName)
            put("emotionTag", emotionTag)
            put("groupId", groupId)
            put("groupName", groupName)
            put("source", source)
        }.toString()
    }

    companion object {
        const val ENGINE_DEFAULT = "default"
        const val ENGINE_SYSTEM = "system"
        const val ENGINE_HTTP = "http"
        const val SOURCE_AUTO = "auto"
        const val SOURCE_MANUAL = "manual"

        fun fromJson(json: String?): SpeechRoute {
            if (json.isNullOrBlank()) return SpeechRoute()
            return runCatching {
                val obj = JSONObject(json)
                SpeechRoute(
                    engineType = obj.optString("engineType", ENGINE_DEFAULT),
                    engineValue = obj.optString("engineValue"),
                    speakerName = obj.optString("speakerName"),
                    toneID = obj.optString("toneID").ifBlank { obj.optString("toneId") },
                    emotionName = obj.optString("emotionName"),
                    emotionTag = obj.optString("emotionTag"),
                    groupId = obj.optString("groupId"),
                    groupName = obj.optString("groupName"),
                    source = obj.optString("source")
                )
            }.getOrDefault(SpeechRoute())
        }

        fun fromTtsEngineValue(value: String?): SpeechRoute {
            val raw = value?.trim().orEmpty()
            if (raw.isBlank()) {
                return SpeechRoute(
                    engineType = ENGINE_SYSTEM,
                    engineValue = "",
                    speakerName = "系统默认",
                    source = SOURCE_MANUAL
                )
            }
            if (raw.toLongOrNull() != null) {
                return SpeechRoute(
                    engineType = ENGINE_HTTP,
                    engineValue = raw,
                    source = SOURCE_MANUAL
                )
            }
            return runCatching {
                val obj = JSONObject(raw)
                if (
                    obj.has("engineType") ||
                    obj.has("speakerName") ||
                    obj.has("toneID") ||
                    obj.has("toneId") ||
                    obj.has("emotionTag")
                ) {
                    fromJson(raw)
                } else {
                    SpeechRoute(
                        engineType = ENGINE_SYSTEM,
                        engineValue = raw,
                        speakerName = obj.optString("title").ifBlank { "系统默认" },
                        source = SOURCE_MANUAL
                    )
                }
            }.getOrDefault(
                SpeechRoute(
                    engineType = ENGINE_SYSTEM,
                    engineValue = raw,
                    speakerName = "系统默认",
                    source = SOURCE_MANUAL
                )
            )
        }
    }
}

data class SpeechSegment(
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val roleType: String,
    val characterId: Long = 0L,
    val characterName: String = "",
    val emotionName: String = "",
    val emotionTag: String = "",
    val confidence: Double = 0.0
)

data class SpeechCharacterCandidate(
    val name: String,
    val identity: String = "",
    val roleLevel: Int = BookCharacter.ROLE_NORMAL,
    val confidence: Double = 0.0,
    val evidence: String = ""
)

object SpeechVoiceCatalogParser {

    fun parseSpeakerGroups(json: String?): List<SpeechCatalogGroup<SpeechSpeaker>> {
        return parseGroups(json, ::speakerFromJson).map { group ->
            group.copy(items = group.items.filter { it.valid })
        }.filter { it.items.isNotEmpty() }
    }

    fun parseEmotionGroups(json: String?): List<SpeechCatalogGroup<SpeechEmotion>> {
        return parseGroups(json, ::emotionFromJson).map { group ->
            group.copy(items = group.items.filter { it.valid })
        }.filter { it.items.isNotEmpty() }
    }

    fun flattenSpeakers(json: String?): List<SpeechSpeaker> {
        return parseSpeakerGroups(json).flatMap { it.items }
    }

    fun flattenEmotions(json: String?): List<SpeechEmotion> {
        return parseEmotionGroups(json).flatMap { it.items }
    }

    private fun <T> parseGroups(
        json: String?,
        itemParser: (JSONObject, String, String) -> T
    ): List<SpeechCatalogGroup<T>> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull()
            ?: runCatching { JSONArray().put(JSONObject(json)) }.getOrNull()
            ?: return emptyList()
        val groups = mutableListOf<SpeechCatalogGroup<T>>()
        val flatItems = mutableListOf<T>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val items = obj.optJSONArray("items")
            if (items == null) {
                flatItems += itemParser(obj, "", "")
            } else {
                val groupId = obj.optString("groupId").ifBlank { obj.optString("id") }
                val groupName = obj.optString("groupName").ifBlank { obj.optString("name") }
                val parsedItems = mutableListOf<T>()
                for (itemIndex in 0 until items.length()) {
                    val item = items.optJSONObject(itemIndex) ?: continue
                    parsedItems += itemParser(item, groupId, groupName)
                }
                groups += SpeechCatalogGroup(groupId, groupName, parsedItems)
            }
        }
        if (flatItems.isNotEmpty()) {
            groups.add(0, SpeechCatalogGroup(groupName = "默认", items = flatItems))
        }
        return groups
    }

    private fun speakerFromJson(
        obj: JSONObject,
        groupId: String,
        groupName: String
    ): SpeechSpeaker {
        return SpeechSpeaker(
            speakerName = obj.optString("speakerName").ifBlank { obj.optString("name") },
            toneID = obj.optString("toneID")
                .ifBlank { obj.optString("toneId") }
                .ifBlank { obj.optString("tone_id") },
            groupId = groupId,
            groupName = groupName,
            tags = obj.optJSONArray("tags")?.let { tags ->
                buildList {
                    for (index in 0 until tags.length()) {
                        tags.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.orEmpty()
        )
    }

    private fun emotionFromJson(
        obj: JSONObject,
        groupId: String,
        groupName: String
    ): SpeechEmotion {
        return SpeechEmotion(
            emotionName = obj.optString("emotionName").ifBlank { obj.optString("name") },
            emotionTag = obj.optString("emotionTag")
                .ifBlank { obj.optString("tag") }
                .ifBlank { obj.optString("value") },
            groupId = groupId,
            groupName = groupName
        )
    }
}

object SpeechVoiceAssigner {

    private data class RouteCandidate(
        val route: SpeechRoute,
        val searchableText: String
    )

    fun assignRoute(
        character: BookCharacter,
        httpTtsList: List<HttpTTS>
    ): SpeechRoute {
        val gender = BookCharacter.normalizeGender(character.gender)
        val ageKeywords = ageKeywords(character)
        val candidates = routesForCharacter(character, httpTtsList, gender, ageKeywords)
            .ifEmpty {
                SpeechVoiceGroupRepository.assignableRoutes(httpTtsList)
                    .ifEmpty { SpeechVoiceCatalogRepository.assignableRoutes(httpTtsList) }
            }
        if (candidates.isEmpty()) return SpeechRoute()
        return stablePick(character, candidates).copy(source = SpeechRoute.SOURCE_AUTO)
    }

    private fun routesForCharacter(
        character: BookCharacter,
        httpTtsList: List<HttpTTS>,
        gender: String,
        ageKeywords: List<String>
    ): List<SpeechRoute> {
        val managedCandidates = SpeechVoiceGroupRepository.managedGroups(httpTtsList)
            .flatMap { group ->
                group.items.mapNotNull { item ->
                    RouteCandidate(
                        route = item.toSpeechRoute(),
                        searchableText = listOf(
                            group.group.name,
                            item.speakerName,
                            item.engineName,
                            item.sourceGroupName
                        ).joinToString(" ")
                    )
                }
            }
        rankedRoutes(managedCandidates, gender, ageKeywords)?.let { return it }

        val catalogCandidates = SpeechVoiceCatalogRepository.httpGroups(httpTtsList)
            .flatMap { group ->
                group.options.map { option ->
                    val route = option.toRoute(group.emotions.firstOrNull(), SpeechRoute.SOURCE_AUTO)
                    RouteCandidate(
                        route = route,
                        searchableText = listOf(
                            group.title,
                            option.speakerName,
                            option.engineName,
                            option.groupName,
                            option.groupId
                        ).joinToString(" ")
                    )
                }
            }
            .filterNot { SpeechVoiceGroupRepository.isBlockedRoute(it.route) }
        rankedRoutes(catalogCandidates, gender, ageKeywords)?.let { return it }

        val inferredGender = BookCharacter.inferGender(
            listOf(character.name, character.identity, character.biography).joinToString(" ")
        )
        return if (inferredGender.isNotBlank() && inferredGender != gender) {
            routesForCharacter(character.copy(gender = inferredGender), httpTtsList, inferredGender, ageKeywords)
        } else {
            emptyList()
        }
    }

    private fun rankedRoutes(
        candidates: List<RouteCandidate>,
        gender: String,
        ageKeywords: List<String>
    ): List<SpeechRoute>? {
        if (candidates.isEmpty()) return null
        val genderMatched = if (gender.isBlank()) {
            emptyList()
        } else {
            candidates.filter { matchesGender(gender, it.searchableText) }
        }
        val ageMatched = if (ageKeywords.isEmpty()) {
            emptyList()
        } else {
            candidates.filter { matchesAge(ageKeywords, it.searchableText) }
        }
        val genderAgeMatched = if (genderMatched.isNotEmpty() && ageKeywords.isNotEmpty()) {
            genderMatched.filter { matchesAge(ageKeywords, it.searchableText) }
        } else {
            emptyList()
        }
        val picked = when {
            genderAgeMatched.isNotEmpty() -> genderAgeMatched
            genderMatched.isNotEmpty() -> genderMatched
            ageMatched.isNotEmpty() -> ageMatched
            else -> return null
        }
        return picked.map { it.route }.distinctBy { it.routeKey() }
    }

    private fun stablePick(character: BookCharacter, candidates: List<SpeechRoute>): SpeechRoute {
        val stableKey = "${character.bookUrl}|${character.id}|${character.name}"
        val index = Math.floorMod(stableKey.hashCode(), candidates.size)
        return candidates[index]
    }

    private fun matchesGender(gender: String, vararg texts: String): Boolean {
        val text = texts.joinToString(" ").lowercase()
        return when (gender) {
            BookCharacter.GENDER_MALE -> maleVoiceKeywords.any { text.contains(it) } &&
                femaleVoiceKeywords.none { text.contains(it) }
            BookCharacter.GENDER_FEMALE -> femaleVoiceKeywords.any { text.contains(it) }
            else -> false
        }
    }

    private fun matchesAge(ageKeywords: List<String>, vararg texts: String): Boolean {
        if (ageKeywords.isEmpty()) return false
        val text = texts.joinToString(" ").lowercase()
        return ageKeywords.any { text.contains(it) }
    }

    private fun ageKeywords(character: BookCharacter): List<String> {
        val text = listOf(
            BookCharacterProfileMeta.ageOf(character),
            character.identity,
            character.attributes,
            character.biography
        ).joinToString(" ").lowercase()
        return when {
            listOf("幼", "童", "儿童", "小孩", "孩子", "萝莉", "正太").any { text.contains(it) } ->
                listOf("幼", "童", "儿童", "小孩", "孩子", "萝莉", "正太", "child", "kid")
            listOf("少年", "少女", "青少年").any { text.contains(it) } ->
                listOf("少年", "少女", "青少年", "boy", "girl", "young")
            listOf("青年", "年轻").any { text.contains(it) } ->
                listOf("青年", "年轻", "young")
            listOf("中年", "大叔", "大妈", "叔", "阿姨").any { text.contains(it) } ->
                listOf("中年", "大叔", "大妈", "叔", "阿姨", "middle")
            listOf("老人", "老年", "老者", "老头", "老太", "婆婆", "爷爷", "奶奶").any { text.contains(it) } ->
                listOf("老人", "老年", "老者", "老头", "老太", "婆婆", "爷爷", "奶奶", "old")
            else -> emptyList()
        }
    }

    private fun SpeechRoute.routeKey(): String {
        return "$engineType|$engineValue|$toneID|$speakerName"
    }

    private val maleVoiceKeywords = listOf("男", "男声", "男性", "male", "man", "boy", "少年", "青年", "大叔", "叔")
    private val femaleVoiceKeywords = listOf("女", "女声", "女性", "female", "woman", "girl", "少女", "姐姐", "妹妹", "萝莉")
}
