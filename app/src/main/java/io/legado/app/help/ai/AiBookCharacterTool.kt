package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.AiGeneratedImage
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterRelation
import io.legado.app.help.ai.AiImageGalleryManager.GalleryFilter
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
            }
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
        put("identity", stringProp("角色身份。"))
        put("skills", stringProp("角色技能。"))
        put("attributes", stringProp("角色属性。"))
        put("appearance", stringProp("角色形象描述。"))
        put("personality", stringProp("角色性格描述。"))
        put("biography", stringProp("角色生平。"))
        put("roleLevel", intProp("角色重要度：0 普通角色，1 重要角色，2 主角。"))
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

    private suspend fun listCharacters(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val characters = appDb.bookCharacterDao.characters(book.bookUrl)
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
        val name = args?.optString("name")?.trim().orEmpty()
        if (name.isBlank()) return@withContext errorJson("name 不能为空")
        val now = System.currentTimeMillis()
        val id = args?.optLong("characterId", 0L) ?: 0L
        val old = id.takeIf { it > 0 }?.let { appDb.bookCharacterDao.getCharacter(it) }
            ?: appDb.bookCharacterDao.getCharacter(book.bookUrl, name)
        val character = (old ?: BookCharacter(bookUrl = book.bookUrl)).copy(
            bookUrl = book.bookUrl,
            name = name,
            avatar = optText(args, "avatar") ?: old?.avatar.orEmpty(),
            identity = optText(args, "identity") ?: old?.identity.orEmpty(),
            skills = optText(args, "skills") ?: old?.skills.orEmpty(),
            attributes = optText(args, "attributes") ?: old?.attributes.orEmpty(),
            appearance = optText(args, "appearance") ?: old?.appearance.orEmpty(),
            personality = optText(args, "personality") ?: old?.personality.orEmpty(),
            biography = optText(args, "biography") ?: old?.biography.orEmpty(),
            roleLevel = (args?.takeIf { it.has("roleLevel") }?.optInt("roleLevel") ?: old?.roleLevel ?: BookCharacter.ROLE_NORMAL)
                .coerceIn(BookCharacter.ROLE_NORMAL, BookCharacter.ROLE_MAIN),
            sortOrder = old?.sortOrder ?: ((appDb.bookCharacterDao.maxCharacterOrder(book.bookUrl) ?: -1) + 1),
            createdAt = old?.createdAt?.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        val savedId = if (character.id > 0) {
            appDb.bookCharacterDao.updateCharacter(character)
            character.id
        } else {
            appDb.bookCharacterDao.insertCharacter(character)
        }
        JSONObject().apply {
            put("ok", true)
            put("character", characterJson(character.copy(id = savedId)))
        }.toString()
    }

    private suspend fun deleteCharacter(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val character = resolveCharacter(book.bookUrl, args)
            ?: return@withContext errorJson("未找到角色")
        appDb.bookCharacterDao.deleteCharacterWithRelations(character)
        JSONObject().apply {
            put("ok", true)
            put("deletedCharacterId", character.id)
        }.toString()
    }

    private suspend fun listRelations(args: JSONObject?): String = withContext(IO) {
        val book = resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val characters = appDb.bookCharacterDao.characters(book.bookUrl)
        val relations = appDb.bookCharacterDao.relations(book.bookUrl)
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
        val characters = appDb.bookCharacterDao.characters(book.bookUrl)
        val fromId = resolveCharacterId(args, "fromCharacterId", "fromName", characters)
        val toId = resolveCharacterId(args, "toCharacterId", "toName", characters)
        if (fromId == null || toId == null || fromId == toId) {
            return@withContext errorJson("请选择两个不同角色")
        }
        val relationId = args?.optLong("relationId", 0L) ?: 0L
        val old = relationId.takeIf { it > 0 }?.let { appDb.bookCharacterDao.getRelation(it) }
        val now = System.currentTimeMillis()
        val relation = (old ?: BookCharacterRelation(bookUrl = book.bookUrl)).copy(
            bookUrl = book.bookUrl,
            fromCharacterId = fromId,
            toCharacterId = toId,
            relationName = args?.optString("relationName")?.trim()?.ifBlank { null }
                ?: old?.relationName
                ?: "关系",
            relationType = optText(args, "relationType") ?: old?.relationType.orEmpty(),
            description = optText(args, "description") ?: old?.description.orEmpty(),
            strength = (args?.takeIf { it.has("strength") }?.optInt("strength") ?: old?.strength ?: 50)
                .coerceIn(0, 100),
            sortOrder = old?.sortOrder ?: ((appDb.bookCharacterDao.maxRelationOrder(book.bookUrl) ?: -1) + 1),
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
        resolveBook(args) ?: return@withContext errorJson("未找到书籍")
        val relationId = args?.optLong("relationId", 0L) ?: 0L
        if (relationId <= 0L) return@withContext errorJson("relationId 不能为空")
        appDb.bookCharacterDao.deleteRelationById(relationId)
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
        val character = resolveCharacter(book.bookUrl, args)
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
            val character = resolveCharacter(book.bookUrl, args) ?: return@withContext null
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
            if (character.avatar.isNotBlank()) {
                put("avatarImage", JSONObject().apply {
                    put("type", "character_avatar")
                    put("imagePath", character.avatar)
                    put("alt", character.displayName())
                })
            }
            put("identity", character.identity)
            put("skills", character.skills)
            put("attributes", character.attributes)
            put("appearance", character.appearance)
            put("personality", character.personality)
            put("biography", character.biography)
            put("roleLevel", character.roleLevel)
            put("roleLabel", character.roleLabel())
            put("updatedAt", character.updatedAt)
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
            character.identity.takeIf { it.isNotBlank() }?.let { add("身份：$it") }
            character.skills.takeIf { it.isNotBlank() }?.let { add("技能：$it") }
            character.attributes.takeIf { it.isNotBlank() }?.let { add("属性：$it") }
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

    private fun errorJson(message: String): String {
        return JSONObject().apply {
            put("ok", false)
            put("error", message)
        }.toString()
    }
}
