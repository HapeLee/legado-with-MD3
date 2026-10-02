package io.legado.app.help.readaloud.cast

import androidx.annotation.Keep
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.data.appDb
import io.legado.app.data.dao.RegexCastRuleDao
import io.legado.app.data.entities.RegexCastGroup
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.utils.GSON
import java.util.UUID

/**
 * 正则角色（规则 + 分组树）的导出/导入，格式是自有 JSON，用法与
 * [VoicePoolTransfer] 的自有格式一一对应。
 *
 * 界面侧只有四个触点，都在 `ui/book/readaloud/cast/` 的 RegexCastRule 那三个文件里：
 * Screen 右上角两个按钮起 SAF（`OpenDocument` / `CreateDocument("application/json")`），
 * 选到的 uri 交 ViewModel，ViewModel 只负责开流并调本对象的
 * [exportJson] / [importJson]，[Summary] 的四个数拼成一行 toast。
 * 格式的字段口径只在本文件里定义，界面不解析任何内容。
 *
 * 文件格式（**[exportJson] 是唯一的写方**，[importJson] 是唯一读方）：
 * ```json
 * {
 *   "kind": "legado-regex-cast", "version": 1, "exportedAt": 1700000000000,
 *   "groups": [ { "id", "name", "parentId", "order", "enabled" } ],
 *   "rules":  [ { "name", "pattern", "useRegex", "poolKind", "voiceEffect", "groupId", "order",
 *                 "enabled", "scope", "excludeScope", "poolName", "itemName" } ]
 * }
 * ```
 * `groups[].id` 与 `rules[].groupId` 只在**文件内部**用来串规则属于哪棵树节点，落库时分组一律换
 * 新 UUID（见 [importGroups]）。
 *
 * 声音目标（池 + 池里的音色/配乐）**只写名字、不写 id**：`poolId`/`itemId` 是本机
 * `UUID.randomUUID()` 出来的，换一台设备就指不到同一个东西；而池名在本机是唯一键
 * （`voice_pools.name`、`bgm_pools.name`、`bgm_tracks.name` 上都建了唯一索引），
 * [RegexCastRuleStore.names] 本来就是按 id 反查名字给列表用的，这里按名字正查回来。
 * 音色名（音色列 `displayName`）不唯一，所以解析时优先取「本机能在这个池里找到的同名音色」，
 * 找不到才退回全局同名第一个——见 [importJson]。
 */
object RegexCastTransfer {

    const val KIND = "legado-regex-cast"
    const val VERSION = 1

    /** 导入结果，四个数都要报给界面：哪些要手动补、哪些本来就重复过。 */
    data class Summary(
        /** 新增的规则数。 */
        val rules: Int = 0,
        /** 新增的分组数（同父同名的分组复用本机已有节点，不计入）。 */
        val groups: Int = 0,
        /**
         * 池名或条目名在本机解不出来的规则数：规则照样落地，但解不出来的那半留空，
         * 朗读时会被 [RegexCastRuleStore.effectsFor] 跳过并记一行日志。
         */
        val unresolvedTargets: Int = 0,
        /** 本机已有同名同匹配串的规则而跳过的条数（合并不清空，重复导同一个文件不会翻倍）。 */
        val existingRules: Int = 0,
    ) {
        val isEmpty: Boolean get() = rules == 0 && groups == 0
    }

    // ---- DTO：release 走 R8，help.readaloud.cast 不在 keep 名单里，字段名必须 @SerializedName 钉死 ----

    @Keep
    private data class ExportPayload(
        @SerializedName("kind") val kind: String? = null,
        @SerializedName("version") val version: Int = 0,
        @SerializedName("exportedAt") val exportedAt: Long = 0L,
        @SerializedName("groups") val groups: List<GroupDto>? = null,
        @SerializedName("rules") val rules: List<RuleDto>? = null,
    )

    /** [id] 只在文件内部标识树节点，落库时一定换成新 UUID。 */
    @Keep
    private data class GroupDto(
        @SerializedName("id") val id: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("parentId") val parentId: String? = null,
        @SerializedName("order") val order: Int = 0,
        @SerializedName("enabled") val enabled: Boolean = true,
    )

