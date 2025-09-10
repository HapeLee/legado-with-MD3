package io.legato.kazusa.ui.config

//import io.legado.app.lib.theme.primaryColor
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.legato.kazusa.R
import io.legato.kazusa.base.AppContextWrapper
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.constant.PreferKey
import io.legato.kazusa.databinding.DialogEditTextBinding
import io.legato.kazusa.databinding.DialogImageBlurringBinding
import io.legato.kazusa.help.LauncherIconHelp
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.config.ThemeConfig
import io.legato.kazusa.lib.dialogs.alert
import io.legato.kazusa.lib.dialogs.selector
import io.legato.kazusa.lib.prefs.ColorPreference
import io.legato.kazusa.lib.prefs.ThemeCardPreference
import io.legato.kazusa.lib.prefs.ThemeModePreference
import io.legato.kazusa.ui.widget.number.NumberPickerDialog
import io.legato.kazusa.ui.widget.seekbar.SeekBarChangeListener
import io.legato.kazusa.utils.ColorUtils
import io.legato.kazusa.utils.FileUtils
import io.legato.kazusa.utils.MD5Utils
import io.legato.kazusa.utils.SelectImageContract
import io.legato.kazusa.utils.externalFiles
import io.legato.kazusa.utils.getPrefInt
import io.legato.kazusa.utils.getPrefString
import io.legato.kazusa.utils.inputStream
import io.legato.kazusa.utils.postEvent
import io.legato.kazusa.utils.putPrefInt
import io.legato.kazusa.utils.putPrefString
import io.legato.kazusa.utils.readUri
import io.legato.kazusa.utils.removePref
import io.legato.kazusa.utils.startActivity
import io.legato.kazusa.utils.toastOnUi
import splitties.init.appCtx
import java.io.FileOutputStream


