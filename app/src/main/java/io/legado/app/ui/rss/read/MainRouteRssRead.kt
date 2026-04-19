package io.legado.app.ui.rss.read

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class MainRouteRssRead(
    val title: String? = null,
    val origin: String,
    val link: String? = null,
    val openUrl: String? = null
) : NavKey
