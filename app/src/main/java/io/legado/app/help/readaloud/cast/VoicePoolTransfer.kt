package io.legado.app.help.readaloud.cast

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.data.entities.VoicePoolEntity
import io.legado.app.data.entities.VoicePoolGroupEntity
import io.legado.app.data.entities.VoicePoolMember
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechIdentity
import io.legado.app.utils.GSON
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * 角色声音池的导出/导入（界面右上角「导入/导出」弹窗的四个入口）。
 *
 * 三种格式：
 * - 自有格式：分组树 + 池 + 音色 + 成员关系，给「导入声音池 / 导出声音池」用。
 * - TTS Server（I·TTS Server / J.TTS Bridge 的音色列表导出）：只导**角色**，
 *   按 [isNotRoleEntry] 剔掉本地音效与智能音效条目——音效不是音色，进了池会污染分配。
 * - multitts：它「导出角色」得到的 zip（里面是 `voice_pool.yaml`，一个分组一个音色列表），
 *   与 TTS Server 共用 [TtsServerPreview] 与落库路径，只是根分组与引擎包名不同。
 *
 * 架构护栏：DAO 只出现在这里，UI/ViewModel 一律经本 Store 读写。
 */
object VoicePoolTransfer {

    const val KIND = "legado-voice-pools"
    const val VERSION = 1

    /** TTS Server 里音效条目共有的特征：插件 id 或音色名带这些字样。 */
    private val EFFECT_HINTS = listOf("yinxiao", "音效")

    /** I·TTS Server 的引擎包名：列表导出里没有来源包名，只能按惯例猜，界面允许改。 */
    const val DEFAULT_TTS_SERVER_PACKAGE = "com.github.jing332.tts_server_android.i"

    /** multitts 的引擎包名，同上：它的角色表导出里也不带包名。 */
    const val DEFAULT_MULTITTS_PACKAGE = "org.nobody.multitts"

    /** 导入结果，供界面提示「新增几个池、几个音色，跳过几个」。 */
    data class Summary(
        val pools: Int = 0,
        val voices: Int = 0,
        val skippedPools: Int = 0,
        val skippedVoices: Int = 0,
    ) {
        val isEmpty: Boolean get() = pools == 0 && voices == 0
    }

    /**
     * 外部格式解析后的预览：只含角色池，音效已在解析阶段剔除。
     *
     * TTS Server 与 multitts 共用这一份，因为两者落库要做的事完全一样
     * （一个根分组 + 每组一个池 + 池里挂音色），差别只在解析器与根分组名。
     */
    data class TtsServerPreview(
        val pools: List<TtsServerPool>,
        /** 被识别为音效而跳过的条数——这正是用户说的「那些音效不要」。 */
        val skippedEffects: Int,
        /** 源软件里被关掉的条数。 */
        val skippedDisabled: Int,
    ) {
        val voiceCount: Int get() = pools.sumOf { it.voices.size }
    }

    data class TtsServerPool(val name: String, val voices: List<TtsServerVoice>)

    /**
     * [voiceName] 要写进音色的 speakerId，必须是引擎在 Android TTS 里暴露的
     * `Voice.name`——I·TTS Server 用的是 `显示名_角色 id`，所以这里同样拼出来；
     * multitts 用它角色表里的 `value`（引擎自己的音色键，如 `xfpeiyin_wangdaye_1`），
     * `name` 只是它界面上的显示名，还可能重复。
     */
    data class TtsServerVoice(val displayName: String, val voiceName: String)

    // ---- 自有格式 ----

    private data class ExportPayload(
        val kind: String = KIND,
        val version: Int = VERSION,
        val exportedAt: Long = System.currentTimeMillis(),
        val groups: List<VoicePoolGroupEntity> = emptyList(),
        val pools: List<VoicePoolEntity> = emptyList(),
        val voices: List<ReadAloudVoiceEntity> = emptyList(),
        val members: List<VoicePoolMember> = emptyList(),
    )

