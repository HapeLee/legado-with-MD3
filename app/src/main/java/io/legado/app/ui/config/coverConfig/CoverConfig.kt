package io.legado.app.ui.config.coverConfig

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object CoverConfig {

    var loadCoverOnlyWifi by prefDelegate(
        PreferKey.loadCoverOnlyWifi,
        false
    )

    var useDefaultCover by prefDelegate(
        PreferKey.useDefaultCover,
        false
    )

    var coverShowShadow by prefDelegate(
        PreferKey.coverShowShadow,
        false
    )

    var coverShowStroke by prefDelegate(
        PreferKey.coverShowStroke,
        true
    )

    var coverDefaultColor by prefDelegate(
        PreferKey.coverDefaultColor,
        true
    )

    var defaultCover by prefDelegate(
        PreferKey.defaultCover,
        ""
    )

    var coverTextColor by prefDelegate(
        PreferKey.coverTextColor,
        -16777216 // This might need a better default or a color resource lookup
    )

    var coverShadowColor by prefDelegate(
        PreferKey.coverShadowColor,
        -16777216
    )

    var coverShowName by prefDelegate(
        PreferKey.coverShowName,
        true
    )

    var coverShowAuthor by prefDelegate(
        PreferKey.coverShowAuthor,
        true
    )

    var defaultCoverDark by prefDelegate(
        PreferKey.defaultCoverDark,
        ""
    )

    var coverTextColorN by prefDelegate(
        PreferKey.coverTextColorN,
        -1
    )

    var coverShadowColorN by prefDelegate(
        PreferKey.coverShadowColorN,
        -1
    )

    var coverShowNameN by prefDelegate(
        PreferKey.coverShowNameN,
        true
    )

    var coverShowAuthorN by prefDelegate(
        PreferKey.coverShowAuthorN,
        true
    )

    var coverInfoOrientation by prefDelegate(
        PreferKey.coverInfoOrientation,
        "0" // 0: vertical, 1: horizontal
    )
}
