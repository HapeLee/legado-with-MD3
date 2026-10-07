package io.legado.app.help.readaloud.cast

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.data.appDb
import io.legado.app.data.entities.BgmPoolMember
import io.legado.app.data.entities.BgmTrackEntity
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 背景音乐池的导出/导入：**zip 包，音频在包里**（`manifest.json` + `audio/<entryName>`）。
 *
 * 为什么是 zip：[BgmTrackEntity.path] 是应用私有目录下的绝对路径副本
 * （`externalFiles/bgm`，见 [BgmPoolStore.import]），只导 JSON 的话换台设备路径全是死的，
 * 列表里每一条都会标成「音频文件已丢失」。所以配乐连同文件一起打包带走。
 * 打包/解包的写法沿用 `help/config/ThemePackageManager.kt`（同样是 zip + 内嵌 JSON 清单）。
 *
 * zip 布局（**[exportZip] 是唯一的写方**，[importZip] 是唯一读方）：
 * ```text
 * manifest.json          GSON(Manifest)：分组树 + 池 + 曲目 + 曲目归属
 * audio/<entryName>      一条曲目一个文件，原始字节不转码
 * ```
 * `manifest.tracks[].entryName` 是那条音频在包内的**工作文件名**（写方按
 * `<名字净化>_<下标>.<扩展名>` 生成，保证包内不重名），读方拿它去 `audio/` 下面取文件。
 *
 * **清单里绝不出现 `path`**：那是本机的绝对路径，到别的设备上毫无意义。
 * 落库时的路径由 [importZip] 重新生成——音频复制进接收端自己的 `externalFiles/bgm`，
 * 用一个不撞车的新文件名，`path` 指向那个新位置，结果与用户手动导入音频
 * （[BgmPoolStore.import]）完全等价。
 *
 * id 与名字的分工（与 [RegexCastTransfer] 同一口径）：`groups[].id` 只在包内部串父子关系，
 * 落库一律换新 UUID；池和曲目一律按**名字**互相引用（`tracks[].pools` 是池名列表），
 * 因为池名/曲目名在本机是唯一键，而 id 是 `UUID.randomUUID()`，换台设备就不是同一个东西了。
 */
object BgmPoolTransfer {

    const val KIND = "legado-bgm-pools"
    const val VERSION = 1

    /** 包内清单与音频目录名：写方与读方共用同一组常量，改一处两边一起改。 */
    const val MANIFEST_NAME = "manifest.json"
    const val AUDIO_DIR = "audio"

    /** 池名/分组名上限，与 [BgmPoolStore] 那两份私有常量保持一致（那边是界面校验的同一口径）。 */
    private const val MAX_POOL_NAME = 12
    private const val MAX_GROUP_NAME = 12

    /** 解包上限：正常音乐包就是几十首曲子，超了就是坏包或 zip 炸弹，直接拒收。 */
    private const val MAX_ENTRY_COUNT = 4096
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

    /**
     * 一次调用的结果，[exportZip] 与 [importZip] 共用：
     * 导出时 `groups/pools/tracks` 是**写进包里**的数量，`skippedMissing` 是副本文件已丢、没进包的曲目；
     * 导入时 `groups/pools/tracks` 是**新增**的数量，`skippedMissing` 是包里拿不出可用音频而跳过的曲目。
     */
    data class Summary(
        val groups: Int = 0,
        val pools: Int = 0,
        val tracks: Int = 0,
        val skippedMissing: Int = 0,
    ) {
        val isEmpty: Boolean get() = groups == 0 && pools == 0 && tracks == 0 && skippedMissing == 0
    }

    // ---- DTO：release 走 R8，help.readaloud.cast 不在 keep 名单里，字段名必须 @SerializedName 钉死 ----

    @Keep
    private data class Manifest(
        @SerializedName("kind") val kind: String? = null,
        @SerializedName("version") val version: Int = 0,
        @SerializedName("exportedAt") val exportedAt: Long = 0L,
        @SerializedName("groups") val groups: List<GroupDto>? = null,
        @SerializedName("pools") val pools: List<PoolDto>? = null,
        @SerializedName("tracks") val tracks: List<TrackDto>? = null,
    )

    @Keep
    private data class GroupDto(
        @SerializedName("id") val id: String? = null,
        @SerializedName("name") val name: String? = null,
        /** 父分组在**包内**的 id，空串 = 根层；落库时经 `旧 id → 新 id` 映射翻译。 */
        @SerializedName("parentId") val parentId: String? = null,
        @SerializedName("order") val order: Int = 0,
        @SerializedName("enabled") val enabled: Boolean = true,
    )

