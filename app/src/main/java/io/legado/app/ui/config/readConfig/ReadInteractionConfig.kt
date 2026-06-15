package io.legado.app.ui.config.readConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

/**
 * 按键/触控交互相关配置
 *
 * 被 ReadBookController 在按键/鼠标事件处理中读取。
 */
object ReadInteractionConfig {

    var mouseWheelPage by prefDelegate(
        PreferKey.mouseWheelPage,
        true
    )

    var volumeKeyPage by prefDelegate(
        PreferKey.volumeKeyPage,
        true
    )

    var volumeKeyPageOnPlay by prefDelegate(
        PreferKey.volumeKeyPageOnPlay,
        true
    )

    var keyPageOnLongPress by prefDelegate(
        PreferKey.keyPageOnLongPress,
        false
    )

    var sliderVibrator by prefDelegate(
        PreferKey.sliderVibrator,
        false
    )

    var selectVibrator by prefDelegate(
        PreferKey.selectVibrator,
        false
    )

    var disableReturnKey by prefDelegate(
        PreferKey.disableReturnKey,
        false
    )

    var prevKeys by prefDelegate(
        PreferKey.prevKeys,
        ""
    )

    var nextKeys by prefDelegate(
        PreferKey.nextKeys,
        ""
    )
}
