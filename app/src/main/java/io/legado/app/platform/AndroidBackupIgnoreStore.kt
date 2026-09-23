package io.legado.app.platform

import io.legado.app.feature.settings.backup.BackupIgnoreKind
import io.legado.app.feature.settings.backup.BackupIgnoreStore
import io.legado.app.help.storage.BackupConfig

/**
 * [BackupIgnoreStore] 的 Android 实现（M5-9a）。逐组转发 `:app` 的
 * `io.legado.app.help.storage.BackupConfig` 那四组结构，**不加任何判断**。
 *
 * ⚠️ 两点如实照搬、不要"顺手优化"：
 *  1. `BackupConfig` 的四个 `HashMap` 都是 `by lazy` **读文件 + GSON** ⇒ 首次访问是
 *     **主线程阻塞 IO**。契约把它标成同步方法就是为此（见 `BackupIgnoreStore` 的 KDoc）。
 *  2. `backupDbIgnoreKeys` / `backupDbIgnoreTitle` 在 `:app` 里**就是** `dbIgnoreKeys` /
 *     `dbIgnoreTitle`（同一数组引用）⇒ 这里也照原样转发同一个数组，不要复制一份，
 *     否则日后 `:app` 加了新表项，两组会不一致。
 */
class AndroidBackupIgnoreStore : BackupIgnoreStore {

    override fun keys(kind: BackupIgnoreKind): List<String> = when (kind) {
        BackupIgnoreKind.RestoreConfig -> BackupConfig.ignoreKeys.toList()
        BackupIgnoreKind.RestoreDb -> BackupConfig.dbIgnoreKeys.toList()
        BackupIgnoreKind.BackupConfig -> BackupConfig.backupIgnoreKeys.toList()
        BackupIgnoreKind.BackupDb -> BackupConfig.backupDbIgnoreKeys.toList()
    }

    override fun titles(kind: BackupIgnoreKind): List<String> = when (kind) {
        BackupIgnoreKind.RestoreConfig -> BackupConfig.ignoreTitle.toList()
        BackupIgnoreKind.RestoreDb -> BackupConfig.dbIgnoreTitle.toList()
        BackupIgnoreKind.BackupConfig -> BackupConfig.backupIgnoreTitle.toList()
        BackupIgnoreKind.BackupDb -> BackupConfig.backupDbIgnoreTitle.toList()
    }

    override fun isIgnored(kind: BackupIgnoreKind, key: String): Boolean =
        configOf(kind)[key] ?: false

    override fun setIgnored(kind: BackupIgnoreKind, key: String, ignored: Boolean) {
        configOf(kind)[key] = ignored
    }

    override fun save(kind: BackupIgnoreKind) {
        when (kind) {
            BackupIgnoreKind.RestoreConfig -> BackupConfig.saveIgnoreConfig()
            BackupIgnoreKind.RestoreDb -> BackupConfig.saveDbIgnoreConfig()
            BackupIgnoreKind.BackupConfig -> BackupConfig.saveBackupIgnoreConfig()
            BackupIgnoreKind.BackupDb -> BackupConfig.saveBackupDbIgnoreConfig()
        }
    }

    /** 只取 map（不落盘）—— 与迁移前 VM 直接读写那几个 `HashMap` 一致。 */
    private fun configOf(kind: BackupIgnoreKind): HashMap<String, Boolean> = when (kind) {
        BackupIgnoreKind.RestoreConfig -> BackupConfig.ignoreConfig
        BackupIgnoreKind.RestoreDb -> BackupConfig.dbIgnoreConfig
        BackupIgnoreKind.BackupConfig -> BackupConfig.backupIgnoreConfig
        BackupIgnoreKind.BackupDb -> BackupConfig.backupDbIgnoreConfig
    }
}