    /** 池没有 id：它只被分组（[groupId]）和曲目（池名）引用，重名一律复用本机已有池。 */
    @Keep
    private data class PoolDto(
        @SerializedName("name") val name: String? = null,
        @SerializedName("groupId") val groupId: String? = null,
        @SerializedName("order") val order: Int = 0,
        @SerializedName("enabled") val enabled: Boolean = true,
    )

    /**
     * 一条配乐：[entryName] 指回包里的音频文件，其余是 [BgmTrackEntity] 里能跨设备搬运的列。
     * 没有 `path`（见文件头）也没有 `id`——曲目在接收端是新行，归属靠 [pools] 里的池名重新挂。
     */
    @Keep
    private data class TrackDto(
        @SerializedName("name") val name: String? = null,
        @SerializedName("entryName") val entryName: String? = null,
        @SerializedName("durationMs") val durationMs: Long = 0L,
        @SerializedName("sizeBytes") val sizeBytes: Long = 0L,
        @SerializedName("volume") val volume: Float = 1f,
        @SerializedName("enabled") val enabled: Boolean = true,
        @SerializedName("order") val order: Int = 0,
        /** 这条配乐所在的池名（一首曲子可以进多个池，与 [BgmPoolMember] 同语义）。 */
        @SerializedName("pools") val pools: List<String>? = null,
    )

