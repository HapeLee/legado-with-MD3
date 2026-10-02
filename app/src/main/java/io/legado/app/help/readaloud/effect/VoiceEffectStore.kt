package io.legado.app.help.readaloud.effect

import io.legado.app.data.appDb
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.utils.GSON

/**
 * 变声器的读写出口（管理页与朗读服务都只经这里，不直连 DAO）。
 *
 * 除了 CRUD 还管两件事：
 * 1. 首次进入时灌入内置的四个免费预设（魔王/哥布林/机器人/心声混响），用户删干净后
 *    下次读取会再补回来；
 * 2. 给朗读侧一份**同步可读**的快照（预设表 + 角色→预设名的映射），因为
 *    `applyPreset` 与 ExoPlayer 回调都在非挂起上下文里，不能为了查一行而阻塞 IO。
 *    快照在每章准备时刷新，[version] 供调用方判断是否白刷。
 */
object VoiceEffectStore {

    /** 平台混响预设号（AudioEffect.PRESET_*），0 = 不加混响。 */
    const val REVERB_NONE = 0
    const val REVERB_SMALL_ROOM = 1
    const val REVERB_MEDIUM_ROOM = 2
    const val REVERB_LARGE_ROOM = 3
    const val REVERB_MEDIUM_HALL = 4
    const val REVERB_LARGE_HALL = 5
    const val REVERB_PLATE = 6

    /** 每次写库 +1，朗读侧据此决定要不要重取快照。 */
    @Volatile
    var version: Int = 0
        private set

    /** 「无变声」选项的取值（角色表里存空串）。 */
    const val NONE = ""

    /**
     * 「心声混响」：正文里用**单引号**包起来的那句台词（心声）默认套的内置预设名，
     * 就是 [builtins] 里那一份，用户可改名、停用或删掉。
     *
     * 唯一消费方是 [io.legado.app.help.readaloud.cast.CastAssignmentStore.assign]：它在**新建**
     * 分配行的那一次把这个名字写进 `chapter_role_assignments.voiceEffect`（段级那一列），
     * 之后不再补写——用户在胶囊里清空或改过就以他的为准。预设不在这个名字上（改名/停用/删掉）
     * 时 [usableName] 给空串，那就是不变声，本 Store 不会凭空造一份预设出来。
     */
    const val THOUGHT_EFFECT = "心声混响"

    /**
     * 这个名字的预设此刻能不能用（存在且启用）：能用返回它自己，否则返回 [NONE]。
     *
     * 走 [list] 而不是 [byName]：那份内存快照只在每章准备时刷新，写默认值的当下可能还没灌过；
     * [list] 顺带负责预设表为空时（全新装机、没进过变声器页）先补内置预设。
     */
    suspend fun usableName(name: String): String =
        if (name in enabledNames()) name else NONE

    /** 内置预设：全部用平台音高/混响/带通组合出来的免费方案，不引第三方库。 */
    fun builtins(): List<VoiceEffectPreset> = listOf(
        VoiceEffectPreset(
            name = "魔王",
            pitch = 0.62f,
            speed = 0.92f,
            reverbPreset = REVERB_LARGE_HALL,
            builtin = true,
        ),
        VoiceEffectPreset(
            name = "哥布林",
            pitch = 1.9f,
            speed = 1.25f,
            builtin = true,
        ),
        VoiceEffectPreset(
            name = "机器人",
            pitch = 1.08f,
            metal = true,
            builtin = true,
        ),
        VoiceEffectPreset(
            name = "心声混响",
            pitch = 0.97f,
            speed = 0.95f,
            reverbPreset = REVERB_MEDIUM_ROOM,
            builtin = true,
        ),
    )

    private var presets: Map<String, VoiceEffectPreset> = emptyMap()
    private var effectsOfCharacter: Map<String, String> = emptyMap()
    private var loadedVersion = -1

    suspend fun list(): List<VoiceEffectPreset> {
        if (appDb.voiceEffectDao.count() == 0) seedBuiltins()
        return appDb.voiceEffectDao.getAll().also { presets = it.associateBy { p -> p.name } }
    }

    /** 分配悬浮窗的下拉项：启用的预设 + 开头的「无变声」。 */
    suspend fun enabledNames(): List<String> = list().filter { it.enabled }.map { it.name }

    private suspend fun seedBuiltins() {
        builtins().forEachIndexed { index, preset ->
            appDb.voiceEffectDao.insertIgnore(preset.copy(order = index))
        }
    }

