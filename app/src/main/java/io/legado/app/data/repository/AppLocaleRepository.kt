package io.legado.app.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.legado.app.constant.PreferKey
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

internal interface AppLocalePlatform {
    fun getApplicationLocales(): LocaleListCompat
    fun setApplicationLocales(locales: LocaleListCompat)
}

private object AppCompatLocalePlatform : AppLocalePlatform {
    override fun getApplicationLocales(): LocaleListCompat =
        AppCompatDelegate.getApplicationLocales()

    override fun setApplicationLocales(locales: LocaleListCompat) {
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

class AppLocaleRepository internal constructor(
    private val platform: AppLocalePlatform = AppCompatLocalePlatform,
    private val persistLanguage: (String) -> Unit = {
        AppConfigStore.putString(PreferKey.language, it)
    },
    readPersistedLanguage: () -> String? = {
        AppConfigStore.getString(PreferKey.language)
    },
) : AppLocaleGateway {

    private var mirroredLanguage = normalizeLanguage(readPersistedLanguage())
    private val initialPlatformLocales = platform.getApplicationLocales()
    private val _language = MutableStateFlow(
        if (initialPlatformLocales.isEmpty) {
            mirroredLanguage
        } else {
            languageForLocaleList(initialPlatformLocales)
        }
    )
    override val language = _language.asStateFlow()
    override val currentLanguage: String
        get() = _language.value

    override fun setLanguage(language: String) {
        val normalized = normalizeLanguage(language)
        publish(normalized)
        val locales = localeListForLanguage(normalized)
        if (platform.getApplicationLocales() != locales) {
            platform.setApplicationLocales(locales)
        }
    }

    override fun synchronizeFromPlatform() {
        publish(languageForLocaleList(platform.getApplicationLocales()))
    }

    override fun migrateLegacyLanguage(language: String) {
        val platformLocales = platform.getApplicationLocales()
        if (platformLocales.isEmpty && normalizeLanguage(language) != "auto") {
            setLanguage(language)
        } else {
            synchronizeFromPlatform()
        }
    }

    private fun publish(language: String) {
        val normalized = normalizeLanguage(language)
        if (_language.value != normalized) {
            _language.value = normalized
        }
        if (mirroredLanguage != normalized) {
            mirroredLanguage = normalized
            persistLanguage(normalized)
        }
    }
}

internal fun normalizeLanguage(language: String?): String = when (language) {
    "zh", "tw", "en" -> language
    else -> "auto"
}

internal fun localeListForLanguage(language: String?): LocaleListCompat =
    when (normalizeLanguage(language)) {
        "zh" -> LocaleListCompat.create(Locale.SIMPLIFIED_CHINESE)
        "tw" -> LocaleListCompat.create(Locale.TRADITIONAL_CHINESE)
        "en" -> LocaleListCompat.create(Locale.ENGLISH)
        else -> LocaleListCompat.getEmptyLocaleList()
    }

internal fun languageForLocaleList(locales: LocaleListCompat): String {
    if (locales.isEmpty) return "auto"
    val locale = locales[0] ?: return "auto"
    return when {
        locale.language == Locale.CHINESE.language &&
            (locale.country.equals("TW", ignoreCase = true) ||
                locale.country.equals("HK", ignoreCase = true) ||
                locale.script.equals("Hant", ignoreCase = true)) -> "tw"
        locale.language == Locale.CHINESE.language -> "zh"
        locale.language == Locale.ENGLISH.language -> "en"
        else -> "auto"
    }
}
