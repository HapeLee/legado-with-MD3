package io.legado.app.domain.model.settings

data class ReadStyleState(
    val items: List<ReadStyleItem> = emptyList(),
    val selectedIndex: Int = 0,
    val shareLayout: Boolean = false,
)

data class ReadStyleItem(
    val name: String,
    val bgType: Int,
    val bgValue: String,
    val bgTypeNight: Int,
    val bgValueNight: String,
    val bgTypeEInk: Int,
    val bgValueEInk: String,
    val textColor: Int,
    val textColorNight: Int,
    val textColorEInk: Int,
)