    /**
     * 写包：分组树 + 池 + 全部配乐，音频从 `path` 指向的副本文件读。
     *
     * 副本已经不在了的行不进包（也计数），否则接收端解出来就是一条永远播不响的配乐。
     * [output] 由调用方从 SAF 的 CreateDocument uri 打开——写流的职责留在 ViewModel，
     * 与角色声音池导出（`MultiRoleRuleViewModel` 的 `ExportPoolsTo` 分支）同一分工。
     */
    suspend fun exportZip(output: OutputStream): Summary = withContext(Dispatchers.IO) {
        val groups = BgmPoolStore.listGroups()
        val pools = BgmPoolStore.listPools()
        val tracks = appDb.bgmPoolDao.getAll()
        val poolNames = pools.associate { it.id to it.name }
        val membersByTrack = appDb.bgmPoolDao.getAllMembers().groupBy { it.trackId }
        var exported = 0
        var missing = 0
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            val trackDtos = ArrayList<TrackDto>(tracks.size)
            tracks.forEachIndexed { index, track ->
                val file = File(track.path)
                if (!file.isFile) {
                    missing++
                    return@forEachIndexed
                }
                // 包内工作文件名：净化后的显示名 + 下标，保证包内不重名；落盘文件名导入时另取
                val entryName = "${safeFilePart(track.name)}_${index}.${extensionOf(file.name)}"
                zip.writeEntry("$AUDIO_DIR/$entryName", file.inputStream())
                trackDtos += TrackDto(
                    name = track.name,
                    entryName = entryName,
                    durationMs = track.durationMs,
                    sizeBytes = track.sizeBytes,
                    volume = track.volume,
                    enabled = track.enabled,
                    order = track.order,
                    pools = membersByTrack[track.id].orEmpty()
                        .mapNotNull { poolNames[it.poolId] }
                        .takeIf { it.isNotEmpty() },
                )
                exported++
            }
            val manifest = Manifest(
                kind = KIND,
                version = VERSION,
                exportedAt = System.currentTimeMillis(),
                groups = groups.map { GroupDto(it.id, it.name, it.parentId, it.order, it.enabled) },
                pools = pools.map { PoolDto(it.name, it.groupId, it.order, it.enabled) },
                tracks = trackDtos,
            )
            // 清单放最后写：读方是整包解完再读清单，位置无所谓，但放最后能保证前面每一条都已落进包里
            zip.writeEntry(MANIFEST_NAME, GSON.toJson(manifest).byteInputStream())
        }
        Summary(groups = groups.size, pools = pools.size, tracks = exported, skippedMissing = missing)
    }

    /**
     * 收包：先整包解到缓存目录（校验路径与大小上限），再按清单落库，全程只增不删。
     *
     * - 分组/池：同名（分组还要同父）复用本机已有节点，名字不合 [BgmPoolStore] 的校验时先净化；
     *   新建走 [BgmPoolStore.createGroup] / [BgmPoolStore.createPool]，不另写建表 DAO 调用。
     * - 曲目：本机已有同名的直接复用那一行（再导一次同一个包不会多份副本文件）；新行才把音频
     *   复制进 `externalFiles/bgm`，文件名不撞车，`path` 指向新位置。
     * - 归属：用增量插行（`insertMembers` 是 ON CONFLICT IGNORE），绝不把用户已有的成员顶掉，
     *   所以这里不能走 [BgmPoolStore.setMembers]——那是全量替换。
     * - 包里缺音频、或复制失败的曲目：整条跳过并计入 [Summary.skippedMissing]，不留死行；
     *   它声明的池归属一起不写（没有文件可播的归属没有意义）。
     */
    suspend fun importZip(input: InputStream): Summary = withContext(Dispatchers.IO) {
        val context = GlobalContext.get().get<Context>()
        val tempRoot = File(context.cacheDir, "bgm_import/${UUID.randomUUID()}")
        try {
            val manifest = extractAndReadManifest(input, tempRoot)
            var summary = Summary()
            val (groupIdMap, groupsAdded) = importGroups(manifest.groups.orEmpty())
            summary = summary.copy(groups = groupsAdded)
            val poolIdByName = BgmPoolStore.listPools().associate { it.name to it.id }.toMutableMap()
            for (pool in manifest.pools.orEmpty()) {
                val name = sanitizePoolName(pool.name)
                if (name.isEmpty() || poolIdByName.containsKey(name)) continue
                BgmPoolStore.createPool(name, groupIdMap[pool.groupId.orEmpty()].orEmpty())
                // createPool 只回布尔：重名（用户已有同名的池）或校验没过时库里就是那一条，
                // 一律按名字回捞出来并入，不再另建、也不重复计数
                val landed = BgmPoolStore.listPools().firstOrNull { it.name == name }?.id
                if (landed != null && poolIdByName.put(name, landed) == null) {
                    summary = summary.copy(pools = summary.pools + 1)
                }
            }
            val dao = appDb.bgmPoolDao
            val dir = File(context.externalFiles, "bgm").apply { mkdirs() }
            for (track in manifest.tracks.orEmpty()) {
                val name = track.name.orEmpty().trim()
                if (name.isEmpty()) continue
                val source = resolveAudioEntry(tempRoot, track.entryName)
                if (source == null) {
                    summary = summary.copy(skippedMissing = summary.skippedMissing + 1)
                    continue
                }
                val existing = dao.getByName(name)
                val trackId = if (existing != null) {
                    existing.id
                } else {
                    val target = collisionFreeName(dir, safeFilePart(name), extensionOf(source.name))
                    val copied = runCatching {
                        source.copyTo(target)
                        target.length() > 0L
                    }.getOrDefault(false)
                    if (!copied) {
                        runCatching { target.delete() }
                        summary = summary.copy(skippedMissing = summary.skippedMissing + 1)
                        continue
                    }
                    val id = UUID.randomUUID().toString()
                    dao.insert(
                        BgmTrackEntity(
                            id = id,
                            name = name,
                            path = target.absolutePath,
                            // 时长沿用包里那份：源设备导入时已经量过，同一串字节的时长不会变；
                            // sizeBytes 用本机副本的真实大小，跟手动导入那条路径算的是同一个东西。
                            durationMs = track.durationMs,
                            sizeBytes = target.length(),
                            order = (dao.count() + 1) * 10,
                            enabled = track.enabled,
                            volume = track.volume.coerceIn(0f, 1f),
                        ),
                    )
                    summary = summary.copy(tracks = summary.tracks + 1)
                    id
                }
                val poolIds = track.pools.orEmpty().mapNotNull { poolIdByName[it] }
                if (poolIds.isNotEmpty()) {
                    dao.insertMembers(poolIds.map { BgmPoolMember(it, trackId) })
                }
            }
            summary
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    /** 解包 + 读清单 + 校验格式：坏包一律抛 [IllegalArgumentException]，界面按「不是本软件导出的」提示。 */
    private fun extractAndReadManifest(input: InputStream, root: File): Manifest {
        root.mkdirs()
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRY_COUNT) { "bad package: too many entries" }
                val name = entry.name
                // 只收清单和 audio/ 下面的东西；带空段、`.` 或 `..` 的相对路径一律丢掉（防越界写文件）
                val accepted = !entry.isDirectory &&
                    (name == MANIFEST_NAME || name.startsWith("$AUDIO_DIR/")) &&
                    name.split('/').none { it.isBlank() || it == "." || it == ".." }
                if (accepted) {
                    val target = File(root, name).apply { parentFile?.mkdirs() }
                    FileOutputStream(target).use { out ->
                        totalBytes += zip.copyTo(out)
                    }
                    require(totalBytes <= MAX_TOTAL_BYTES) { "bad package: too large" }
                }
                zip.closeEntry()
            }
        }
        val manifestFile = File(root, MANIFEST_NAME)
        require(manifestFile.isFile) { "bad package: no manifest" }
        val text = manifestFile.readText()
        val parsed = runCatching { JsonParser.parseString(text) }
            .getOrElse { throw IllegalArgumentException("bad package: bad json") }
        require(parsed.isJsonObject && parsed.asJsonObject.has("tracks")) { "bad package: manifest" }
        val manifest = runCatching { GSON.fromJson(text, Manifest::class.java) }.getOrNull()
        require(manifest != null && manifest.kind == KIND) { "bad package: kind" }
        return manifest
    }

    /**
     * 建分组树，返回 `包内 id → 本机 id` 映射和新增条数。
     *
     * 与 [RegexCastTransfer] 的分组同一套规则：同父同名复用本机节点（`bgm_pool_groups` 上
     * `(parentId, name)` 是唯一索引，另起 UUID 插同名兄弟会 REPLACE 掉用户那一行），
     * 父级排在后面就反复扫到一轮加不进任何行为止，父级落不了的挂到根层。
     * 名字要过 [BgmPoolStore.createGroup] 的校验（≤12 字、不含分组分隔符），所以先净化再建。
     */
    private suspend fun importGroups(groups: List<GroupDto>): Pair<Map<String, String>, Int> {
        val idMap = HashMap<String, String>()
        var added = 0
        val byParentName = BgmPoolStore.listGroups()
            .associate { (it.parentId to it.name) to it.id }
            .toMutableMap()
        var pending = groups.filter { it.id.orEmpty().isNotEmpty() }
        var progressed = true
        while (pending.isNotEmpty() && progressed) {
            progressed = false
            val next = ArrayList<GroupDto>()
            for (group in pending) {
                val key = group.id.orEmpty()
                val name = sanitizeGroupName(group.name)
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
                if (!BgmPoolStore.createGroup(name, parent)) continue
                val landed = BgmPoolStore.listGroups().firstOrNull {
                    it.parentId == parent && it.name == name
                }?.id ?: continue
                byParentName[parent to name] = landed
                idMap[key] = landed
                added++
                progressed = true
            }
            pending = next
        }
        return idMap to added
    }

    /** 清单里的 entryName 只允许指向 `audio/` 下面；越界、缺失、不是文件都算「包里没这段音频」。 */
    private fun resolveAudioEntry(root: File, entryName: String?): File? {
        val relative = entryName?.trim().orEmpty()
        if (relative.isEmpty() || relative.startsWith("/") || '\\' in relative) return null
        if (relative.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        val audioRoot = File(root, AUDIO_DIR).canonicalFile
        val target = File(audioRoot, relative).canonicalFile
        if (!target.path.startsWith(audioRoot.path + File.separator)) return null
        return target.takeIf { it.isFile }
    }

    /** 与 [BgmPoolStore.import] 同源的不撞车命名：撞了就 `_1`、`_2` 往后加。 */
    private fun collisionFreeName(dir: File, baseName: String, extension: String): File {
        var target = File(dir, "$baseName.$extension")
        var index = 1
        while (target.exists()) {
            target = File(dir, "${baseName}_$index.$extension")
            index++
        }
        return target
    }

    /** 显示名 → 文件名片段：抹掉路径分隔符与控制字符，压到 64 字，空名兜底成 `bgm`。 */
    private fun safeFilePart(name: String): String {
        val cleaned = name.trim()
            .map { if (it.code < 0x20 || it in "\\/:*?\"<>|") '_' else it }
            .joinToString("")
            .trim('.', '_')
        return cleaned.take(64).ifBlank { "bgm" }
    }

    /** 扩展名沿用包里那个工作文件的写法，认不出来当 mp3，与 [BgmPoolStore.import] 的兜底一致。 */
    private fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            ?: "mp3"

    /** 池名要过 [BgmPoolStore.createPool] 的校验：非空、不含分组分隔符、≤12 字。 */
    private fun sanitizePoolName(name: String?): String = sanitizeName(name).take(MAX_POOL_NAME)

    /** 分组名同上，还不许含分组分隔符——显示路径就是用它拼的（[BgmPoolStore.GROUP_SEPARATOR]）。 */
    private fun sanitizeGroupName(name: String?): String = sanitizeName(name).take(MAX_GROUP_NAME)

    private fun sanitizeName(name: String?): String =
        name.orEmpty().trim().replace(BgmPoolStore.GROUP_SEPARATOR, "_")

    /** 沿用 `ThemePackageManager` 的写法：一个条目一条流，写完即关。 */
    private fun ZipOutputStream.writeEntry(path: String, input: InputStream) {
        putNextEntry(ZipEntry(path))
        input.use { it.copyTo(this) }
        closeEntry()
    }
}
