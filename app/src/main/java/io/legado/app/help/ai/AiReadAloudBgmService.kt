package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.AiReadAloudUsageRecord
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadAloudBgmAssignmentCache
import io.legado.app.data.entities.ReadAloudBgmGroup
import io.legado.app.data.entities.ReadAloudBgmTrack
import io.legado.app.help.config.AppConfig
import io.legado.app.help.readaloud.role.ReadAloudSoundEffectCandidate
import io.legado.app.help.readaloud.role.ReadAloudSoundEffectPreprocessor
import io.legado.app.ui.book.read.page.entities.ReadAloudCue
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AiReadAloudBgmService {

    const val TOOL_ASSIGN_BGM_RANGES = "assign_read_aloud_bgm_ranges"
    private const val SCHEMA_VERSION = 2
    private const val RUNNING_CACHE_STALE_MILLIS = 180_000L

    data class Assignment(
        val fromCueIndex: Int,
        val toCueIndex: Int,
        val trackId: Long,
        val volume: Float = 0.22f,
        val fadeInMs: Int = 1800,
        val fadeOutMs: Int = 1800,
        val reason: String = "",
        val confidence: Double = 0.0
    )

    data class ResolvedAssignment(
        val assignment: Assignment,
        val track: ReadAloudBgmTrack
    )

    data class SoundEffectEvent(
        val cueIndex: Int,
        val trackId: Long,
        val volume: Float = 0.72f,
        val offsetMs: Int = 0,
        val reason: String = "",
        val confidence: Double = 0.0
    )

    data class ResolvedSoundEffectEvent(
        val event: SoundEffectEvent,
        val track: ReadAloudBgmTrack
    )

    data class CachedAudioInfo(
        val cacheKey: String,
        val status: String,
        val assignmentCount: Int,
        val soundEffectCount: Int,
        val bgmTrackNames: List<String>,
        val soundEffectTrackNames: List<String>,
        val updatedAt: Long
    )

    data class EnsureResult(
        val status: String,
        val assignmentCount: Int = 0,
        val soundEffectCount: Int = 0,
        val message: String = "",
        val error: String = "",
        val cacheKey: String = ""
    )

    private data class CatalogSnapshot(
        val groups: List<ReadAloudBgmGroup>,
        val bgmTracks: List<ReadAloudBgmTrack>,
        val soundEffectTracks: List<ReadAloudBgmTrack>,
        val hash: String
    )

    private data class AudioAssignmentResult(
        val assignments: List<Assignment>,
        val soundEffects: List<SoundEffectEvent>
    )

    private data class BgmCacheKey(
        val cacheKey: String,
        val contentHash: String,
        val catalogHash: String,
        val modelId: String,
        val promptCacheKey: String
    )

    suspend fun ensureChapterAssignments(
        book: Book?,
        textChapter: TextChapter?,
        cues: List<ReadAloudCue>
    ): EnsureResult = withContext(IO) {
        if (!AppConfig.aiReadAloudBgmEnabled) {
            return@withContext EnsureResult(ReadAloudBgmAssignmentCache.STATUS_FAILED, message = "智能配乐未开启")
        }
        val modelConfig = AppConfig.aiReadAloudAudioModelConfig
            ?: return@withContext EnsureResult(ReadAloudBgmAssignmentCache.STATUS_FAILED, message = "未配置多角色模型")
        if (AppConfig.aiProviderForModel(modelConfig)?.baseUrl.isNullOrBlank()) {
            return@withContext EnsureResult(ReadAloudBgmAssignmentCache.STATUS_FAILED, message = "模型供应商不可用")
        }
        val currentBook = book ?: return@withContext EnsureResult(ReadAloudBgmAssignmentCache.STATUS_FAILED, message = "书籍为空")
        val currentChapter = textChapter
            ?: return@withContext EnsureResult(ReadAloudBgmAssignmentCache.STATUS_FAILED, message = "章节为空")
        val cleanCues = cues.filter { it.text.isNotBlank() }
        if (cleanCues.isEmpty()) {
            return@withContext EnsureResult(ReadAloudBgmAssignmentCache.STATUS_FAILED, message = "当前章节无可配乐文本")
        }
        val catalog = catalogSnapshot()
        val sfxCandidates = soundEffectCandidates(cleanCues)
        if (catalog.bgmTracks.isEmpty() && (catalog.soundEffectTracks.isEmpty() || sfxCandidates.isEmpty())) {
            return@withContext EnsureResult(ReadAloudBgmAssignmentCache.STATUS_FAILED, message = "暂无可用配乐或音效")
        }
        val key = buildCacheKey(currentBook, currentChapter, cleanCues, catalog.hash, modelConfig.id)
        appDb.readAloudBgmDao.latestAssignmentCache(
            currentBook.bookUrl,
            currentChapter.chapter.index,
            key.contentHash,
            key.catalogHash
        )?.takeIf { it.assignmentsJson.isNotBlank() }?.let { cache ->
            val cachedAudio = audioAssignmentsFromJson(cache.assignmentsJson)
            return@withContext EnsureResult(
                status = ReadAloudBgmAssignmentCache.STATUS_SUCCESS,
                assignmentCount = cachedAudio.assignments.size,
                soundEffectCount = cachedAudio.soundEffects.size,
                message = "当前章节智能音频已缓存",
                cacheKey = cache.cacheKey
            )
        }
        val now = System.currentTimeMillis()
        val old = appDb.readAloudBgmDao.assignmentCache(key.cacheKey)
        if (old?.status == ReadAloudBgmAssignmentCache.STATUS_RUNNING &&
            now - old.updatedAt <= RUNNING_CACHE_STALE_MILLIS
        ) {
            return@withContext EnsureResult(
                status = ReadAloudBgmAssignmentCache.STATUS_RUNNING,
                message = "智能配乐分析中",
                cacheKey = key.cacheKey
            )
        }
        appDb.readAloudBgmDao.upsertAssignmentCache(
            ReadAloudBgmAssignmentCache(
                cacheKey = key.cacheKey,
                bookUrl = currentBook.bookUrl,
                chapterKey = currentChapter.chapter.url?.ifBlank { currentChapter.chapter.title }.orEmpty(),
                chapterIndex = currentChapter.chapter.index,
                chapterTitle = currentChapter.chapter.title,
                contentHash = key.contentHash,
                modelId = key.modelId,
                catalogHash = key.catalogHash,
                status = ReadAloudBgmAssignmentCache.STATUS_RUNNING,
                createdAt = old?.createdAt?.takeIf { it > 0 } ?: now,
                updatedAt = now
            )
        )
        val usageTracker = AiReadAloudUsageRecorder.Tracker()
        try {
            usageTracker.onRequest()
            val response = AiChatService.requestSingleToolCall(
                messages = listOf(
                    AiChatMessage(
                        role = AiChatMessage.Role.USER,
                        content = buildAssignmentPrompt(currentBook, currentChapter, cleanCues, catalog, sfxCandidates)
                    )
                ),
                tool = AiResolvedTool(TOOL_ASSIGN_BGM_RANGES, assignmentToolDefinition()) {
                    JSONObject().put("ok", true).toString()
                },
                modelConfigOverride = modelConfig,
                fallbackModelConfig = AppConfig.aiReadAloudAudioBackupModelConfig,
                promptCacheKeyOverride = key.promptCacheKey,
                firstResponseTimeoutMillis = AppConfig.aiReadAloudRoleFirstResponseTimeoutMillis,
                includeChatContext = false,
                onUsage = usageTracker::onUsage
            )
            val audio = when {
                response.hasToolCall -> parseAudioAssignments(
                    JSONObject(response.arguments),
                    cleanCues.size,
                    catalog.bgmTracks,
                    catalog.soundEffectTracks
                )
                else -> parseAudioAssignmentsFromContent(
                    response.content,
                    cleanCues.size,
                    catalog.bgmTracks,
                    catalog.soundEffectTracks
                )
            }
            val json = assignmentsToJson(audio.assignments, audio.soundEffects, key.contentHash, key.catalogHash)
            appDb.readAloudBgmDao.upsertAssignmentCache(
                ReadAloudBgmAssignmentCache(
                    cacheKey = key.cacheKey,
                    bookUrl = currentBook.bookUrl,
                    chapterKey = currentChapter.chapter.url?.ifBlank { currentChapter.chapter.title }.orEmpty(),
                    chapterIndex = currentChapter.chapter.index,
                    chapterTitle = currentChapter.chapter.title,
                    contentHash = key.contentHash,
                    modelId = key.modelId,
                    catalogHash = key.catalogHash,
                    assignmentsJson = json,
                    status = ReadAloudBgmAssignmentCache.STATUS_SUCCESS,
                    createdAt = old?.createdAt?.takeIf { it > 0 } ?: now,
                    updatedAt = System.currentTimeMillis()
                )
            )
            AiReadAloudUsageRecorder.record(
                type = AiReadAloudUsageRecord.TYPE_AUDIO,
                status = AiReadAloudUsageRecord.STATUS_SUCCESS,
                book = currentBook,
                chapter = currentChapter,
                cacheKey = key.cacheKey,
                batchName = "配乐音效分析",
                modelConfig = response.modelConfig ?: modelConfig,
                snapshot = usageTracker.snapshot(),
                summary = "bgm=${audio.assignments.size}, sfx=${audio.soundEffects.size}, candidates=${sfxCandidates.size}"
            )
            EnsureResult(
                status = ReadAloudBgmAssignmentCache.STATUS_SUCCESS,
                assignmentCount = audio.assignments.size,
                soundEffectCount = audio.soundEffects.size,
                message = "智能音频已缓存",
                cacheKey = key.cacheKey
            )
        } catch (throwable: Throwable) {
            val error = throwable.localizedMessage ?: throwable.javaClass.simpleName
            appDb.readAloudBgmDao.upsertAssignmentCache(
                ReadAloudBgmAssignmentCache(
                    cacheKey = key.cacheKey,
                    bookUrl = currentBook.bookUrl,
                    chapterKey = currentChapter.chapter.url?.ifBlank { currentChapter.chapter.title }.orEmpty(),
                    chapterIndex = currentChapter.chapter.index,
                    chapterTitle = currentChapter.chapter.title,
                    contentHash = key.contentHash,
                    modelId = key.modelId,
                    catalogHash = key.catalogHash,
                    assignmentsJson = old?.assignmentsJson.orEmpty(),
                    status = ReadAloudBgmAssignmentCache.STATUS_FAILED,
                    lastError = error,
                    createdAt = old?.createdAt?.takeIf { it > 0 } ?: now,
                    updatedAt = System.currentTimeMillis()
                )
            )
            AiReadAloudUsageRecorder.record(
                type = AiReadAloudUsageRecord.TYPE_AUDIO,
                status = AiReadAloudUsageRecord.STATUS_FAILED,
                book = currentBook,
                chapter = currentChapter,
                cacheKey = key.cacheKey,
                batchName = "配乐音效分析",
                modelConfig = modelConfig,
                snapshot = usageTracker.snapshot(),
                summary = "candidates=${sfxCandidates.size}",
                error = error
            )
            EnsureResult(
                status = ReadAloudBgmAssignmentCache.STATUS_FAILED,
                error = error,
                cacheKey = key.cacheKey
            )
        }
    }

    fun catalogJson(): JSONObject {
        val snapshot = catalogSnapshot()
        return JSONObject().apply {
            put("ok", true)
            put("catalogHash", snapshot.hash)
            put("groups", JSONArray().apply {
                snapshot.groups.forEach { group ->
                    put(JSONObject().apply {
                        put("groupId", group.id)
                        put("name", group.displayName())
                    })
                }
            })
            put("tracks", JSONArray().apply {
                snapshot.bgmTracks.forEach { track ->
                    put(trackJson(track, snapshot.groups))
                }
            })
            put("soundEffectTracks", JSONArray().apply {
                snapshot.soundEffectTracks.forEach { track ->
                    put(trackJson(track, snapshot.groups))
                }
            })
        }
    }

    fun assignmentToolDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_ASSIGN_BGM_RANGES)
                put("description", "给朗读章节按 cue 范围选择背景音乐，并给候选事件选择音效。配乐用 assignments，音效用 soundEffects。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("assignments", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("fromCueIndex", intProp("0-based cue index, inclusive."))
                                    put("toCueIndex", intProp("0-based cue index, inclusive."))
                                    put("trackId", intProp("music track id from catalog."))
                                    put("volume", numberProp("0.0..1.0, default 0.22."))
                                    put("fadeInMs", intProp("fade-in milliseconds, default 1800."))
                                    put("fadeOutMs", intProp("fade-out milliseconds, default 1800."))
                                    put("reason", stringProp("short reason."))
                                    put("confidence", numberProp("0.0..1.0."))
                                })
                                put("required", JSONArray().put("fromCueIndex").put("toCueIndex").put("trackId"))
                                put("additionalProperties", false)
                            })
                        })
                        put("soundEffects", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("cueIndex", intProp("0-based cue index where sound effect should play."))
                                    put("trackId", intProp("sound effect track id from soundEffectTracks."))
                                    put("volume", numberProp("0.0..1.0, default 0.72."))
                                    put("offsetMs", intProp("delay from cue start, milliseconds, default 0."))
                                    put("reason", stringProp("short reason."))
                                    put("confidence", numberProp("0.0..1.0."))
                                })
                                put("required", JSONArray().put("cueIndex").put("trackId"))
                                put("additionalProperties", false)
                            })
                        })
                        put("bookUrl", stringProp("optional, used only when persisting from general AI tools."))
                        put("chapterIndex", intProp("optional, 0-based chapter index."))
                        put("contentHash", stringProp("optional content hash."))
                        put("catalogHash", stringProp("optional catalog hash."))
                    })
                    put("required", JSONArray().put("assignments"))
                    put("additionalProperties", false)
                })
            })
        }
    }

    fun toolAssign(args: JSONObject?): String {
        val snapshot = catalogSnapshot()
        val audio = parseAudioAssignments(
            args ?: JSONObject(),
            args?.optInt("cueCount", 10_000) ?: 10_000,
            snapshot.bgmTracks,
            snapshot.soundEffectTracks
        )
        val bookUrl = args?.optString("bookUrl").orEmpty()
        val chapterIndex = args?.optInt("chapterIndex", -1) ?: -1
        val contentHash = args?.optString("contentHash").orEmpty()
        val catalogHash = args?.optString("catalogHash").orEmpty().ifBlank { snapshot.hash }
        val persisted = if (bookUrl.isNotBlank() && chapterIndex >= 0 && contentHash.isNotBlank()) {
            val now = System.currentTimeMillis()
            val modelId = AppConfig.aiReadAloudAudioModelConfig?.id.orEmpty()
            val cacheKey = MD5Utils.md5Encode("read-aloud-bgm-tool|$bookUrl|$chapterIndex|$contentHash|$catalogHash|$modelId")
            appDb.readAloudBgmDao.upsertAssignmentCache(
                ReadAloudBgmAssignmentCache(
                    cacheKey = cacheKey,
                    bookUrl = bookUrl,
                    chapterIndex = chapterIndex,
                    contentHash = contentHash,
                    catalogHash = catalogHash,
                    modelId = modelId,
                    assignmentsJson = assignmentsToJson(audio.assignments, audio.soundEffects, contentHash, catalogHash),
                    status = ReadAloudBgmAssignmentCache.STATUS_SUCCESS,
                    createdAt = now,
                    updatedAt = now
                )
            )
            true
        } else {
            false
        }
        return JSONObject().apply {
            put("ok", true)
            put("persisted", persisted)
            put("assignmentCount", audio.assignments.size)
            put("soundEffectCount", audio.soundEffects.size)
            put("assignments", JSONArray().apply {
                audio.assignments.forEach { put(it.toJson()) }
            })
            put("soundEffects", JSONArray().apply {
                audio.soundEffects.forEach { put(it.toJson()) }
            })
        }.toString()
    }

    fun cachedAssignmentForCue(
        bookUrl: String?,
        chapterIndex: Int,
        cueIndex: Int
    ): ResolvedAssignment? {
        if (bookUrl.isNullOrBlank() || chapterIndex < 0 || cueIndex < 0) return null
        val cache = appDb.readAloudBgmDao.latestAssignmentCacheByChapter(bookUrl, chapterIndex)
            ?: return null
        val assignment = assignmentsFromJson(cache.assignmentsJson)
            .firstOrNull { cueIndex in it.fromCueIndex..it.toCueIndex }
            ?: return null
        val track = appDb.readAloudBgmDao.track(assignment.trackId)
            ?.takeIf {
                it.enabled &&
                    it.normalizedAssetType() == ReadAloudBgmTrack.TYPE_BGM &&
                    it.filePath.isNotBlank() &&
                    File(it.filePath).exists()
            }
            ?: return null
        return ResolvedAssignment(assignment, track)
    }

    fun cachedSoundEffectsForCue(
        bookUrl: String?,
        chapterIndex: Int,
        cueIndex: Int
    ): List<ResolvedSoundEffectEvent> {
        if (bookUrl.isNullOrBlank() || chapterIndex < 0 || cueIndex < 0) return emptyList()
        val cache = appDb.readAloudBgmDao.latestAssignmentCacheByChapter(bookUrl, chapterIndex)
            ?: return emptyList()
        return soundEffectsFromJson(cache.assignmentsJson)
            .filter { it.cueIndex == cueIndex }
            .mapNotNull { event ->
                val track = appDb.readAloudBgmDao.track(event.trackId)
                    ?.takeIf {
                        it.enabled &&
                            it.normalizedAssetType() == ReadAloudBgmTrack.TYPE_SFX &&
                            it.filePath.isNotBlank() &&
                            File(it.filePath).exists()
                    }
                    ?: return@mapNotNull null
                ResolvedSoundEffectEvent(event, track)
            }
    }

    fun cachedAudioInfoForChapter(
        bookUrl: String?,
        chapterIndex: Int
    ): CachedAudioInfo? {
        if (bookUrl.isNullOrBlank() || chapterIndex < 0) return null
        val cache = appDb.readAloudBgmDao.latestAnyAssignmentCacheByChapter(bookUrl, chapterIndex)
            ?: return null
        val audio = audioAssignmentsFromJson(cache.assignmentsJson)
        val bgmNames = audio.assignments
            .mapNotNull { assignment ->
                appDb.readAloudBgmDao.track(assignment.trackId)
                    ?.takeIf { it.normalizedAssetType() == ReadAloudBgmTrack.TYPE_BGM }
                    ?.displayName()
            }
            .distinct()
            .take(4)
        val sfxNames = audio.soundEffects
            .mapNotNull { event ->
                appDb.readAloudBgmDao.track(event.trackId)
                    ?.takeIf { it.normalizedAssetType() == ReadAloudBgmTrack.TYPE_SFX }
                    ?.displayName()
            }
            .distinct()
            .take(6)
        return CachedAudioInfo(
            cacheKey = cache.cacheKey,
            status = cache.status,
            assignmentCount = audio.assignments.size,
            soundEffectCount = audio.soundEffects.size,
            bgmTrackNames = bgmNames,
            soundEffectTrackNames = sfxNames,
            updatedAt = cache.updatedAt
        )
    }

    private fun buildAssignmentPrompt(
        book: Book,
        chapter: TextChapter,
        cues: List<ReadAloudCue>,
        catalog: CatalogSnapshot,
        soundEffectCandidates: List<ReadAloudSoundEffectCandidate>
    ): String {
        val catalogText = catalog.bgmTracks.joinToString("\n") { track ->
            val group = catalog.groups.firstOrNull { it.id == track.groupId }?.displayName() ?: "默认分组"
            val tags = track.tags.ifBlank { "-" }
            "${track.id}|${track.displayName()}|$group|$tags|${track.durationMs / 1000}s|vol=${track.defaultVolume}"
        }.ifBlank { "无可用配乐" }
        val sfxCatalogText = catalog.soundEffectTracks.joinToString("\n") { track ->
            val group = catalog.groups.firstOrNull { it.id == track.groupId }?.displayName() ?: "默认分组"
            val tags = track.tags.ifBlank { "-" }
            "${track.id}|${track.displayName()}|$group|$tags|${track.durationMs / 1000}s|vol=${track.defaultVolume}"
        }.ifBlank { "无可用音效" }
        val cueText = cues.mapIndexed { index, cue ->
            "C$index|pos=${cue.chapterPosition}|${cue.text.compactForPrompt(120)}"
        }.joinToString("\n")
        val sfxCandidateText = soundEffectCandidates.take(80).joinToString("\n") { item ->
            "S${item.id}|cue=C${item.paragraphIndex}|word=${item.cue}|${item.context.compactForPrompt(80)}"
        }
        return """
            任务：为小说朗读选择背景音乐范围和音效事件。必须调用 $TOOL_ASSIGN_BGM_RANGES。
            规则：
            1. 配乐写入 assignments，只给需要配乐的连续 cue 范围；普通对话和安静过渡可以不配乐，避免逐句切换。
            2. 音效写入 soundEffects，只能从“音效候选”里选择确实需要播放的事件；没有合适音效就不要返回。
            3. cueIndex/fromCueIndex/toCueIndex 都是 0-based。trackId 必须来自对应曲库。
            4. 配乐音量建议 0.12-0.28，音效音量建议 0.45-0.85，不能盖过朗读。
            5. 不要臆造不存在的音效，不要把人物说话声音当音效。
            书：${book.name} / ${book.author}
            章：${chapter.chapter.title}

            配乐策略：
            ${AppConfig.aiReadAloudBgmPrompt}

            音效策略：
            ${AppConfig.aiReadAloudSoundEffectPrompt}

            配乐曲库(trackId|名称|分组|标签|时长|默认音量)：
            $catalogText

            音效曲库(trackId|名称|分组|标签|时长|默认音量)：
            $sfxCatalogText

            章节 cue：
            $cueText

            音效候选(candidateId|cue|候选词|上下文)：
            ${sfxCandidateText.ifBlank { "无音效候选" }}
        """.trimIndent()
    }

    private fun buildCacheKey(
        book: Book,
        chapter: TextChapter,
        cues: List<ReadAloudCue>,
        catalogHash: String,
        modelId: String
    ): BgmCacheKey {
        val contentHash = MD5Utils.md5Encode(
            cues.joinToString("\n") { "${it.chapterPosition}|${it.text}" }
        )
        val promptHash = MD5Utils.md5Encode(
            "${AppConfig.aiReadAloudBgmPrompt}\n${AppConfig.aiReadAloudSoundEffectPrompt}"
        )
        val cacheKey = MD5Utils.md5Encode(
            "read-aloud-audio|${book.bookUrl}|${chapter.chapter.index}|${chapter.chapter.url}|$contentHash|$catalogHash|$modelId|$promptHash|$SCHEMA_VERSION"
        )
        val promptCacheKey = MD5Utils.md5Encode(
            "read-aloud-audio-prompt|${book.bookUrl}|$catalogHash|$modelId|$promptHash|$SCHEMA_VERSION"
        )
        return BgmCacheKey(
            cacheKey = cacheKey,
            contentHash = contentHash,
            catalogHash = catalogHash,
            modelId = modelId,
            promptCacheKey = "read_aloud_audio_$promptCacheKey"
        )
    }

    private fun catalogSnapshot(): CatalogSnapshot {
        val groups = appDb.readAloudBgmDao.groups()
        val bgmTracks = appDb.readAloudBgmDao.enabledTracksByType(ReadAloudBgmTrack.TYPE_BGM)
        val sfxTracks = appDb.readAloudBgmDao.enabledTracksByType(ReadAloudBgmTrack.TYPE_SFX)
        val hash = MD5Utils.md5Encode(
            (bgmTracks + sfxTracks).joinToString("|") {
                "${it.id}:${it.normalizedAssetType()}:${it.groupId}:${it.displayName()}:${it.tags}:${it.checksum}:${it.enabled}:${it.defaultVolume}:${it.updatedAt}"
            }
        )
        return CatalogSnapshot(groups, bgmTracks, sfxTracks, hash)
    }

    private fun soundEffectCandidates(cues: List<ReadAloudCue>): List<ReadAloudSoundEffectCandidate> {
        return ReadAloudSoundEffectPreprocessor.process(cues.map { it.text }).candidates
    }

    private fun parseAudioAssignmentsFromContent(
        content: String,
        cueCount: Int,
        bgmTracks: List<ReadAloudBgmTrack>,
        soundEffectTracks: List<ReadAloudBgmTrack>
    ): AudioAssignmentResult {
        return runCatching {
            parseAudioAssignments(JSONObject(content), cueCount, bgmTracks, soundEffectTracks)
        }.getOrDefault(AudioAssignmentResult(emptyList(), emptyList()))
    }

    private fun parseAudioAssignments(
        args: JSONObject,
        cueCount: Int,
        bgmTracks: List<ReadAloudBgmTrack>,
        soundEffectTracks: List<ReadAloudBgmTrack>
    ): AudioAssignmentResult {
        return AudioAssignmentResult(
            assignments = parseAssignments(args, cueCount, bgmTracks),
            soundEffects = parseSoundEffects(args, cueCount, soundEffectTracks)
        )
    }

    private fun parseAssignments(
        args: JSONObject,
        cueCount: Int,
        tracks: List<ReadAloudBgmTrack>
    ): List<Assignment> {
        val validTrackIds = tracks.mapTo(hashSetOf()) { it.id }
        val array = args.optJSONArray("assignments")
            ?: args.optJSONArray("ranges")
            ?: JSONArray()
        val maxCueIndex = (cueCount - 1).coerceAtLeast(0)
        val result = mutableListOf<Assignment>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val trackId = item.optLong("trackId", 0L)
            if (trackId !in validTrackIds) continue
            val from = item.firstInt("fromCueIndex", "cueFrom", "startCueIndex", "from", "fromParagraph")
                ?.let { normalizeCueIndex(it, item.has("fromParagraph")) }
                ?.coerceIn(0, maxCueIndex)
                ?: continue
            val to = item.firstInt("toCueIndex", "cueTo", "endCueIndex", "to", "toParagraph")
                ?.let { normalizeCueIndex(it, item.has("toParagraph")) }
                ?.coerceIn(from, maxCueIndex)
                ?: from
            result += Assignment(
                fromCueIndex = from,
                toCueIndex = to,
                trackId = trackId,
                volume = item.optDouble("volume", 0.22).toFloat().coerceIn(0.0f, 0.8f),
                fadeInMs = item.optInt("fadeInMs", 1800).coerceIn(0, 12_000),
                fadeOutMs = item.optInt("fadeOutMs", 1800).coerceIn(0, 12_000),
                reason = item.optString("reason").trim().take(120),
                confidence = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            )
        }
        return result
            .sortedWith(compareBy<Assignment> { it.fromCueIndex }.thenBy { it.toCueIndex })
            .fold(mutableListOf()) { acc, assignment ->
                val last = acc.lastOrNull()
                if (last != null && assignment.fromCueIndex <= last.toCueIndex) {
                    if (assignment.confidence > last.confidence) {
                        acc[acc.lastIndex] = assignment.copy(fromCueIndex = last.fromCueIndex)
                    }
                } else {
                    acc += assignment
                }
                acc
            }
    }

    private fun assignmentsFromJson(json: String): List<Assignment> {
        return audioAssignmentsFromJson(json).assignments
    }

    private fun soundEffectsFromJson(json: String): List<SoundEffectEvent> {
        return audioAssignmentsFromJson(json).soundEffects
    }

    private fun audioAssignmentsFromJson(json: String): AudioAssignmentResult {
        if (json.isBlank()) return AudioAssignmentResult(emptyList(), emptyList())
        return runCatching {
            val root = JSONObject(json)
            val array = root.optJSONArray("assignments") ?: JSONArray()
            val result = mutableListOf<Assignment>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val from = item.optInt("fromCueIndex", -1)
                val to = item.optInt("toCueIndex", from)
                val trackId = item.optLong("trackId", 0L)
                if (from < 0 || to < from || trackId <= 0L) continue
                result += Assignment(
                    fromCueIndex = from,
                    toCueIndex = to,
                    trackId = trackId,
                    volume = item.optDouble("volume", 0.22).toFloat().coerceIn(0.0f, 0.8f),
                    fadeInMs = item.optInt("fadeInMs", 1800).coerceIn(0, 12_000),
                    fadeOutMs = item.optInt("fadeOutMs", 1800).coerceIn(0, 12_000),
                    reason = item.optString("reason").trim().take(120),
                    confidence = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
                )
            }
            AudioAssignmentResult(
                assignments = result,
                soundEffects = soundEffectsFromJsonRoot(root)
            )
        }.getOrDefault(AudioAssignmentResult(emptyList(), emptyList()))
    }

    private fun assignmentsToJson(
        assignments: List<Assignment>,
        soundEffects: List<SoundEffectEvent>,
        contentHash: String,
        catalogHash: String
    ): String {
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("contentHash", contentHash)
            put("catalogHash", catalogHash)
            put("assignments", JSONArray().apply {
                assignments.forEach { put(it.toJson()) }
            })
            put("soundEffects", JSONArray().apply {
                soundEffects.forEach { put(it.toJson()) }
            })
        }.toString()
    }

    private fun Assignment.toJson(): JSONObject {
        return JSONObject().apply {
            put("fromCueIndex", fromCueIndex)
            put("toCueIndex", toCueIndex)
            put("trackId", trackId)
            put("volume", volume.toDouble())
            put("fadeInMs", fadeInMs)
            put("fadeOutMs", fadeOutMs)
            put("reason", reason)
            put("confidence", confidence)
        }
    }

    private fun parseSoundEffects(
        args: JSONObject,
        cueCount: Int,
        tracks: List<ReadAloudBgmTrack>
    ): List<SoundEffectEvent> {
        val validTrackIds = tracks.mapTo(hashSetOf()) { it.id }
        val array = args.optJSONArray("soundEffects")
            ?: args.optJSONArray("sfx")
            ?: JSONArray()
        val maxCueIndex = (cueCount - 1).coerceAtLeast(0)
        val result = mutableListOf<SoundEffectEvent>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val trackId = item.optLong("trackId", 0L)
            if (trackId !in validTrackIds) continue
            val cueIndex = item.firstInt("cueIndex", "cue", "paragraphIndex", "paragraph")
                ?.let { normalizeCueIndex(it, item.has("paragraph")) }
                ?.coerceIn(0, maxCueIndex)
                ?: continue
            result += SoundEffectEvent(
                cueIndex = cueIndex,
                trackId = trackId,
                volume = item.optDouble("volume", 0.72).toFloat().coerceIn(0.0f, 1.0f),
                offsetMs = item.optInt("offsetMs", 0).coerceIn(0, 20_000),
                reason = item.optString("reason").trim().take(120),
                confidence = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            )
        }
        return result
            .sortedWith(compareBy<SoundEffectEvent> { it.cueIndex }.thenBy { it.offsetMs })
            .distinctBy { "${it.cueIndex}:${it.trackId}:${it.offsetMs}" }
    }

    private fun soundEffectsFromJsonRoot(root: JSONObject): List<SoundEffectEvent> {
        val array = root.optJSONArray("soundEffects") ?: JSONArray()
        val result = mutableListOf<SoundEffectEvent>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val cueIndex = item.optInt("cueIndex", -1)
            val trackId = item.optLong("trackId", 0L)
            if (cueIndex < 0 || trackId <= 0L) continue
            result += SoundEffectEvent(
                cueIndex = cueIndex,
                trackId = trackId,
                volume = item.optDouble("volume", 0.72).toFloat().coerceIn(0.0f, 1.0f),
                offsetMs = item.optInt("offsetMs", 0).coerceIn(0, 20_000),
                reason = item.optString("reason").trim().take(120),
                confidence = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            )
        }
        return result
    }

    private fun SoundEffectEvent.toJson(): JSONObject {
        return JSONObject().apply {
            put("cueIndex", cueIndex)
            put("trackId", trackId)
            put("volume", volume.toDouble())
            put("offsetMs", offsetMs)
            put("reason", reason)
            put("confidence", confidence)
        }
    }

    private fun trackJson(track: ReadAloudBgmTrack, groups: List<ReadAloudBgmGroup>): JSONObject {
        return JSONObject().apply {
            put("trackId", track.id)
            put("name", track.displayName())
            put("groupId", track.groupId)
            put("groupName", groups.firstOrNull { it.id == track.groupId }?.displayName() ?: "默认分组")
            put("tags", JSONArray().apply {
                track.tags.split(',', '，')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach(::put)
            })
            put("durationMs", track.durationMs)
            put("assetType", track.normalizedAssetType())
            put("defaultVolume", track.defaultVolume.toDouble())
            put("enabled", track.enabled)
        }
    }

    private fun normalizeCueIndex(value: Int, oneBased: Boolean): Int {
        return if (oneBased && value > 0) value - 1 else value
    }

    private fun JSONObject.firstInt(vararg keys: String): Int? {
        for (key in keys) {
            if (has(key)) return optInt(key)
        }
        return null
    }

    private fun String.compactForPrompt(maxLength: Int): String {
        val compact = replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= maxLength) compact else compact.take(maxLength) + "…"
    }

    private fun stringProp(description: String) = JSONObject().apply {
        put("type", "string")
        put("description", description)
    }

    private fun intProp(description: String) = JSONObject().apply {
        put("type", "integer")
        put("description", description)
    }

    private fun numberProp(description: String) = JSONObject().apply {
        put("type", "number")
        put("description", description)
    }
}
