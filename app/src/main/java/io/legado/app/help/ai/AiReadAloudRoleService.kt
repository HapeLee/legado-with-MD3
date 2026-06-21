package io.legado.app.help.ai

import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiReadAloudRoleCache
import io.legado.app.data.entities.AiReadAloudUsageRecord
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.help.book.characterBookKey
import io.legado.app.help.character.BookCharacterIdentityMigrator
import io.legado.app.help.character.BookCharacterProfileMeta
import io.legado.app.help.config.AppConfig
import io.legado.app.help.readaloud.ReadAloudConfigChangeNotifier
import io.legado.app.help.readaloud.role.ReadAloudRolePreprocessor
import io.legado.app.help.readaloud.role.ReadAloudRoleUnit
import io.legado.app.help.readaloud.speech.SpeechRoute
import io.legado.app.help.readaloud.speech.SpeechRouteSanitizer
import io.legado.app.help.readaloud.speech.SpeechVoiceAssigner
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.postEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object AiReadAloudRoleService {

    private const val TOOL_RECORD_SEGMENTS = "record_read_aloud_role_segments"
    private const val TOOL_CONFIRM_UNITS = "confirm_read_aloud_role_units"
    private const val TARGET_GROUP_SIZE = 12
    private const val MAX_SEARCH_BATCH_TARGET_UNITS = 48
    private const val MAX_SEARCH_BATCH_CONTEXT_CHARS = 18_000
    private const val PLAYBACK_ASSIGNMENT_ATTEMPTS = 3
    private const val UNKNOWN_RETRY_ATTEMPTS = 1
    private const val RUNNING_WAIT_STEP_MILLIS = 600L
    private const val RUNNING_WAIT_TIMEOUT_MILLIS = 120_000L
    private const val PLAYBACK_CACHE_SCHEMA_VERSION = "playback-v1"
    private const val MIN_AUTO_CREATE_CHARACTER_CONFIDENCE = 0.62
    private val runningCacheKeys = ConcurrentHashMap.newKeySet<String>()
    private val avatarScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speechVerbRegex = Regex(
        "([\\p{IsHan}A-Za-z0-9_·]{1,24})\\s*(?:说道|说|道|问道|问|答道|答|笑道|冷声道|沉声道|低声道|怒道|喝道|喊道|叫道|开口道|喃喃道)\\s*[，,、：:]?\\s*$"
    )
    private val invalidCharacterNameKeywordRegex = Regex(
        "(?:说道|问道|答道|笑道|冷声道|沉声道|低声道|怒道|喝道|喊道|叫道|开口道|喃喃道|警告|提醒|告诉|炫耀|得意|有些|突然|已经|正在)"
    )

    data class Segment(
        val paragraphIndex: Int,
        val start: Int,
        val end: Int,
        val roleType: String,
        val characterName: String,
        val characterId: Long = 0L,
        val emotionName: String = "",
        val emotionTag: String = "",
        val confidence: Double
    )

    private data class CharacterCandidate(
        val name: String,
        val identity: String,
        val gender: String,
        val age: String,
        val appearance: String,
        val roleLevel: Int,
        val confidence: Double,
        val evidence: String
    )

    private data class RequestResult(
        val segments: List<Segment> = emptyList(),
        val candidates: List<CharacterCandidate> = emptyList(),
        val aiRequired: Boolean = false,
        val aiSatisfied: Boolean = true
    )

    private class RoleUsageTracker {
        private val startAt = System.currentTimeMillis()
        private var requestCounter = 0
        private var usage = AiUsageStats()

        @Synchronized
        fun onRequest() {
            requestCounter += 1
        }

        @Synchronized
        fun onUsage(stats: AiUsageStats) {
            usage += stats
        }

        @Synchronized
        fun snapshot(): RoleUsageSnapshot {
            return RoleUsageSnapshot(
                elapsedMillis = System.currentTimeMillis() - startAt,
                requestCount = requestCounter,
                usage = usage
            )
        }
    }

    private data class RoleUsageSnapshot(
        val elapsedMillis: Long = 0L,
        val requestCount: Int = 0,
        val usage: AiUsageStats = AiUsageStats()
    )

    private data class UnitResolution(
        val unitId: String,
        val roleType: String,
        val characterName: String,
        val characterId: Long,
        val emotionName: String,
        val emotionTag: String,
        val confidence: Double,
        val status: String = "assigned",
        val evidence: String = ""
    )

    private data class RoleCacheKey(
        val cacheKey: String,
        val promptCacheKey: String,
        val bookKey: String,
        val mode: String,
        val legacyMode: String,
        val prompt: String,
        val contentHash: String,
        val contextParagraphs: Int,
        val mergeGapParagraphs: Int
    )

    private data class CacheRepairResult(
        val segments: List<Segment>,
        val createdIds: List<Long> = emptyList()
    )

    private data class UnitAssignmentBatch(
        val index: Int,
        val targetParagraphs: List<Int>,
        val units: List<ReadAloudRoleUnit>
    )

    private data class UnitAssignmentBatchRunResult(
        val batch: UnitAssignmentBatch,
        val result: UnitAssignmentResult,
        val failed: Boolean
    )

    private data class UnitAssignmentResult(
        val resolutions: List<UnitResolution> = emptyList(),
        val candidates: List<CharacterCandidate> = emptyList()
    )

    data class EnsureResult(
        val status: String,
        val segmentCount: Int = 0,
        val createdCharacterCount: Int = 0,
        val message: String = "",
        val error: String = "",
        val cacheKey: String = ""
    ) {
        val usable: Boolean
            get() = segmentCount > 0
    }

    data class ManualSegmentAssignment(
        val paragraphIndex: Int,
        val start: Int,
        val end: Int,
        val roleType: String,
        val characterId: Long = 0L,
        val characterName: String = "",
        val emotionName: String = "",
        val emotionTag: String = ""
    )

    data class ManualSegmentAssignmentResult(
        val success: Boolean,
        val message: String,
        val previewSegments: List<AiReadAloudRolePreviewSegment> = emptyList()
    )

    fun cacheKeyFor(
        book: Book?,
        textChapter: TextChapter?,
        paragraphs: List<String>
    ): String? {
        val currentBook = book ?: return null
        val currentChapter = textChapter ?: return null
        BookCharacterIdentityMigrator.migrate(currentBook)
        val cleanParagraphs = paragraphs.map { it.trimEnd() }.filter { it.isNotBlank() }
        if (cleanParagraphs.isEmpty()) return null
        return buildRoleCacheKey(currentBook, currentChapter, cleanParagraphs)?.cacheKey
    }

    fun cacheForPlayback(
        book: Book?,
        textChapter: TextChapter?,
        paragraphs: List<String>
    ): AiReadAloudRoleCache? {
        val currentBook = book ?: return null
        val currentChapter = textChapter ?: return null
        val cleanParagraphs = paragraphs.map { it.trimEnd() }.filter { it.isNotBlank() }
        if (cleanParagraphs.isEmpty()) return null
        val roleKey = buildRoleCacheKey(currentBook, currentChapter, cleanParagraphs) ?: return null
        return appDb.aiReadAloudRoleCacheDao.get(roleKey.cacheKey)
            ?.takeIf { it.status == AiReadAloudRoleCache.STATUS_SUCCESS && it.segmentsJson.isNotBlank() }
            ?: latestSuccessCache(currentBook, currentChapter, roleKey)
    }

    fun updateCachedSegmentAssignment(
        book: Book?,
        textChapter: TextChapter?,
        paragraphs: List<String>,
        assignment: ManualSegmentAssignment
    ): ManualSegmentAssignmentResult {
        val currentBook = book
            ?: return ManualSegmentAssignmentResult(false, "书籍不存在")
        val currentChapter = textChapter
            ?: return ManualSegmentAssignmentResult(false, "章节不存在")
        val cleanParagraphs = paragraphs.map { it.trimEnd() }.filter { it.isNotBlank() }
        if (cleanParagraphs.isEmpty()) {
            return ManualSegmentAssignmentResult(false, "章节内容为空")
        }
        val roleKey = buildRoleCacheKey(currentBook, currentChapter, cleanParagraphs)
            ?: return ManualSegmentAssignmentResult(false, "多角色模型未配置")
        val characterBookKey = roleKey.bookKey
        val cache = appDb.aiReadAloudRoleCacheDao.get(roleKey.cacheKey)
            ?.takeIf { it.segmentsJson.isNotBlank() }
            ?: cacheForPlayback(currentBook, currentChapter, cleanParagraphs)
            ?: appDb.aiReadAloudRoleCacheDao.latestUsableByChapter(
                characterBookKey,
                currentChapter.chapter.index
            )
            ?: return ManualSegmentAssignmentResult(false, "当前章节没有可修改的分配缓存")
        val segments = segmentsFromJson(cache.segmentsJson).toMutableList()
        val targetIndex = segments.indexOfFirst {
            it.paragraphIndex == assignment.paragraphIndex &&
                    it.start == assignment.start &&
                    it.end == assignment.end
        }
        if (targetIndex < 0) {
            return ManualSegmentAssignmentResult(false, "没有找到对应片段")
        }
        val safeRoleType = assignment.roleType
            .takeIf { it in setOf("narrator", "character", "thought", "other") }
            ?: "character"
        val characters = appDb.bookCharacterDao.characters(characterBookKey)
        val selectedCharacter = if (safeRoleType == "narrator") {
            null
        } else {
            when {
                assignment.characterId > 0L -> characters.firstOrNull { it.id == assignment.characterId }
                assignment.characterName.isNotBlank() -> {
                    val byName = charactersByNormalizedName(characters)
                    byName[characterNameKey(assignment.characterName)]
                }
                else -> null
            }
        }
        val old = segments[targetIndex]
        val updated = old.copy(
            roleType = safeRoleType,
            characterId = selectedCharacter?.id ?: 0L,
            characterName = when {
                safeRoleType == "narrator" -> ""
                selectedCharacter != null -> selectedCharacter.name
                else -> assignment.characterName.trim().take(80)
            },
            emotionName = assignment.emotionName.trim().take(40),
            emotionTag = assignment.emotionTag.trim().take(40),
            confidence = 1.0
        )
        segments[targetIndex] = updated
        val now = System.currentTimeMillis()
        appDb.aiReadAloudRoleCacheDao.upsert(
            cache.copy(
                status = AiReadAloudRoleCache.STATUS_SUCCESS,
                retryCount = 0,
                lastError = "",
                segmentsJson = replaceSegmentsInCacheJson(cache.segmentsJson, segments),
                characterHash = characterHash(characterBookKey),
                voiceHash = voiceHash(characterBookKey),
                updatedAt = now
            )
        )
        return ManualSegmentAssignmentResult(
            success = true,
            message = "已更新片段分配",
            previewSegments = buildPreviewSegments(
                characterBookKey,
                segments,
                cleanParagraphs,
                AiReadAloudRoleState.SOURCE_RESOLVED
            )
        )
    }

    fun isRunningCacheActive(cacheKey: String?, updatedAt: Long): Boolean {
        if (cacheKey.isNullOrBlank() || updatedAt <= 0L) return false
        return cacheKey in runningCacheKeys &&
                System.currentTimeMillis() - updatedAt < 5 * 60 * 1000L
    }

    private fun buildRoleCacheKey(
        book: Book,
        textChapter: TextChapter,
        cleanParagraphs: List<String>
    ): RoleCacheKey? {
        val modelConfig = AppConfig.aiReadAloudRoleModelConfig ?: return null
        val prompt = AppConfig.aiReadAloudRolePrompt.trim()
        val autoCreatePrompt = AppConfig.aiReadAloudAutoCreateCharacterPrompt.trim()
        val baseMode = AppConfig.aiReadAloudRoleMode
        val preprocessRuleHash = MD5Utils.md5Encode(AppConfig.aiReadAloudRolePreprocessRules)
        val contentHash = MD5Utils.md5Encode(cleanParagraphs.joinToString("\n"))
        val promptHash = MD5Utils.md5Encode(prompt)
        val autoCreatePromptHash = MD5Utils.md5Encode(autoCreatePrompt)
        val contextParagraphs = AppConfig.aiReadAloudRoleContextParagraphs
        val mergeGapParagraphs = AppConfig.aiReadAloudRoleMergeGapParagraphs
        val bookKey = book.characterBookKey()
        val legacyMode = "$baseMode|${ReadAloudRolePreprocessor.VERSION}|rules=$preprocessRuleHash"
        val mode = "$legacyMode|ctx=$contextParagraphs|gap=$mergeGapParagraphs|prompt=$promptHash|auto=$autoCreatePromptHash"
        val cacheKey = MD5Utils.md5Encode(
            "read-aloud-role-playback|$bookKey|${textChapter.chapter.index}|$contentHash|$PLAYBACK_CACHE_SCHEMA_VERSION"
        )
        val promptCacheKey = MD5Utils.md5Encode(
            "read-aloud-role-prompt|$bookKey|$mode|ctx=$contextParagraphs|gap=$mergeGapParagraphs|$promptHash|auto=$autoCreatePromptHash|${modelConfig.id}"
        )
        return RoleCacheKey(
            cacheKey = cacheKey,
            promptCacheKey = "read_aloud_role_$promptCacheKey",
            bookKey = bookKey,
            mode = mode,
            legacyMode = legacyMode,
            prompt = prompt,
            contentHash = contentHash,
            contextParagraphs = contextParagraphs,
            mergeGapParagraphs = mergeGapParagraphs
        )
    }

    private fun latestSuccessCache(
        book: Book,
        textChapter: TextChapter,
        roleKey: RoleCacheKey
    ): AiReadAloudRoleCache? {
        val candidates = appDb.aiReadAloudRoleCacheDao.successCandidatesByChapterContent(
            bookUrl = roleKey.bookKey,
            chapterIndex = textChapter.chapter.index,
            contentHash = roleKey.contentHash,
            preprocessVersion = ReadAloudRolePreprocessor.VERSION
        ).filter { cache ->
            cache.status == AiReadAloudRoleCache.STATUS_SUCCESS &&
                    cache.segmentsJson.isNotBlank() &&
                    cache.contentHash == roleKey.contentHash
        }
        return candidates.firstOrNull { it.cacheKey == roleKey.cacheKey }
            ?: candidates.firstOrNull { it.mode == roleKey.mode }
            ?: candidates.firstOrNull { it.mode == roleKey.legacyMode }
            ?: candidates.firstOrNull()
    }

    suspend fun ensurePlayableCache(
        book: Book?,
        textChapter: TextChapter?,
        paragraphs: List<String>,
        stage: String = AiReadAloudRoleState.STAGE_CURRENT
    ): EnsureResult {
        var lastResult = EnsureResult(AiReadAloudRoleState.STATUS_FAILED, error = "多角色分配失败")
        repeat(PLAYBACK_ASSIGNMENT_ATTEMPTS) {
            val result = ensureCache(book, textChapter, paragraphs, stage)
            lastResult = result
            if (result.status == AiReadAloudRoleState.STATUS_SUCCESS ||
                result.status == AiReadAloudRoleState.STATUS_SKIPPED && result.segmentCount > 0
            ) {
                return result.copy(status = AiReadAloudRoleState.STATUS_SUCCESS)
            }
            if (result.status == AiReadAloudRoleState.STATUS_RUNNING) {
                val currentBook = book ?: return result
                val currentChapter = textChapter ?: return result
                val waited = waitForRunningCache(result.cacheKey, currentBook, currentChapter, paragraphs, stage)
                lastResult = waited ?: result
                if (waited != null && waited.segmentCount > 0) {
                    return waited.copy(status = AiReadAloudRoleState.STATUS_SUCCESS)
                }
                return lastResult
            }
        }
        return lastResult
    }

    fun clearChapterCache(bookUrl: String?, chapterIndex: Int) {
        if (bookUrl.isNullOrBlank() || chapterIndex < 0) return
        appDb.aiReadAloudRoleCacheDao.deleteByChapter(bookUrl, chapterIndex)
    }

    suspend fun ensureCache(
        book: Book?,
        textChapter: TextChapter?,
        paragraphs: List<String>,
        stage: String = AiReadAloudRoleState.STAGE_CURRENT
    ): EnsureResult {
        if (!AppConfig.aiReadAloudRoleEnabled) {
            return EnsureResult(AiReadAloudRoleState.STATUS_SKIPPED, message = "多角色未开启")
        }
        val modelConfig = AppConfig.aiReadAloudRoleModelConfig
            ?: return EnsureResult(AiReadAloudRoleState.STATUS_SKIPPED, message = "未配置多角色模型")
        if (AppConfig.aiProviderForModel(modelConfig)?.baseUrl.isNullOrBlank()) {
            return EnsureResult(AiReadAloudRoleState.STATUS_SKIPPED, message = "多角色模型供应商不可用")
        }
        val currentBook = book
            ?: return EnsureResult(AiReadAloudRoleState.STATUS_SKIPPED, message = "书籍为空")
        val currentChapter = textChapter
            ?: return EnsureResult(AiReadAloudRoleState.STATUS_SKIPPED, message = "章节为空")
        BookCharacterIdentityMigrator.migrate(currentBook)
        val cleanParagraphs = paragraphs.map { it.trimEnd() }.filter { it.isNotBlank() }
        if (cleanParagraphs.isEmpty()) {
            return EnsureResult(AiReadAloudRoleState.STATUS_SKIPPED, message = "当前章节无可朗读段落")
        }
        val roleKey = buildRoleCacheKey(currentBook, currentChapter, cleanParagraphs)
            ?: return EnsureResult(AiReadAloudRoleState.STATUS_SKIPPED, message = "未配置多角色模型")
        val mode = roleKey.mode
        val prompt = roleKey.prompt
        val contentHash = roleKey.contentHash
        val contextParagraphs = roleKey.contextParagraphs
        val mergeGapParagraphs = roleKey.mergeGapParagraphs
        val cacheKey = roleKey.cacheKey
        val characterBookKey = roleKey.bookKey
        val previewBuffer = mutableListOf<AiReadAloudRolePreviewSegment>()
        val usageTracker = RoleUsageTracker()
        var keepAliveId: String? = null
        fun postPreview(
            status: String,
            message: String,
            source: String,
            segments: List<AiReadAloudRolePreviewSegment>,
            createdCharacterCount: Int = 0,
            newCharacterCandidateCount: Int = 0,
            error: String = ""
        ) {
            val snapshot = synchronized(previewBuffer) {
                if (source == AiReadAloudRoleState.SOURCE_RESOLVED ||
                    source == AiReadAloudRoleState.SOURCE_CACHE ||
                    source == AiReadAloudRoleState.SOURCE_FALLBACK
                ) {
                    previewBuffer.clear()
                }
                if (segments.isNotEmpty()) {
                    val byKey = (previewBuffer + segments).associateBy { it.key }
                    previewBuffer.clear()
                    previewBuffer += byKey.values.sortedWith(
                        compareBy<AiReadAloudRolePreviewSegment> { it.paragraphIndex }
                            .thenBy { it.start }
                            .thenBy { it.end }
                    )
                }
                previewBuffer.toList()
            }
            postState(
                currentBook,
                currentChapter,
                stage,
                status,
                message,
                cleanParagraphs.size,
                snapshot.size,
                createdCharacterCount,
                newCharacterCandidateCount,
                source,
                snapshot,
                error,
                usageTracker.snapshot()
            )
            updateRoleKeepAlive(
                taskId = keepAliveId,
                book = currentBook,
                chapter = currentChapter,
                stage = stage,
                status = status,
                message = message,
                paragraphCount = cleanParagraphs.size,
                segmentCount = snapshot.size,
                usageSnapshot = usageTracker.snapshot()
            )
        }
        val oldCache = appDb.aiReadAloudRoleCacheDao.get(cacheKey)
        if (oldCache?.status == AiReadAloudRoleCache.STATUS_SUCCESS && oldCache.segmentsJson.isNotBlank()) {
            val repair = repairCachedCharactersIfNeeded(
                cache = oldCache,
                characterBookKey = characterBookKey,
                sourceBookUrl = currentBook.bookUrl,
                paragraphs = cleanParagraphs
            )
            val cachedSegments = repair.segments
            val preview = buildPreviewSegments(
                characterBookKey,
                cachedSegments,
                cleanParagraphs,
                AiReadAloudRoleState.SOURCE_CACHE
            )
            val count = cachedSegments.size
            postPreview(
                AiReadAloudRoleState.STATUS_SKIPPED,
                "当前章节角色已分配",
                AiReadAloudRoleState.SOURCE_CACHE,
                preview,
                createdCharacterCount = repair.createdIds.size
            )
            return EnsureResult(
                AiReadAloudRoleState.STATUS_SKIPPED,
                count,
                message = "当前章节角色已分配",
                cacheKey = cacheKey
            )
        }
        latestSuccessCache(currentBook, currentChapter, roleKey)
            ?.takeIf { it.cacheKey != cacheKey }
            ?.let { usableCache ->
                val repair = repairCachedCharactersIfNeeded(
                    cache = usableCache,
                    characterBookKey = characterBookKey,
                    sourceBookUrl = currentBook.bookUrl,
                    paragraphs = cleanParagraphs
                )
                val cachedSegments = repair.segments
                val now = System.currentTimeMillis()
                appDb.aiReadAloudRoleCacheDao.upsert(
                    usableCache.copy(
                        cacheKey = cacheKey,
                        bookUrl = characterBookKey,
                        mode = mode,
                        paragraphCount = cleanParagraphs.size,
                        retryCount = 0,
                        lastError = "",
                        segmentsJson = replaceSegmentsInCacheJson(usableCache.segmentsJson, cachedSegments),
                        createdCharacterIdsJson = mergeCreatedCharacterIds(
                            usableCache.createdCharacterIdsJson,
                            repair.createdIds
                        ),
                        characterHash = characterHash(characterBookKey),
                        voiceHash = voiceHash(characterBookKey),
                        updatedAt = now
                    )
                )
                val preview = buildPreviewSegments(
                    characterBookKey,
                    cachedSegments,
                    cleanParagraphs,
                    AiReadAloudRoleState.SOURCE_CACHE
                )
                postPreview(
                    AiReadAloudRoleState.STATUS_SKIPPED,
                    "当前章节角色已分配",
                    AiReadAloudRoleState.SOURCE_CACHE,
                    preview,
                    createdCharacterCount = repair.createdIds.size
                )
                return EnsureResult(
                    AiReadAloudRoleState.STATUS_SKIPPED,
                    cachedSegments.size,
                    message = "当前章节角色已分配",
                    cacheKey = cacheKey
                )
            }
        if (oldCache?.status == AiReadAloudRoleCache.STATUS_RUNNING) {
            if (isRunningCacheActive(cacheKey, oldCache.updatedAt)) {
                postState(currentBook, currentChapter, stage, AiReadAloudRoleState.STATUS_RUNNING, stageMessage(stage, "分配角色中"), cleanParagraphs.size)
                return EnsureResult(AiReadAloudRoleState.STATUS_RUNNING, message = "分配角色中", cacheKey = cacheKey)
            }
            if (oldCache.segmentsJson.isNotBlank()) {
                val repair = repairCachedCharactersIfNeeded(
                    cache = oldCache,
                    characterBookKey = characterBookKey,
                    sourceBookUrl = currentBook.bookUrl,
                    paragraphs = cleanParagraphs
                )
                val cachedSegments = repair.segments
                val preview = buildPreviewSegments(
                    characterBookKey,
                    cachedSegments,
                    cleanParagraphs,
                    AiReadAloudRoleState.SOURCE_CACHE
                )
                appDb.aiReadAloudRoleCacheDao.upsert(
                    oldCache.copy(
                        status = AiReadAloudRoleCache.STATUS_SUCCESS,
                        retryCount = 0,
                        lastError = "",
                        segmentsJson = replaceSegmentsInCacheJson(oldCache.segmentsJson, cachedSegments),
                        createdCharacterIdsJson = mergeCreatedCharacterIds(
                            oldCache.createdCharacterIdsJson,
                            repair.createdIds
                        ),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                postPreview(
                    AiReadAloudRoleState.STATUS_SKIPPED,
                    "当前章节角色已分配",
                    AiReadAloudRoleState.SOURCE_CACHE,
                    preview,
                    createdCharacterCount = repair.createdIds.size
                )
                return EnsureResult(
                    AiReadAloudRoleState.STATUS_SKIPPED,
                    cachedSegments.size,
                    message = "当前章节角色已分配",
                    cacheKey = cacheKey
                )
            }
            AppLog.putDebug("上次多角色分配中断，重新分配当前章节")
        }
        if ((oldCache?.retryCount ?: 0) >= 3 && oldCache?.segmentsJson?.isNotBlank() == true) {
            val fallbackSegments = segmentsFromJson(oldCache.segmentsJson)
            val preview = buildPreviewSegments(
                characterBookKey,
                fallbackSegments,
                cleanParagraphs,
                AiReadAloudRoleState.SOURCE_FALLBACK
            )
            val count = fallbackSegments.size
            postPreview(
                AiReadAloudRoleState.STATUS_FAILED,
                oldCache.lastError.ifBlank { "AI分角色连续失败，请重新分配当前章节" },
                AiReadAloudRoleState.SOURCE_FALLBACK,
                preview,
                error = oldCache.lastError.ifBlank { "AI分角色连续失败" }
            )
            return EnsureResult(
                AiReadAloudRoleState.STATUS_FAILED,
                error = oldCache.lastError.ifBlank { "AI分角色连续失败" },
                cacheKey = cacheKey
            )
        }
        if ((oldCache?.retryCount ?: 0) >= 3) {
            val error = oldCache?.lastError?.ifBlank { "AI分角色连续失败" } ?: "AI分角色连续失败"
            postState(currentBook, currentChapter, stage, AiReadAloudRoleState.STATUS_FAILED, error, cleanParagraphs.size, error = error)
            return EnsureResult(AiReadAloudRoleState.STATUS_FAILED, error = error, cacheKey = cacheKey)
        }
        if (!runningCacheKeys.add(cacheKey)) {
            return EnsureResult(AiReadAloudRoleState.STATUS_RUNNING, message = "分配角色中", cacheKey = cacheKey)
        }
        val now = System.currentTimeMillis()
        keepAliveId = AiTaskKeepAlive.retain(
            title = stageMessage(stage, "分配角色中"),
            content = "${currentBook.name} · ${currentChapter.chapter.title}",
            kind = AiTaskKeepAlive.KIND_ROLE_ASSIGN
        )
        postState(currentBook, currentChapter, stage, AiReadAloudRoleState.STATUS_RUNNING, stageMessage(stage, "分配角色中"), cleanParagraphs.size)
        updateRoleKeepAlive(
            taskId = keepAliveId,
            book = currentBook,
            chapter = currentChapter,
            stage = stage,
            status = AiReadAloudRoleState.STATUS_RUNNING,
            message = stageMessage(stage, "分配角色中"),
            paragraphCount = cleanParagraphs.size,
            segmentCount = 0,
            usageSnapshot = usageTracker.snapshot(),
            force = true
        )
        appDb.aiReadAloudRoleCacheDao.upsert(
            AiReadAloudRoleCache(
                cacheKey = cacheKey,
                bookUrl = characterBookKey,
                chapterKey = currentChapter.chapter.url?.ifBlank { currentChapter.chapter.title }.orEmpty(),
                chapterIndex = currentChapter.chapter.index,
                chapterTitle = currentChapter.chapter.title,
                contentHash = contentHash,
                mode = mode,
                paragraphCount = cleanParagraphs.size,
                status = AiReadAloudRoleCache.STATUS_RUNNING,
                retryCount = oldCache?.retryCount ?: 0,
                characterHash = characterHash(characterBookKey),
                voiceHash = voiceHash(characterBookKey),
                createdAt = oldCache?.createdAt?.takeIf { it > 0 } ?: now,
                updatedAt = now
            )
        )
        try {
            val preprocess = ReadAloudRolePreprocessor.process(cleanParagraphs)
            val result = requestBatchedUnitAssignments(
                book = currentBook,
                textChapter = currentChapter,
                paragraphs = cleanParagraphs,
                preprocess = preprocess,
                contextParagraphs = contextParagraphs,
                mergeGapParagraphs = mergeGapParagraphs,
                prompt = prompt,
                promptCacheKey = roleKey.promptCacheKey,
                usageTracker = usageTracker,
                fullChapterMode = mode.substringBefore("|") == AppConfig.AI_READ_ALOUD_ROLE_MODE_FULL,
                onPreview = { preview, candidateCount, source ->
                    postPreview(
                        AiReadAloudRoleState.STATUS_RUNNING,
                        if (source == AiReadAloudRoleState.SOURCE_RULE) "本地预处理 ${preview.size} 个片段" else "已确认 ${preview.size} 个分配片段",
                        source,
                        preview,
                        newCharacterCandidateCount = candidateCount
                    )
                }
            )
            val aiSegments = result.segments
                .distinctBy { "${it.paragraphIndex}:${it.start}:${it.end}:${it.roleType}:${it.characterName}" }
                .sortedWith(compareBy<Segment> { it.paragraphIndex }.thenBy { it.start }.thenBy { it.end })
            val successAt = System.currentTimeMillis()
            if (aiSegments.isEmpty() || result.aiRequired && !result.aiSatisfied) {
                val fallback = resolveSegmentCharacters(
                    aiSegments.ifEmpty { buildDefaultSegments(cleanParagraphs) },
                    appDb.bookCharacterDao.characters(characterBookKey)
                )
                val error = if (aiSegments.isEmpty()) {
                    "AI未返回有效分角色片段，已使用默认分角色"
                } else {
                    "AI未完整确认不确定角色片段，请重新分配当前章节"
                }
                appDb.aiReadAloudRoleCacheDao.upsert(
                    AiReadAloudRoleCache(
                        cacheKey = cacheKey,
                        bookUrl = characterBookKey,
                        chapterKey = currentChapter.chapter.url?.ifBlank { currentChapter.chapter.title }.orEmpty(),
                        chapterIndex = currentChapter.chapter.index,
                        chapterTitle = currentChapter.chapter.title,
                        contentHash = contentHash,
                        mode = mode,
                        paragraphCount = cleanParagraphs.size,
                        status = AiReadAloudRoleCache.STATUS_FAILED,
                        retryCount = ((oldCache?.retryCount ?: 0) + 1).coerceAtMost(3),
                        lastError = error,
                        segmentsJson = fallback.toJsonArray().toString(),
                        characterHash = characterHash(characterBookKey),
                        voiceHash = voiceHash(characterBookKey),
                        createdAt = oldCache?.createdAt?.takeIf { it > 0 } ?: successAt,
                        updatedAt = successAt
                    )
                )
                postPreview(
                    AiReadAloudRoleState.STATUS_FAILED,
                    if (aiSegments.isEmpty()) "AI未返回有效分角色片段，请重新分配当前章节" else "AI未完整确认不确定角色片段，请重新分配当前章节",
                    AiReadAloudRoleState.SOURCE_FALLBACK,
                    buildPreviewSegments(
                        characterBookKey,
                        fallback,
                        cleanParagraphs,
                        AiReadAloudRoleState.SOURCE_FALLBACK
                    ),
                    error = error
                )
                return EnsureResult(AiReadAloudRoleState.STATUS_FAILED, error = error, cacheKey = cacheKey)
            }
            val resolved = persistDetectedCharacters(
                characterBookKey,
                currentBook.bookUrl,
                aiSegments,
                result.candidates,
                cleanParagraphs
            )
            val finalUsage = usageTracker.snapshot()
            appDb.aiReadAloudRoleCacheDao.upsert(
                AiReadAloudRoleCache(
                    cacheKey = cacheKey,
                    bookUrl = characterBookKey,
                    chapterKey = currentChapter.chapter.url?.ifBlank { currentChapter.chapter.title }.orEmpty(),
                    chapterIndex = currentChapter.chapter.index,
                    chapterTitle = currentChapter.chapter.title,
                    contentHash = contentHash,
                    mode = mode,
                    paragraphCount = cleanParagraphs.size,
                    status = AiReadAloudRoleCache.STATUS_SUCCESS,
                    retryCount = oldCache?.retryCount ?: 0,
                    segmentsJson = resolved.first.toCacheJson(
                        preprocessVersion = ReadAloudRolePreprocessor.VERSION,
                        contentHash = contentHash,
                        usageSnapshot = finalUsage
                    ),
                    createdCharacterIdsJson = JSONArray(resolved.second).toString(),
                    characterHash = characterHash(characterBookKey),
                    voiceHash = voiceHash(characterBookKey),
                    createdAt = oldCache?.createdAt?.takeIf { it > 0 } ?: successAt,
                    updatedAt = successAt
                )
            )
            postPreview(
                AiReadAloudRoleState.STATUS_SUCCESS,
                stageMessage(stage, "角色分配完成"),
                AiReadAloudRoleState.SOURCE_RESOLVED,
                buildPreviewSegments(
                    characterBookKey,
                    resolved.first,
                    cleanParagraphs,
                    AiReadAloudRoleState.SOURCE_RESOLVED
                ),
                createdCharacterCount = resolved.second.size,
                newCharacterCandidateCount = result.candidates.size
            )
            return EnsureResult(
                status = AiReadAloudRoleState.STATUS_SUCCESS,
                segmentCount = resolved.first.size,
                createdCharacterCount = resolved.second.size,
                message = "角色分配完成",
                cacheKey = cacheKey
            )
        } catch (throwable: Throwable) {
            val failedAt = System.currentTimeMillis()
            val fallback = resolveSegmentCharacters(
                buildDefaultSegments(cleanParagraphs),
                appDb.bookCharacterDao.characters(characterBookKey)
            )
            val error = throwable.localizedMessage ?: throwable.javaClass.simpleName
            appDb.aiReadAloudRoleCacheDao.upsert(
                    AiReadAloudRoleCache(
                        cacheKey = cacheKey,
                        bookUrl = characterBookKey,
                    chapterKey = currentChapter.chapter.url?.ifBlank { currentChapter.chapter.title }.orEmpty(),
                    chapterIndex = currentChapter.chapter.index,
                    chapterTitle = currentChapter.chapter.title,
                    contentHash = contentHash,
                    mode = mode,
                    paragraphCount = cleanParagraphs.size,
                    status = AiReadAloudRoleCache.STATUS_FAILED,
                    retryCount = ((oldCache?.retryCount ?: 0) + 1).coerceAtMost(3),
                    lastError = error.take(400),
                    segmentsJson = fallback.toJsonArray().toString(),
                        characterHash = characterHash(characterBookKey),
                        voiceHash = voiceHash(characterBookKey),
                    createdAt = oldCache?.createdAt?.takeIf { it > 0 } ?: failedAt,
                    updatedAt = failedAt
                )
            )
            postPreview(
                AiReadAloudRoleState.STATUS_FAILED,
                "AI分角色失败，请重新分配当前章节",
                AiReadAloudRoleState.SOURCE_FALLBACK,
                buildPreviewSegments(
                    characterBookKey,
                    fallback,
                    cleanParagraphs,
                    AiReadAloudRoleState.SOURCE_FALLBACK
                ),
                error = error
            )
            AppLog.put("AI分角色标注失败\n$error", throwable)
            return EnsureResult(AiReadAloudRoleState.STATUS_FAILED, error = error, cacheKey = cacheKey)
        } finally {
            runningCacheKeys.remove(cacheKey)
            AiTaskKeepAlive.release(keepAliveId)
        }
    }

    private suspend fun waitForRunningCache(
        cacheKey: String,
        book: Book,
        textChapter: TextChapter,
        paragraphs: List<String>,
        stage: String
    ): EnsureResult? {
        if (cacheKey.isBlank()) return null
        val cleanParagraphs = paragraphs.map { it.trimEnd() }.filter { it.isNotBlank() }
        val deadline = System.currentTimeMillis() + RUNNING_WAIT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val cache = appDb.aiReadAloudRoleCacheDao.get(cacheKey)
            when {
                cache?.status == AiReadAloudRoleCache.STATUS_SUCCESS &&
                        cache.segmentsJson.isNotBlank() -> {
                    val segments = segmentsFromJson(cache.segmentsJson)
                    postState(
                        book = book,
                        chapter = textChapter,
                        stage = stage,
                        status = AiReadAloudRoleState.STATUS_SUCCESS,
                        message = stageMessage(stage, "角色分配完成"),
                        paragraphCount = cleanParagraphs.size,
                        segmentCount = segments.size,
                        previewSource = AiReadAloudRoleState.SOURCE_CACHE,
                        previewSegments = buildPreviewSegments(
                            book.characterBookKey(),
                            segments,
                            cleanParagraphs,
                            AiReadAloudRoleState.SOURCE_CACHE
                        )
                    )
                    return EnsureResult(
                        status = AiReadAloudRoleState.STATUS_SUCCESS,
                        segmentCount = segments.size,
                        message = "角色分配完成",
                        cacheKey = cacheKey
                    )
                }
                cache?.status == AiReadAloudRoleCache.STATUS_FAILED -> {
                    return EnsureResult(
                        status = AiReadAloudRoleState.STATUS_FAILED,
                        error = cache.lastError.ifBlank { "多角色分配失败" },
                        cacheKey = cacheKey
                    )
                }
            }
            delay(RUNNING_WAIT_STEP_MILLIS)
        }
        return EnsureResult(AiReadAloudRoleState.STATUS_FAILED, error = "等待多角色分配超时", cacheKey = cacheKey)
    }

    fun routeForCue(
        bookUrl: String?,
        chapterIndex: Int,
        cueIndex: Int,
        cueText: String? = null,
        cacheKey: String? = null
    ): SpeechRoute? {
        if (bookUrl.isNullOrBlank() || cueIndex < 0) return null
        val best = assignedSegmentsForCue(bookUrl, chapterIndex, cueIndex, cacheKey)
            .filter { it.roleType == "character" || it.roleType == "thought" }
            .maxWithOrNull(compareBy<Segment> { it.confidence }.thenBy { it.end - it.start })
            ?: return null
        return routeForSegment(bookUrl, best)
    }

    fun routeForSegment(
        bookUrl: String?,
        segment: Segment
    ): SpeechRoute? {
        if (bookUrl.isNullOrBlank()) return null
        val character = when {
            segment.characterId > 0L -> appDb.bookCharacterDao.getCharacter(segment.characterId)
                ?.takeIf { it.bookUrl == bookUrl }
            segment.characterName.isNotBlank() -> {
                val byName = charactersByNormalizedName(appDb.bookCharacterDao.characters(bookUrl))
                byName[characterNameKey(segment.characterName)]
            }
            else -> null
        } ?: return null
        val route = SpeechRouteSanitizer.validOrNull(SpeechRoute.fromJson(character.speechRouteJson))
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

    fun segmentsForCue(
        bookUrl: String?,
        chapterIndex: Int,
        cueIndex: Int,
        cueText: String? = null,
        cacheKey: String? = null
    ): List<Segment> {
        return assignedSegmentsForCue(bookUrl, chapterIndex, cueIndex, cacheKey)
            .ifEmpty { cueText?.let { buildDefaultSegments(listOf(it), paragraphOffset = cueIndex) }.orEmpty() }
    }

    fun assignedSegmentsForCue(
        bookUrl: String?,
        chapterIndex: Int,
        cueIndex: Int,
        cacheKey: String? = null
    ): List<Segment> {
        if (cueIndex < 0) return emptyList()
        return assignedSegmentsByCue(bookUrl, chapterIndex, cacheKey)[cueIndex].orEmpty()
    }

    fun assignedSegmentsByCue(
        bookUrl: String?,
        chapterIndex: Int,
        cacheKey: String? = null
    ): Map<Int, List<Segment>> {
        if (bookUrl.isNullOrBlank()) return emptyMap()
        if (cacheKey.isNullOrBlank()) return emptyMap()
        val cache = appDb.aiReadAloudRoleCacheDao.get(cacheKey)
            ?.takeIf { it.status == AiReadAloudRoleCache.STATUS_SUCCESS }
            ?: return emptyMap()
        return segmentsFromJson(cache.segmentsJson)
            .groupBy { it.paragraphIndex }
            .mapValues { (_, segments) ->
                segments.sortedWith(compareBy<Segment> { it.start }.thenBy { it.end })
            }
    }

    fun defaultSegmentsForCue(cueIndex: Int, cueText: String): List<Segment> {
        return buildDefaultSegments(listOf(cueText), paragraphOffset = cueIndex)
    }

    private fun buildDefaultSegments(
        paragraphs: List<String>,
        paragraphOffset: Int = 0
    ): List<Segment> {
        return segmentsFromUnits(ReadAloudRolePreprocessor.process(paragraphs, paragraphOffset).units)
    }

    private fun buildDefaultSegmentsForText(paragraphIndex: Int, text: String): List<Segment> {
        if (text.isBlank()) return emptyList()
        parseSpeakerColonSegment(paragraphIndex, text)?.let { return listOf(it) }
        val speechRanges = parseQuotedSpeechRanges(text)
        if (speechRanges.isEmpty()) {
            return listOf(narratorSegment(paragraphIndex, 0, text.length))
        }
        val result = mutableListOf<Segment>()
        var cursor = 0
        speechRanges.forEach { range ->
            if (range.start > cursor) {
                result += narratorSegment(paragraphIndex, cursor, range.start)
            }
            result += Segment(
                paragraphIndex = paragraphIndex,
                start = range.start,
                end = range.end,
                roleType = "character",
                characterName = range.speakerName,
                confidence = if (range.speakerName.isBlank()) 0.45 else 0.62
            )
            cursor = range.end.coerceAtLeast(cursor)
        }
        if (cursor < text.length) {
            result += narratorSegment(paragraphIndex, cursor, text.length)
        }
        return result.filter { it.start < it.end }
    }

    private data class SpeechRange(
        val start: Int,
        val end: Int,
        val speakerName: String
    )

    private fun parseSpeakerColonSegment(paragraphIndex: Int, text: String): Segment? {
        val colonIndex = text.indexOfFirst { it == '：' || it == ':' }
        if (colonIndex !in 1..24) return null
        val speaker = text.substring(0, colonIndex).trim()
        if (!isLikelySpeakerName(speaker)) return null
        val start = (colonIndex + 1).coerceAtMost(text.length)
        if (start >= text.length) return null
        return Segment(
            paragraphIndex = paragraphIndex,
            start = start,
            end = text.length,
            roleType = "character",
            characterName = speaker,
            confidence = 0.68
        )
    }

    private fun parseQuotedSpeechRanges(text: String): List<SpeechRange> {
        val result = mutableListOf<SpeechRange>()
        var index = 0
        while (index < text.length) {
            val close = when (text[index]) {
                '“' -> '”'
                '‘' -> '’'
                '"' -> '"'
                '\'' -> '\''
                else -> null
            }
            if (close == null) {
                index++
                continue
            }
            val endQuote = text.indexOf(close, index + 1)
            if (endQuote <= index + 1) {
                index++
                continue
            }
            val start = index
            val end = endQuote + 1
            result += SpeechRange(
                start = start,
                end = end,
                speakerName = inferSpeakerNameBeforeQuote(text, index)
            )
            index = endQuote + 1
        }
        return result
    }

    private fun inferSpeakerNameBeforeQuote(text: String, quoteIndex: Int): String {
        val prefix = text.substring((quoteIndex - 48).coerceAtLeast(0), quoteIndex)
        val match = speechVerbRegex.find(prefix) ?: return ""
        val name = match.groupValues.getOrNull(1).orEmpty().trim()
        return name.takeIf(::isLikelySpeakerName).orEmpty()
    }

    private fun narratorSegment(paragraphIndex: Int, start: Int, end: Int): Segment {
        return Segment(
            paragraphIndex = paragraphIndex,
            start = start,
            end = end,
            roleType = "narrator",
            characterName = "旁白",
            confidence = 0.5
        )
    }

    private fun isLikelySpeakerName(value: String): Boolean {
        val name = value.trim().trim('“', '”', '‘', '’', '"', '\'', '，', ',', '。', '：', ':')
        if (name.length !in 2..24) return false
        if (name.any { it.isWhitespace() }) return false
        if (name.any { it in "，。！？；,.!?;、（）()《》<>[]【】" }) return false
        return name !in setOf("旁白", "作者", "读者", "我", "你", "他", "她", "它", "我们", "你们", "他们", "她们", "众人", "有人")
    }

    private fun postState(
        book: Book,
        chapter: TextChapter,
        stage: String,
        status: String,
        message: String,
        paragraphCount: Int,
        segmentCount: Int = 0,
        createdCharacterCount: Int = 0,
        newCharacterCandidateCount: Int = 0,
        previewSource: String = AiReadAloudRoleState.SOURCE_NONE,
        previewSegments: List<AiReadAloudRolePreviewSegment> = emptyList(),
        error: String = "",
        usageSnapshot: RoleUsageSnapshot = RoleUsageSnapshot()
    ) {
        val usage = usageSnapshot.usage
        postEvent(
            EventBus.AI_READ_ALOUD_ROLE_STATE,
            AiReadAloudRoleState(
                bookUrl = book.bookUrl,
                chapterIndex = chapter.chapter.index,
                chapterTitle = chapter.chapter.title,
                stage = stage,
                status = status,
                message = message,
                paragraphCount = paragraphCount,
                segmentCount = segmentCount,
                createdCharacterCount = createdCharacterCount,
                newCharacterCandidateCount = newCharacterCandidateCount,
                previewSource = previewSource,
                previewSegments = previewSegments,
                elapsedMillis = usageSnapshot.elapsedMillis,
                requestCount = usageSnapshot.requestCount,
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                totalTokens = usage.totalTokens,
                cachedInputTokens = usage.cachedInputTokens,
                error = error
            )
        )
    }

    private fun stageMessage(stage: String, action: String): String {
        val prefix = if (stage == AiReadAloudRoleState.STAGE_NEXT) "下一章节" else "当前章节"
        return "$prefix$action"
    }

    private fun updateRoleKeepAlive(
        taskId: String?,
        book: Book,
        chapter: TextChapter,
        stage: String,
        status: String,
        message: String,
        paragraphCount: Int,
        segmentCount: Int,
        usageSnapshot: RoleUsageSnapshot,
        force: Boolean = status != AiReadAloudRoleState.STATUS_RUNNING
    ) {
        if (taskId.isNullOrBlank()) return
        val title = when (status) {
            AiReadAloudRoleState.STATUS_SUCCESS -> stageMessage(stage, "角色分配完成")
            AiReadAloudRoleState.STATUS_FAILED -> stageMessage(stage, "角色分配失败")
            else -> stageMessage(stage, "分配角色中")
        }
        AiTaskKeepAlive.update(
            taskId = taskId,
            title = title,
            content = "${book.name} · ${chapter.chapter.title}",
            progressText = buildRoleKeepAliveProgress(
                message = message,
                paragraphCount = paragraphCount,
                segmentCount = segmentCount,
                usageSnapshot = usageSnapshot
            ),
            force = force
        )
    }

    private fun buildRoleKeepAliveProgress(
        message: String,
        paragraphCount: Int,
        segmentCount: Int,
        usageSnapshot: RoleUsageSnapshot
    ): String {
        return buildList {
            add(message)
            if (segmentCount > 0) add("已确认 $segmentCount 个片段")
            if (paragraphCount > 0) add("$paragraphCount 段")
            if (usageSnapshot.requestCount > 0) add("请求 ${usageSnapshot.requestCount} 次")
            if (usageSnapshot.elapsedMillis > 0) add("${(usageSnapshot.elapsedMillis / 1000L).coerceAtLeast(1L)}s")
        }.joinToString(" · ")
    }

    private fun segmentCount(json: String): Int {
        val text = json.trim()
        if (text.isBlank()) return 0
        if (text.startsWith("{")) {
            return runCatching {
                JSONObject(text).optJSONArray("segments")?.length() ?: 0
            }.getOrDefault(0)
        }
        return runCatching { JSONArray(text).length() }.getOrDefault(0)
    }

    private fun segmentsFromJson(json: String): List<Segment> {
        val text = json.trim()
        if (text.isBlank()) return emptyList()
        val array = if (text.startsWith("{")) {
            runCatching { JSONObject(text).optJSONArray("segments") }.getOrNull()
        } else {
            runCatching { JSONArray(text) }.getOrNull()
        } ?: return emptyList()
        val result = mutableListOf<Segment>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val start = item.optInt("start", -1)
            val end = item.optInt("end", -1)
            if (start < 0 || end <= start) continue
            result += Segment(
                paragraphIndex = item.optInt("paragraphIndex", -1),
                start = start,
                end = end,
                roleType = item.optString("roleType").trim()
                    .takeIf { it in setOf("narrator", "character", "thought", "other") }
                    ?: "other",
                characterName = item.optString("characterName").trim().take(80),
                characterId = item.optLong("characterId", 0L).coerceAtLeast(0L),
                emotionName = item.optString("emotionName").trim().take(40),
                emotionTag = item.optString("emotionTag").trim().take(40),
                confidence = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            )
        }
        return result.filter { it.paragraphIndex >= 0 }
    }

    private fun buildPreviewSegments(
        bookUrl: String,
        segments: List<Segment>,
        paragraphs: List<String>,
        source: String,
        paragraphOffset: Int = 0
    ): List<AiReadAloudRolePreviewSegment> {
        if (segments.isEmpty()) return emptyList()
        val characters = appDb.bookCharacterDao.characters(bookUrl)
        val byId = characters.associateBy { it.id }
        val byName = charactersByNormalizedName(characters)
        return segments
            .sortedWith(compareBy<Segment> { it.paragraphIndex }.thenBy { it.start }.thenBy { it.end })
            .mapNotNull { segment ->
                val paragraph = paragraphs.getOrNull(segment.paragraphIndex - paragraphOffset)
                    ?: return@mapNotNull null
                val start = segment.start.coerceIn(0, paragraph.length)
                val end = segment.end.coerceIn(start, paragraph.length)
                if (start >= end) return@mapNotNull null
                val character = when {
                    segment.characterId > 0L -> byId[segment.characterId]
                    segment.characterName.isNotBlank() -> byName[characterNameKey(segment.characterName)]
                    else -> null
                }
                val route = character
                    ?.speechRouteJson
                    ?.let(SpeechRoute::fromJson)
                    ?.let { route ->
                        if (segment.emotionName.isNotBlank() || segment.emotionTag.isNotBlank()) {
                            route.copy(
                                emotionName = segment.emotionName.ifBlank { route.emotionName },
                                emotionTag = segment.emotionTag.ifBlank { route.emotionTag }
                            )
                        } else {
                            route
                        }
                    }
                AiReadAloudRolePreviewSegment(
                    paragraphIndex = segment.paragraphIndex,
                    start = start,
                    end = end,
                    text = paragraph.substring(start, end),
                    roleType = segment.roleType,
                    characterName = character?.name
                        ?: segment.characterName.ifBlank {
                            if (segment.roleType == "narrator") "旁白" else ""
                        },
                    characterId = character?.id ?: segment.characterId,
                    matchedCharacter = character != null,
                    emotionName = segment.emotionName.ifBlank { route?.emotionName.orEmpty() },
                    emotionTag = segment.emotionTag.ifBlank { route?.emotionTag.orEmpty() },
                    speakerName = route?.speakerName.orEmpty(),
                    toneID = route?.toneID.orEmpty(),
                    groupName = route?.groupName.orEmpty(),
                    engineType = route?.engineType.orEmpty(),
                    confidence = segment.confidence,
                    source = source
                )
            }
    }

    private suspend fun requestBatchedUnitAssignments(
        book: Book,
        textChapter: TextChapter,
        paragraphs: List<String>,
        preprocess: io.legado.app.help.readaloud.role.ReadAloudRolePreprocessResult,
        contextParagraphs: Int,
        mergeGapParagraphs: Int,
        prompt: String,
        promptCacheKey: String,
        usageTracker: RoleUsageTracker,
        fullChapterMode: Boolean,
        onPreview: (List<AiReadAloudRolePreviewSegment>, Int, String) -> Unit
    ): RequestResult = coroutineScope {
        val allTargetParagraphs = paragraphs.indices.toSet()
        val localSegments = segmentsFromUnits(preprocess.units, allTargetParagraphs)
        if (localSegments.isNotEmpty()) {
            onPreview(
                buildPreviewSegments(
                    book.characterBookKey(),
                    localSegments,
                    paragraphs,
                    AiReadAloudRoleState.SOURCE_RULE
                ),
                0,
                AiReadAloudRoleState.SOURCE_RULE
            )
        }
        val uncertainUnits = preprocess.units.filter { it.needsAi }
        if (uncertainUnits.isEmpty()) {
            return@coroutineScope RequestResult(localSegments, aiRequired = false, aiSatisfied = true)
        }

        val resolutionMap = linkedMapOf<String, UnitResolution>()
        val candidates = mutableListOf<CharacterCandidate>()
        val firstPassBatches = buildUnitAssignmentBatches(
            uncertainUnits,
            paragraphs,
            fullChapterMode,
            contextParagraphs,
            mergeGapParagraphs
        )
        var failedBatches = requestUnitAssignmentBatches(
            book = book,
            textChapter = textChapter,
            paragraphs = paragraphs,
            allUnits = preprocess.units,
            batches = firstPassBatches,
            fullChapterMode = fullChapterMode,
            prompt = prompt,
            promptCacheKey = promptCacheKey,
            attempt = 0,
            knownResolutions = emptyList(),
            candidates = candidates,
            resolutionMap = resolutionMap,
            usageTracker = usageTracker,
            onPreview = onPreview
        )

        repeat(UNKNOWN_RETRY_ATTEMPTS) { retryIndex ->
            val unresolved = unresolvedUnits(uncertainUnits, resolutionMap)
            val failedUnits = failedBatches.flatMap { it.units }
            val retryUnits = (unresolved + failedUnits)
                .distinctBy { it.id }
                .sortedWith(compareBy<ReadAloudRoleUnit> { it.firstParagraphIndex }.thenBy { it.firstStart })
            if (retryUnits.isEmpty()) {
                return@repeat
            }
            val retryContextParagraphs = expandedRetryContextParagraphs(
                contextParagraphs = contextParagraphs,
                fullChapterMode = fullChapterMode
            )
            val retryBatches = buildUnitAssignmentBatches(
                retryUnits,
                paragraphs,
                fullChapterMode,
                retryContextParagraphs,
                mergeGapParagraphs
            )
            failedBatches = requestUnitAssignmentBatches(
                book = book,
                textChapter = textChapter,
                paragraphs = paragraphs,
                allUnits = preprocess.units,
                batches = retryBatches,
                fullChapterMode = fullChapterMode,
                prompt = prompt,
                promptCacheKey = promptCacheKey,
                attempt = retryIndex + 1,
                knownResolutions = resolutionMap.values.toList(),
                candidates = candidates,
                resolutionMap = resolutionMap,
                usageTracker = usageTracker,
                onPreview = onPreview
            )
        }

        unresolvedUnits(uncertainUnits, resolutionMap)
            .map(::unknownFinalResolution)
            .forEach { resolutionMap[it.unitId] = it }
        val resolvedUnits = applyUnitResolutions(preprocess.units, resolutionMap.values.toList())
        val resolvedSegments = segmentsFromUnits(resolvedUnits, allTargetParagraphs)
        RequestResult(
            segments = resolvedSegments,
            candidates = candidates.distinctBy { it.name },
            aiRequired = true,
            aiSatisfied = true
        )
    }

    private suspend fun requestUnitAssignmentBatches(
        book: Book,
        textChapter: TextChapter,
        paragraphs: List<String>,
        allUnits: List<ReadAloudRoleUnit>,
        batches: List<UnitAssignmentBatch>,
        fullChapterMode: Boolean,
        prompt: String,
        promptCacheKey: String,
        attempt: Int,
        knownResolutions: List<UnitResolution>,
        candidates: MutableList<CharacterCandidate>,
        resolutionMap: MutableMap<String, UnitResolution>,
        usageTracker: RoleUsageTracker,
        onPreview: (List<AiReadAloudRolePreviewSegment>, Int, String) -> Unit
    ): List<UnitAssignmentBatch> = supervisorScope {
        if (batches.isEmpty()) return@supervisorScope emptyList()
        val semaphore = Semaphore(
            if (fullChapterMode) 1
            else AppConfig.aiReadAloudRoleThreadCount
        )
        val results = batches.map { batch ->
            async {
                semaphore.withPermit {
                    runCatching {
                        UnitAssignmentBatchRunResult(
                            batch = batch,
                            result = requestUnitAssignmentBatch(
                                book = book,
                                textChapter = textChapter,
                                paragraphs = paragraphs,
                                allUnits = allUnits,
                                batch = batch,
                                fullChapterMode = fullChapterMode,
                                prompt = prompt,
                                promptCacheKey = promptCacheKey,
                                attempt = attempt,
                                knownResolutions = knownResolutions,
                                usageTracker = usageTracker
                            ),
                            failed = false
                        )
                    }.getOrElse {
                        AppLog.putDebug("多角色分配批次失败，将只重试该批次：batch=${batch.index}", it)
                        UnitAssignmentBatchRunResult(
                            batch = batch,
                            result = UnitAssignmentResult(emptyList(), emptyList()),
                            failed = true
                        )
                    }
                }
            }
        }.awaitAll()
        results.forEach { batchResult ->
            val result = batchResult.result
            result.resolutions.forEach { resolution ->
                resolutionMap[resolution.unitId] = resolution
            }
            candidates += result.candidates
            val previewSegments = segmentsFromUnits(
                applyUnitResolutions(allUnits, resolutionMap.values.toList()),
                paragraphs.indices.toSet()
            )
            onPreview(
                buildPreviewSegments(
                    book.characterBookKey(),
                    previewSegments,
                    paragraphs,
                    AiReadAloudRoleState.SOURCE_AI_CONFIRM
                ),
                candidates.distinctBy { it.name }.size,
                AiReadAloudRoleState.SOURCE_AI_CONFIRM
            )
        }
        return@supervisorScope results
            .filter { it.failed }
            .map { it.batch }
    }

    private fun expandedRetryContextParagraphs(
        contextParagraphs: Int,
        fullChapterMode: Boolean
    ): Int {
        if (fullChapterMode) return contextParagraphs
        val safeContext = contextParagraphs.coerceIn(0, 20)
        return (safeContext * 2 + 2).coerceAtMost(20).coerceAtLeast(safeContext)
    }

    private suspend fun requestUnitAssignmentBatch(
        book: Book,
        textChapter: TextChapter,
        paragraphs: List<String>,
        allUnits: List<ReadAloudRoleUnit>,
        batch: UnitAssignmentBatch,
        fullChapterMode: Boolean,
        prompt: String,
        promptCacheKey: String,
        attempt: Int,
        knownResolutions: List<UnitResolution>,
        usageTracker: RoleUsageTracker
    ): UnitAssignmentResult {
        val requestedUnitIds = batch.units.map { it.id }.toSet()
        val collectedCandidates = mutableListOf<CharacterCandidate>()
        val collectedResolutions = mutableListOf<UnitResolution>()
        val tool = AiResolvedTool(TOOL_CONFIRM_UNITS, confirmUnitsDefinition()) { args ->
            val resolutions = parseUnitResolutions(args, requestedUnitIds)
            val candidates = parseCandidates(args)
            collectedResolutions += resolutions
            collectedCandidates += candidates
            JSONObject().apply {
                put("ok", true)
                put("recorded", resolutions.size)
                put("newCharacters", candidates.size)
            }.toString()
        }
        val requestUsage = AiReadAloudUsageRecorder.Tracker()
        usageTracker.onRequest()
        requestUsage.onRequest()
        val response = try {
            AiChatService.requestSingleToolCall(
                messages = listOf(
                    AiChatMessage(
                        role = AiChatMessage.Role.USER,
                        content = buildBatchUnitPrompt(
                            book = book,
                            textChapter = textChapter,
                            paragraphs = paragraphs,
                            allUnits = allUnits,
                            batch = batch,
                            fullChapterMode = fullChapterMode,
                            prompt = prompt,
                            attempt = attempt,
                            knownResolutions = knownResolutions
                        )
                    )
                ),
                tool = tool,
                modelConfigOverride = AppConfig.aiReadAloudRoleModelConfig,
                fallbackModelConfig = AppConfig.aiReadAloudRoleBackupModelConfig,
                promptCacheKeyOverride = promptCacheKey,
                firstResponseTimeoutMillis = AppConfig.aiReadAloudRoleFirstResponseTimeoutMillis,
                includeChatContext = false,
                onUsage = {
                    usageTracker.onUsage(it)
                    requestUsage.onUsage(it)
                }
            ).also {
                AiReadAloudUsageRecorder.record(
                    type = AiReadAloudUsageRecord.TYPE_ROLE,
                    status = AiReadAloudUsageRecord.STATUS_SUCCESS,
                    book = book,
                    chapter = textChapter,
                    cacheKey = promptCacheKey,
                    batchName = if (fullChapterMode) "全文批处理" else "并发查找批次 ${batch.index + 1}",
                    modelConfig = it.modelConfig ?: AppConfig.aiReadAloudRoleModelConfig,
                    snapshot = requestUsage.snapshot(),
                    summary = "targetUnits=${batch.units.size}, attempt=$attempt"
                )
            }
        } catch (throwable: Throwable) {
            AiReadAloudUsageRecorder.record(
                type = AiReadAloudUsageRecord.TYPE_ROLE,
                status = AiReadAloudUsageRecord.STATUS_FAILED,
                book = book,
                chapter = textChapter,
                cacheKey = promptCacheKey,
                batchName = if (fullChapterMode) "全文批处理" else "并发查找批次 ${batch.index + 1}",
                modelConfig = AppConfig.aiReadAloudRoleModelConfig,
                snapshot = requestUsage.snapshot(),
                summary = "targetUnits=${batch.units.size}, attempt=$attempt",
                error = throwable.localizedMessage ?: throwable.javaClass.simpleName
            )
            throw throwable
        }
        if (response.hasToolCall) {
            val args = runCatching { JSONObject(response.arguments) }.getOrNull()
            collectedResolutions += parseUnitResolutions(args, requestedUnitIds)
            collectedCandidates += parseCandidates(args)
        }
        if (collectedResolutions.isEmpty()) {
            val fallback = parseUnitFallbackResult(response.content, requestedUnitIds)
            collectedResolutions += fallback.first
            collectedCandidates += fallback.second
        }
        val normalizedResolutions = collectedResolutions
            .map { normalizeUnitResolution(it) }
            .distinctBy { it.unitId }
        collectedCandidates += missingNewCharacterCandidates(
            book = book,
            units = batch.units,
            resolutions = normalizedResolutions,
            candidates = collectedCandidates
        )
        return UnitAssignmentResult(
            resolutions = normalizedResolutions,
            candidates = collectedCandidates.distinctBy { it.name }
        )
    }

    private fun buildUnitAssignmentBatches(
        units: List<ReadAloudRoleUnit>,
        paragraphs: List<String>,
        fullChapterMode: Boolean,
        contextParagraphs: Int,
        mergeGapParagraphs: Int
    ): List<UnitAssignmentBatch> {
        val paragraphCount = paragraphs.size
        if (units.isEmpty() || paragraphCount <= 0) return emptyList()
        val sortedUnits = units.sortedWith(
            compareBy<ReadAloudRoleUnit> { it.firstParagraphIndex }.thenBy { it.firstStart }
        )
        if (fullChapterMode) {
            return listOf(
                UnitAssignmentBatch(
                    index = 0,
                    targetParagraphs = (0 until paragraphCount).toList(),
                    units = sortedUnits
                )
            )
        }
        val safeContext = contextParagraphs.coerceIn(0, 20)
        val safeMergeGap = mergeGapParagraphs.coerceIn(0, 10)
        val batches = mutableListOf<UnitAssignmentBatch>()
        var currentUnits = mutableListOf<ReadAloudRoleUnit>()
        var currentStart = -1
        var currentEnd = -1

        fun contextCharCount(start: Int, end: Int): Int {
            if (start < 0 || end < start) return 0
            return (start..end).sumOf { paragraphs.getOrNull(it)?.length ?: 0 }
        }

        fun flushCurrent() {
            if (currentUnits.isEmpty()) return
            batches += UnitAssignmentBatch(
                index = batches.size,
                targetParagraphs = (currentStart..currentEnd).toList(),
                units = currentUnits.sortedWith(
                    compareBy<ReadAloudRoleUnit> { it.firstParagraphIndex }.thenBy { it.firstStart }
                )
            )
            currentUnits = mutableListOf()
            currentStart = -1
            currentEnd = -1
        }

        sortedUnits.forEach { unit ->
            val firstParagraph = unit.ranges.minOfOrNull { it.paragraphIndex } ?: unit.firstParagraphIndex
            val lastParagraph = unit.ranges.maxOfOrNull { it.paragraphIndex } ?: firstParagraph
            val unitStart = (firstParagraph - safeContext).coerceAtLeast(0)
            val unitEnd = (lastParagraph + safeContext).coerceAtMost(paragraphCount - 1)
            if (currentUnits.isEmpty()) {
                currentUnits += unit
                currentStart = unitStart
                currentEnd = unitEnd
                return@forEach
            }
            val mergedStart = minOf(currentStart, unitStart)
            val mergedEnd = maxOf(currentEnd, unitEnd)
            val contextGap = (unitStart - currentEnd - 1).coerceAtLeast(0)
            val shouldMerge = contextGap <= safeMergeGap &&
                    currentUnits.size < MAX_SEARCH_BATCH_TARGET_UNITS &&
                    contextCharCount(mergedStart, mergedEnd) <= MAX_SEARCH_BATCH_CONTEXT_CHARS
            if (!shouldMerge) {
                flushCurrent()
                currentUnits += unit
                currentStart = unitStart
                currentEnd = unitEnd
            } else {
                currentUnits += unit
                currentStart = mergedStart
                currentEnd = mergedEnd
            }
        }
        flushCurrent()
        return batches
    }

    private fun unresolvedUnits(
        units: List<ReadAloudRoleUnit>,
        resolutions: Map<String, UnitResolution>
    ): List<ReadAloudRoleUnit> {
        return units.filter { unit ->
            val resolution = resolutions[unit.id] ?: return@filter true
            isUnresolvedResolution(resolution)
        }
    }

    private fun isUnresolvedResolution(resolution: UnitResolution): Boolean {
        if (resolution.status == "unknown-final") return false
        if (resolution.status == "unknown") return true
        if (resolution.roleType !in setOf("character", "thought")) return false
        if (resolution.characterName.isBlank()) return true
        return resolution.confidence < 0.62
    }

    private fun unknownFinalResolution(unit: ReadAloudRoleUnit): UnitResolution {
        return UnitResolution(
            unitId = unit.id,
            roleType = unit.roleType.takeIf { it in setOf("character", "thought") } ?: "character",
            characterName = "",
            characterId = 0L,
            emotionName = unit.emotionName,
            emotionTag = unit.emotionTag,
            confidence = unit.confidence.coerceAtLeast(0.45),
            status = "unknown-final",
            evidence = "retry_exhausted"
        )
    }

    private fun normalizeUnitResolution(resolution: UnitResolution): UnitResolution {
        val status = resolution.status.trim().lowercase().ifBlank { "assigned" }
            .takeIf { it in setOf("assigned", "unknown", "unknown-final") }
            ?: "assigned"
        val roleType = resolution.roleType.takeIf { it in setOf("narrator", "character", "thought", "other") }
            ?: "other"
        val rawName = resolution.characterName.trim()
        val characterName = when {
            status.startsWith("unknown") -> ""
            rawName in setOf("unknown", "未知", "不确定", "未识别", "旁白") && roleType != "narrator" -> ""
            roleType == "narrator" -> "旁白"
            else -> rawName
        }
        return resolution.copy(
            roleType = roleType,
            characterName = characterName.take(80),
            status = status,
            confidence = resolution.confidence.coerceIn(0.0, 1.0),
            evidence = resolution.evidence.take(200)
        )
    }

    private fun missingNewCharacterCandidates(
        book: Book,
        units: List<ReadAloudRoleUnit>,
        resolutions: List<UnitResolution>,
        candidates: List<CharacterCandidate>
    ): List<CharacterCandidate> {
        if (!AppConfig.aiReadAloudAutoCreateCharacters || resolutions.isEmpty()) return emptyList()
        val characterBookKey = book.characterBookKey()
        val existingNames = appDb.bookCharacterDao.characters(characterBookKey).map { it.name }.toSet()
        val candidateNames = candidates.map { it.name }.toSet()
        val unitById = units.associateBy { it.id }
        val missing = resolutions
            .asSequence()
            .filter { it.status == "assigned" }
            .filter { it.roleType == "character" || it.roleType == "thought" }
            .filter { it.characterId <= 0L }
            .filter { isCreatableCharacterName(it.characterName) }
            .filterNot { it.characterName in existingNames || it.characterName in candidateNames }
            .groupBy { it.characterName }
        if (missing.isEmpty()) return emptyList()
        val names = missing.keys.sorted()
        AppLog.putDebug(
            "多角色工具未同步返回 newCharacters，已本地兜底候选：${names.joinToString("、")}"
        )
        return missing.mapNotNull { (name, roleResolutions) ->
            val best = roleResolutions.maxByOrNull { it.confidence } ?: return@mapNotNull null
            val evidence = best.evidence
                .ifBlank { unitById[best.unitId]?.text.orEmpty() }
                .trim()
                .take(200)
            if (evidence.isBlank()) return@mapNotNull null
            CharacterCandidate(
                name = name,
                identity = "",
                gender = BookCharacter.inferGender("$name $evidence"),
                age = "",
                appearance = "",
                roleLevel = BookCharacter.ROLE_NORMAL,
                confidence = best.confidence.coerceAtLeast(MIN_AUTO_CREATE_CHARACTER_CONFIDENCE),
                evidence = evidence
            )
        }
    }

    private fun buildBatchUnitPrompt(
        book: Book,
        textChapter: TextChapter,
        paragraphs: List<String>,
        allUnits: List<ReadAloudRoleUnit>,
        batch: UnitAssignmentBatch,
        fullChapterMode: Boolean,
        prompt: String,
        attempt: Int,
        knownResolutions: List<UnitResolution>
    ): String {
        val characters = appDb.bookCharacterDao.characters(book.characterBookKey())
            .sortedWith(compareBy<BookCharacter> { it.name }.thenBy { it.id })
            .joinToString("\n") {
                val detail = listOf(it.genderLabel(), it.identity, it.personality)
                    .filter { value -> value.isNotBlank() && value != "未知" }
                    .joinToString("/")
                "${it.id}|${it.name}|${detail.ifBlank { "-" }}"
            }
            .ifBlank { "无" }
        val targetParagraphSet = batch.units
            .flatMap { unit -> unit.ranges.map { it.paragraphIndex } }
            .toSet()
        val contextParagraphSet = batch.targetParagraphs.toSet()
        val contextText = if (fullChapterMode) {
            paragraphs.mapIndexed { index, text ->
                "P${index + 1}: $text"
            }.joinToString("\n")
        } else {
            batch.targetParagraphs.joinToString("\n") { index ->
                val mark = if (index in targetParagraphSet) "[TARGET]" else "[CONTEXT]"
                "$mark P${index + 1}: ${paragraphs.getOrNull(index).orEmpty()}"
            }
        }
        val relatedUnits = if (fullChapterMode) {
            batch.units
        } else {
            allUnits
                .asSequence()
                .filter { it.needsAi && it.touches(contextParagraphSet) }
                .sortedWith(compareBy<ReadAloudRoleUnit> { it.firstParagraphIndex }.thenBy { it.firstStart })
                .toList()
        }
        val unitSummary = relatedUnits.joinToString("\n") { unit ->
            "${unit.id}|${unit.roleType}|${unitParagraphLabel(unit)}|${unit.reason}|前=${unit.cueBefore.compactForPrompt(50)}|后=${unit.cueAfter.compactForPrompt(50)}|${unit.text.compactForPrompt(220)}"
        }.ifBlank { "无" }
        val targetUnits = batch.units.joinToString("\n") { unit ->
            "${unit.id}|${unit.roleType}|${unitParagraphLabel(unit)}|${unit.text.compactForPrompt(360)}"
        }
        val resolvedSummary = knownResolutions
            .sortedBy { it.unitId }
            .joinToString("\n") { resolution ->
                "${resolution.unitId}=>${resolution.roleType}/${resolution.characterName.ifBlank { "unknown" }}/${resolution.status}/${"%.2f".format(resolution.confidence)}"
            }
            .ifBlank { "无" }
        val retryInstruction = when (attempt) {
            0 -> "首轮：覆盖全部 targetUnitIds。证据不足返回 status=unknown，characterName 空。"
            else -> "补救：只处理未解决 unit；仍无明确证据就 unknown，不猜。"
        }
        val autoCreatePrompt = AppConfig.aiReadAloudAutoCreateCharacterPrompt
        return """
            任务：小说朗读分角色。客户端已切好 unit；只给 targetUnitIds 归因，不新增 unit，不改原文。
            规则：必须调用 $TOOL_CONFIRM_UNITS；units 只含 targetUnitIds 且逐个覆盖。不要标注未列入 targetUnitIds 的旁白，客户端会自动补齐旁白。roleType=narrator 只用于说明某个候选 unit 实际不是台词/心理活动。证据不足 status=unknown、characterName 空。引号和句末符号跟随同一句台词。强调/称号/书名引用不是发言时归旁白。优先用角色卡 name。不要把代词、称呼对象、动作、语气、副词当角色。情绪不明确留空。如果 units 中的 characterName 不在下方角色卡列表，且不是 unknown/旁白，必须在 newCharacters 同步返回该角色；只在当前原文有证据时新增，未知字段留空。
            角色(id|name|info)：
            $characters

            分角色附加提示：
            ${prompt.ifBlank { "无" }}

            自动建卡提示：
            ${autoCreatePrompt.ifBlank { "无" }}

            书：${book.name} / ${book.author}
            章：${textChapter.chapter.title}

            ${if (fullChapterMode) "整章原文（用于上下文缓存和归因，不可改写）" else "局部上下文（只发送当前请求附近段落，[TARGET] 是目标 unit 所在段落）"}：
            $contextText

            相关unit：
            $unitSummary

            已确认结果：
            $resolvedSummary

            $retryInstruction
            batchIndex=${batch.index}
            targetParagraphs=${batch.targetParagraphs.joinToString { "P${it + 1}" }}
            targetUnitIds=${batch.units.joinToString { it.id }}

            本轮目标 unit：
            $targetUnits
        """.trimIndent()
    }

    private suspend fun requestChunkedSegments(
        book: Book,
        textChapter: TextChapter,
        paragraphs: List<String>,
        contextParagraphs: Int,
        prompt: String,
        stage: String,
        onPreview: (List<AiReadAloudRolePreviewSegment>, Int, String) -> Unit
    ): RequestResult = coroutineScope {
        val semaphore = Semaphore(AppConfig.aiReadAloudRoleThreadCount)
        val results = paragraphs.indices
            .chunked(TARGET_GROUP_SIZE)
            .map { targetIndices ->
                async {
                    semaphore.withPermit {
                        val start = (targetIndices.first() - contextParagraphs).coerceAtLeast(0)
                        val end = (targetIndices.last() + contextParagraphs).coerceAtMost(paragraphs.lastIndex)
                        requestSegments(
                            book = book,
                            textChapter = textChapter,
                            paragraphs = paragraphs.subList(start, end + 1),
                            targetIndices = targetIndices,
                            contextTitle = "分线程上下文模式，当前任务只记录目标段落 ${targetIndices.first() + 1}-${targetIndices.last() + 1}",
                            prompt = prompt,
                            paragraphOffset = start,
                            totalParagraphCount = paragraphs.size,
                            stage = stage,
                            onPreview = onPreview
                        )
                    }
                }
            }
            .awaitAll()
        RequestResult(
            segments = results
                .flatMap { it.segments }
                .distinctBy { "${it.paragraphIndex}:${it.start}:${it.end}:${it.roleType}:${it.characterName}" }
                .sortedWith(compareBy<Segment> { it.paragraphIndex }.thenBy { it.start }.thenBy { it.end }),
            candidates = results
                .flatMap { it.candidates }
                .distinctBy { it.name },
            aiRequired = results.any { it.aiRequired },
            aiSatisfied = results.all { it.aiSatisfied }
        )
    }

    private suspend fun requestSegments(
        book: Book,
        textChapter: TextChapter,
        paragraphs: List<String>,
        targetIndices: List<Int>,
        contextTitle: String,
        prompt: String,
        paragraphOffset: Int = 0,
        totalParagraphCount: Int,
        stage: String,
        onPreview: (List<AiReadAloudRolePreviewSegment>, Int, String) -> Unit
    ): RequestResult {
        val preprocess = ReadAloudRolePreprocessor.process(paragraphs, paragraphOffset)
        val targetSet = targetIndices.toSet()
        val targetUnits = preprocess.units.filter { it.touches(targetSet) }
        val localSegments = segmentsFromUnits(targetUnits, targetSet)
        if (localSegments.isNotEmpty()) {
            onPreview(
                buildPreviewSegments(
                    book.characterBookKey(),
                    localSegments,
                    paragraphs,
                    AiReadAloudRoleState.SOURCE_RULE,
                    paragraphOffset
                ),
                0,
                AiReadAloudRoleState.SOURCE_RULE
            )
        }
        val uncertainUnits = targetUnits.filter { it.needsAi }
        if (uncertainUnits.isEmpty()) {
            return RequestResult(localSegments, aiRequired = false, aiSatisfied = true)
        }
        val collectedCandidates = mutableListOf<CharacterCandidate>()
        val collectedResolutions = mutableListOf<UnitResolution>()
        val requestedUnitIds = uncertainUnits.map { it.id }.toSet()
        val tool = AiResolvedTool(TOOL_CONFIRM_UNITS, confirmUnitsDefinition()) { args ->
            val resolutions = parseUnitResolutions(args, requestedUnitIds)
            val candidates = parseCandidates(args)
            collectedResolutions += resolutions
            collectedCandidates += candidates
            val confirmedSegments = segmentsFromUnits(applyUnitResolutions(targetUnits, collectedResolutions), targetSet)
            onPreview(
                buildPreviewSegments(
                    book.characterBookKey(),
                    confirmedSegments,
                    paragraphs,
                    AiReadAloudRoleState.SOURCE_AI_CONFIRM,
                    paragraphOffset
                ),
                collectedCandidates.distinctBy { it.name }.size,
                AiReadAloudRoleState.SOURCE_AI_CONFIRM
            )
            JSONObject().apply {
                put("ok", true)
                put("recorded", resolutions.size)
                put("newCharacters", candidates.size)
            }.toString()
        }
        val response = AiChatService.chatStream(
            messages = listOf(
                AiChatMessage(
                    role = AiChatMessage.Role.USER,
                    content = buildUnitPrompt(
                        book = book,
                        textChapter = textChapter,
                        paragraphs = paragraphs,
                        targetIndices = targetIndices,
                        contextTitle = contextTitle,
                        prompt = prompt,
                        paragraphOffset = paragraphOffset,
                        totalParagraphCount = totalParagraphCount,
                        units = targetUnits,
                        uncertainUnits = uncertainUnits
                    )
                )
            ),
            onPartial = {},
            includeStructuredBlocks = false,
            useAllTools = false,
            extraTools = listOf(tool),
            modelConfigOverride = AppConfig.aiReadAloudRoleModelConfig,
            fallbackModelConfigOverride = AppConfig.aiReadAloudRoleBackupModelConfig,
            firstResponseTimeoutMillis = AppConfig.aiReadAloudRoleFirstResponseTimeoutMillis
        )
        if (collectedResolutions.isEmpty()) {
            val fallback = parseUnitFallbackResult(response, requestedUnitIds)
            collectedResolutions += fallback.first
            collectedCandidates += fallback.second
        }
        val answeredUnitIds = collectedResolutions.map { it.unitId }.toSet()
        val resolvedSegments = segmentsFromUnits(applyUnitResolutions(targetUnits, collectedResolutions), targetSet)
        if (collectedResolutions.isNotEmpty()) {
            onPreview(
                buildPreviewSegments(
                    book.characterBookKey(),
                    resolvedSegments,
                    paragraphs,
                    AiReadAloudRoleState.SOURCE_AI_CONFIRM,
                    paragraphOffset
                ),
                collectedCandidates.distinctBy { it.name }.size,
                AiReadAloudRoleState.SOURCE_AI_CONFIRM
            )
        }
        return RequestResult(
            segments = resolvedSegments,
            candidates = collectedCandidates,
            aiRequired = true,
            aiSatisfied = requestedUnitIds.all { it in answeredUnitIds }
        )
    }

    private fun buildUnitPrompt(
        book: Book,
        textChapter: TextChapter,
        paragraphs: List<String>,
        targetIndices: List<Int>,
        contextTitle: String,
        prompt: String,
        paragraphOffset: Int,
        totalParagraphCount: Int,
        units: List<ReadAloudRoleUnit>,
        uncertainUnits: List<ReadAloudRoleUnit>
    ): String {
        val characters = appDb.bookCharacterDao.characters(book.characterBookKey())
            .joinToString("\n") {
                "- ${it.name}：${listOf(it.genderLabel(), it.identity, it.skills, it.attributes).filter { value -> value.isNotBlank() && value != "未知" }.joinToString("；")}"
            }
            .ifBlank { "暂无角色卡" }
        val indexed = paragraphs.mapIndexed { index, text ->
            val absolute = paragraphOffset + index
            val mark = if (absolute in targetIndices) "[TARGET]" else "[CONTEXT]"
            "$mark 段落${absolute + 1}: $text"
        }.joinToString("\n")
        val localSummary = units.joinToString("\n") { unit ->
            "- ${unit.id} | ${unit.kind}/${unit.roleType} | ${unitParagraphLabel(unit)} | speakerHint=${unit.speakerHint.ifBlank { "无" }} | confidence=${"%.2f".format(unit.confidence)} | text=${unit.text.compactForPrompt(220)}"
        }.ifBlank { "无" }
        val uncertainSummary = uncertainUnits.joinToString("\n") { unit ->
            "- ${unit.id} | reason=${unit.reason} | ${unitParagraphLabel(unit)} | text=${unit.text.compactForPrompt(360)}"
        }
        return """
            你是小说朗读分角色确认器。客户端已经用本地规则把章节切成稳定 unit，已经确定的 unit 不需要你重复标注；你只需要确认“不确定 unit”的朗读身份、说话人和情绪。

            必须遵守：
            1. 必须调用工具 $TOOL_CONFIRM_UNITS 记录结果。
            2. 只处理“不确定 unit 列表”里的 unitId，不要新造 unitId，不要改写原文。
            3. roleType 只能使用 narrator、character、thought、other。
            4. 引号、句末标点、省略号属于同一句台词时，应跟随台词角色，不要把符号单独标为旁白。
            5. ““卧龙”军师”这类强调、称号、书名、外号引用，不是直接发言时应保持 narrator。
            6. 跨段引号如果是同一句直接发言，应使用同一个说话人；无法判断具体角色时 roleType 可为 character，但 characterName 留空。
            7. 优先使用已有角色卡里的准确名称；稳定新角色或路人称谓可以写入 newCharacters，能明确判断性别时填写 gender=male/female，能判断年纪阶段时填写 ageStage=幼童/少年/青年/中年/老年，能从原文看出外貌服饰时填写 appearance；不能判断留空。
            8. 情绪明确时填写 emotionName 和 emotionTag，例如 高兴 / [高兴]；不明确时留空。
            9. 如果工具不可用，最终只输出 JSON：{"units":[...],"newCharacters":[...]}。

            书籍：${book.name}
            作者：${book.author}
            章节：${textChapter.chapter.title}
            模式：$contextTitle
            全章节段落数：$totalParagraphCount
            目标段落：${targetIndices.joinToString { (it + 1).toString() }}

            已有角色卡：
            $characters

            用户附加提示：
            ${prompt.ifBlank { "无" }}

            本地预处理结果：
            $localSummary

            不确定 unit 列表：
            $uncertainSummary

            段落上下文：
            $indexed
        """.trimIndent()
    }

    private fun unitParagraphLabel(unit: ReadAloudRoleUnit): String {
        return unit.ranges
            .map { "P${it.paragraphIndex + 1}" }
            .distinct()
            .joinToString("+")
    }

    private fun String.compactForPrompt(maxLength: Int): String {
        val value = replace("\n", "\\n").trim()
        return if (value.length <= maxLength) value else value.take(maxLength) + "..."
    }

    private fun parseUnitResolutions(
        args: JSONObject?,
        requestedUnitIds: Set<String>
    ): List<UnitResolution> {
        val array = args?.optJSONArray("units")
            ?: args?.optJSONArray("resolutions")
            ?: args?.optJSONArray("segments")
            ?: return emptyList()
        return parseUnitResolutionArray(array, requestedUnitIds)
    }

    private fun parseUnitFallbackResult(
        response: String,
        requestedUnitIds: Set<String>
    ): Pair<List<UnitResolution>, List<CharacterCandidate>> {
        val jsonText = response.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return emptyList<UnitResolution>() to emptyList()
        val array = root.optJSONArray("units")
            ?: root.optJSONArray("resolutions")
            ?: root.optJSONArray("segments")
        return (array?.let { parseUnitResolutionArray(it, requestedUnitIds) }.orEmpty()) to parseCandidates(root)
    }

    private fun parseUnitResolutionArray(
        array: JSONArray,
        requestedUnitIds: Set<String>
    ): List<UnitResolution> {
        val result = mutableListOf<UnitResolution>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val unitId = item.optString("unitId").trim()
            if (unitId !in requestedUnitIds) continue
            val roleType = item.optString("roleType").trim()
                .takeIf { it in setOf("narrator", "character", "thought", "other") }
                ?: "other"
            result += UnitResolution(
                unitId = unitId,
                roleType = roleType,
                characterName = item.optString("characterName").trim().take(80),
                characterId = item.optLong("characterId", 0L).coerceAtLeast(0L),
                emotionName = item.optString("emotionName").trim().take(40),
                emotionTag = item.optString("emotionTag").trim().take(40),
                confidence = item.optDouble("confidence", 0.72).coerceIn(0.0, 1.0),
                status = item.optString("status", "assigned").trim(),
                evidence = item.optString("evidence").trim().take(200)
            )
        }
        return result.distinctBy { it.unitId }
    }

    private fun applyUnitResolutions(
        units: List<ReadAloudRoleUnit>,
        resolutions: List<UnitResolution>
    ): List<ReadAloudRoleUnit> {
        if (resolutions.isEmpty()) return units
        val byId = resolutions.associateBy { it.unitId }
        return units.map { unit ->
            val resolution = byId[unit.id] ?: return@map unit
            val roleType = resolution.roleType.ifBlank { unit.roleType }
            val characterName = when {
                roleType == "narrator" -> "旁白"
                resolution.characterName.isNotBlank() -> resolution.characterName
                roleType == "character" || roleType == "thought" -> ""
                else -> unit.characterName
            }
            unit.copy(
                roleType = roleType,
                characterName = characterName,
                characterId = resolution.characterId.takeIf { it > 0L } ?: unit.characterId,
                emotionName = resolution.emotionName.ifBlank { unit.emotionName },
                emotionTag = resolution.emotionTag.ifBlank { unit.emotionTag },
                confidence = maxOf(unit.confidence, resolution.confidence),
                needsAi = false,
                reason = "ai_confirmed"
            )
        }
    }

    private fun segmentsFromUnits(
        units: List<ReadAloudRoleUnit>,
        paragraphFilter: Set<Int>? = null
    ): List<Segment> {
        return units.flatMap { unit ->
            unit.ranges
                .filter { range -> paragraphFilter == null || range.paragraphIndex in paragraphFilter }
                .mapNotNull { range ->
                if (range.start >= range.end) return@mapNotNull null
                Segment(
                    paragraphIndex = range.paragraphIndex,
                    start = range.start,
                    end = range.end,
                    roleType = unit.roleType.takeIf { it in setOf("narrator", "character", "thought", "other") } ?: "other",
                    characterName = unit.characterName.take(80),
                    characterId = unit.characterId.coerceAtLeast(0L),
                    emotionName = unit.emotionName.take(40),
                    emotionTag = unit.emotionTag.take(40),
                    confidence = unit.confidence.coerceIn(0.0, 1.0)
                )
            }
        }
    }

    private fun buildPrompt(
        book: Book,
        textChapter: TextChapter,
        paragraphs: List<String>,
        targetIndices: List<Int>,
        contextTitle: String,
        prompt: String,
        paragraphOffset: Int
    ): String {
        val characters = appDb.bookCharacterDao.characters(book.characterBookKey())
            .joinToString("\n") { "- ${it.name}：${listOf(it.genderLabel(), it.identity, it.skills, it.attributes).filter { value -> value.isNotBlank() && value != "未知" }.joinToString("；")}" }
            .ifBlank { "暂无角色卡" }
        val indexed = paragraphs.mapIndexed { index, text ->
            val absolute = paragraphOffset + index
            val mark = if (absolute in targetIndices) "[TARGET]" else "[CONTEXT]"
            "$mark 段落${absolute + 1}: $text"
        }.joinToString("\n")
        return """
            你是小说朗读分角色标注器。请为目标段落逐字逐句标注旁白、角色台词、心理活动或其他需要区分朗读身份的片段。

            默认规则：
            1. 必须调用工具 $TOOL_RECORD_SEGMENTS 记录结果。
            2. 只记录 [TARGET] 段落，不要记录 [CONTEXT] 段落。
            3. paragraphIndex 使用从 0 开始的绝对段落索引。
            4. 片段位置使用 Kotlin 字符串字符下标，左闭右开。
            5. 一个段落可以有多个片段，例如“我说‘你好’”应拆成旁白和角色台词。
            6. roleType 只能使用 narrator、character、thought、other。
            7. characterName 不确定时留空；已有角色请优先使用角色卡中的准确名称。
            8. 情绪明确时可填写 emotionName 和 emotionTag，例如 高兴 / [高兴]；不明确时留空。
            9. 如果发现明确新角色或稳定路人称谓，可在 newCharacters 中记录候选；不要把“我、你、他、众人、旁白”当成新角色。能明确判断性别时填写 gender=male/female，能判断年纪阶段时填写 ageStage=幼童/少年/青年/中年/老年，能从原文看出外貌服饰时填写 appearance；不能判断留空。
            10. 如果工具不可用，最终只输出 JSON：{"segments":[...],"newCharacters":[...]}。
            11. 引号内文本优先判断为台词；角色台词片段必须尽量包含紧贴台词的开闭引号、句末标点和省略号，不要把“”“。”等符号单独拆成 narrator。
            12. “张三道/问/笑道/冷声道”等说话提示要反推 speaker，不要把整段标成旁白。
            13. “张三：你好”这类冒号格式应把冒号后的内容标成张三台词。
            14. 第一人称叙述不要直接当作角色名；只有明确“我说/我问”且角色卡能对应时才填角色名。
            15. 不确定说话人时 roleType 仍可标 character，但 characterName 留空，不要改成 narrator。
            16. 不要返回只包含引号、逗号、句号、感叹号、问号、省略号的独立片段。

            书籍：${book.name}
            作者：${book.author}
            章节：${textChapter.chapter.title}
            模式：$contextTitle
            目标段落：${targetIndices.joinToString { (it + 1).toString() }}
            已有角色卡：
            $characters

            用户附加提示：
            ${prompt.ifBlank { "无" }}

            段落：
            $indexed
        """.trimIndent()
    }

    private fun parseSegments(
        args: JSONObject?,
        targetSet: Set<Int>,
        paragraphOffset: Int,
        paragraphs: List<String>
    ): List<Segment> {
        val array = args?.optJSONArray("segments") ?: return emptyList()
        return parseSegmentsArray(array, targetSet, paragraphOffset, paragraphs)
    }

    private fun parseFallbackResult(
        response: String,
        targetSet: Set<Int>,
        paragraphOffset: Int,
        paragraphs: List<String>
    ): RequestResult {
        val jsonText = response.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val root = runCatching {
            JSONObject(jsonText)
        }.getOrNull() ?: return RequestResult()
        val array = runCatching {
            val root = JSONObject(jsonText)
            root.optJSONArray("segments")
        }.getOrNull()
        return RequestResult(
            segments = array?.let { parseSegmentsArray(it, targetSet, paragraphOffset, paragraphs) }.orEmpty(),
            candidates = parseCandidates(root)
        )
    }

    private fun parseSegmentsArray(
        array: JSONArray,
        targetSet: Set<Int>,
        paragraphOffset: Int,
        paragraphs: List<String>
    ): List<Segment> {
        val result = mutableListOf<Segment>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val paragraphIndex = item.optInt("paragraphIndex", -1)
            if (paragraphIndex !in targetSet) continue
            val localIndex = paragraphIndex - paragraphOffset
            val text = paragraphs.getOrNull(localIndex) ?: continue
            val start = item.optInt("start", -1).coerceAtLeast(0)
            val end = item.optInt("end", -1).coerceAtMost(text.length)
            if (start >= end) continue
            val roleType = item.optString("roleType").trim()
                .takeIf { it in setOf("narrator", "character", "thought", "other") }
                ?: "other"
            val characterName = item.optString("characterName").trim().take(80)
            val confidence = if (item.has("confidence")) {
                item.optDouble("confidence", 0.0)
            } else if (characterName.isNotBlank() && roleType in setOf("character", "thought")) {
                0.76
            } else {
                0.5
            }
            result += Segment(
                paragraphIndex = paragraphIndex,
                start = start,
                end = end,
                roleType = roleType,
                characterName = characterName,
                characterId = item.optLong("characterId", 0L).coerceAtLeast(0L),
                emotionName = item.optString("emotionName").trim().take(40),
                emotionTag = item.optString("emotionTag").trim().take(40),
                confidence = confidence.coerceIn(0.0, 1.0)
            )
        }
        return normalizeSegmentBoundaries(result, paragraphs, paragraphOffset)
    }

    private fun parseCandidates(args: JSONObject?): List<CharacterCandidate> {
        val array = args?.optJSONArray("newCharacters")
            ?: args?.optJSONArray("newCharacterCandidates")
            ?: args?.optJSONArray("characterCandidates")
            ?: args?.optJSONArray("characters")
            ?: return emptyList()
        val result = mutableListOf<CharacterCandidate>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name")
                .ifBlank { item.optString("characterName") }
                .trim()
                .take(80)
            if (name.isBlank()) continue
            val identity = item.optString("identity").trim().take(200)
            val evidence = item.optString("evidence").trim().take(200)
            val appearance = item.optString("appearance")
                .ifBlank { item.optString("visual") }
                .ifBlank { item.optString("description") }
                .trim()
                .take(240)
            val gender = BookCharacter.normalizeGender(item.optString("gender").trim())
                .ifBlank { BookCharacter.inferGender("$name $identity $appearance $evidence") }
            val age = BookCharacterProfileMeta.sanitizeAge(
                item.optString("ageStage").ifBlank { item.optString("age") }.trim()
            )
            result += CharacterCandidate(
                name = name,
                identity = identity,
                gender = gender,
                age = age,
                appearance = appearance,
                roleLevel = item.optInt("roleLevel", BookCharacter.ROLE_NORMAL)
                    .coerceIn(BookCharacter.ROLE_NORMAL, BookCharacter.ROLE_MAIN),
                confidence = item.optDouble("confidence", 0.72).coerceIn(0.0, 1.0),
                evidence = evidence
            )
        }
        return result
    }

    private fun normalizeSegmentBoundaries(
        segments: List<Segment>,
        paragraphs: List<String>,
        paragraphOffset: Int
    ): List<Segment> {
        if (segments.isEmpty()) return emptyList()
        return segments
            .groupBy { it.paragraphIndex }
            .flatMap { (paragraphIndex, rawSegments) ->
                val text = paragraphs.getOrNull(paragraphIndex - paragraphOffset) ?: return@flatMap emptyList()
                normalizeParagraphSegments(text, rawSegments)
            }
            .sortedWith(compareBy<Segment> { it.paragraphIndex }.thenBy { it.start }.thenBy { it.end })
    }

    private fun normalizeParagraphSegments(text: String, rawSegments: List<Segment>): List<Segment> {
        if (text.isBlank()) return emptyList()
        val expanded = rawSegments
            .map { segment ->
                val start = segment.start.coerceIn(0, text.length)
                val end = segment.end.coerceIn(start, text.length)
                if (segment.roleType == "character" || segment.roleType == "thought") {
                    expandDialogueBoundary(text, segment.copy(start = start, end = end))
                } else {
                    segment.copy(start = start, end = end)
                }
            }
            .filter { it.start < it.end }
        val dialogueRanges = expanded
            .filter { it.roleType == "character" || it.roleType == "thought" }
            .sortedWith(compareBy<Segment> { it.start }.thenByDescending { it.end })
        val result = mutableListOf<Segment>()
        expanded.forEach { segment ->
            if (segment.roleType == "character" || segment.roleType == "thought") {
                if (!text.substring(segment.start, segment.end).isPunctuationOnly()) {
                    result += segment
                }
                return@forEach
            }
            subtractRanges(segment.start, segment.end, dialogueRanges).forEach { (start, end) ->
                val part = text.substring(start, end)
                if (!part.isPunctuationOnly()) {
                    result += segment.copy(start = start, end = end)
                }
            }
        }
        return result
            .distinctBy { "${it.paragraphIndex}:${it.start}:${it.end}:${it.roleType}:${it.characterName}" }
            .sortedWith(compareBy<Segment> { it.start }.thenBy { it.end })
    }

    private fun expandDialogueBoundary(text: String, segment: Segment): Segment {
        var start = segment.start
        var end = segment.end
        while (start > 0 && text[start - 1] in openingQuoteChars) {
            start--
        }
        while (end < text.length && text[end] in closingDialogueChars) {
            end++
        }
        return segment.copy(start = start, end = end)
    }

    private fun subtractRanges(
        start: Int,
        end: Int,
        blockers: List<Segment>
    ): List<Pair<Int, Int>> {
        if (start >= end) return emptyList()
        val result = mutableListOf<Pair<Int, Int>>()
        var cursor = start
        blockers.forEach { blocker ->
            if (blocker.end <= cursor || blocker.start >= end) return@forEach
            if (blocker.start > cursor) {
                result += cursor to blocker.start.coerceAtMost(end)
            }
            cursor = cursor.coerceAtLeast(blocker.end.coerceAtMost(end))
        }
        if (cursor < end) {
            result += cursor to end
        }
        return result.filter { it.first < it.second }
    }

    private fun String.isPunctuationOnly(): Boolean {
        val value = trim()
        return value.isNotEmpty() && value.all { it in punctuationOnlyChars }
    }

    private fun persistDetectedCharacters(
        characterBookKey: String,
        sourceBookUrl: String,
        segments: List<Segment>,
        candidates: List<CharacterCandidate>,
        paragraphs: List<String> = emptyList()
    ): Pair<List<Segment>, List<Long>> {
        if (!AppConfig.aiReadAloudAutoCreateCharacters) {
            val characters = appDb.bookCharacterDao.characters(characterBookKey)
            return resolveSegmentCharacters(segments, characters) to emptyList()
        }
        val now = System.currentTimeMillis()
        val httpTtsList = appDb.httpTTSDao.all
        val existing = appDb.bookCharacterDao.characters(characterBookKey).toMutableList()
        val byName = existing.associateBy { it.name }.toMutableMap()
        val createdIds = mutableListOf<Long>()
        var characterChanged = false
        val segmentEvidenceByName = segmentEvidenceByName(segments, paragraphs)
        val candidateMap = buildAutoCreateCandidateMap(
            candidates = candidates,
            segments = segments,
            paragraphs = paragraphs,
            existingNames = byName.keys
        )
        candidateMap.values
            .sortedByDescending { it.confidence }
            .take(20)
            .forEach { candidate ->
                val old = byName[candidate.name]
                if (old == null) {
                    val draft = BookCharacter(
                        bookUrl = characterBookKey,
                        name = candidate.name,
                        identity = candidate.identity,
                        gender = candidate.gender,
                        attributes = BookCharacterProfileMeta.mergeAgeIntoAttributes(candidate.age, ""),
                        appearance = candidate.appearance,
                        biography = candidate.evidence.ifBlank { segmentEvidenceByName[candidate.name].orEmpty() },
                        roleLevel = candidate.roleLevel,
                        sortOrder = (appDb.bookCharacterDao.maxCharacterOrder(characterBookKey) ?: -1) + 1,
                        speechRouteJson = SpeechVoiceAssigner
                            .assignRoute(
                                BookCharacter(
                                    bookUrl = characterBookKey,
                                    name = candidate.name,
                                    identity = candidate.identity,
                                    gender = candidate.gender,
                                    attributes = BookCharacterProfileMeta.mergeAgeIntoAttributes(candidate.age, ""),
                                    appearance = candidate.appearance
                                ),
                                httpTtsList
                            )
                            .toJson(),
                        autoCreated = true,
                        source = "ai_read_aloud",
                        lastDetectedAt = now,
                        createdAt = now,
                        updatedAt = now
                    )
                    val id = appDb.bookCharacterDao.insertCharacter(draft)
                    val saved = draft.copy(id = id)
                    byName[saved.name] = saved
                    existing += saved
                    createdIds += id
                    characterChanged = true
                } else if (old.speechRouteJson.isBlank()) {
                    val characterForRoute = old.copy(
                        gender = old.gender.ifBlank { candidate.gender },
                        identity = old.identity.ifBlank { candidate.identity },
                        appearance = old.appearance.ifBlank { candidate.appearance },
                        attributes = if (BookCharacterProfileMeta.ageOf(old).isBlank()) {
                            BookCharacterProfileMeta.mergeAgeIntoAttributes(candidate.age, old.attributes)
                        } else {
                            old.attributes
                        }
                    )
                    val route = SpeechVoiceAssigner.assignRoute(characterForRoute, httpTtsList)
                    if (route.isConfigured) {
                        val updated = characterForRoute.copy(
                            gender = old.gender.ifBlank { candidate.gender },
                            identity = old.identity.ifBlank { candidate.identity },
                            appearance = old.appearance.ifBlank { candidate.appearance },
                            speechRouteJson = route.toJson(),
                            lastDetectedAt = now,
                            updatedAt = now
                        )
                        appDb.bookCharacterDao.updateCharacter(updated)
                        byName[updated.name] = updated
                        characterChanged = true
                    }
                } else {
                    val updated = old.copy(
                        gender = old.gender.ifBlank { candidate.gender },
                        identity = old.identity.ifBlank { candidate.identity },
                        appearance = old.appearance.ifBlank { candidate.appearance },
                        attributes = if (BookCharacterProfileMeta.ageOf(old).isBlank()) {
                            BookCharacterProfileMeta.mergeAgeIntoAttributes(candidate.age, old.attributes)
                        } else {
                            old.attributes
                        },
                        lastDetectedAt = now,
                        updatedAt = now
                    )
                    if (updated != old) {
                        appDb.bookCharacterDao.updateCharacter(updated)
                        byName[updated.name] = updated
                        characterChanged = true
                    }
                }
            }
        segments.asSequence()
            .filter { it.roleType == "character" || it.roleType == "thought" }
            .mapNotNull { byName[it.characterName] }
            .filter { it.speechRouteJson.isBlank() }
            .distinctBy { it.id }
            .forEach { old ->
                val route = SpeechVoiceAssigner.assignRoute(old, httpTtsList)
                if (route.isConfigured) {
                    val updated = old.copy(
                        speechRouteJson = route.toJson(),
                        lastDetectedAt = now,
                        updatedAt = now
                    )
                    appDb.bookCharacterDao.updateCharacter(updated)
                    byName[updated.name] = updated
                    characterChanged = true
                }
            }
        if (characterChanged) {
            ReadAloudConfigChangeNotifier.notifySpeech()
        }
        queueAutoCharacterAvatars(characterBookKey, sourceBookUrl, createdIds)
        return resolveSegmentCharacters(segments, byName.values.toList()) to createdIds
    }

    private fun buildAutoCreateCandidateMap(
        candidates: List<CharacterCandidate>,
        segments: List<Segment>,
        paragraphs: List<String>,
        existingNames: Set<String>
    ): MutableMap<String, CharacterCandidate> {
        val evidenceByName = segmentEvidenceByName(segments, paragraphs)
        val result = linkedMapOf<String, CharacterCandidate>()
        candidates
            .filter { candidate ->
                candidate.confidence >= MIN_AUTO_CREATE_CHARACTER_CONFIDENCE &&
                        isCreatableCharacterName(candidate.name)
            }
            .forEach { candidate ->
                val evidence = candidate.evidence.ifBlank { evidenceByName[candidate.name].orEmpty() }
                if (candidate.name in existingNames || evidence.isNotBlank()) {
                    result[candidate.name] = candidate.copy(evidence = evidence)
                }
            }
        segments.asSequence()
            .filter { it.roleType == "character" || it.roleType == "thought" }
            .filter { it.characterId <= 0L }
            .filter { it.confidence >= MIN_AUTO_CREATE_CHARACTER_CONFIDENCE }
            .filter { isCreatableCharacterName(it.characterName) }
            .filterNot { it.characterName in existingNames || it.characterName in result }
            .groupBy { it.characterName }
            .forEach { (name, roleSegments) ->
                val best = roleSegments.maxByOrNull { it.confidence } ?: return@forEach
                val evidence = evidenceByName[name].orEmpty()
                if (evidence.isBlank()) return@forEach
                result[name] = CharacterCandidate(
                    name = name,
                    identity = "",
                    gender = BookCharacter.inferGender("$name $evidence"),
                    age = "",
                    appearance = "",
                    roleLevel = BookCharacter.ROLE_NORMAL,
                    confidence = best.confidence.coerceAtLeast(MIN_AUTO_CREATE_CHARACTER_CONFIDENCE),
                    evidence = evidence
                )
            }
        return result
    }

    private fun segmentEvidenceByName(
        segments: List<Segment>,
        paragraphs: List<String>
    ): Map<String, String> {
        if (paragraphs.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        segments
            .filter { it.roleType == "character" || it.roleType == "thought" }
            .filter { it.characterName.isNotBlank() }
            .sortedWith(compareBy<Segment> { it.paragraphIndex }.thenBy { it.start })
            .forEach { segment ->
                if (segment.characterName in result) return@forEach
                val paragraph = paragraphs.getOrNull(segment.paragraphIndex) ?: return@forEach
                val start = segment.start.coerceIn(0, paragraph.length)
                val end = segment.end.coerceIn(start, paragraph.length)
                val evidence = paragraph.substring(start, end).trim().take(200)
                if (evidence.isNotBlank()) {
                    result[segment.characterName] = evidence
                }
            }
        return result
    }

    private fun repairCachedCharactersIfNeeded(
        cache: AiReadAloudRoleCache,
        characterBookKey: String,
        sourceBookUrl: String,
        paragraphs: List<String>
    ): CacheRepairResult {
        val segments = segmentsFromJson(cache.segmentsJson)
        if (segments.isEmpty()) return CacheRepairResult(segments)
        val repaired = persistDetectedCharacters(
            characterBookKey = characterBookKey,
            sourceBookUrl = sourceBookUrl,
            segments = segments,
            candidates = emptyList(),
            paragraphs = paragraphs
        )
        if (repaired.first != segments || repaired.second.isNotEmpty()) {
            appDb.aiReadAloudRoleCacheDao.upsert(
                cache.copy(
                    segmentsJson = replaceSegmentsInCacheJson(cache.segmentsJson, repaired.first),
                    createdCharacterIdsJson = mergeCreatedCharacterIds(
                        cache.createdCharacterIdsJson,
                        repaired.second
                    ),
                    characterHash = characterHash(characterBookKey),
                    voiceHash = voiceHash(characterBookKey),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return CacheRepairResult(repaired.first, repaired.second)
    }

    private fun replaceSegmentsInCacheJson(
        oldJson: String,
        segments: List<Segment>
    ): String {
        val text = oldJson.trim()
        return if (text.startsWith("{")) {
            runCatching {
                JSONObject(text).apply {
                    put("segments", segments.toJsonArray())
                }.toString()
            }.getOrElse {
                segments.toJsonArray().toString()
            }
        } else {
            segments.toJsonArray().toString()
        }
    }

    private fun mergeCreatedCharacterIds(
        oldJson: String,
        newIds: List<Long>
    ): String {
        if (newIds.isEmpty()) return oldJson
        val ids = linkedSetOf<Long>()
        runCatching { JSONArray(oldJson) }.getOrNull()?.let { array ->
            for (index in 0 until array.length()) {
                array.optLong(index, 0L).takeIf { it > 0L }?.let(ids::add)
            }
        }
        ids += newIds.filter { it > 0L }
        return JSONArray(ids.toList()).toString()
    }

    private fun queueAutoCharacterAvatars(
        characterBookKey: String,
        sourceBookUrl: String,
        characterIds: List<Long>
    ) {
        if (!AppConfig.aiReadAloudAutoCreateAvatar) return
        if (AiImageService.currentProviderOrNull() == null) return
        val ids = characterIds.distinct().take(6)
        if (ids.isEmpty()) return
        avatarScope.launch {
            ids.forEach { characterId ->
                runCatching {
                    if (!AppConfig.aiReadAloudAutoCreateAvatar || AiImageService.currentProviderOrNull() == null) {
                        return@forEach
                    }
                    val character = appDb.bookCharacterDao.getCharacter(characterId)
                        ?.takeIf { it.bookUrl == characterBookKey && it.avatar.isBlank() }
                        ?: return@forEach
                    val book = appDb.bookDao.getBook(sourceBookUrl)
                    val image = AiImageService.generateAndStore(
                        prompt = buildAutoCharacterAvatarPrompt(character),
                        metadata = AiImageGalleryManager.ImageMetadata(
                            bookName = book?.name.orEmpty(),
                            bookAuthor = book?.author.orEmpty(),
                            characterId = character.id,
                            characterName = character.displayName(),
                            sourceType = AiImageGalleryManager.SOURCE_TYPE_CHARACTER_AVATAR,
                            sourceText = "auto_created_character"
                        )
                    )
                    AiImageGalleryManager.setFavorite(image.id, true, null)
                    val latest = appDb.bookCharacterDao.getCharacter(characterId)
                        ?.takeIf { it.bookUrl == characterBookKey && it.avatar.isBlank() }
                        ?: return@forEach
                    appDb.bookCharacterDao.updateCharacter(
                        latest.copy(
                            avatar = image.localPath,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }.onFailure {
                    AppLog.put("自动生成角色头像失败\n${it.localizedMessage}", it, false)
                }
            }
        }
    }

    private fun buildAutoCharacterAvatarPrompt(character: BookCharacter): String {
        return buildList {
            add("小说角色头像，单人头像，清晰，适合角色卡，不要文字。")
            add("优先表现可见形象、服饰、气质和年龄感；不要把角色名或证据文字画进图片。")
            add("角色名：${character.displayName()}")
            character.genderLabel().takeIf { it != "未知" }?.let { add("性别：$it") }
            BookCharacterProfileMeta.ageOf(character).takeIf { it.isNotBlank() }?.let { add("年纪：$it") }
            character.identity.takeIf { it.isNotBlank() }?.let { add("身份：$it") }
            character.appearance.takeIf { it.isNotBlank() }?.let { add("形象：$it") }
            character.personality.takeIf { it.isNotBlank() }?.let { add("性格：$it") }
            character.biography.takeIf { it.isNotBlank() }?.let { add("章节证据：$it") }
        }.joinToString("\n")
    }

    private fun resolveSegmentCharacters(
        segments: List<Segment>,
        characters: List<BookCharacter>
    ): List<Segment> {
        val byName = charactersByNormalizedName(characters)
        return segments.map { segment ->
            if (segment.characterId > 0 || segment.characterName.isBlank()) {
                segment
            } else if (!isCreatableCharacterName(segment.characterName) && segment.characterName != "旁白") {
                segment.copy(characterName = "", characterId = 0L)
            } else {
                segment.copy(characterId = byName[characterNameKey(segment.characterName)]?.id ?: 0L)
            }
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

    private fun isCreatableCharacterName(name: String): Boolean {
        val value = name.trim()
        if (value.length !in 2..40) return false
        if (value.any { it == '\n' || it == '\r' || it == '\t' }) return false
        if (value.any { it in "，。！？；,.!?;、（）()《》<>[]【】\"“”‘’：" }) return false
        if (invalidCharacterNameKeywordRegex.containsMatchIn(value)) return false
        return value !in setOf("旁白", "作者", "读者", "我", "你", "他", "她", "它", "我们", "你们", "他们", "她们", "众人", "有人")
    }

    private fun characterHash(bookUrl: String): String {
        return MD5Utils.md5Encode(
            appDb.bookCharacterDao.characters(bookUrl)
                .joinToString("|") { "${it.id}:${it.name}:${it.gender}:${BookCharacterProfileMeta.ageOf(it)}:${it.updatedAt}:${it.speechRouteJson}" }
        )
    }

    private fun voiceHash(bookUrl: String): String {
        val characters = appDb.bookCharacterDao.characters(bookUrl)
            .joinToString("|") { "${it.id}:${it.speechRouteJson}" }
        val engines = appDb.httpTTSDao.all
            .joinToString("|") { "${it.id}:${it.speakersJson}:${it.emotionsJson}" }
        val speakerGroups = appDb.readAloudSpeakerGroupDao.groups()
            .joinToString("|") { "${it.id}:${it.name}:${it.enabled}:${it.updatedAt}" }
        val speakerItems = appDb.readAloudSpeakerGroupDao.items()
            .joinToString("|") {
                "${it.groupId}:${it.engineType}:${it.engineValue}:${it.speakerName}:${it.toneID}:${it.updatedAt}"
            }
        return MD5Utils.md5Encode("$characters\n$engines\n$speakerGroups\n$speakerItems")
    }

    private fun confirmUnitsDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_CONFIRM_UNITS)
                put(
                    "description",
                    "批量确认朗读unit的角色、说话人和情绪。如果 units 里填写了不在已知角色卡列表中的 characterName，必须在 newCharacters 中同步返回该角色。"
                )
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("units", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("unitId", stringProp("prompt unit id"))
                                    put("roleType", stringProp("narrator/character/thought/other"))
                                    put("characterName", stringProp("known character card name, new character name that is also listed in newCharacters, or blank if unknown"))
                                    put("characterId", intProp("known id or 0"))
                                    put("emotionName", stringProp("optional"))
                                    put("emotionTag", stringProp("optional"))
                                    put("confidence", numberProp("0..1"))
                                    put("status", stringProp("assigned/unknown"))
                                    put("evidence", stringProp("short evidence"))
                                })
                                put("required", JSONArray().put("unitId").put("roleType").put("status"))
                                put("additionalProperties", false)
                            })
                        })
                        put("newCharacters", JSONObject().apply {
                            put("type", "array")
                            put("description", "Required for every assigned characterName that is not already in the injected character card list.")
                            put("items", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("name", stringProp("new character name"))
                                    put("identity", stringProp("short identity"))
                                    put("gender", stringProp("male/female, blank if unknown"))
                                    put("ageStage", stringProp("life stage such as 少年/青年/中年/老年, blank if unknown"))
                                    put("appearance", stringProp("short visual appearance useful for avatar generation, blank if unknown"))
                                    put("roleLevel", intProp("0 normal, 1 important, 2 main"))
                                    put("confidence", numberProp("0..1"))
                                    put("evidence", stringProp("current chapter evidence"))
                                })
                                put("required", JSONArray().put("name"))
                                put("additionalProperties", false)
                            })
                        })
                    })
                    put("required", JSONArray().put("units"))
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun recordSegmentsDefinition(): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_RECORD_SEGMENTS)
                put("description", "记录朗读分角色片段标注。")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("segments", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("paragraphIndex", intProp("0-based absolute paragraph index."))
                                    put("start", intProp("Inclusive start offset in the paragraph."))
                                    put("end", intProp("Exclusive end offset in the paragraph."))
                                    put("roleType", stringProp("narrator, character, thought, or other."))
                                    put("characterName", stringProp("Character name, blank if unknown."))
                                    put("characterId", intProp("Known character id if available, otherwise 0."))
                                    put("emotionName", stringProp("Optional emotion name, blank if unknown."))
                                    put("emotionTag", stringProp("Optional emotion tag, e.g. [高兴]."))
                                    put("confidence", numberProp("Confidence from 0 to 1."))
                                })
                                put("required", JSONArray().put("paragraphIndex").put("start").put("end").put("roleType"))
                                put("additionalProperties", false)
                            })
                        })
                        put("newCharacters", JSONObject().apply {
                            put("type", "array")
                            put("items", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("name", stringProp("New character name."))
                                    put("identity", stringProp("Short identity or role hint."))
                                    put("gender", stringProp("male/female, blank if unknown."))
                                    put("ageStage", stringProp("Life stage such as 少年/青年/中年/老年, blank if unknown."))
                                    put("appearance", stringProp("Short visual appearance useful for avatar generation, blank if unknown."))
                                    put("roleLevel", intProp("0 normal, 1 important, 2 main."))
                                    put("confidence", numberProp("Confidence from 0 to 1."))
                                    put("evidence", stringProp("Short evidence from current chapter."))
                                })
                                put("required", JSONArray().put("name"))
                                put("additionalProperties", false)
                            })
                        })
                    })
                    put("required", JSONArray().put("segments"))
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun List<Segment>.toJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { segment ->
                array.put(JSONObject().apply {
                    put("paragraphIndex", segment.paragraphIndex)
                    put("start", segment.start)
                    put("end", segment.end)
                    put("roleType", segment.roleType)
                    put("characterName", segment.characterName)
                    put("characterId", segment.characterId)
                    put("emotionName", segment.emotionName)
                    put("emotionTag", segment.emotionTag)
                    put("confidence", segment.confidence)
                })
            }
        }
    }

    private fun List<Segment>.toCacheJson(
        preprocessVersion: String,
        contentHash: String,
        usageSnapshot: RoleUsageSnapshot = RoleUsageSnapshot()
    ): String {
        return JSONObject().apply {
            put("schemaVersion", 4)
            put("preprocessVersion", preprocessVersion)
            put("contentHash", contentHash)
            put("usage", JSONObject().apply {
                put("elapsedMillis", usageSnapshot.elapsedMillis)
                put("requestCount", usageSnapshot.requestCount)
                put("inputTokens", usageSnapshot.usage.inputTokens)
                put("cachedInputTokens", usageSnapshot.usage.cachedInputTokens)
                put("outputTokens", usageSnapshot.usage.outputTokens)
                put("totalTokens", usageSnapshot.usage.totalTokens)
            })
            put("segments", toJsonArray())
        }.toString()
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

    private val openingQuoteChars = setOf('“', '‘', '"', '\'', '「', '『', '（', '(', '【', '[', '《')
    private val closingDialogueChars = setOf(
        '”', '’', '"', '\'', '」', '』',
        '，', ',', '。', '.', '！', '!', '？', '?',
        '；', ';', '：', ':', '、', '…'
    )
    private val punctuationOnlyChars = openingQuoteChars + closingDialogueChars + setOf(
        '）', ')', '】', ']', '》', '—', '-', ' '
    )
}
