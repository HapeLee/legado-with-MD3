package io.legado.app.ui.config.labConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object LabConfig {

    var labEnabled by prefDelegate(
        PreferKey.labEnabled,
        false
    )

    var eInkDisplay by prefDelegate(
        PreferKey.labEInkDisplay,
        false
    )

    var eyeProtection by prefDelegate(
        PreferKey.labEyeProtection,
        false
    )

}
