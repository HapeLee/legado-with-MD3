package io.legado.app.ui.welcome

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.lib.prefs.ThemeCardPreference
import io.legado.app.lib.prefs.ThemeModePreference

class ThemeFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_strat_theme, rootKey)

        findPreference<ThemeModePreference>(PreferKey.themeMode)?.let {
            it.setOnPreferenceChangeListener { _, _ ->
                true
            }
        }

        findPreference<ThemeCardPreference>(PreferKey.themePref)?.let {
            it.setOnPreferenceChangeListener { _, _ ->
                true
            }
        }
    }
}