    suspend fun exportJson(): String {
        VoicePoolStore.ensureGroups()
        val dao = appDb.voicePoolDao
        val poolIds = dao.getAll().map { it.id }.toSet()
        val memberVoiceIds = dao.getAllMembers()
            .filter { it.poolId in poolIds }
            .map { it.voiceId }
            .toSet()
        val payload = ExportPayload(
            groups = dao.getGroups(),
            pools = dao.getAll(),
            voices = appDb.readAloudVoiceDao.getVoices().filter { it.id in memberVoiceIds },
            members = dao.getAllMembers().filter { it.poolId in poolIds },
        )
        return GSON.toJson(payload)
    }

    /**
     * 导入自有格式：库里已有的同 id 行一律保留用户当前数据，只补少的部分。
     *
     * 分组按「同父同名即同一个」复用，复用它要先把导出文件的分组 id 映射到库里的真实 id；
     * 池重名则并入已有池（不动它的归属）；音色按 [SpeechIdentity] 推出的固定 id 判重。
     * 所以同一个文件重复导两次不会翻倍。
     */
    suspend fun importJson(text: String): Summary {
        val payload = runCatching { GSON.fromJson(text, ExportPayload::class.java) }.getOrNull()
        if (payload == null || payload.kind != KIND || payload.pools.isEmpty()) {
            throw IllegalArgumentException("bad payload")
        }
        val dao = appDb.voicePoolDao
        val groupIdMap = mapExportedGroupIds(payload.groups, dao)
        var summary = Summary()
        val poolIdByName = dao.getAll().associate { it.name to it.id }.toMutableMap()
        for (pool in payload.pools.sortedBy { it.name }) {
            if (poolIdByName.values.contains(pool.id)) continue
            val name = pool.name.take(VOICE_POOL_NAME_MAX)
            val mapped = poolIdByName[name]
            if (mapped != null) {
                // 重名不改用户给已有池选的分组，只把它的 id 当作本次成员关系的落点
                poolIdByName[pool.name] = mapped
                summary = summary.copy(skippedPools = summary.skippedPools + 1)
                continue
            }
            val groupId = groupIdMap[pool.groupId].orEmpty()
            val id = UUID.randomUUID().toString()
            dao.insert(
                pool.copy(
                    id = id,
                    name = name,
                    groupId = groupId,
                    order = (dao.getAll().filter { it.groupId == groupId }.maxOfOrNull { it.order } ?: 0) + 1,
                ),
            )
            poolIdByName[pool.name] = id
            poolIdByName[name] = id
            summary = summary.copy(pools = summary.pools + 1)
        }
        val aliveVoices = appDb.readAloudVoiceDao.getVoices().map { it.id }.toMutableSet()
        for (voice in payload.voices) {
            if (voice.id in aliveVoices) continue
            appDb.readAloudVoiceDao.upsertVoice(voice)
            aliveVoices += voice.id
            summary = summary.copy(voices = summary.voices + 1)
        }
        var missing = 0
        val members = payload.members.mapNotNull { m ->
            val poolId = poolIdByName[payload.pools.firstOrNull { it.id == m.poolId }?.name.orEmpty()]
                ?: return@mapNotNull null
            if (m.voiceId !in aliveVoices) {
                missing++
                return@mapNotNull null
            }
            VoicePoolMember(poolId, m.voiceId, m.enabled)
        }
        if (members.isNotEmpty()) dao.insertMembers(members)
        VoicePoolStore.syncGroupPaths()
        return summary.copy(skippedVoices = missing)
    }

