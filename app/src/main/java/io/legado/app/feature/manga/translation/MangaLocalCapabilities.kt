package io.legado.app.feature.manga.translation

import io.legado.app.BuildConfig
import io.legado.app.domain.model.manga.MangaProductCapabilities

/**
 * 产物能力只在这一处声明。界面与说明都从这里取，不再逐个判断 [BuildConfig]，
 * 避免同一件事在不同控件里写出两种口径。
 */
internal val LocalMangaCapabilities = MangaProductCapabilities(localEngine = BuildConfig.MANGA_LOCAL_ENGINE)
