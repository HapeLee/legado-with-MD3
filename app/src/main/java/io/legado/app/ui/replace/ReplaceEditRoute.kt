package io.legado.app.ui.replace

import kotlinx.serialization.Serializable

@Serializable
object ReplaceRuleRoute

@Serializable
data class ReplaceEditRoute(
    val id: Long = -1,
    val pattern: String? = null,
    val isRegex: Boolean = false,
    val scope: String? = null,
    val isScopeTitle: Boolean = false,
    val isScopeContent: Boolean = false
)