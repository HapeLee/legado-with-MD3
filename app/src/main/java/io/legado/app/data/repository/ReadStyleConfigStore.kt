package io.legado.app.data.repository

import io.legado.app.domain.model.settings.READ_STYLE_PRESET_COUNT
import io.legado.app.help.DefaultData
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.move
import java.util.UUID

/**
 * 排版配置列表的**唯一所有者**。
 *
 * R4.4 把写面收进 gateway 实现之后，`ReadBookConfig` 只剩「选哪一份 + 怎么读」这两件事，
 * 但列表本体和那把 `@Synchronized` 锁还挂在它身上——状态和保护它的锁分居两处，
 * 谁都能顺手再往那个全局 object 上塞新状态。本类把两者放回一起：
 * 上游是文件层 [ReadStyleRepository]，下游是 gateway 实现（写）与 `ReadBookConfig`（只读投影）。
 *
 * 本类**不知道当前选中哪一份**：`styleSelect` / `shareLayout` 是设置项，
 * 由 `ReadBookConfig` 解析成下标后再来取用。
 */
class ReadStyleConfigStore(private val readStyleRepository: ReadStyleRepository) {

    private val lock = Any()
    private val configList = arrayListOf<ReadBookConfig.Config>()
    private lateinit var shareConfigRef: ReadBookConfig.Config

    val configFilePath: String get() = readStyleRepository.configFilePath
    val shareConfigFilePath: String get() = readStyleRepository.shareConfigFilePath

    /** 共享排版那一份。 */
    val shareConfig: ReadBookConfig.Config get() = synchronized(lock) { shareConfigRef }

    fun initConfigs() {
        val configs = readStyleRepository.readConfigs()
        synchronized(lock) {
            configList.clear()
            configList.addAll(configs)
        }
    }

    fun initShareConfig() {
        val fallback = synchronized(lock) { configList.getOrNull(READ_STYLE_PRESET_COUNT) } ?: ReadBookConfig.Config()
        val config = readStyleRepository.readShareConfig(fallback)
        synchronized(lock) { shareConfigRef = config }
    }

    /** 按下标取用。配置文件缺斤少两时先恢复默认，保证 5 份预设始终在。 */
    fun configAt(index: Int): ReadBookConfig.Config = synchronized(lock) {
        if (configList.size < READ_STYLE_PRESET_COUNT) {
            resetAllLocked()
        }
        configList.getOrNull(index) ?: configList[0]
    }

    /** 整份替换（应用预设 / 导入）。共享排版开着时，共享那份跟着换。 */
    fun replaceConfigAt(index: Int, config: ReadBookConfig.Config, alsoShare: Boolean) {
        synchronized(lock) {
            val replacement = config.copy(id = configList[index].id)
            configList[index] = replacement
            if (alsoShare) {
                shareConfigRef = replacement
            }
        }
    }

    /**
     * 改**当前样式**那一份的字段。共享排版开着时也只动这一份——背景、虚线、状态栏图标
     * 这些按样式独立的项走这里。
     *
     * 下标解析与 [configAt] 保持一致（含配置文件缺斤少两时先恢复默认、越界回落到 0），
     * 否则「读得到、写不进」会成为一类静默失效。
     */
    fun updateStyleAt(index: Int, transform: (ReadBookConfig.Config) -> ReadBookConfig.Config) {
        synchronized(lock) {
            if (configList.size < READ_STYLE_PRESET_COUNT) {
                resetAllLocked()
            }
            val target = if (index in configList.indices) index else 0
            configList[target] = transform(configList[target])
        }
    }

    /** 改**当前生效**那一份：共享排版开着时是共享那份，否则就是当前样式。 */
    fun updateEffective(
        index: Int,
        useShare: Boolean,
        transform: (ReadBookConfig.Config) -> ReadBookConfig.Config,
    ) {
        if (useShare) {
            synchronized(lock) { shareConfigRef = transform(shareConfigRef) }
        } else {
            updateStyleAt(index, transform)
        }
    }

