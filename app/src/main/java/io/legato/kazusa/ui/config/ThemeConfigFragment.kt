package io.legato.kazusa.ui.config

//import io.legado.app.lib.theme.primaryColor
import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
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
import io.legato.kazusa.lib.prefs.ImagePreviewPreference
import io.legato.kazusa.lib.prefs.NameListPreference
import io.legato.kazusa.lib.prefs.ThemeCardPreference
import io.legato.kazusa.lib.prefs.ThemeModePreference
import io.legato.kazusa.lib.theme.ThemeStore
import io.legato.kazusa.lib.theme.primaryColor
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
import io.legato.kazusa.utils.restart
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

        private val requestCodeColorImage = 123

        private val selectImage = registerForActivityResult(SelectImageContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeBgLight -> setBgFromUri(uri, PreferKey.bgImage) {
                    upTheme(false)
                }

                requestCodeBgDark -> setBgFromUri(uri, PreferKey.bgImageN) {
                    upTheme(true)
                }

                requestCodeColorImage -> setBgFromUri(uri, PreferKey.colorImage) {

                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_config_theme)

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

        val themePref = findPreference<ThemeCardPreference>(PreferKey.themePref)
        val colorImage = findPreference<ImagePreviewPreference>(PreferKey.colorImage)
        val colorPrimary = findPreference<ColorPreference>("colorPrimary")
        val customMode = findPreference<NameListPreference>("customMode")
        val currentTheme = getPrefString("app_theme")

        colorPrimary?.isVisible = currentTheme == "11"
        colorImage?.isVisible = currentTheme == "11"
        customMode?.isVisible = currentTheme == "11"

        themePref?.let {
            it.setOnPreferenceChangeListener { _, _ ->
                true
            }
        }

        themePref?.setOnPreferenceChangeListener { _, newValue ->
            colorPrimary?.isVisible = newValue == "11"
            colorImage?.isVisible = currentTheme == "11"
            customMode?.isVisible = newValue == "11"
            true
        }

        val hasColorImage = !getPrefString(PreferKey.colorImage).isNullOrBlank()
        colorPrimary?.isEnabled = !hasColorImage
        if (hasColorImage)
        {
            upPreferenceSummary("colorPrimary", getString(R.string.seed_photo_alart))
            upPreferenceSummary(PreferKey.colorImage, getString(R.string.click_to_delete))
        }
        if (!getPrefString(PreferKey.bgImage).isNullOrBlank())
            upPreferenceSummary(PreferKey.bgImage, getString(R.string.click_to_delete))
        if (!getPrefString(PreferKey.bgImageN).isNullOrBlank())
            upPreferenceSummary(PreferKey.bgImageN, getString(R.string.click_to_delete))
        upPreferenceSummary(PreferKey.bgImage)
        upPreferenceSummary(PreferKey.bgImageN)
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

            PreferKey.customMode -> handleRestartRequired()

            PreferKey.pureBlack -> {
                Handler(Looper.getMainLooper()).postDelayed({
                    recreateActivities()
                }, 100)
            }

            PreferKey.cPrimary -> {
                val color = getPrefInt(key, ThemeStore.primaryColor(requireContext()))
                ThemeStore.editTheme(requireContext())
                    .primaryColor(color)
                    .apply()
                DynamicColors.applyToActivitiesIfAvailable(requireContext().applicationContext as Application, DynamicColorsOptions.Builder()
                    .setContentBasedSource(requireContext().primaryColor)
                    .build())
                Handler(Looper.getMainLooper()).postDelayed({
                    recreateActivities()
                }, 100)
            }

            PreferKey.cBackground,
            PreferKey.cBBackground -> {
                upTheme(false)
            }

            //PreferKey.cNPrimary,

            PreferKey.cNBackground,
            PreferKey.cNBBackground -> {
                upTheme(true)
            }

            PreferKey.themeMode -> {
                //recreateActivities()
            }

            PreferKey.colorImage -> handleRestartRequired()

            PreferKey.showDiscovery, PreferKey.showRss,
            PreferKey.showBottomView, PreferKey.tabletInterface,
            PreferKey.labelVisibilityMode -> postEvent(EventBus.NOTIFY_MAIN, true)
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
            "colorImage" -> selectBgAction(null)

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

        private fun selectBgAction(isNight: Boolean?) {
            val bgKey = when (isNight) {
                true -> PreferKey.bgImageN
                false -> PreferKey.bgImage
                null -> PreferKey.colorImage
            }
            val blurringKey = when (isNight) {
                true -> PreferKey.bgImageNBlurring
                false -> PreferKey.bgImageBlurring
                else -> null
            }

            val actions = mutableListOf<String>()

            if (isNight != null) {
                actions.add(getString(R.string.background_image_blurring))
            }

            actions.add(getString(R.string.select_image))

            if (!getPrefString(bgKey).isNullOrEmpty()) {
                actions.add(getString(R.string.delete))
            }

            context?.selector(items = actions) { _, i ->
                when {
                    isNight != null && i == 0 -> {
                        alertImageBlurring(blurringKey!!) {
                            upTheme(isNight)
                        }
                    }

                    (isNight == null && i == 0) || (isNight != null && i == 1) -> {
                        when (isNight) {
                            true -> selectImage.launch(requestCodeBgDark)
                            false -> selectImage.launch(requestCodeBgLight)
                            null -> selectImage.launch(requestCodeColorImage)
                        }
                    }

                    (isNight == null && i == 1) || (isNight != null && i == 2) -> {
                        removePref(bgKey)
                        if (isNight != null) {
                            upTheme(isNight)
                        }
                    }
                }
            }
        }

        private fun handleRestartRequired() {
            alert(getString(R.string.restart_required_message)) {
                okButton {
                    Handler(Looper.getMainLooper()).postDelayed({
                        requireContext().restart()
                    }, 100)
                }
                cancelButton {
                    toastOnUi(R.string.restart_later_message)
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