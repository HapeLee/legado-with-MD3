package io.legado.app.ui.config.importBookConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object ImportBookConfig {

    var defaultBookTreeUri by prefDelegate(
        PreferKey.defaultBookTreeUri,
        null as String?
    )

    var importBookPath by prefDelegate(
        PreferKey.importBookPath,
        null as String?
    )

    var bookImportFileName by prefDelegate(
        PreferKey.bookImportFileName,
        null as String?
    )

    var localBookImportSort by prefDelegate(
        PreferKey.localBookImportSort,
        0
    )

    var remoteServerId by prefDelegate(
        PreferKey.remoteServerId,
        0L
    )
}
