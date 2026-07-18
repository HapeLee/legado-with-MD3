package io.legado.app.ui.config

import androidx.compose.runtime.State
import io.legado.app.constant.PreferKey

object FontConfig {
    private val _fontSort = prefStateDelegate(PreferKey.fontSort, 0)
    var fontSort by _fontSort
    val fontSortState: State<Int> get() = _fontSort.state
}
