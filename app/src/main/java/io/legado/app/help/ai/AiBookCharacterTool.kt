package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.AiGeneratedImage
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterRelation
import io.legado.app.data.entities.ReadAloudSpeakerGroup
import io.legado.app.data.entities.ReadAloudSpeakerGroupItem
import io.legado.app.help.ai.AiImageGalleryManager.GalleryFilter
import io.legado.app.help.book.characterBookKey
import io.legado.app.help.character.BookCharacterIdentityMigrator
import io.legado.app.help.character.BookCharacterProfileMeta
import io.legado.app.help.readaloud.ReadAloudConfigChangeNotifier
import io.legado.app.help.readaloud.speech.SpeechRoute
import io.legado.app.help.readaloud.speech.SpeechRouteSanitizer
import io.legado.app.help.readaloud.speech.SpeechVoiceAssigner
import io.legado.app.help.readaloud.speech.SpeechVoiceCatalogParser
import io.legado.app.help.readaloud.speech.SpeechVoiceGroupRepository
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AiBookCharacterTool {

    private const val TOOL_LIST_CHARACTERS = "list_book_characters"
    private const val TOOL_UPSERT_CHARACTER = "upsert_book_character"
    private const val TOOL_DELETE_CHARACTER = "delete_book_character"
    private const val TOOL_LIST_RELATIONS = "list_book_character_relations"
    private const val TOOL_UPSERT_RELATION = "upsert_book_character_relation"
    private const val TOOL_DELETE_RELATION = "delete_book_character_relation"
    private const val TOOL_LIST_GALLERY_IMAGES = "list_ai_gallery_images"
    private const val TOOL_SET_CHARACTER_AVATAR_FROM_GALLERY = "set_book_character_avatar_from_gallery"
    private const val TOOL_GENERATE_CHARACTER_AVATAR = "generate_book_character_avatar"
    private const val TOOL_LIST_SPEECH_CATALOGS = "list_speech_catalogs"
    private const val TOOL_LIST_SPEECH_VOICE_GROUPS = "list_speech_voice_groups"
    private const val TOOL_UPSERT_SPEECH_VOICE_GROUP = "upsert_speech_voice_group"
    private const val TOOL_DELETE_SPEECH_VOICE_GROUP = "delete_speech_voice_group"
    private const val TOOL_BATCH_MANAGE_SPEECH_VOICE_GROUPS = "batch_manage_speech_voice_groups"
    private const val TOOL_ASSIGN_CHARACTER_SPEECH_ROUTE = "assign_character_speech_route"
    private const val TOOL_BATCH_ASSIGN_CHARACTER_SPEECH_ROUTES = "batch_assign_character_speech_routes"
    private const val TOOL_CLEAR_CHARACTER_SPEECH_ROUTES = "clear_character_speech_routes"

    private fun characterKey(book: Book): String {
        return BookCharacterIdentityMigrator.migrate(book).ifBlank { book.characterBookKey() }
    }

    fun resolvedTools(): List<AiResolvedTool> {
        return listOf(
            AiResolvedTool(TOOL_LIST_CHARACTERS, listCharactersDefinition()) { args -> listCharacters(args) },
            AiResolvedTool(TOOL_UPSERT_CHARACTER, upsertCharacterDefinition()) { args -> upsertCharacter(args) },
            AiResolvedTool(TOOL_DELETE_CHARACTER, deleteCharacterDefinition()) { args -> deleteCharacter(args) },
            AiResolvedTool(TOOL_LIST_RELATIONS, listRelationsDefinition()) { args -> listRelations(args) },
            AiResolvedTool(TOOL_UPSERT_RELATION, upsertRelationDefinition()) { args -> upsertRelation(args) },
            AiResolvedTool(TOOL_DELETE_RELATION, deleteRelationDefinition()) { args -> deleteRelation(args) },
            AiResolvedTool(TOOL_LIST_GALLERY_IMAGES, listGalleryImagesDefinition()) { args -> listGalleryImages(args) },
            AiResolvedTool(TOOL_SET_CHARACTER_AVATAR_FROM_GALLERY, setCharacterAvatarFromGalleryDefinition()) { args ->
                setCharacterAvatarFromGallery(args)
            },
            AiResolvedTool(TOOL_GENERATE_CHARACTER_AVATAR, generateCharacterAvatarDefinition()) { args ->
                generateCharacterAvatar(args)
            },
            AiResolvedTool(TOOL_LIST_SPEECH_CATALOGS, listSpeechCatalogsDefinition()) { args -> listSpeechCatalogs(args) },
            AiResolvedTool(TOOL_LIST_SPEECH_VOICE_GROUPS, listSpeechVoiceGroupsDefinition()) { args ->
                listSpeechVoiceGroups(args)
            },
            AiResolvedTool(TOOL_UPSERT_SPEECH_VOICE_GROUP, upsertSpeechVoiceGroupDefinition()) { args ->
                upsertSpeechVoiceGroup(args)
            },
            AiResolvedTool(TOOL_DELETE_SPEECH_VOICE_GROUP, deleteSpeechVoiceGroupDefinition()) { args ->
                deleteSpeechVoiceGroup(args)
            },
            AiResolvedTool(TOOL_BATCH_MANAGE_SPEECH_VOICE_GROUPS, batchManageSpeechVoiceGroupsDefinition()) { args ->
                batchManageSpeechVoiceGroups(args)
            },
            AiResolvedTool(TOOL_ASSIGN_CHARACTER_SPEECH_ROUTE, assignCharacterSpeechRouteDefinition()) { args ->
                assignCharacterSpeechRoute(args)
            },
            AiResolvedTool(TOOL_BATCH_ASSIGN_CHARACTER_SPEECH_ROUTES, batchAssignCharacterSpeechRoutesDefinition()) { args ->
                batchAssignCharacterSpeechRoutes(args)
            },
            AiResolvedTool(TOOL_CLEAR_CHARACTER_SPEECH_ROUTES, clearCharacterSpeechRoutesDefinition()) { args ->
                clearCharacterSpeechRoutes(args)
            },
        )
    }

    private fun listCharactersDefinition() = function(
        TOOL_LIST_CHARACTERS,
        "读取指定书籍的角色资料列表，返回角色头像 avatar 和 avatarImage。用户要求查看或展示已有头像时使用本工具，不要生成新头像。优先传 bookUrl；没有 bookUrl 时可传 bookName 和 author。"
    ) {
        bookProps(this)
    }

    private fun upsertCharacterDefinition() = function(
        TOOL_UPSERT_CHARACTER,
        "新增或更新指定书籍的角色资料。更新时优先用 characterId，其次用同名角色。"
    ) {
        bookProps(this)
        put("characterId", intProp("可选，角色 ID。"))
        put("name", stringProp("角色名称，必填。"))
        put("avatar", stringProp("角色头像 URL 或本地路径。"))
        put("gender", stringProp("角色性别：male 男，female 女，unknown/空 未知。"))
        put("ageStage", stringProp("角色年纪，例如 少年、青年、中年、老年；不确定时留空，不要填精确年龄。"))
        put("identity", stringProp("角色身份。"))
        put("skills", stringProp("角色技能。"))
        put("attributes", stringProp("角色属性。"))
        put("appearance", stringProp("角色形象描述。"))
        put("personality", stringProp("角色性格描述。"))
        put("biography", stringProp("角色生平。"))
        put("roleLevel", intProp("角色重要度：0 普通角色，1 重要角色，2 主角。"))
        put("speechRouteJson", stringProp("可选，角色配音路由 JSON。未明确要求配音时不要传。"))
        put("autoCreated", booleanProp("可选，是否为 AI 自动创建角色。"))
        put("source", stringProp("可选，角色来源，例如 ai_read_aloud。"))
    }

    private fun deleteCharacterDefinition() = function(
        TOOL_DELETE_CHARACTER,
        "删除指定书籍的角色资料，并同步删除该角色相关关系。"
    ) {
        bookProps(this)
        put("characterId", intProp("可选，角色 ID。"))
        put("name", stringProp("可选，角色名称。"))
    }

    private fun listRelationsDefinition() = function(
        TOOL_LIST_RELATIONS,
        "读取指定书籍的角色关系网。"
    ) {
        bookProps(this)
    }

    private fun upsertRelationDefinition() = function(
        TOOL_UPSERT_RELATION,
        "新增或更新两个角色之间的关系。可用角色 ID 或角色名称定位。"
    ) {
        bookProps(this)
        put("relationId", intProp("可选，关系 ID。"))
        put("fromCharacterId", intProp("角色 A 的 ID。"))
        put("toCharacterId", intProp("角色 B 的 ID。"))
        put("fromName", stringProp("角色 A 名称。"))
        put("toName", stringProp("角色 B 名称。"))
        put("relationName", stringProp("关系名称，例如 师徒、同伴、敌对。"))
        put("relationType", stringProp("关系属性，例如 亲密、敌对、利益、血缘。"))
        put("description", stringProp("关系说明。"))
        put("strength", intProp("关系强度，0 到 100。"))
    }

    private fun deleteRelationDefinition() = function(
        TOOL_DELETE_RELATION,
        "删除指定书籍中的一条角色关系。"
    ) {
        bookProps(this)
        put("relationId", intProp("关系 ID。"))
    }

    private fun listGalleryImagesDefinition() = function(
        TOOL_LIST_GALLERY_IMAGES,
        "读取 AI 图片库图片，可用于给角色选择头像。"
    ) {
        put("keyword", stringProp("可选，按图片名称、提示词、供应商或模型筛选。"))
        put("favoriteOnly", booleanProp("可选，只读取已收藏图片。"))
        put("limit", intProp("可选，返回数量上限，默认 20，最大 50。"))
    }

    private fun setCharacterAvatarFromGalleryDefinition() = function(
        TOOL_SET_CHARACTER_AVATAR_FROM_GALLERY,
        "把 AI 图片库中的图片设为指定角色头像，并自动收藏该图片。"
    ) {
        bookProps(this)
        put("characterId", intProp("可选，角色 ID。"))
        put("name", stringProp("可选，角色名称。"))
        put("imageId", stringProp("AI 图片库图片 ID。"))
    }

    private fun generateCharacterAvatarDefinition() = function(
        TOOL_GENERATE_CHARACTER_AVATAR,
        "根据指定角色资料生成全新头像，自动保存到 AI 图片库、收藏，并设为角色头像。只有用户明确要求生成、重绘、重新生成或换头像时才调用；用户只是查看已有头像时不要调用。"
    ) {
        bookProps(this)
        put("characterId", intProp("可选，角色 ID。"))
        put("name", stringProp("可选，角色名称。"))
        put("prompt", stringProp("可选，头像生成提示词；为空时会根据角色资料自动生成。"))
        put("providerId", stringProp("可选，指定生图提供商 ID；只有用户明确选择某个生图模型时才传入，否则留空。"))
    }

    private fun listSpeechCatalogsDefinition() = function(
        TOOL_LIST_SPEECH_CATALOGS,
        "读取所有 HTTP TTS 的发言人和情绪目录，以及用户维护的发言人分组，供角色配音或多角色朗读分配使用。"
    ) {
        put("includeEmpty", booleanProp("可选，是否返回没有发言人目录的引擎。默认 false。"))
    }

    private fun listSpeechVoiceGroupsDefinition() = function(
        TOOL_LIST_SPEECH_VOICE_GROUPS,
        "读取用户维护的发言人分组。多角色自动分配会优先从这些分组里选择发言人。"
    ) {
        put("includeDisabled", booleanProp("可选，是否包含已停用分组。默认 true。"))
    }

    private fun upsertSpeechVoiceGroupDefinition() = function(
        TOOL_UPSERT_SPEECH_VOICE_GROUP,
        "新增或更新一个发言人分组，并可批量替换组内发言人。itemsJson 是发言人数组 JSON，每项包含 engineType、engineValue、engineName、speakerName、toneID、sourceGroupId、sourceGroupName。"
    ) {
        put("groupId", intProp("可选，已有分组 ID。为空时创建新分组。"))
        put("name", stringProp("分组名称。新建时必填。"))
        put("enabled", booleanProp("可选，是否启用。默认 true。"))
        put("replaceItems", booleanProp("可选，是否替换组内所有发言人。默认 true。"))
        put("itemsJson", stringProp("可选，发言人数组 JSON。每个条目会校验当前 TTS 是否存在。"))
    }

    private fun deleteSpeechVoiceGroupDefinition() = function(
        TOOL_DELETE_SPEECH_VOICE_GROUP,
        "删除一个用户维护的发言人分组。只删除分组配置，不删除 TTS 引擎。"
    ) {
        put("groupId", intProp("发言人分组 ID。"))
    }

    private fun batchManageSpeechVoiceGroupsDefinition() = function(
        TOOL_BATCH_MANAGE_SPEECH_VOICE_GROUPS,
        "Batch manage TTS speaker groups. operationsJson is an array; action supports create/update/delete/addItems/replaceItems/merge/enable/disable. items/itemsJson uses the same item structure as upsert_speech_voice_group."
    ) {
        put("operationsJson", stringProp("JSON array. Each item may contain action, groupId, targetGroupId, sourceGroupIds, name, enabled, items/itemsJson."))
    }

    private fun assignCharacterSpeechRouteDefinition() = function(
        TOOL_ASSIGN_CHARACTER_SPEECH_ROUTE,
        "给指定角色设置配音。可以传具体发言人，也可以 autoAssign=true 让本地根据可用发言人稳定分配。"
    ) {
        bookProps(this)
        put("characterId", intProp("可选，角色 ID。"))
        put("name", stringProp("可选，角色名称。"))
        put("autoAssign", booleanProp("可选，自动分配未配置发言人。"))
        put("engineValue", stringProp("可选，朗读引擎值。HTTP TTS 使用引擎 ID 字符串，系统 TTS 使用系统引擎配置字符串。"))
        put("speakerName", stringProp("可选，发言人展示名。"))
        put("toneID", stringProp("可选，发言人的 toneID。"))
        put("emotionName", stringProp("可选，默认情绪名。"))
        put("emotionTag", stringProp("可选，默认情绪标志。"))
        put("overwrite", booleanProp("可选，是否覆盖已有配音。默认 false。"))
    }

    private fun batchAssignCharacterSpeechRoutesDefinition() = function(
        TOOL_BATCH_ASSIGN_CHARACTER_SPEECH_ROUTES,
        "批量给角色分配配音。默认只处理未配置配音的角色，可选择只处理 AI 自动创建角色。"
    ) {
        bookProps(this)
        put("autoCreatedOnly", booleanProp("可选，只处理 AI 自动创建角色。"))
        put("unassignedOnly", booleanProp("可选，只处理未配置配音角色，默认 true。"))
        put("overwrite", booleanProp("可选，是否覆盖已有配音。默认 false。"))
        put("limit", intProp("可选，最多处理数量，默认 200。"))
    }

    private fun clearCharacterSpeechRoutesDefinition() = function(
        TOOL_CLEAR_CHARACTER_SPEECH_ROUTES,
        "批量清空角色配音配置。默认只清空 AI 自动创建角色，避免误删用户手动配置。"
    ) {
        bookProps(this)
        put("autoCreatedOnly", booleanProp("可选，只清空 AI 自动创建角色，默认 true。"))
        put("characterIdsJson", stringProp("可选，角色 ID 数组 JSON。传入时只清空这些角色。"))
    }

    private suspend fun listCharacters(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val bookKey = characterKey(book)
        val characters = appDb.bookCharacterDao.characters(bookKey)
        JSONObject().apply {
            put("ok", true)
            put("book", bookJson(book))
            put("characters", JSONArray().apply {
                characters.forEach { put(characterJson(it)) }
            })
        }.toString()
    }

    private suspend fun upsertCharacter(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val bookKey = characterKey(book)
        val name = args?.optString("name")?.trim().orEmpty()
        if (name.isBlank()) return@withContext errorJson("name 不能为空")
        val now = System.currentTimeMillis()
        val id = args?.optLong("characterId", 0L) ?: 0L
        val old = id.takeIf { it > 0 }?.let { appDb.bookCharacterDao.getCharacter(it) }
            ?.takeIf { it.bookUrl == bookKey }
            ?: appDb.bookCharacterDao.getCharacter(bookKey, name)
        val rawAttributes = optText(args, "attributes") ?: old?.attributes.orEmpty()
        val age = (optText(args, "ageStage") ?: optText(args, "age"))
            ?.let(BookCharacterProfileMeta::sanitizeAge)
            ?: old?.let(BookCharacterProfileMeta::ageOf).orEmpty()
        val speechRouteJson = optText(args, "speechRouteJson") ?: old?.speechRouteJson.orEmpty()
        val character = (old ?: BookCharacter(bookUrl = bookKey)).copy(
            bookUrl = bookKey,
            name = name,
            avatar = optText(args, "avatar") ?: old?.avatar.orEmpty(),
            gender = optText(args, "gender")
                ?.let(BookCharacter::normalizeGender)
                ?: old?.gender.orEmpty(),
            identity = optText(args, "identity") ?: old?.identity.orEmpty(),
            skills = optText(args, "skills") ?: old?.skills.orEmpty(),
            attributes = BookCharacterProfileMeta.mergeAgeIntoAttributes(age, rawAttributes),
            appearance = optText(args, "appearance") ?: old?.appearance.orEmpty(),
            personality = optText(args, "personality") ?: old?.personality.orEmpty(),
            biography = optText(args, "biography") ?: old?.biography.orEmpty(),
            speechRouteJson = speechRouteJson,
            autoCreated = args?.takeIf { it.has("autoCreated") }?.optBoolean("autoCreated")
                ?: old?.autoCreated
                ?: false,
            source = optText(args, "source") ?: old?.source.orEmpty(),
            lastDetectedAt = old?.lastDetectedAt ?: 0L,
            roleLevel = (args?.takeIf { it.has("roleLevel") }?.optInt("roleLevel") ?: old?.roleLevel ?: BookCharacter.ROLE_NORMAL)
                .coerceIn(BookCharacter.ROLE_NORMAL, BookCharacter.ROLE_MAIN),
            sortOrder = old?.sortOrder ?: ((appDb.bookCharacterDao.maxCharacterOrder(bookKey) ?: -1) + 1),
            createdAt = old?.createdAt?.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        val savedId = if (character.id > 0) {
            appDb.bookCharacterDao.updateCharacter(character)
            character.id
        } else {
            appDb.bookCharacterDao.insertCharacter(character)
        }
        if (old?.speechRouteJson.orEmpty() != character.speechRouteJson) {
            ReadAloudConfigChangeNotifier.notifySpeech()
        }
        JSONObject().apply {
            put("ok", true)
            put("character", characterJson(character.copy(id = savedId)))
        }.toString()
    }

    private suspend fun listSpeechCatalogs(args: JSONObject?): String = withContext(IO) {
        val includeEmpty = args?.optBoolean("includeEmpty", true) ?: true
        val engines = appDb.httpTTSDao.all.mapNotNull { httpTts ->
            val speakerGroups = SpeechVoiceCatalogParser.parseSpeakerGroups(httpTts.speakersJson)
            val emotionGroups = SpeechVoiceCatalogParser.parseEmotionGroups(httpTts.emotionsJson)
            if (!includeEmpty && speakerGroups.isEmpty() && emotionGroups.isEmpty()) {
                null
            } else {
                JSONObject().apply {
                    put("id", httpTts.id)
                    put("name", httpTts.name)
                    put("engineType", SpeechRoute.ENGINE_HTTP)
                    put("engineValue", httpTts.id.toString())
                    put("speakerGroups", JSONArray().apply {
                        val groups = speakerGroups.ifEmpty {
                            listOf(
                                io.legado.app.help.readaloud.speech.SpeechCatalogGroup(
                                    groupId = "default",
                                    groupName = "默认",
                                    items = listOf(
                                        io.legado.app.help.readaloud.speech.SpeechSpeaker(
                                            speakerName = httpTts.name.ifBlank { "HTTP TTS" },
                                            toneID = ""
                                        )
                                    )
                                )
                            )
                        }
                        groups.forEach { group ->
                            put(JSONObject().apply {
                                put("groupId", group.groupId)
                                put("groupName", group.groupName)
                                put("items", JSONArray().apply {
                                    group.items.forEach { speaker ->
                                        val route = SpeechRoute(
                                            engineType = SpeechRoute.ENGINE_HTTP,
                                            engineValue = httpTts.id.toString(),
                                            speakerName = speaker.speakerName,
                                            toneID = speaker.toneID,
                                            groupId = group.groupId,
                                            groupName = group.groupName,
                                            source = SpeechRoute.SOURCE_AUTO
                                        )
                                        if (SpeechVoiceGroupRepository.isBlockedRoute(route)) {
                                            return@forEach
                                        }
                                        put(JSONObject().apply {
                                            put("speakerName", speaker.speakerName)
                                            put("toneID", speaker.toneID)
                                            put("tags", JSONArray(speaker.tags))
                                        })
                                    }
                                })
                            })
                        }
                    })
                    put("emotionGroups", JSONArray().apply {
                        emotionGroups.forEach { group ->
                            put(JSONObject().apply {
                                put("groupId", group.groupId)
                                put("groupName", group.groupName)
                                put("items", JSONArray().apply {
                                    group.items.forEach { emotion ->
                                        put(JSONObject().apply {
                                            put("emotionName", emotion.emotionName)
                                            put("emotionTag", emotion.emotionTag)
                                        })
                                    }
                                })
                            })
                        }
                    })
                }
            }
        }
        JSONObject().apply {
            put("ok", true)
            put("engines", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "system_default")
                    put("name", "系统默认")
                    put("engineType", SpeechRoute.ENGINE_SYSTEM)
                    put("engineValue", "")
                    put("speakerGroups", JSONArray().put(JSONObject().apply {
                        put("groupId", "default")
                        put("groupName", "默认")
                        put("items", JSONArray().put(JSONObject().apply {
                            put("speakerName", "系统默认")
                            put("toneID", "")
                            put("tags", JSONArray())
                        }))
                    }))
                    put("emotionGroups", JSONArray())
                })
                engines.forEach { put(it) }
            })
            put("managedGroups", speechVoiceGroupsJson(includeDisabled = true, includeInvalid = false))
        }.toString()
    }

    private suspend fun listSpeechVoiceGroups(args: JSONObject?): String = withContext(IO) {
        val includeDisabled = args?.optBoolean("includeDisabled", true) ?: true
        JSONObject().apply {
            put("ok", true)
            put("groups", speechVoiceGroupsJson(includeDisabled = includeDisabled, includeInvalid = true))
        }.toString()
    }

    private suspend fun upsertSpeechVoiceGroup(args: JSONObject?): String = withContext(IO) {
        val now = System.currentTimeMillis()
        val groupId = args?.optLong("groupId", 0L) ?: 0L
        val old = groupId.takeIf { it > 0L }
            ?.let { id -> appDb.readAloudSpeakerGroupDao.groups().firstOrNull { it.id == id } }
        val name = optText(args, "name") ?: old?.name.orEmpty()
        if (name.isBlank()) return@withContext errorJson("name 不能为空")
        val invalidGroup = SpeechVoiceGroupRepository.isInvalidGroupName(name)
        val group = (old ?: ReadAloudSpeakerGroup()).copy(
            name = name,
            enabled = if (invalidGroup) {
                false
            } else {
                args?.takeIf { it.has("enabled") }?.optBoolean("enabled")
                    ?: old?.enabled
                    ?: true
            },
            sortOrder = old?.sortOrder ?: ((appDb.readAloudSpeakerGroupDao.maxGroupOrder() ?: -1) + 1),
            createdAt = old?.createdAt?.takeIf { it > 0L } ?: now,
            updatedAt = now
        )
        val savedId = if (group.id > 0L) {
            appDb.readAloudSpeakerGroupDao.updateGroup(group)
            group.id
        } else {
            appDb.readAloudSpeakerGroupDao.insertGroup(group)
        }
        val replaceItems = args?.optBoolean("replaceItems", true) ?: true
        if (replaceItems) {
            appDb.readAloudSpeakerGroupDao.deleteItemsByGroup(savedId)
        }
        val startOrder = (appDb.readAloudSpeakerGroupDao.maxItemOrder(savedId) ?: -1) + 1
        val httpTtsList = appDb.httpTTSDao.all
        val items = parseSpeechVoiceGroupItems(args, savedId, startOrder, now)
            .asSequence()
            .filter { SpeechVoiceGroupRepository.isValidItem(it, httpTtsList) }
            .distinctBy { "${it.engineType}|${it.engineValue}|${it.toneID}|${it.speakerName}" }
            .toList()
        if (items.isNotEmpty()) {
            appDb.readAloudSpeakerGroupDao.insertItems(items)
        }
        JSONObject().apply {
            put("ok", true)
            put("group", speechVoiceGroupJson(group.copy(id = savedId), includeItems = true))
            put("insertedItems", items.size)
            put("skippedInvalidItems", parseSpeechVoiceGroupItems(args, savedId, startOrder, now).size - items.size)
        }.toString()
    }

    private suspend fun deleteSpeechVoiceGroup(args: JSONObject?): String = withContext(IO) {
        val groupId = args?.optLong("groupId", 0L) ?: 0L
        if (groupId <= 0L) return@withContext errorJson("groupId 不能为空")
        appDb.readAloudSpeakerGroupDao.deleteItemsByGroup(groupId)
        appDb.readAloudSpeakerGroupDao.deleteGroup(groupId)
        JSONObject().apply {
            put("ok", true)
            put("deletedGroupId", groupId)
        }.toString()
    }

    private suspend fun batchManageSpeechVoiceGroups(args: JSONObject?): String = withContext(IO) {
        val operations = args?.optJSONArray("operations")
            ?: args?.optString("operationsJson")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { JSONArray(it) }.getOrNull() }
            ?: return@withContext errorJson("operationsJson 不能为空")
        val results = JSONArray()
        for (index in 0 until operations.length()) {
            val op = operations.optJSONObject(index) ?: continue
            val action = op.optString("action").trim().lowercase()
            val result = runCatching {
                when (action) {
                    "create", "update", "upsert" -> JSONObject(upsertSpeechVoiceGroup(op))
                    "additems" -> {
                        op.put("replaceItems", false)
                        JSONObject(upsertSpeechVoiceGroup(op))
                    }
                    "replaceitems" -> {
                        op.put("replaceItems", true)
                        JSONObject(upsertSpeechVoiceGroup(op))
                    }
                    "delete" -> JSONObject(deleteSpeechVoiceGroup(op))
                    "enable", "disable" -> {
                        val groupId = op.optLong("groupId", 0L)
                        val group = appDb.readAloudSpeakerGroupDao.groups().firstOrNull { it.id == groupId }
                            ?: return@runCatching errorJsonObject("groupId 不存在")
                        appDb.readAloudSpeakerGroupDao.updateGroup(
                            group.copy(enabled = action == "enable", updatedAt = System.currentTimeMillis())
                        )
                        JSONObject().put("ok", true).put("groupId", groupId).put("enabled", action == "enable")
                    }
                    "merge" -> mergeSpeechVoiceGroups(op)
                    else -> errorJsonObject("unsupported action: $action")
                }
            }.getOrElse { throwable ->
                errorJsonObject(throwable.localizedMessage ?: throwable.javaClass.simpleName)
            }
            results.put(JSONObject().apply {
                put("index", index)
                put("action", action)
                put("result", result)
            })
        }
        JSONObject().apply {
            put("ok", true)
            put("results", results)
            put("groups", speechVoiceGroupsJson(includeDisabled = true, includeInvalid = true))
        }.toString()
    }

    private fun mergeSpeechVoiceGroups(args: JSONObject): JSONObject {
        val targetGroupId = args.optLong("targetGroupId", args.optLong("groupId", 0L))
        if (targetGroupId <= 0L) return errorJsonObject("targetGroupId 不能为空")
        val sourceIds = longArrayFromJson(args.optJSONArray("sourceGroupIds"))
            .ifEmpty {
                args.optString("sourceGroupIdsJson")
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { longArrayFromJson(JSONArray(it)) }.getOrDefault(emptyList()) }
                    .orEmpty()
            }
            .filter { it > 0L && it != targetGroupId }
            .distinct()
        if (sourceIds.isEmpty()) return errorJsonObject("sourceGroupIds 不能为空")
        val now = System.currentTimeMillis()
        val existingKeys = appDb.readAloudSpeakerGroupDao.itemsByGroup(targetGroupId)
            .mapTo(hashSetOf()) { SpeechVoiceGroupRepository.itemKey(it) }
        var sortOrder = (appDb.readAloudSpeakerGroupDao.maxItemOrder(targetGroupId) ?: -1) + 1
        val movingItems = sourceIds
            .flatMap { appDb.readAloudSpeakerGroupDao.itemsByGroup(it) }
            .mapNotNull { item ->
                if (!existingKeys.add(SpeechVoiceGroupRepository.itemKey(item))) null
                else item.copy(id = 0L, groupId = targetGroupId, sortOrder = sortOrder++, updatedAt = now)
            }
        if (movingItems.isNotEmpty()) {
            appDb.readAloudSpeakerGroupDao.insertItems(movingItems)
        }
        sourceIds.forEach { sourceId ->
            appDb.readAloudSpeakerGroupDao.deleteItemsByGroup(sourceId)
            appDb.readAloudSpeakerGroupDao.deleteGroup(sourceId)
        }
        return JSONObject().apply {
            put("ok", true)
            put("targetGroupId", targetGroupId)
            put("mergedGroupIds", JSONArray(sourceIds))
            put("insertedItems", movingItems.size)
        }
    }

    private suspend fun assignCharacterSpeechRoute(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val character = resolveCharacter(characterKey(book), args)
            ?: return@withContext errorJson("未找到角色")
        val overwrite = args?.optBoolean("overwrite", false) ?: false
        if (!overwrite && character.speechRouteJson.isNotBlank()) {
            return@withContext JSONObject().apply {
                put("ok", true)
                put("skipped", true)
                put("reason", "角色已有配音配置")
                put("character", characterJson(character))
            }.toString()
        }
        val route = if (args?.optBoolean("autoAssign", false) == true) {
            SpeechVoiceAssigner.assignRoute(character, appDb.httpTTSDao.all)
        } else {
            routeFromArgs(args, SpeechRoute.SOURCE_MANUAL)
        }
        if (!route.isConfigured) return@withContext errorJson("没有可用发言人或配音参数为空")
        val updated = character.copy(
            speechRouteJson = route.toJson(),
            updatedAt = System.currentTimeMillis()
        )
        appDb.bookCharacterDao.updateCharacter(updated)
        if (updated.speechRouteJson != character.speechRouteJson) {
            ReadAloudConfigChangeNotifier.notifySpeech()
        }
        JSONObject().apply {
            put("ok", true)
            put("character", characterJson(updated))
        }.toString()
    }

    private suspend fun batchAssignCharacterSpeechRoutes(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val bookKey = characterKey(book)
        val autoCreatedOnly = args?.optBoolean("autoCreatedOnly", false) ?: false
        val unassignedOnly = args?.optBoolean("unassignedOnly", true) ?: true
        val overwrite = args?.optBoolean("overwrite", false) ?: false
        val limit = (args?.optInt("limit", 200) ?: 200).coerceIn(1, 500)
        val httpTtsList = appDb.httpTTSDao.all
        val updatedCharacters = mutableListOf<BookCharacter>()
        appDb.bookCharacterDao.characters(bookKey)
            .asSequence()
            .filter { !autoCreatedOnly || it.autoCreated }
            .filter { overwrite || !unassignedOnly || it.speechRouteJson.isBlank() }
            .take(limit)
            .forEach { character ->
                val route = SpeechVoiceAssigner.assignRoute(character, httpTtsList)
                if (route.isConfigured) {
                    val updated = character.copy(
                        speechRouteJson = route.toJson(),
                        updatedAt = System.currentTimeMillis()
                    )
                    appDb.bookCharacterDao.updateCharacter(updated)
                    updatedCharacters += updated
                }
            }
        if (updatedCharacters.isNotEmpty()) {
            ReadAloudConfigChangeNotifier.notifySpeech()
        }
        JSONObject().apply {
            put("ok", true)
            put("updated", updatedCharacters.size)
            put("characters", JSONArray().apply {
                updatedCharacters.forEach { put(characterJson(it)) }
            })
        }.toString()
    }

    private suspend fun clearCharacterSpeechRoutes(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val bookKey = characterKey(book)
        val autoCreatedOnly = args?.optBoolean("autoCreatedOnly", true) ?: true
        val idFilter = parseLongSet(args?.optString("characterIdsJson"))
        val updatedCharacters = mutableListOf<BookCharacter>()
        appDb.bookCharacterDao.characters(bookKey)
            .asSequence()
            .filter { idFilter.isEmpty() || it.id in idFilter }
            .filter { !autoCreatedOnly || it.autoCreated }
            .filter { it.speechRouteJson.isNotBlank() }
            .forEach { character ->
                val updated = character.copy(
                    speechRouteJson = "",
                    updatedAt = System.currentTimeMillis()
                )
                appDb.bookCharacterDao.updateCharacter(updated)
                updatedCharacters += updated
            }
        if (updatedCharacters.isNotEmpty()) {
            ReadAloudConfigChangeNotifier.notifySpeech()
        }
        JSONObject().apply {
            put("ok", true)
            put("updated", updatedCharacters.size)
            put("characters", JSONArray().apply {
                updatedCharacters.forEach { put(characterJson(it)) }
            })
        }.toString()
    }

    private suspend fun deleteCharacter(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val character = resolveCharacter(characterKey(book), args)
            ?: return@withContext errorJson("未找到角色")
        appDb.bookCharacterDao.deleteCharacterWithRelations(character)
        JSONObject().apply {
            put("ok", true)
            put("deletedCharacterId", character.id)
        }.toString()
    }

    private suspend fun listRelations(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val bookKey = characterKey(book)
        val characters = appDb.bookCharacterDao.characters(bookKey)
        val relations = appDb.bookCharacterDao.relations(bookKey)
        JSONObject().apply {
            put("ok", true)
            put("book", bookJson(book))
            put("characters", JSONArray().apply {
                characters.forEach { put(characterJson(it)) }
            })
            put("relations", JSONArray().apply {
                relations.forEach { put(relationJson(it, characters)) }
            })
        }.toString()
    }

    private suspend fun upsertRelation(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val bookKey = characterKey(book)
        val characters = appDb.bookCharacterDao.characters(bookKey)
        val fromId = resolveCharacterId(args, "fromCharacterId", "fromName", characters)
        val toId = resolveCharacterId(args, "toCharacterId", "toName", characters)
        if (fromId == null || toId == null || fromId == toId) {
            return@withContext errorJson("请选择两个不同角色")
        }
        val relationId = args?.optLong("relationId", 0L) ?: 0L
        val old = relationId.takeIf { it > 0 }?.let { appDb.bookCharacterDao.getRelation(it) }
            ?.takeIf { it.bookUrl == bookKey }
        val now = System.currentTimeMillis()
        val relation = (old ?: BookCharacterRelation(bookUrl = bookKey)).copy(
            bookUrl = bookKey,
            fromCharacterId = fromId,
            toCharacterId = toId,
            relationName = args?.optString("relationName")?.trim()?.ifBlank { null }
                ?: old?.relationName
                ?: "关系",
            relationType = optText(args, "relationType") ?: old?.relationType.orEmpty(),
            description = optText(args, "description") ?: old?.description.orEmpty(),
            strength = (args?.takeIf { it.has("strength") }?.optInt("strength") ?: old?.strength ?: 50)
                .coerceIn(0, 100),
            sortOrder = old?.sortOrder ?: ((appDb.bookCharacterDao.maxRelationOrder(bookKey) ?: -1) + 1),
            updatedAt = now
        )
        val savedId = if (relation.id > 0) {
            appDb.bookCharacterDao.updateRelation(relation)
            relation.id
        } else {
            appDb.bookCharacterDao.insertRelation(relation)
        }
        JSONObject().apply {
            put("ok", true)
            put("relation", relationJson(relation.copy(id = savedId), characters))
        }.toString()
    }

    private suspend fun deleteRelation(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val bookKey = characterKey(book)
        val relationId = args?.optLong("relationId", 0L) ?: 0L
        if (relationId <= 0L) return@withContext errorJson("relationId 不能为空")
        val relation = appDb.bookCharacterDao.getRelation(relationId)
            ?.takeIf { it.bookUrl == bookKey }
            ?: return@withContext errorJson("未找到关系")
        appDb.bookCharacterDao.deleteRelation(relation)
        JSONObject().apply {
            put("ok", true)
            put("deletedRelationId", relationId)
        }.toString()
    }

    private suspend fun listGalleryImages(args: JSONObject?): String = withContext(IO) {
        val keyword = args?.optString("keyword")?.trim().orEmpty()
        val favoriteOnly = args?.optBoolean("favoriteOnly", false) ?: false
        val limit = (args?.optInt("limit", 20) ?: 20).coerceIn(1, 50)
        val filter = if (favoriteOnly) GalleryFilter.FAVORITE else GalleryFilter.ALL
        val images = AiImageGalleryManager.listImages(filter)
            .asSequence()
            .filter { image ->
                keyword.isBlank() ||
                    image.name.contains(keyword, true) ||
                    image.prompt.contains(keyword, true) ||
                    image.providerName.contains(keyword, true) ||
                    image.model.contains(keyword, true)
            }
            .take(limit)
            .toList()
        JSONObject().apply {
            put("ok", true)
            put("images", JSONArray().apply {
                images.forEach { put(imageJson(it)) }
            })
        }.toString()
    }

    private suspend fun setCharacterAvatarFromGallery(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val character = resolveCharacter(characterKey(book), args)
            ?: return@withContext errorJson("未找到角色")
        val imageId = args?.optString("imageId")?.trim().orEmpty()
        if (imageId.isBlank()) return@withContext errorJson("imageId 不能为空")
        val image = AiImageGalleryManager.getImage(imageId)
            ?: return@withContext errorJson("未找到图片")
        AiImageGalleryManager.setFavorite(image.id, true, null)
        val favoriteImage = image.copy(
            favorite = true,
            groupId = image.groupId ?: AiImageGalleryManager.DEFAULT_GROUP_ID,
            updatedAt = System.currentTimeMillis()
        )
        val updated = character.copy(
            avatar = image.localPath,
            updatedAt = System.currentTimeMillis()
        )
        appDb.bookCharacterDao.updateCharacter(updated)
        JSONObject().apply {
            put("ok", true)
            put("character", characterJson(updated))
            put("image", imageJson(favoriteImage))
        }.toString()
    }

    private suspend fun generateCharacterAvatar(args: JSONObject?): String {
        val resolved = withContext(IO) {
            val book = resolveBook(args) ?: return@withContext null
            val character = resolveCharacter(characterKey(book), args) ?: return@withContext null
            book to character
        } ?: return errorJson("未找到书籍或角色")
        val book = resolved.first
        val character = resolved.second
        val prompt = args?.optString("prompt")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: buildCharacterAvatarPrompt(character)
        val providerId = args?.optString("providerId").orEmpty().trim()
        val provider = if (providerId.isBlank()) {
            null
        } else {
            AiImageService.providerByIdOrNull(providerId)
        }
        if (providerId.isNotBlank() && provider == null) {
            return errorJson("image provider is unavailable: $providerId")
        }
        val image = runCatching {
            AiImageService.generateAndStore(
                prompt,
                provider,
                metadata = AiImageGalleryManager.ImageMetadata(
                    bookName = book.name,
                    bookAuthor = book.author,
                    characterId = character.id,
                    characterName = character.name,
                    sourceType = AiImageGalleryManager.SOURCE_TYPE_CHARACTER_AVATAR,
                    sourceText = prompt
                )
            )
        }.getOrElse {
            return errorJson("生成头像失败：${it.localizedMessage ?: it.javaClass.simpleName}")
        }
        val updated = withContext(IO) {
            AiImageGalleryManager.setFavorite(image.id, true, null)
            val latest = appDb.bookCharacterDao.getCharacter(character.id) ?: character
            latest.copy(
                avatar = image.localPath,
                updatedAt = System.currentTimeMillis()
            ).also { appDb.bookCharacterDao.updateCharacter(it) }
        }
        return JSONObject().apply {
            put("ok", true)
            put("character", characterJson(updated))
            put("image", imageJson(image.copy(favorite = true, groupId = image.groupId ?: AiImageGalleryManager.DEFAULT_GROUP_ID)))
            put("prompt", prompt)
        }.toString()
    }

    private fun resolveBook(args: JSONObject?): Book? {
        val bookUrl = args?.optString("bookUrl")?.trim().orEmpty()
        if (bookUrl.isNotBlank()) {
            appDb.bookDao.getBook(bookUrl)?.let { return it }
        }
        val name = args?.optString("bookName")?.trim()
            ?: args?.optString("name")?.trim()
            ?: ""
        val author = args?.optString("author")?.trim().orEmpty()
        if (name.isBlank()) return null
        return if (author.isBlank()) {
            appDb.bookDao.findByName(name).firstOrNull()
        } else {
            appDb.bookDao.getBook(name, author)
                ?: appDb.bookDao.findByName(name).firstOrNull { it.author == author }
        }
    }

    private fun resolveCharacter(bookUrl: String, args: JSONObject?): BookCharacter? {
        val id = args?.optLong("characterId", 0L) ?: 0L
        if (id > 0L) return appDb.bookCharacterDao.getCharacter(id)?.takeIf { it.bookUrl == bookUrl }
        val name = args?.optString("name")?.trim().orEmpty()
        if (name.isBlank()) return null
        return appDb.bookCharacterDao.getCharacter(bookUrl, name)
    }

    private fun resolveCharacterId(
        args: JSONObject?,
        idKey: String,
        nameKey: String,
        characters: List<BookCharacter>
    ): Long? {
        val id = args?.optLong(idKey, 0L) ?: 0L
        if (id > 0L && characters.any { it.id == id }) return id
        val name = args?.optString(nameKey)?.trim().orEmpty()
        if (name.isBlank()) return null
        return characters.firstOrNull { it.name == name }?.id
            ?: characters.firstOrNull { it.name.contains(name, ignoreCase = true) }?.id
    }

    private fun characterJson(character: BookCharacter): JSONObject {
        return JSONObject().apply {
            put("id", character.id)
            put("bookUrl", character.bookUrl)
            put("name", character.name)
            put("avatar", character.avatar)
            put("gender", BookCharacter.normalizeGender(character.gender))
            put("genderLabel", character.genderLabel())
            put("ageStage", BookCharacterProfileMeta.ageOf(character))
            if (character.avatar.isNotBlank()) {
                put("avatarImage", JSONObject().apply {
                    put("type", "character_avatar")
                    put("imagePath", character.avatar)
                    put("alt", character.displayName())
                })
            }
            put("identity", character.identity)
            put("skills", character.skills)
            put("attributes", BookCharacterProfileMeta.attributesWithoutAge(character.attributes))
            put("appearance", character.appearance)
            put("personality", character.personality)
            put("biography", character.biography)
            put("roleLevel", character.roleLevel)
            put("roleLabel", character.roleLabel())
            put("speechRouteJson", character.speechRouteJson)
            put("speechRoute", speechRouteJson(character.speechRouteJson))
            put("autoCreated", character.autoCreated)
            put("source", character.source)
            put("lastDetectedAt", character.lastDetectedAt)
            put("updatedAt", character.updatedAt)
        }
    }

    private fun speechVoiceGroupsJson(includeDisabled: Boolean, includeInvalid: Boolean): JSONArray {
        val groups = appDb.readAloudSpeakerGroupDao.groups()
            .filter { includeDisabled || it.enabled }
            .filter { includeInvalid || !SpeechVoiceGroupRepository.isInvalidGroup(it) }
        return JSONArray().apply {
            groups.forEach { group ->
                put(speechVoiceGroupJson(group, includeItems = true))
            }
        }
    }

    private fun speechVoiceGroupJson(
        group: ReadAloudSpeakerGroup,
        includeItems: Boolean
    ): JSONObject {
        return JSONObject().apply {
            put("id", group.id)
            put("name", group.name)
            put("enabled", group.enabled)
            put("invalidGroup", SpeechVoiceGroupRepository.isInvalidGroup(group))
            put("assignable", group.enabled && !SpeechVoiceGroupRepository.isInvalidGroup(group))
            put("sortOrder", group.sortOrder)
            put("updatedAt", group.updatedAt)
            if (includeItems) {
                val httpTtsList = appDb.httpTTSDao.all
                put("items", JSONArray().apply {
                    appDb.readAloudSpeakerGroupDao.itemsByGroup(group.id).forEach { item ->
                        put(speechVoiceGroupItemJson(item, SpeechVoiceGroupRepository.isValidItem(item, httpTtsList)))
                    }
                })
            }
        }
    }

    private fun speechVoiceGroupItemJson(
        item: ReadAloudSpeakerGroupItem,
        valid: Boolean
    ): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("groupId", item.groupId)
            put("engineType", item.engineType)
            put("engineValue", item.engineValue)
            put("engineName", item.engineName)
            put("speakerName", item.speakerName)
            put("toneID", item.toneID)
            put("sourceGroupId", item.sourceGroupId)
            put("sourceGroupName", item.sourceGroupName)
            put("sortOrder", item.sortOrder)
            put("valid", valid)
            put("blocked", SpeechVoiceGroupRepository.isBlockedItem(item))
        }
    }

    private fun parseSpeechVoiceGroupItems(
        args: JSONObject?,
        groupId: Long,
        startOrder: Int,
        now: Long
    ): List<ReadAloudSpeakerGroupItem> {
        val array = args?.optJSONArray("items")
            ?: args?.optString("itemsJson")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { JSONArray(it) }.getOrNull() }
            ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val engineValue = obj.optString("engineValue").trim()
                val engineType = obj.optString("engineType").trim().ifBlank {
                    if (engineValue.toLongOrNull() != null) {
                        SpeechRoute.ENGINE_HTTP
                    } else {
                        SpeechRoute.ENGINE_SYSTEM
                    }
                }
                val engineName = obj.optString("engineName").trim()
                val speakerName = obj.optString("speakerName").trim()
                    .ifBlank { engineName }
                    .ifBlank { if (engineType == SpeechRoute.ENGINE_SYSTEM) "系统默认" else "HTTP TTS" }
                add(
                    ReadAloudSpeakerGroupItem(
                        groupId = groupId,
                        engineType = engineType,
                        engineValue = engineValue,
                        engineName = engineName,
                        speakerName = speakerName,
                        toneID = obj.optString("toneID").ifBlank { obj.optString("toneId") }.trim(),
                        sourceGroupId = obj.optString("sourceGroupId").trim(),
                        sourceGroupName = obj.optString("sourceGroupName").trim(),
                        sortOrder = startOrder + index,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    private fun speechRouteJson(json: String): JSONObject {
        val route = SpeechRouteSanitizer.validOrDefault(SpeechRoute.fromJson(json))
        return JSONObject().apply {
            put("configured", route.isConfigured)
            put("engineType", route.engineType)
            put("engineValue", route.engineValue)
            put("speakerName", route.speakerName)
            put("toneID", route.toneID)
            put("emotionName", route.emotionName)
            put("emotionTag", route.emotionTag)
            put("source", route.source)
        }
    }

    private fun relationJson(relation: BookCharacterRelation, characters: List<BookCharacter>): JSONObject {
        val from = characters.firstOrNull { it.id == relation.fromCharacterId }
        val to = characters.firstOrNull { it.id == relation.toCharacterId }
        return JSONObject().apply {
            put("id", relation.id)
            put("bookUrl", relation.bookUrl)
            put("fromCharacterId", relation.fromCharacterId)
            put("fromName", from?.name.orEmpty())
            put("toCharacterId", relation.toCharacterId)
            put("toName", to?.name.orEmpty())
            put("relationName", relation.relationName)
            put("relationType", relation.relationType)
            put("description", relation.description)
            put("strength", relation.strength)
            put("updatedAt", relation.updatedAt)
        }
    }

    private fun imageJson(image: AiGeneratedImage): JSONObject {
        return JSONObject().apply {
            put("id", image.id)
            put("name", image.name)
            put("prompt", image.prompt)
            put("providerName", image.providerName)
            put("model", image.model)
            put("localPath", image.localPath)
            put("favorite", image.favorite)
            put("groupId", image.groupId)
            put("createdAt", image.createdAt)
            put("updatedAt", image.updatedAt)
        }
    }

    private fun buildCharacterAvatarPrompt(character: BookCharacter): String {
        return buildList {
            add("为小说角色生成一张角色头像，头像构图，清晰，适合角色资料卡。")
            add("角色名：${character.displayName()}")
            character.genderLabel().takeIf { it != "未知" }?.let { add("性别：$it") }
            BookCharacterProfileMeta.ageOf(character).takeIf { it.isNotBlank() }?.let { add("年纪：$it") }
            character.identity.takeIf { it.isNotBlank() }?.let { add("身份：$it") }
            character.skills.takeIf { it.isNotBlank() }?.let { add("技能：$it") }
            BookCharacterProfileMeta.attributesWithoutAge(character.attributes)
                .takeIf { it.isNotBlank() }
                ?.let { add("属性：$it") }
            character.appearance.takeIf { it.isNotBlank() }?.let { add("形象：$it") }
            character.personality.takeIf { it.isNotBlank() }?.let { add("性格：$it") }
            character.biography.takeIf { it.isNotBlank() }?.let { add("生平：$it") }
        }.joinToString("\n")
    }

    private fun bookJson(book: Book): JSONObject {
        return JSONObject().apply {
            put("bookUrl", book.bookUrl)
            put("name", book.name)
            put("author", book.author)
            put("origin", book.origin)
            put("originName", book.originName)
        }
    }

    private fun function(name: String, description: String, props: JSONObject.() -> Unit): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply(props))
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun bookProps(props: JSONObject) {
        props.put("bookUrl", stringProp("书籍 URL，优先用于精确定位当前书。"))
        props.put("bookName", stringProp("书名。没有 bookUrl 时使用。"))
        props.put("author", stringProp("作者名。"))
    }

    private fun stringProp(description: String) = JSONObject().apply {
        put("type", "string")
        put("description", description)
    }

    private fun intProp(description: String) = JSONObject().apply {
        put("type", "integer")
        put("description", description)
    }

    private fun booleanProp(description: String) = JSONObject().apply {
        put("type", "boolean")
        put("description", description)
    }

    private fun optText(args: JSONObject?, key: String): String? {
        return args?.takeIf { it.has(key) }?.optString(key)?.trim()
    }

    private fun routeFromArgs(args: JSONObject?, source: String): SpeechRoute {
        val engineValue = args?.optString("engineValue").orEmpty()
        return SpeechRoute(
            engineType = if (engineValue.toLongOrNull() != null) {
                SpeechRoute.ENGINE_HTTP
            } else if (engineValue.isNotBlank()) {
                SpeechRoute.ENGINE_SYSTEM
            } else {
                SpeechRoute.ENGINE_DEFAULT
            },
            engineValue = engineValue,
            speakerName = args?.optString("speakerName").orEmpty(),
            toneID = args?.optString("toneID").orEmpty()
                .ifBlank { args?.optString("toneId").orEmpty() },
            emotionName = args?.optString("emotionName").orEmpty(),
            emotionTag = args?.optString("emotionTag").orEmpty(),
            source = source
        )
    }

    private fun parseLongSet(json: String?): Set<Long> {
        if (json.isNullOrBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(json)
            buildSet {
                for (index in 0 until array.length()) {
                    array.optLong(index, 0L).takeIf { it > 0L }?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun longArrayFromJson(array: JSONArray?): List<Long> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optLong(index, 0L).takeIf { it > 0L }?.let(::add)
            }
        }
    }

    private fun errorJsonObject(message: String): JSONObject {
        return JSONObject().apply {
            put("ok", false)
            put("error", message)
        }
    }

    private fun errorJson(message: String): String {
        return JSONObject().apply {
            put("ok", false)
            put("error", message)
        }.toString()
    }
}