@Suppress("SameParameterValue")
class ThemeConfigFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener
    //MenuProvider
    {

    private val requestCodeBgLight = 121
    private val requestCodeBgDark = 122
    private val selectImage = registerForActivityResult(SelectImageContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeBgLight -> setBgFromUri(uri, PreferKey.bgImage) {
                    upTheme(false)
                }

                requestCodeBgDark -> setBgFromUri(uri, PreferKey.bgImageN) {
                    upTheme(true)
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_theme)

        upPreferenceSummary(PreferKey.bgImage, getPrefString(PreferKey.bgImage))
        upPreferenceSummary(PreferKey.bgImageN, getPrefString(PreferKey.bgImageN))
        upPreferenceSummary(PreferKey.fontScale)

        findPreference<ColorPreference>(PreferKey.cBackground)?.let {
            it.onSaveColor = { color ->
                if (!ColorUtils.isColorLight(color)) {
                    toastOnUi(R.string.day_background_too_dark)
                    true
                } else {
                    false
                }
            }
        }

        findPreference<ColorPreference>(PreferKey.cNBackground)?.let {
            it.onSaveColor = { color ->
                if (ColorUtils.isColorLight(color)) {
                    toastOnUi(R.string.night_background_too_light)
                    true
                } else {
                    false
                }
            }
        }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.theme_setting)
        //listView.setEdgeEffectColor(primaryColor)
        //activity?.addMenuProvider(this, viewLifecycleOwner)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences ?: return
        when (key) {
            PreferKey.launcherIcon -> LauncherIconHelp.changeIcon(getPrefString(key))
            //PreferKey.transparentStatusBar -> recreateActivities()
            //PreferKey.immNavigationBar -> recreateActivities()

            PreferKey.themePref -> {
                //recreateActivities()
            }

            PreferKey.pureBlack -> {
                recreateActivities()
            }

            PreferKey.cPrimary,
            PreferKey.cAccent,
            PreferKey.cBackground,
            PreferKey.cBBackground -> {
                upTheme(false)
            }

            PreferKey.cNPrimary,
            PreferKey.cNAccent,
            PreferKey.cNBackground,
            PreferKey.cNBBackground -> {
                upTheme(true)
            }

            PreferKey.themeMode -> {
                //recreateActivities()
            }

            PreferKey.bgImage,
            PreferKey.bgImageN -> {
                upPreferenceSummary(key, getPrefString(key))
            }

            PreferKey.tabletInterface -> {
                recreateActivities()
            }
        }

    }

    @SuppressLint("PrivateResource")
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (val key = preference.key) {

            PreferKey.fontScale -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.font_scale))
                .setMaxValue(16)
                .setMinValue(8)
                .setValue(10)
                .setCustomButton((R.string.btn_default_s)) {
                    putPrefInt(PreferKey.fontScale, 0)
                    recreateActivities()
                }
                .show {
                    putPrefInt(PreferKey.fontScale, it)
                    recreateActivities()
                }

            PreferKey.bgImage -> selectBgAction(false)
            PreferKey.bgImageN -> selectBgAction(true)
            "themeList" -> ThemeListDialog().show(childFragmentManager, "themeList")
            "saveDayTheme",
            "saveNightTheme" -> alertSaveTheme(key)

            "coverConfig" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.COVER_CONFIG)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    @SuppressLint("InflateParams")
    private fun alertSaveTheme(key: String) {
        alert(R.string.theme_name) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "name"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { themeName ->
                    when (key) {
                        "saveDayTheme" -> {
                            ThemeConfig.saveDayTheme(requireContext(), themeName)
                        }

                        "saveNightTheme" -> {
                            ThemeConfig.saveNightTheme(requireContext(), themeName)
                        }
                    }
                }
            }
            cancelButton()
        }
    }

    private fun selectBgAction(isNight: Boolean) {
        val bgKey = if (isNight) PreferKey.bgImageN else PreferKey.bgImage
        val blurringKey = if (isNight) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring
        val actions = arrayListOf(
            getString(R.string.background_image_blurring),
            getString(R.string.select_image)
        )
        if (!getPrefString(bgKey).isNullOrEmpty()) {
            actions.add(getString(R.string.delete))
        }
        context?.selector(items = actions) { _, i ->
            when (i) {
                0 -> alertImageBlurring(blurringKey) {
                    upTheme(isNight)
                }

                1 -> {
                    if (isNight) {
                        selectImage.launch(requestCodeBgDark)
                    } else {
                        selectImage.launch(requestCodeBgLight)
                    }
                }

                2 -> {
                    removePref(bgKey)
                    upTheme(isNight)
                }
            }
        }
    }

    private fun alertImageBlurring(preferKey: String, success: () -> Unit) {
        alert(R.string.background_image_blurring) {
            val alertBinding = DialogImageBlurringBinding.inflate(layoutInflater).apply {
                getPrefInt(preferKey, 0).let {
                    seekBar.progress = it
                    textViewValue.text = it.toString()
                }
                seekBar.setOnSeekBarChangeListener(object : SeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        textViewValue.text = progress.toString()
                    }
                })
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.seekBar.progress.let {
                    putPrefInt(preferKey, it)
                    success.invoke()
                }
            }
            cancelButton()
        }
    }

    private fun upTheme(isNightTheme: Boolean) {
        if (AppConfig.isNightTheme == isNightTheme) {
            listView.post {
                //ThemeConfig.applyTheme(requireContext())
                recreateActivities()
            }
        }
    }

    private fun recreateActivities() {
        postEvent(EventBus.RECREATE, "")
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String? = null) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            //PreferKey.barElevation -> preference.summary =
            //    getString(R.string.bar_elevation_s, value)

            PreferKey.fontScale -> {
                val fontScale = AppContextWrapper.getFontScale(requireContext())
                preference.summary = getString(R.string.font_scale_summary, fontScale)
            }

            PreferKey.bgImage,
            PreferKey.bgImageN -> preference.summary = if (value.isNullOrBlank()) {
                getString(R.string.select_image)
            } else {
                value
            }

            else -> preference.summary = value
        }
    }

    private fun setBgFromUri(uri: Uri, preferenceKey: String, success: () -> Unit) {
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = fileDoc.name.substringAfterLast(".")
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + ".$suffix"
                }
                file = FileUtils.createFileIfNotExist(file, preferenceKey, fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                putPrefString(preferenceKey, file.absolutePath)
                success()
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

}