    /** 导出文件的分组 id → 库里的分组 id：同父同名复用，否则新建（父级没落地的行挂到未分组）。 */
    private suspend fun mapExportedGroupIds(
        groups: List<VoicePoolGroupEntity>,
        dao: io.legado.app.data.dao.VoicePoolDao,
    ): Map<String, String> {
        val library = dao.getGroups()
        val byParentName = library.associate { (it.parentId to it.name) to it.id }.toMutableMap()
        val idMap = groups.filter { g -> library.any { it.id == g.id } }
            .associate { it.id to it.id }.toMutableMap()
        var pending = groups.filter { it.id !in idMap }
        var progressed = true
        // 父级可能排在后面，所以反复扫，直到一轮插不进任何行
        while (pending.isNotEmpty() && progressed) {
            progressed = false
            val next = ArrayList<VoicePoolGroupEntity>()
            for (group in pending) {
                val parent = if (group.parentId.isEmpty()) {
                    ""
                } else {
                    idMap[group.parentId] ?: run { next += group; continue }
                }
                val name = group.name.take(MAX_GROUP_NAME)
                val existing = byParentName[parent to name]
                if (existing != null) {
                    idMap[group.id] = existing
                    progressed = true
                    continue
                }
                val id = UUID.randomUUID().toString()
                dao.insertGroup(
                    VoicePoolGroupEntity(
                        id = id,
                        name = name,
                        parentId = parent,
                        order = nextGroupOrder(dao, parent),
                        enabled = group.enabled,
                    ),
                )
                byParentName[parent to name] = id
                idMap[group.id] = id
                progressed = true
            }
            pending = next
        }
        return idMap
    }

    // ---- TTS Server ----

    /**
     * 解析 I·TTS Server 的音色列表导出。
     *
     * 文件结构是 `[{group:{id,name}, list:[{id,displayName,isEnabled,config:{...}}]}]`。
     * 角色与音效在这份导出里混在同一个字段里，只能靠 `config.source.pluginId`
     * （本地音效是 `bendiyinxiao`、智能音效是 `zhinengyinxiao*`）和
     * `config.speechRule.tag`（`localSound<N>`）区分，见 [isNotRoleEntry]。
     */
    fun parseTtsServer(text: String): TtsServerPreview {
        val root = runCatching { JsonParser.parseString(text) }
            .getOrElse { throw IllegalArgumentException("bad json") }
        val arrays = if (root.isJsonArray) {
            root.asJsonArray.mapNotNull { it as? JsonObject }
        } else if (root.isJsonObject && root.asJsonObject.has("list")) {
            root.asJsonObject.getAsJsonArray("list").mapNotNull { it as? JsonObject }
        } else {
            throw IllegalArgumentException("bad payload")
        }
        var effects = 0
        var disabled = 0
        val pools = ArrayList<TtsServerPool>()
        for (item in arrays) {
            val groupName = item.getAsJsonObject("group")?.str("name").orEmpty().trim()
            if (groupName.isEmpty()) continue
            val entries = item.getAsJsonArray("list")
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
            val voices = ArrayList<TtsServerVoice>()
            for (entry in entries) {
                if (isNotRoleEntry(entry)) {
                    effects++
                    continue
                }
                if (entry.has("isEnabled") && !entry.get("isEnabled").asBoolean) {
                    disabled++
                    continue
                }
                val displayName = entry.str("displayName")?.trim().orEmpty()
                if (displayName.isEmpty()) continue
                val id = entry.str("id").orEmpty()
                voices += TtsServerVoice(displayName, "${displayName}_$id")
            }
            if (voices.isNotEmpty()) {
                pools += TtsServerPool(groupName.take(VOICE_POOL_NAME_MAX), voices)
            }
        }
        return TtsServerPreview(pools, effects, disabled)
    }

    /**
     * 不是角色音色的条目：背景音乐、占位音色，以及插件名/音色名带「音效」字样、
     * 或说话人标签是 `localSound<N>` 的音效。
     */
    private fun isNotRoleEntry(entry: JsonObject): Boolean {
        val config = entry.getAsJsonObject("config") ?: return true
        if (config.str("#type") != "tts") return true
        val source = config.getAsJsonObject("source")
        val pluginId = source?.str("pluginId").orEmpty()
        val voiceName = source?.str("voice").orEmpty()
        val tag = config.getAsJsonObject("speechRule")?.str("tag").orEmpty()
        if (pluginId.isBlank() && voiceName.isBlank()) return true
        if (tag.startsWith("localSound", ignoreCase = true)) return true
        if (voiceName.contains("placeholder", ignoreCase = true)) return true
        return isEffectText(pluginId) || isEffectText(voiceName)
    }

