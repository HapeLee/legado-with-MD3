package io.legado.app.feature.settings.backup

/**
 * 四组「备份/恢复忽略项」（M5-9a）。
 *
 * 迁移前 `BackupConfigViewModel` 直接读写 `:app` 的
 * `io.legado.app.help.storage.BackupConfig` 里的四组结构，**每组结构完全对称**：
 * 一个 `HashMap<String, Boolean>`（key → 是否忽略）+ 两个同序数组（`keys` / `titles`）
 * + 一个 `saveXxx()` 落盘。四组的语义（已核对 `BackupConfig.kt` 的字段与落盘文件名）：
 *
 * | 本枚举 | `:app` 的字段 | 语义 | 落盘文件 |
 * |---|---|---|---|
 * | [RestoreConfig] | `ignoreConfig` | **恢复时**忽略的**配置项** | `restoreIgnore.json` |
 * | [RestoreDb] | `dbIgnoreConfig` | **恢复时**忽略的**数据库表** | `dbIgnore.json` |
 * | [BackupConfig] | `backupIgnoreConfig` | **备份时**忽略的**配置项** | `backupIgnore.json` |
 * | [BackupDb] | `backupDbIgnoreConfig` | **备份时**忽略的**数据库表** | `backupDbIgnore.json` |
 *
 * ⚠️ `:app` 里 `backupDbIgnoreKeys` / `backupDbIgnoreTitle` **就是** `dbIgnoreKeys` /
 * `dbIgnoreTitle`（同一个数组引用，见 `BackupConfig.kt:117-118`）⇒ [BackupDb] 与 [RestoreDb]
 * 的 keys/titles 相同，只有「是否忽略」的 map 不同。实现侧照原样转发即可，不要各自复制一份。
 *
 * ⚠️ 落盘文件名**不放进契约** —— 那是实现的存储细节，共享层不该知道。
 */
enum class BackupIgnoreKind {
    /** 恢复时忽略的配置项。 */
    RestoreConfig,

    /** 恢复时忽略的数据库表。 */
    RestoreDb,

    /** 备份时忽略的配置项。 */
    BackupConfig,

    /** 备份时忽略的数据库表。 */
    BackupDb,
}

/**
 * 备份/恢复「忽略项」的存取契约（M5-9a）。
 *
 * ⚠️ **`keys` / `titles` / [isIgnored] 刻意是同步方法，不是 `suspend`**：迁移前 VM 在
 * `_uiState` 的初始化表达式里就调 `loadIgnoreItems()` 等（VM 在**主线程**构造），
 * 而 `:app` 的实现是 `by lazy` 读文件 + GSON —— 也就是**现状本身在主线程做了阻塞 IO**。
 * 本契约如实照搬这个时序，不在这里"顺手改成异步"：那会让首页第一帧的空列表多闪一下
 * （checkbox 列表短暂为空），属于可感知的行为变化，应当由独立切片评估，而不是夹杂在这次搬迁里。
 *
 * [setIgnored] 只写内存（迁移前就是直接改那个 `HashMap`），落盘要另外调 [save] ——
 * 与迁移前 `ignoreConfig[key] = v` + `saveIgnoreConfig()` 两步一致，**不合并**。
 */
interface BackupIgnoreStore {

    /** 该组忽略项的 key，顺序与 [titles] 一一对应。 */
    fun keys(kind: BackupIgnoreKind): List<String>

    /** 该组忽略项的显示标题，与 [keys] 同序、同长度。 */
    fun titles(kind: BackupIgnoreKind): List<String>

    /** 该 key 当前是否被忽略；**从未记录过的 key 返回 `false`**（迁移前 `config[key] ?: false`）。 */
    fun isIgnored(kind: BackupIgnoreKind, key: String): Boolean

    /** 只写内存。要持久化请再调 [save]。 */
    fun setIgnored(kind: BackupIgnoreKind, key: String, ignored: Boolean)

    /** 把该组落盘（迁移前的 `saveIgnoreConfig()` / `saveDbIgnoreConfig()` / … 四个）。 */
    fun save(kind: BackupIgnoreKind)
}
