package io.legado.app.help.readaloud.cast

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.data.appDb
import io.legado.app.data.entities.BgmPoolEntity
import io.legado.app.data.entities.BgmPoolGroupEntity
import io.legado.app.data.entities.BgmPoolMember
import io.legado.app.data.entities.BgmTrackEntity
import io.legado.app.utils.externalFiles
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.UUID

/**
 * 背景音乐池的数据出口（UI/ViewModel 一律经本 Store 访问 DAO，架构护栏）。
 *
 * 结构与 [VoicePoolStore] 一一对应：配乐库扁平（导入 = 复制进 `externalFiles/bgm`），
 * 池与分组是另外的三张表，池通过成员表引用配乐，所以一首曲子可以进多个池。
 * 行模型共用 [CastPoolRow]/[CastGroupRow]/[CastMemberRow]，页面的树形列表与拖动规则
 * 两边完全一致（与角色声音池同一口径）。
 */
object BgmPoolStore {

    /** 导入结果，供界面提示「成功几个、跳过几个」。 */
    data class ImportResult(val added: Int, val skipped: Int)

    private const val DIR = "bgm"
    private const val PREFS = "cast_prefs"
    private const val KEY_FADE_IN = "bgmFadeIn"
    private const val KEY_FADE_OUT = "bgmFadeOut"
    private const val KEY_BGM_VOLUME = "bgmMasterVolume"
    private const val MAX_NAME = 12
    private const val MAX_GROUP_NAME = 12

    /** 分组显示路径的分隔符，与角色声音池一致。 */
    const val GROUP_SEPARATOR = "/"

    private fun prefs(): android.content.SharedPreferences =
        GlobalContext.get().get<Context>().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- 切场景时的音量渐变（背景音乐池页右上角设置） ----------

    /** 缓入：新场景的配乐淡入。 */
    fun fadeIn(): Boolean = prefs().getBoolean(KEY_FADE_IN, true)

    /** 缓出：上一场景的配乐淡出后再停。 */
    fun fadeOut(): Boolean = prefs().getBoolean(KEY_FADE_OUT, true)

    fun setFade(enabled: Boolean, fadeIn: Boolean) {
        prefs().edit()
            .putBoolean(if (fadeIn) KEY_FADE_IN else KEY_FADE_OUT, enabled)
            .apply()
    }

    // ---------- 背景音乐总音量（朗读设置里那一根滑杆） ----------

    /**
     * 全局总音量（0f–1f），默认 1f = 不缩放播放音量。
     *
     * 实际播放音量 = 总音量 × 曲目自身音量 × 段内音量。三层各管一件事：总音量是「背景音乐
     * 相对人声整体多响」，曲目音量是「这首曲子本身偏响/偏闷」，段内音量是「这一场要压一点」。
     */
    fun volume(): Float = prefs().getFloat(KEY_BGM_VOLUME, 1f).coerceIn(0f, 1f)

    /** 改了总音量要自增版本：朗读中的配乐轨靠它发现「用户刚拖了滑杆」。 */
    @Volatile
    var volumeVersion = 0
        private set

    fun setVolume(volume: Float) {
        prefs().edit().putFloat(KEY_BGM_VOLUME, volume.coerceIn(0f, 1f)).apply()
        volumeVersion++
    }

    // ---------- 配乐库 ----------

    suspend fun list(): List<BgmTrackEntity> = appDb.bgmPoolDao.getAll()

    suspend fun setEnabled(id: String, enabled: Boolean) {
        appDb.bgmPoolDao.setEnabled(id, enabled)
    }

    /** 这条配乐自己的音量（0f–1f）。段内还能再压一档，实际播放取乘积。 */
    suspend fun setTrackVolume(id: String, volume: Float) {
        appDb.bgmPoolDao.setVolume(id, volume.coerceIn(0f, 1f))
    }

    /**
     * 改显示名。磁盘上的副本文件不动（`path` 保持原样），只改库里那一列。
     *
     * 必须连带改场景标记：[BgmSceneMark] 认的是 `poolName` + `trackName` **两个名字**，
     * 只改曲库这一行会让已分配的场景指向一条不存在的曲目（朗读时静默没有配乐）。
     * 同名可能撞车（两个池各有一条「雨声」），所以空名、重名都判非法返回 false，
     * 让界面提示而不是写进库里留下歧义。
     */
    suspend fun renameTrack(id: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val dao = appDb.bgmPoolDao
        val track = dao.getTrack(id) ?: return false
        if (track.name == trimmed) return true
        if (dao.getByName(trimmed)?.takeIf { it.id != id } != null) return false
        dao.setName(id, trimmed)
        val pools = dao.getPoolNamesOfTrack(id)
        if (pools.isNotEmpty()) {
            appDb.bgmSceneDao.renameTrackInPools(track.name, trimmed, pools)
        }
        return true
    }

