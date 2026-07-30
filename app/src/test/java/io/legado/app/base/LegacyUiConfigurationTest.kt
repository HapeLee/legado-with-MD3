package io.legado.app.base

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyUiConfigurationTest {

    @Test
    fun followSystem_detectsPlatformNightModeChange() {
        assertTrue(
            hasPlatformNightModeChanged(
                themeMode = "0",
                previousUiMode = Configuration.UI_MODE_NIGHT_YES,
                newUiMode = Configuration.UI_MODE_NIGHT_NO,
            )
        )
    }

    @Test
    fun fixedDark_ignoresPlatformNightModeChange() {
        assertFalse(
            hasPlatformNightModeChanged(
                themeMode = "2",
                previousUiMode = Configuration.UI_MODE_NIGHT_YES,
                newUiMode = Configuration.UI_MODE_NIGHT_NO,
            )
        )
    }

    @Test
    fun missingPreviousConfiguration_isNotAChange() {
        assertFalse(
            hasPlatformNightModeChanged(
                themeMode = "0",
                previousUiMode = null,
                newUiMode = Configuration.UI_MODE_NIGHT_NO,
            )
        )
    }
}