    /**
     * 落库：一个根分组（[rootGroup]，按来源软件分开放），下面每个源分组一个池，池里是本次导入的音色。
     *
     * 池名撞上已有池（比如默认池「男童」）时并入已有池，不改它的分组归属；
     * 音色按 engineType/engineId/speakerId 推出的固定 id 判重，重复导入不会翻倍。
     */
    suspend fun applyTtsServer(
        preview: TtsServerPreview,
        enginePackage: String,
        rootGroup: String = TTS_SERVER_ROOT_GROUP_NAME,
    ): Summary {
        if (preview.pools.isEmpty() || enginePackage.isBlank()) return Summary()
        VoicePoolStore.ensureGroups()
        val dao = appDb.voicePoolDao
        val groupId = importRootGroupId(dao, rootGroup)
        var summary = Summary()
        for (pool in preview.pools) {
            val existing = dao.getByName(pool.name)
            val target = existing ?: VoicePoolEntity(
                id = UUID.randomUUID().toString(),
                name = pool.name,
                groupId = groupId,
                order = nextPoolOrder(dao, groupId),
            ).also {
                dao.insert(it)
                summary = summary.copy(pools = summary.pools + 1)
            }
            if (existing != null) {
                summary = summary.copy(skippedPools = summary.skippedPools + 1)
            }
            val members = ArrayList<VoicePoolMember>()
            for (voice in pool.voices) {
                val id = SpeechIdentity.voiceId(ReadAloudVoice.ENGINE_SYSTEM, enginePackage, voice.voiceName)
                if (appDb.readAloudVoiceDao.getVoice(id) == null) {
                    appDb.readAloudVoiceDao.upsertVoice(
                        ReadAloudVoiceEntity(
                            id = id,
                            engineType = ReadAloudVoice.ENGINE_SYSTEM,
                            engineId = enginePackage,
                            speakerId = voice.voiceName,
                            displayName = voice.displayName,
                            traitsJson = "{}",
                        ),
                    )
                    summary = summary.copy(voices = summary.voices + 1)
                }
                members += VoicePoolMember(target.id, id)
            }
            dao.insertMembers(members)
        }
        VoicePoolStore.syncGroupPaths()
        return summary
    }

    /** 导入落点的根分组（「TTS Server」/「MultiTTS」）：已有同名根分组就复用，不会导入两次建两个。 */
    private suspend fun importRootGroupId(
        dao: io.legado.app.data.dao.VoicePoolDao,
        rootGroup: String,
    ): String {
        dao.getGroups().firstOrNull { it.parentId.isEmpty() && it.name == rootGroup }
            ?.let { return it.id }
        val id = UUID.randomUUID().toString()
        dao.insertGroup(
            VoicePoolGroupEntity(
                id = id,
                name = rootGroup.take(MAX_GROUP_NAME),
                parentId = "",
                order = nextGroupOrder(dao, ""),
            ),
        )
        return id
    }

    // ---- multitts ----

