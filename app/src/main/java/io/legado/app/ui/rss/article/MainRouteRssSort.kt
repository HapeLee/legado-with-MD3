package io.legado.app.ui.rss.article

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class MainRouteRssSort(
    val sourceUrl: String,
    val sortUrl: String? = null,
    val key: String? = null
) : NavKey

