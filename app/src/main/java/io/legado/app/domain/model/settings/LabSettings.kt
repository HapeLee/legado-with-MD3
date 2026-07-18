package io.legado.app.domain.model.settings

data class LabSettings(
    val enabled: Boolean = false,
    val eInkDisplay: Boolean = false,
    val eyeProtection: Boolean = false,
)
