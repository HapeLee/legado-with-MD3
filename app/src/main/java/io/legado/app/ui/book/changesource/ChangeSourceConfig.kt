package io.legado.app.ui.book.changesource

import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.prefDelegate

object ChangeSourceConfig {

    var searchGroup by prefDelegate(
        key = "searchGroup",
        defaultValue = ""
    )

    var checkAuthor by prefDelegate(
        key = PreferKey.changeSourceCheckAuthor,
        defaultValue = false
    )

    var loadInfo by prefDelegate(
        key = PreferKey.changeSourceLoadInfo,
        defaultValue = false
    )

    var loadToc by prefDelegate(
        key = PreferKey.changeSourceLoadToc,
        defaultValue = false
    )

    var loadWordCount by prefDelegate(
        key = PreferKey.changeSourceLoadWordCount,
        defaultValue = false
    )
}
