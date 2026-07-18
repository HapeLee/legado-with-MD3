package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ReadStyleState
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

interface ReadStyleGateway {
    val currentState: ReadStyleState
    val state: StateFlow<ReadStyleState>

    fun refresh()
    fun save()
    fun updateCurrentStyle(mutation: ReadStyleMutation)
    fun applyPreset(index: Int): Boolean
    fun addStyle(): Int
    fun deleteCurrentStyle(): Boolean
    fun importCurrentStyle(bytes: ByteArray)
    fun importOrReplaceStyle(bytes: ByteArray): String
    fun exportCurrentStyle(): ByteArray
    fun saveBackgroundImage(inputStream: InputStream, displayName: String?): String
    fun setCurrentBackgroundImage(path: String)
    fun setCurrentBackgroundImageForMode(path: String, isNight: Boolean)
    fun exportConfigsJson(): String
    fun exportShareConfigJson(): String
}