    /**
     * 规则本体照 [RegexCastRule] 的列导出，另外两个字段是声音目标的**名字**：
     * [poolName] 是 `poolKind` 那一侧的池名，[itemName] 是池内条目名
     * （role = 音色显示名，bgm = 配乐名）。池或条目在本机已不存在时这两个就是 null。
     */
    @Keep
    private data class RuleDto(
        @SerializedName("name") val name: String? = null,
        @SerializedName("pattern") val pattern: String? = null,
        @SerializedName("useRegex") val useRegex: Boolean = true,
        @SerializedName("poolKind") val poolKind: String? = null,
        /** 变声器预设名（与预设表按名字对，不落 id）；本机没有这份预设也照原样带过去。 */
        @SerializedName("voiceEffect") val voiceEffect: String? = null,
        @SerializedName("groupId") val groupId: String? = null,
        @SerializedName("order") val order: Int = 0,
        @SerializedName("enabled") val enabled: Boolean = true,
        @SerializedName("scope") val scope: String? = null,
        @SerializedName("excludeScope") val excludeScope: String? = null,
        @SerializedName("poolName") val poolName: String? = null,
        @SerializedName("itemName") val itemName: String? = null,
    )

    suspend fun exportJson(): String {
        val dao = appDb.regexCastRuleDao
        val (poolNames, itemNames) = RegexCastRuleStore.names()
        val payload = ExportPayload(
            kind = KIND,
            version = VERSION,
            exportedAt = System.currentTimeMillis(),
            groups = dao.getGroups().map {
                GroupDto(
                    id = it.id,
                    name = it.name,
                    parentId = it.parentId,
                    order = it.order,
                    enabled = it.enabled,
                )
            },
            rules = dao.all().map { rule ->
                RuleDto(
                    name = rule.name,
                    pattern = rule.pattern,
                    useRegex = rule.useRegex,
                    poolKind = rule.poolKind,
                    voiceEffect = rule.voiceEffect.ifBlank { null },
                    groupId = rule.groupId,
                    order = rule.order,
                    enabled = rule.enabled,
                    scope = rule.scope,
                    excludeScope = rule.excludeScope,
                    poolName = poolNames[rule.poolId],
                    itemName = itemNames[rule.itemId],
                )
            },
        )
        return GSON.toJson(payload)
    }

    /**
     * 导入：只增不删、重判重。
     *
     * - 分组按「同父同名即同一个」复用本机已有节点，其余新建并换 UUID，父级没落地的挂到未分组。
     * - 规则以 `name` + `pattern` 判重，本机已有就跳过；新增的规则一律排在已有规则之后
     *   （按文件里的 `order` 保相对顺序），不会把用户调好的顺序插乱。
     * - 目标按名字回解，解不出来不清掉整条规则：只把解不出的那半留空，朗读侧
     *   [RegexCastRuleStore.effectsFor] 会跳过它并记日志，用户在列表里补一下就能用。
     *   池名解不出但条目名解得出时保留条目 id——正则角色的弹窗本来就允许只挑音色不挑池。
     */
    suspend fun importJson(text: String): Summary {
        val root = runCatching { JsonParser.parseString(text) }
            .getOrElse { throw IllegalArgumentException("bad json") }
        require(root.isJsonObject && root.asJsonObject.has("rules")) { "bad payload" }
        val payload = runCatching { GSON.fromJson(text, ExportPayload::class.java) }.getOrNull()
        if (payload == null || payload.kind != KIND) {
            throw IllegalArgumentException("bad payload")
        }
        val dao = appDb.regexCastRuleDao
        val (groupIdMap, groupsAdded) = importGroups(payload.groups.orEmpty(), dao)
        val local = dao.all()
        val taken = local.mapTo(mutableSetOf()) { it.name to it.pattern }
        var nextOrder = (local.maxOfOrNull { it.order } ?: 0) + 1
        val targets = readLocalTargets()
        val roleMembersByPool = HashMap<String, Set<String>>()

        suspend fun roleMembers(poolId: String): Set<String> {
            roleMembersByPool[poolId]?.let { return it }
            val ids = appDb.voicePoolDao.getMembers(poolId).mapTo(mutableSetOf()) { it.voiceId }
            roleMembersByPool[poolId] = ids
            return ids
        }

        var summary = Summary(groups = groupsAdded)
        for (dto in payload.rules.orEmpty().sortedBy { it.order }) {
            val name = dto.name.orEmpty().trim()
            val pattern = dto.pattern.orEmpty()
            if (pattern.isBlank()) continue
            if (!taken.add(name to pattern)) {
                summary = summary.copy(existingRules = summary.existingRules + 1)
                continue
            }
            val isBgm = dto.poolKind == RegexCastRule.POOL_BGM
            val kind = if (isBgm) RegexCastRule.POOL_BGM else RegexCastRule.POOL_ROLE
            val poolName = dto.poolName.orEmpty().trim()
            val itemName = dto.itemName.orEmpty().trim()
            val poolId = when {
                poolName.isEmpty() -> ""
                isBgm -> targets.bgmPoolIds[poolName].orEmpty()
                else -> targets.rolePoolIds[poolName].orEmpty()
            }
            var itemId = ""
            if (itemName.isNotEmpty()) {
                itemId = if (isBgm) {
                    targets.trackIds[itemName].orEmpty()
                } else {
                    val candidates = targets.voiceIdsByName[itemName].orEmpty()
                    if (poolId.isEmpty()) {
                        candidates.firstOrNull().orEmpty()
                    } else {
                        val members = roleMembers(poolId)
                        candidates.firstOrNull { it in members } ?: candidates.firstOrNull().orEmpty()
                    }
                }
            }
            val unresolved = (poolName.isNotEmpty() && poolId.isEmpty()) ||
                (itemName.isNotEmpty() && itemId.isEmpty())
            dao.insert(
                RegexCastRule(
                    name = name,
                    pattern = pattern,
                    useRegex = dto.useRegex,
                    poolKind = kind,
                    // 配乐那一种命中处不念、没有声音可变，变声器一律不带过来
                    voiceEffect = if (isBgm) "" else dto.voiceEffect.orEmpty().trim().take(24),
                    poolId = poolId,
                    itemId = itemId,
                    groupId = groupIdMap[dto.groupId.orEmpty()].orEmpty(),
                    enabled = dto.enabled,
                    order = nextOrder,
                    scope = nullableText(dto.scope),
                    excludeScope = nullableText(dto.excludeScope),
                ),
            )
            nextOrder++
            summary = summary.copy(
                rules = summary.rules + 1,
                unresolvedTargets = summary.unresolvedTargets + if (unresolved) 1 else 0,
            )
        }
        return summary
    }

