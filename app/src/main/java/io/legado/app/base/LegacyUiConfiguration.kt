package io.legado.app.base

import android.content.res.Configuration

enum class LegacyUiConfigurationPolicy {
    HotApply,
    RebindViewTree,
    ControlledRecreate,
    ApplyOnNextOpen,
}

internal fun hasPlatformNightModeChanged(
    themeMode: String,
    previousUiMode: Int?,
    newUiMode: Int,
): Boolean = themeMode == "0" && previousUiMode != null &&
    (previousUiMode and Configuration.UI_MODE_NIGHT_MASK) !=
    (newUiMode and Configuration.UI_MODE_NIGHT_MASK)