    suspend fun saveOrder(ids: List<String>) {
        ids.forEachIndexed { index, id -> appDb.bgmPoolDao.setOrder(id, index) }
    }

    /** 删行 + 删副本文件 + 清归属行（不然池里会留下计数却列不出项）。 */
    suspend fun delete(track: BgmTrackEntity) {
        appDb.bgmPoolDao.clearTrackMembership(track.id)
        appDb.bgmPoolDao.delete(track)
        runCatching { File(track.path).delete() }
    }

    suspend fun import(uris: List<Uri>): ImportResult {
        if (uris.isEmpty()) return ImportResult(0, 0)
        val context = GlobalContext.get().get<Context>()
        val dir = File(context.externalFiles, DIR).apply { mkdirs() }
        var added = 0
        var skipped = 0
        uris.forEach { uri ->
            val source = DocumentFile.fromSingleUri(context, uri)
            val displayName = source?.name.orEmpty().ifBlank { "bgm" }
            val baseName = displayName.substringBeforeLast('.')
            val extension = displayName.substringAfterLast('.', "mp3")
            // 同名不覆盖：已有同名条目直接跳过，避免把用户正在用的配乐悄悄换掉
            if (appDb.bgmPoolDao.getByName(baseName) != null) {
                skipped++
                return@forEach
            }
            val target = File(dir, "${baseName}_${System.currentTimeMillis()}.$extension")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!copied || target.length() == 0L) {
                runCatching { target.delete() }
                skipped++
                return@forEach
            }
            appDb.bgmPoolDao.insert(
                BgmTrackEntity(
                    id = UUID.randomUUID().toString(),
                    name = baseName,
                    path = target.absolutePath,
                    durationMs = readDuration(target),
                    sizeBytes = target.length(),
                    order = (appDb.bgmPoolDao.count() + 1) * 10,
                ),
            )
            added++
        }
        return ImportResult(added, skipped)
    }

    private fun readDuration(file: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    // ---------- 分组树 ----------

    /** 分组树（DFS 拉平，带 depth/path），根层在前、子组紧跟其父。 */
    suspend fun listGroups(): List<CastGroupRow> {
        val all = appDb.bgmPoolDao.getGroups()
        // 父组不存在的孤儿按根层处理：不然它会整棵从页面上消失，看起来像数据丢了
        val ids = all.mapTo(mutableSetOf()) { it.id }
        val byParent = all.groupBy { if (it.parentId in ids) it.parentId else "" }
            .mapValues { (_, rows) -> rows.sortedWith(compareBy({ it.order }, { it.name })) }
        val rows = ArrayList<CastGroupRow>(all.size)
        fun walk(group: BgmPoolGroupEntity, depth: Int, parentPath: String, parentUsable: Boolean) {
            val path =
                if (parentPath.isEmpty()) group.name else "$parentPath$GROUP_SEPARATOR${group.name}"
            val usable = parentUsable && group.enabled
            rows += CastGroupRow(
                group.id, group.name, group.parentId, path, depth, group.order, group.enabled, usable,
            )
            byParent[group.id].orEmpty().forEach { walk(it, depth + 1, path, usable) }
        }
        byParent[""].orEmpty().forEach { walk(it, 0, "", true) }
        return rows
    }

    /** groupId → 显示路径。 */
    private suspend fun pathByGroupId(): Map<String, String> =
        listGroups().associate { it.id to it.path }

    // ---------- 池 ----------

    /** 池行 + 计数（total = 池内配乐数，enabledCount = 其中勾上的）。 */
    suspend fun listPools(): List<CastPoolRow> {
        val groups = listGroups()
        val pathByGroup = groups.associate { it.id to it.path }
        val usableByGroup = groups.associate { it.id to it.usable }
        // 配乐被删掉后归属行可能还在（异常中断），计数只按还在的配乐算
        val alive = appDb.bgmPoolDao.getAll().mapTo(mutableSetOf()) { it.id }
        val members = appDb.bgmPoolDao.getAllMembers().filter { it.trackId in alive }
        val totalByPool = members.groupingBy { it.poolId }.eachCount()
        val enabledByPool = members.filter { it.enabled }.groupingBy { it.poolId }.eachCount()
        return appDb.bgmPoolDao.getPools().map {
            CastPoolRow(
                id = it.id,
                name = it.name,
                groupId = it.groupId,
                groupName = pathByGroup[it.groupId].orEmpty(),
                order = it.order,
                enabled = it.enabled,
                groupEnabled = usableByGroup[it.groupId] ?: true,
                isDefault = false,
                total = totalByPool[it.id] ?: 0,
                enabledCount = enabledByPool[it.id] ?: 0,
            )
        }
    }

    /** 展开池：池内配乐 + 启用状态（按配乐库顺序）。 */
    suspend fun poolDetail(poolId: String): CastPoolDetail {
        val tracks = appDb.bgmPoolDao.getAll()
        val members = appDb.bgmPoolDao.getMembers(poolId).associateBy { it.trackId }
        return CastPoolDetail(
            pool = listPools().first { it.id == poolId },
            members = tracks
                .filter { members.containsKey(it.id) }
                .map {
                    CastMemberRow(
                        id = it.id,
                        displayName = it.name,
                        enabled = members.getValue(it.id).enabled,
                    )
                },
        )
    }

    suspend fun setMemberEnabled(poolId: String, trackId: String, enabled: Boolean) {
        appDb.bgmPoolDao.setMemberEnabled(poolId, trackId, enabled)
    }

    /** 成员全量替换（成员选择页保存）。 */
    suspend fun setMembers(poolId: String, trackIds: Set<String>) {
        appDb.bgmPoolDao.setMembers(poolId, trackIds)
    }

    suspend fun memberTrackIds(poolId: String): Set<String> =
        appDb.bgmPoolDao.getMembers(poolId).map { it.trackId }.toSet()

    /** 成员页候选：全部配乐 (id → 名字)。 */
    suspend fun allTrackPairs(): List<Pair<String, String>> =
        appDb.bgmPoolDao.getAll().map { it.id to it.name }

    /** 能作为场景分配候选的池名（池自己开 + 分组链全开）。 */
    suspend fun enabledPoolNames(): List<String> = listPools().filter { it.usable }.map { it.name }

    /**
     * 池名 → 池内可播放的配乐（启用成员 ∩ 副本文件还在），顺序按配乐库顺序。
     *
     * 随机抽取与播放侧都用它；文件被用户清掉时在这里过滤掉，播放就不会撞空。
     */
    suspend fun playableTracksOfPool(poolName: String): List<BgmTrackEntity> {
        val pool = appDb.bgmPoolDao.getPoolByName(poolName)?.takeIf { it.enabled } ?: return emptyList()
        val ids = appDb.bgmPoolDao.getMembers(pool.id).filter { it.enabled }.map { it.trackId }.toSet()
        if (ids.isEmpty()) return emptyList()
        return appDb.bgmPoolDao.getEnabled().filter { it.id in ids && File(it.path).exists() }
    }

    /** 名字全局唯一（与角色声音池同规则）；非法/重名返回 false。[groupId] 空串 = 未分组。 */
    suspend fun createPool(name: String, groupId: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_NAME) return false
        if (appDb.bgmPoolDao.getPoolByName(n) != null) return false
        appDb.bgmPoolDao.insertPool(
            BgmPoolEntity(
                id = UUID.randomUUID().toString(),
                name = n,
                groupId = groupId,
                order = nextPoolOrder(groupId),
            ),
        )
        return true
    }

    /** 改名/换组；改名为已存在的其它池名时失败。 */
    suspend fun updatePool(id: String, name: String, groupId: String): Boolean {
        val dao = appDb.bgmPoolDao
        val pool = dao.getPool(id) ?: return false
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_NAME) return false
        val other = dao.getPoolByName(n)
        if (other != null && other.id != id) return false
        dao.updatePool(
            pool.copy(
                name = n,
                groupId = groupId,
                order = if (pool.groupId == groupId) pool.order else nextPoolOrder(groupId),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    private suspend fun nextPoolOrder(groupId: String): Int =
        (appDb.bgmPoolDao.getPools().filter { it.groupId == groupId }.maxOfOrNull { it.order } ?: 0) + 1

    /** 删池：只清归属，配乐本身留在库里（和角色声音池删池不删音色一致）。 */
    suspend fun deletePool(id: String) {
        appDb.bgmPoolDao.clearMembers(id)
        appDb.bgmPoolDao.deletePoolById(id)
    }

    suspend fun setPoolEnabled(id: String, enabled: Boolean) {
        val pool = appDb.bgmPoolDao.getPool(id) ?: return
        appDb.bgmPoolDao.updatePool(pool.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    // ---------- 分组增删改 ----------

    /** 新建分组；同级重名/非法名返回 false。[parentId] 空串 = 根层。 */
    suspend fun createGroup(name: String, parentId: String): Boolean {
        val n = name.trim()
        if (!validGroupName(n)) return false
        val dao = appDb.bgmPoolDao
        val siblings = dao.getGroups().filter { it.parentId == parentId }
        if (siblings.any { it.name == n }) return false
        dao.insertGroup(
            BgmPoolGroupEntity(
                id = UUID.randomUUID().toString(),
                name = n,
                parentId = parentId,
                order = (siblings.maxOfOrNull { it.order } ?: 0) + 1,
            ),
        )
        return true
    }

    suspend fun renameGroup(groupId: String, newGroup: String): Boolean {
        val n = newGroup.trim()
        if (!validGroupName(n)) return false
        val dao = appDb.bgmPoolDao
        val group = dao.getGroup(groupId) ?: return false
        if (dao.getGroups().any { it.parentId == group.parentId && it.name == n && it.id != groupId }) {
            return false
        }
        dao.updateGroups(listOf(group.copy(name = n, updatedAt = System.currentTimeMillis())))
        return true
    }

    /** 删除分组（解散）：连子组带池一起上提到被删组的父级。 */
    suspend fun dissolveGroup(groupId: String): Boolean {
        val dao = appDb.bgmPoolDao
        val group = dao.getGroup(groupId) ?: return false
        val parent = group.parentId
        val now = System.currentTimeMillis()
        dao.getGroups().filter { it.parentId == groupId }.forEach {
            dao.setGroupParent(it.id, parent, now)
        }
        val pools = dao.getPools().filter { it.groupId == groupId }
        var order = pools.map { it.order }.maxOrNull() ?: 0
        val moved = pools.map {
            order += 1
            it.copy(groupId = parent, order = order)
        }
        moved.forEach { dao.updatePool(it) }
        dao.deleteGroupById(groupId)
        return true
    }

    suspend fun setGroupEnabled(groupId: String, enabled: Boolean) {
        val dao = appDb.bgmPoolDao
        dao.getGroup(groupId)?.let {
            dao.updateGroups(listOf(it.copy(enabled = enabled, updatedAt = System.currentTimeMillis())))
        }
    }

    /** 换父级（菜单「移动到」）。禁止挂到自己或自己的子孙下面，否则树成环。 */
    suspend fun moveGroupToParent(groupId: String, parentId: String): Boolean {
        if (groupId == parentId) return false
        val dao = appDb.bgmPoolDao
        val group = dao.getGroup(groupId) ?: return false
        if (parentId.isNotEmpty()) {
            var cursor: String? = parentId
            var guard = 0
            while (cursor != null && cursor.isNotEmpty() && guard++ < 32) {
                if (cursor == groupId) return false
                cursor = dao.getGroup(cursor)?.parentId
            }
        }
        if (group.parentId == parentId) return true
        dao.setGroupParent(groupId, parentId, System.currentTimeMillis())
        return true
    }

    /** 池换组（不在拖动路径上时走菜单/编辑弹窗）。 */
    suspend fun movePoolToGroup(poolId: String, groupId: String) {
        val dao = appDb.bgmPoolDao
        val pool = dao.getPool(poolId) ?: return
        if (pool.groupId == groupId) return
        dao.setPoolSlot(poolId, groupId, nextPoolOrder(groupId), System.currentTimeMillis())
    }

    /** 拖动结束后的整表回写（顺序 + 被拖那一行的父级）。 */
    suspend fun saveSlots(
        poolSlots: List<Pair<String, Pair<String, Int>>>,
        groupSlots: List<Pair<String, Pair<String, Int>>>,
    ) {
        appDb.bgmPoolDao.saveSlots(poolSlots, groupSlots, System.currentTimeMillis())
    }

    private fun validGroupName(name: String): Boolean =
        name.isNotEmpty() && name.length <= MAX_GROUP_NAME && !name.contains(GROUP_SEPARATOR)
}
