package io.legado.app.ui.book.read.config

//import io.legado.app.lib.theme.backgroundColor
//import io.legado.app.lib.theme.primaryColor
// 【新增引用】为了显示清理成功的提示
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.preference.ListPreference
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.base.BasePrefDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.help.IntentHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.prefs.SwitchPreference
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.StringUtils
import io.legado.app.utils.TTSCacheUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi

class ReadAloudConfigDialog : BasePrefDialogFragment() {
    private val readAloudPreferTag = "readAloudPreferTag"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = LinearLayout(requireContext())
        //view.setBackgroundColor(requireContext().backgroundColor)
        view.id = R.id.tag1
        container?.addView(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var preferenceFragment = childFragmentManager.findFragmentByTag(readAloudPreferTag)
        if (preferenceFragment == null) preferenceFragment = ReadAloudPreferenceFragment()
        childFragmentManager.beginTransaction()
            .replace(view.id, preferenceFragment, readAloudPreferTag)
            .commit()
    }

    class ReadAloudPreferenceFragment : PreferenceFragment(),
        SpeakEngineDialog.CallBack,
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val speakEngineSummary: String
            get() {
                val ttsEngine = ReadAloud.ttsEngine
                    ?: return getString(R.string.system_tts)
                if (StringUtils.isNumeric(ttsEngine)) {
                    return appDb.httpTTSDao.getName(ttsEngine.toLong())
                        ?: getString(R.string.system_tts)
                }
                return GSON.fromJsonObject<SelectItem<String>>(ttsEngine).getOrNull()?.title
                    ?: getString(R.string.system_tts)
            }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.pref_config_aloud)
            upSpeakEngineSummary()
            upPreferenceSummary(PreferKey.audioPreDownloadNum)
            upPreferenceSummary(PreferKey.audioCacheCleanTime)
            findPreference<SwitchPreference>(PreferKey.pauseReadAloudWhilePhoneCalls)?.let {
                it.isEnabled = AppConfig.ignoreAudioFocus
            }

            findPreference<Preference>("clear_cache")?.let {
                it.summary = getString(R.string.clear_cache)
                it.setOnPreferenceClickListener {
                    TTSCacheUtils.clearTtsCache()
                    toastOnUi("音频缓存已清理")
                    true
                }
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            //listView.setEdgeEffectColor(primaryColor)
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            when (preference.key) {
                PreferKey.audioPreDownloadNum -> {
                    NumberPickerDialog(requireContext())
                        .setTitle(getString(R.string.read_aloud_preload))
                        .setMaxValue(50)
                        .setMinValue(0)
                        .setValue(10)
                        .setCustomButton((R.string.btn_default_s)) {
                            putPrefInt(PreferKey.audioPreDownloadNum, 10)
                            upPreferenceSummary(PreferKey.audioPreDownloadNum)
                        }
                        .show {
                            putPrefInt(PreferKey.audioPreDownloadNum, it)
                            upPreferenceSummary(PreferKey.audioPreDownloadNum)
                        }
                }

                PreferKey.audioCacheCleanTime -> {
                    NumberPickerDialog(requireContext())
                        .setTitle(getString(R.string.audio_cache_clean_time))
                        .setMaxValue(50)
                        .setMinValue(0)
                        .setValue(1)
                        .setCustomButton((R.string.btn_default_s)) {
                            putPrefInt(PreferKey.audioCacheCleanTime, 10)
                            upPreferenceSummary(PreferKey.audioCacheCleanTime)
                        }
                        .show {
                            putPrefInt(PreferKey.audioCacheCleanTime, it)
                            upPreferenceSummary(PreferKey.audioCacheCleanTime)
                        }
                }

                PreferKey.ttsEngine -> showDialogFragment(SpeakEngineDialog())
                "sysTtsConfig" -> IntentHelp.openTTSSetting()
            }
            return super.onPreferenceTreeClick(preference)
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                PreferKey.readAloudByPage, PreferKey.streamReadAloudAudio -> {
                    if (BaseReadAloudService.isRun) {
                        postEvent(EventBus.MEDIA_BUTTON, false)
                    }
                }

                PreferKey.ignoreAudioFocus -> {
                    findPreference<SwitchPreference>(PreferKey.pauseReadAloudWhilePhoneCalls)?.let {
                        it.isEnabled = AppConfig.ignoreAudioFocus
                    }
                }
            }
        }

        private fun upPreferenceSummary(preference: Preference?, value: String) {
            when (preference) {
                is ListPreference -> {
                    val index = preference.findIndexOfValue(value)
                    preference.summary = if (index >= 0) preference.entries[index] else null
                }


                else -> {
                    preference?.summary = value
                }
            }
        }

        private fun upPreferenceSummary(preferenceKey: String, value: String? = null) {
            val preference = findPreference<Preference>(preferenceKey) ?: return
            when (preferenceKey) {
                PreferKey.audioPreDownloadNum -> {
                    preference.summary = getString(
                        R.string.read_aloud_preload_summary,
                        AppConfig.audioPreDownloadNum
                    )
                }

                PreferKey.audioCacheCleanTime -> {
                    preference.summary = getString(
                        R.string.audio_cache_clean_time_summary,
                        AppConfig.audioCacheCleanTimeOrgin
                    )
                }

                else -> preference.summary = value
            }
        }

        override fun upSpeakEngineSummary() {
            upPreferenceSummary(
                findPreference(PreferKey.ttsEngine),
                speakEngineSummary
            )
        }
    }
}
