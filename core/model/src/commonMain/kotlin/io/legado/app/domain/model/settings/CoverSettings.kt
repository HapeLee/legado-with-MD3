package io.legado.app.domain.model.settings

data class CoverSettings(
    val loadOnlyOnWifi: Boolean = false,
    val useDefaultCover: Boolean = false,
    val showShadow: Boolean = false,
    val showStroke: Boolean = true,
    val useDefaultColor: Boolean = true,
    val textColor: Int = -16777216,
    val shadowColor: Int = -16777216,
    val showName: Boolean = true,
    val showAuthor: Boolean = true,
    val textColorDark: Int = -1,
    val shadowColorDark: Int = -1,
    val showNameDark: Boolean = true,
    val showAuthorDark: Boolean = true,
    val infoOrientation: String = "0",
    val exploreFilterState: Int = 0,
    val defaultCover: String = "",
    val defaultCoverDark: String = "",
)