    /** 新建/改名保存：同名覆盖，重排顺序由管理页整体写回。 */
    suspend fun save(preset: VoiceEffectPreset) {
        val old = appDb.voiceEffectDao.getOne(preset.name)
        if (old == null) {
            appDb.voiceEffectDao.insert(
                preset.copy(
                    order = (list().maxOfOrNull { it.order } ?: 0) + 1,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        } else {
            appDb.voiceEffectDao.update(
                preset.copy(builtin = old.builtin, createdAt = old.createdAt, updatedAt = System.currentTimeMillis()),
            )
        }
        bump()
    }

    suspend fun setEnabled(name: String, enabled: Boolean) {
        appDb.voiceEffectDao.getOne(name)?.let {
            it.enabled = enabled
            it.updatedAt = System.currentTimeMillis()
            appDb.voiceEffectDao.update(it)
        }
        bump()
    }

    suspend fun delete(name: String) {
        appDb.voiceEffectDao.deleteByName(name)
        bump()
    }

    /** 恢复被删掉的内置预设（用户想找回默认四个时用）。 */
    suspend fun restoreBuiltins() {
        builtins().forEachIndexed { index, preset ->
            appDb.voiceEffectDao.insertIgnore(preset.copy(order = -100 + index))
        }
        bump()
    }

    suspend fun saveOrder(names: List<String>) {
        val byName = list().associateBy { it.name }
        names.forEachIndexed { index, name ->
            byName[name]?.let {
                it.order = index
                appDb.voiceEffectDao.update(it)
            }
        }
        bump()
    }

    suspend fun exportText(): String = GSON.toJson(list())

    /** 导入：同名覆盖参数，新名字追加；返回导入条数，格式不对返回 0。 */
    suspend fun importText(json: String): Int {
        val rows = runCatching {
            GSON.fromJson(json, Array<VoiceEffectPreset>::class.java)?.toList()
        }.getOrNull().orEmpty().map { it.copy(name = it.name.trim()) }
            .filter { it.name.isNotEmpty() }
        if (rows.isEmpty()) return 0
        var nextOrder = (list().maxOfOrNull { it.order } ?: 0) + 1
        rows.forEach { row ->
            val old = appDb.voiceEffectDao.getOne(row.name)
            appDb.voiceEffectDao.insert(row.copy(order = old?.order ?: nextOrder++))
        }
        bump()
        return rows.size
    }

    // ---- 朗读侧（同步快照） ----

    /** 每章准备时刷一次；版本没变就是空操作。 */
    suspend fun refresh() {
        if (loadedVersion == version && effectsOfCharacter.isNotEmpty()) return
        presets = appDb.voiceEffectDao.getAll().associateBy { it.name }
        effectsOfCharacter = appDb.castCharacterDao.getAllGlobal()
            .filter { it.voiceEffect.isNotBlank() }
            .associate { it.id to it.voiceEffect }
        loadedVersion = version
    }

    /** 这个名字的预设（停用的算没有）。 */
    fun byName(name: String): VoiceEffectPreset? =
        presets[name]?.takeIf { it.enabled }

    /**
     * 这一层是不是**会话级**效果（混响 / 金属感）：只有我们持有播放器的路径挂得上，
     * 系统 TTS 直读时会被整个丢掉，见 [io.legado.app.model.ReadAloud.supportsSessionAudioEffect]。
     */
    fun needsSessionEffect(preset: VoiceEffectPreset?): Boolean =
        preset != null && (preset.reverbPreset != REVERB_NONE || preset.metal)

    /** 这个角色说话时该套哪个预设；没分配/预设已删都返回 null。 */
    fun ofCharacter(characterId: String?): VoiceEffectPreset? {
        if (characterId.isNullOrEmpty()) return null
        return byName(effectsOfCharacter[characterId] ?: return null)
    }

    /**
     * 朗读这一句该套哪个预设：**段级优先**，没设段级才跟随角色的全局预设。
     *
     * 段级那份来自正文胶囊悬浮窗（只改那一句，存在 `chapter_role_assignments.voiceEffect`，
     * 由 [io.legado.app.help.readaloud.cast.CastSpeechOverlay] 抄进播放单元）；
     * 全局那份来自「人物与角色配音」页（存在 `cast_characters.voiceEffect`）。
     */
    fun ofSpeech(paragraphEffect: String?, characterId: String?): VoiceEffectPreset? =
        paragraphEffect?.takeIf { it.isNotBlank() }?.let(::byName) ?: ofCharacter(characterId)

    private fun bump() {
        version++
    }

    /** 角色被删掉时忘掉它的快照：版本号没变，[refresh] 不会自己重读。 */
    suspend fun forgetCharacter(characterId: String) {
        if (effectsOfCharacter[characterId] == null) return
        effectsOfCharacter = effectsOfCharacter - characterId
        bump()
    }

    /**
     * 角色的变声器状态被改写（`cast_characters.voiceEffect`）后必须调一次。
     *
     * 快照只按**预设表**的版本号失效，改角色那一列不会让它重读：不调这里，
     * 刚设置的变声要等下次预设变动或重启才听得到，用户看到的就是「改了没反应」。
     */
    suspend fun invalidateCharacters() {
        effectsOfCharacter = emptyMap()
        bump()
    }
}