    /**
     * 取出 multitts 导出包里的 `voice_pool.yaml` 文本。
     *
     * 它「导出角色」给的是一个 zip（里面就一个 yaml），但用户也可能直接给 .yaml，
     * 所以按 zip 魔数分流：是包就取包里第一个 .yaml 条目，不是就整份当文本读。
     */
    fun readMultiTtsYaml(input: InputStream): String {
        val bytes = input.readBytes()
        if (bytes.size < 2 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
            return bytes.toString(Charsets.UTF_8)
        }
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".yaml", ignoreCase = true)) {
                    return zip.reader().readText()
                }
                zip.closeEntry()
            }
        }
        throw IllegalArgumentException("no yaml entry")
    }

    /**
     * 解析 multitts 的 `voice_pool.yaml`：顶层是「分组名:」，下面每条是
     * `- !!org.nobody.multitts.tts.role.VoiceItem` 加 activate / group / name / value 四个字段。
     *
     * 没有用 YAML 库：文件带 `!!` 自定义标签，通用解析器会把它当未知类型直接抛错，
     * 而这份结构只有三层，逐行读反而稳。音效与停用的条目在这里就剔掉，与 TTS Server 同一套规矩。
     */
    fun parseMultiTts(text: String): TtsServerPreview {
        val pools = LinkedHashMap<String, ArrayList<TtsServerVoice>>()
        var effects = 0
        var disabled = 0
        var group = ""
        var itemGroup = ""
        var name: String? = null
        var value: String? = null
        var enabled = true
        var inItem = false

        fun endItem() {
            if (!inItem) return
            inItem = false
            val label = name.orEmpty()
            val key = value.orEmpty()
            val poolName = group.ifBlank { itemGroup }
            if (label.isBlank() || key.isBlank() || poolName.isBlank()) return
            if (!enabled) {
                disabled++
                return
            }
            if (isEffectText(poolName) || isEffectText(label) ||
                key.contains("placeholder", ignoreCase = true)
            ) {
                effects++
                return
            }
            pools.getOrPut(poolName.take(VOICE_POOL_NAME_MAX)) { ArrayList() } +=
                TtsServerVoice(label, key)
        }

        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            val body = line.trim()
            if (body.isEmpty() || body.startsWith("#")) continue
            when {
                // 顶格且以冒号结尾的是分组；条目行以 - 开头，字段行有缩进
                !line.startsWith(" ") && !line.startsWith("-") && body.endsWith(":") -> {
                    endItem()
                    group = unquote(body.dropLast(1))
                }

                body.startsWith("-") -> {
                    endItem()
                    inItem = true
                    itemGroup = ""
                    name = null
                    value = null
                    enabled = true
                }

                inItem -> {
                    val separator = body.indexOf(':')
                    if (separator < 0) continue
                    val field = body.substring(0, separator).trim()
                    val content = unquote(body.substring(separator + 1))
                    when (field) {
                        "activate" -> enabled = content.toBooleanStrictOrNull() ?: true
                        "group" -> itemGroup = content
                        "name" -> name = content
                        "value" -> value = content
                    }
                }
            }
        }
        endItem()
        return TtsServerPreview(
            pools = pools.filter { it.value.isNotEmpty() }
                .map { (poolName, voices) -> TtsServerPool(poolName, voices) },
            skippedEffects = effects,
            skippedDisabled = disabled,
        )
    }

    /** 分组名或音色名带音效字样就不算角色音色，与 [isNotRoleEntry] 用同一份判据。 */
    private fun isEffectText(text: String): Boolean =
        EFFECT_HINTS.any { text.contains(it, ignoreCase = true) }

    /** 去掉 YAML 里可能出现的成对引号。 */
    private fun unquote(text: String): String {
        val trimmed = text.trim()
        val quoted = trimmed.length >= 2 &&
            ((trimmed.startsWith('"') && trimmed.endsWith('"')) ||
                (trimmed.startsWith('\'') && trimmed.endsWith('\'')))
        return if (quoted) trimmed.substring(1, trimmed.length - 1) else trimmed
    }

    private suspend fun nextPoolOrder(
        dao: io.legado.app.data.dao.VoicePoolDao,
        groupId: String,
    ): Int = (dao.getAll().filter { it.groupId == groupId }.maxOfOrNull { it.order } ?: 0) + 1

    private suspend fun nextGroupOrder(
        dao: io.legado.app.data.dao.VoicePoolDao,
        parentId: String,
    ): Int = (dao.getGroups().filter { it.parentId == parentId }.maxOfOrNull { it.order } ?: 0) + 1

    /** 与 [VoicePoolStore] 的池名/分组名上限保持一致。 */
    private const val VOICE_POOL_NAME_MAX = 12
    private const val MAX_GROUP_NAME = 12

    /** 两种外部格式各归一个根分组，导过的东西在池列表里能一眼看出来源。 */
    const val TTS_SERVER_ROOT_GROUP_NAME = "TTS Server"
    const val MULTITTS_ROOT_GROUP_NAME = "MultiTTS"

    private fun JsonObject.str(key: String): String? =
        if (has(key) && !get(key).isJsonNull) get(key).asString else null
}
