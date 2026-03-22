package io.legado.app.ui.config.backupConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object BackupConfig {

    var webDavUrl by prefDelegate(
        PreferKey.webDavUrl,
        ""
    )

    var webDavAccount by prefDelegate(
        PreferKey.webDavAccount,
        ""
    )

    var webDavPassword by prefDelegate(
        PreferKey.webDavPassword,
        ""
    )

    var webDavDir by prefDelegate(
        PreferKey.webDavDir,
        "legado"
    )

    var webDavDeviceName by prefDelegate(
        PreferKey.webDavDeviceName,
        ""
    )

    var syncBookProgress by prefDelegate(
        PreferKey.syncBookProgress,
        true
    )

    var syncBookProgressPlus by prefDelegate(
        PreferKey.syncBookProgressPlus,
        false
    )

    var autoCheckNewBackup by prefDelegate(
        PreferKey.autoCheckNewBackup,
        true
    )

    var onlyLatestBackup by prefDelegate(
        PreferKey.onlyLatestBackup,
        true
    )

    var backupPath by prefDelegate<String?>(
        PreferKey.backupPath,
        null
    )

}
