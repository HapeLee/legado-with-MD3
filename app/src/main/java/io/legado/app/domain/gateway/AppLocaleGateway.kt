package io.legado.app.domain.gateway

import kotlinx.coroutines.flow.StateFlow

interface AppLocaleGateway {
    val currentLanguage: String
    val language: StateFlow<String>

    fun setLanguage(language: String)

    fun synchronizeFromPlatform()

    fun migrateLegacyLanguage(language: String)
}