    /**
     * 建分组树：文件内的父 id 通过 `旧 id → 新 id` 映射翻译成真实分组 id。
     *
     * 同父同名直接复用本机分组——[io.legado.app.data.entities.RegexCastGroup] 上
     * `(parentId, name)` 是唯一索引，另起 UUID 插一条同名兄弟会撞上它并 REPLACE 掉用户那一行。
     * 父级可能排在子级后面，所以反复扫到一轮插不进任何行为止；没落地的父级对应的组挂到未分组。
     */
    private suspend fun importGroups(
        groups: List<GroupDto>,
        dao: RegexCastRuleDao,
    ): Pair<Map<String, String>, Int> {
        val library = dao.getGroups()
        val byParentName = library.associate { (it.parentId to it.name) to it.id }.toMutableMap()
        val idMap = HashMap<String, String>()
        var added = 0
        var pending = groups.filter { it.id.orEmpty().isNotEmpty() }
        var progressed = true
        while (pending.isNotEmpty() && progressed) {
            progressed = false
            val next = ArrayList<GroupDto>()
            for (group in pending) {
                val key = group.id.orEmpty()
                val name = group.name.orEmpty().trim()
                if (name.isEmpty()) continue
                val parent = if (group.parentId.orEmpty().isEmpty()) {
                    ""
                } else {
                    idMap[group.parentId] ?: run { next += group; continue }
                }
                val existing = byParentName[parent to name]
                if (existing != null) {
                    idMap[key] = existing
                    progressed = true
                    continue
                }
                val id = UUID.randomUUID().toString()
                dao.insertGroup(
                    RegexCastGroup(
                        id = id,
                        name = name,
                        parentId = parent,
                        order = (dao.getGroups().filter { it.parentId == parent }
                            .maxOfOrNull { it.order } ?: 0) + 1,
                        enabled = group.enabled,
                    ),
                )
                byParentName[parent to name] = id
                idMap[key] = id
                added++
                progressed = true
            }
            pending = next
        }
        return idMap to added
    }

    /** 本机的「名字 → id」索引，一次取全（规则多时比逐条查库便宜）。 */
    private suspend fun readLocalTargets(): LocalTargets {
        val voices = appDb.readAloudVoiceDao.getVoices()
            .groupBy { it.displayName }
            .mapValues { (_, rows) -> rows.map { it.id }.sorted() }
        return LocalTargets(
            rolePoolIds = appDb.voicePoolDao.getAll().associate { it.name to it.id },
            bgmPoolIds = appDb.bgmPoolDao.getPools().associate { it.name to it.id },
            voiceIdsByName = voices,
            trackIds = appDb.bgmPoolDao.getAll().associate { it.name to it.id },
        )
    }

    private class LocalTargets(
        val rolePoolIds: Map<String, String>,
        val bgmPoolIds: Map<String, String>,
        /** 音色显示名 → 音色 id；同名跨池会有多条，所以是列表。 */
        val voiceIdsByName: Map<String, List<String>>,
        val trackIds: Map<String, String>,
    )

    /** 空串与纯空白都按「不限」处理，与 [RegexCastRule.scope] 在库里的口径一致。 */
    private fun nullableText(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }
}
