package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.OtherSettings
import kotlinx.coroutines.flow.Flow

interface OtherSettingsGateway {
    val settings: Flow<OtherSettings>
    suspend fun update(update: OtherSettingsUpdate)
}

sealed interface OtherSettingsUpdate {
    data class UpdateToVariant(val value: String) : OtherSettingsUpdate
    data class AutoCheckUpdateOnStart(val value: Boolean) : OtherSettingsUpdate
    data class WebServiceAutoStart(val value: Boolean) : OtherSettingsUpdate
    data class AutoRefresh(val value: Boolean) : OtherSettingsUpdate
    data class DefaultToRead(val value: Boolean) : OtherSettingsUpdate
    data class FirebaseEnable(val value: Boolean) : OtherSettingsUpdate
    data class DefaultBookTreeUri(val value: String?) : OtherSettingsUpdate
    data class AntiAlias(val value: Boolean) : OtherSettingsUpdate
    data class ReplaceEnableDefault(val value: Boolean) : OtherSettingsUpdate
    data class AutoClearExpired(val value: Boolean) : OtherSettingsUpdate
    data class ShowAddToShelfAlert(val value: Boolean) : OtherSettingsUpdate
    data class ShowMangaUi(val value: Boolean) : OtherSettingsUpdate
    data class WebServiceWakeLock(val value: Boolean) : OtherSettingsUpdate
    data class SourceEditMaxLine(val value: Int) : OtherSettingsUpdate
    data class WebPort(val value: Int) : OtherSettingsUpdate
    data class ProcessText(val value: Boolean) : OtherSettingsUpdate
    data class RecordLog(val value: Boolean) : OtherSettingsUpdate
    data class RecordHeapDump(val value: Boolean) : OtherSettingsUpdate
}