    /** `Config` 的值字段不可变，快照直接共享实例即可。 */
    fun configsSnapshot(): List<ReadBookConfig.Config> = synchronized(lock) {
        configList.toList()
    }

    fun shareConfigSnapshot(): ReadBookConfig.Config = synchronized(lock) { shareConfigRef }

    fun addConfig(config: ReadBookConfig.Config): Int = synchronized(lock) {
        configList.add(config.copy(id = UUID.randomUUID().toString()))
        configList.lastIndex
    }

    fun importOrReplaceConfig(config: ReadBookConfig.Config): String = synchronized(lock) {
        val index = configList.indexOfFirst { it.name == config.name }
        if (index >= 0) {
            configList[index] = config.copy(id = configList[index].id)
        } else {
            configList.add(config.copy(id = UUID.randomUUID().toString()))
        }
        config.name
    }

    /** 5 份预设不允许删。 */
    fun deleteConfigAt(index: Int): Boolean = synchronized(lock) {
        if (configList.size <= READ_STYLE_PRESET_COUNT) return@synchronized false
        configList.removeAt(index)
        true
    }

    /**
     * 把 [from] 处的一份样式移到 [to]（都是列表绝对下标，语义同 [io.legado.app.utils.move]：
     * 先移除，再插入到 [to]）。
     *
     * 只允许在自建样式区（下标 ≥ [READ_STYLE_PRESET_COUNT]）内移动：前 [READ_STYLE_PRESET_COUNT] 份是内置预设，
     * 既是 `applyPreset(index)` 的取值依据，也是配置缺斤少两时的补齐来源，位置不能变。
     */
    fun moveStyle(from: Int, to: Int): Boolean = synchronized(lock) {
        if (!isCustomIndex(from) || !isCustomIndex(to) || from == to) return@synchronized false
        configList.move(from, to)
        true
    }

    /**
     * 批量删除自建样式（下标按删除前的列表算），返回是否真删了。
     * 选中下标修正与高亮规则解绑由上层负责——本类不知道当前选中哪一份。
     */
    fun deleteStylesAt(indices: List<Int>): Boolean = synchronized(lock) {
        val targets = indices.filter { isCustomIndex(it) }.distinct().sortedDescending()
        if (targets.isEmpty()) return@synchronized false
        targets.forEach { configList.removeAt(it) }
        true
    }

    /**
     * 把 [indices] 里那些样式**整块**搬到边缘（[toTop] = 紧跟预设之后，否则列表末尾），
     * 块内保持它们原来的相对次序。返回是否真的动了。
     */
    fun moveStylesToEdge(indices: List<Int>, toTop: Boolean): Boolean = synchronized(lock) {
        val targets = indices.filter { isCustomIndex(it) }.distinct().sorted()
        if (targets.isEmpty()) return@synchronized false
        val moving = targets.map { configList[it] }
        // 从后往前删，避免下标漂移
        targets.sortedDescending().forEach { configList.removeAt(it) }
        if (toTop) {
            configList.addAll(READ_STYLE_PRESET_COUNT, moving)
        } else {
            configList.addAll(moving)
        }
        true
    }

    /** 自建样式区：预设那 [READ_STYLE_PRESET_COUNT] 份钉死，不可拖、不可删。 */
    private fun isCustomIndex(index: Int): Boolean =
        index >= READ_STYLE_PRESET_COUNT && index < configList.size

    /** 备份要把排版引用到的背景图一起带走。 */
    fun allPicBgStr(): List<String> = readStyleRepository.getAllPicBgStr(configsSnapshot())

    /** 清掉没有任何排版引用的背景图，以及导入导出留下的缓存。 */
    fun clearBgAndCache() {
        readStyleRepository.clearBgAndCache(configsSnapshot())
    }

    private fun resetAllLocked() {
        configList.clear()
        configList.addAll(DefaultData.readConfigs)
        save()
    }

    private fun save() {
        // 列表与共享配置在同一临界区内取快照，落盘的两份内容彼此自洽
        val (configs, share) = synchronized(lock) { configList.toList() to shareConfigRef }
        Coroutine.async {
            readStyleRepository.save(configs, share)
        }
    }
}
