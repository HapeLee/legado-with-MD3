package io.legado.app.ui.replace

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
object ReplaceRuleRoute : NavKey

@Serializable
data class ReplaceEditRoute(
    val id: Long = -1,
    val pattern: String? = null,
    val isRegex: Boolean = false,
    val scope: String? = null,
    val isScopeTitle: Boolean = false,
    val isScopeContent: Boolean = false
) : NavKey
