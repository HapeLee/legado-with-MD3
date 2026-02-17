package io.legado.app.ui.config.readConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object ReadConfig {

    var tocUiUseReplace by prefDelegate(
        PreferKey.tocUiUseReplace,
        false
    )

    var tocCountWords by prefDelegate(
        PreferKey.tocCountWords,
        true
    )

}