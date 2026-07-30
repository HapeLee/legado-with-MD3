package io.legado.app.data.repository

import android.app.Application
import androidx.core.os.LocaleListCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// application = Application::class 与其余 Robolectric 测试一致：不指定的话 Robolectric 会从
// manifest 取真的 io.legado.app.App，App.onCreate() 要拉 Koin/DB/Cronet，单测里必炸。
// 开 isIncludeAndroidResources 之前 manifest 不被读取，这里才侥幸没暴露。
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AppLocaleRepositoryTest {

    @Test
    fun initialization_keepsPersistedLanguageWhenPlatformLocalesAreNotReady() {
        val repository = AppLocaleRepository(
            FakeAppLocalePlatform(),
            { },
            { "zh" },
        )

        assertEquals("zh", repository.currentLanguage)
    }

    @Test
    fun setLanguage_updatesPlatformStateAndMirror() {
        val platform = FakeAppLocalePlatform()
        val persisted = mutableListOf<String>()
        val repository = AppLocaleRepository(
            platform,
            persisted::add,
            { persisted.lastOrNull() },
        )

        repository.setLanguage("en")

        assertEquals("en", repository.currentLanguage)
        assertEquals("en", languageForLocaleList(platform.locales))
        assertEquals("en", persisted.last())
    }

    @Test
    fun synchronizeFromPlatform_updatesExternalLanguageSelection() {
        val platform = FakeAppLocalePlatform()
        val persisted = mutableListOf<String>()
        val repository = AppLocaleRepository(
            platform,
            persisted::add,
            { persisted.lastOrNull() },
        )
        platform.locales = localeListForLanguage("tw")

        repository.synchronizeFromPlatform()

        assertEquals("tw", repository.currentLanguage)
        assertEquals("tw", persisted.last())
    }

    @Test
    fun synchronizeFromPlatform_keepsExternalResetToAutoMeaningful() {
        val platform = FakeAppLocalePlatform(localeListForLanguage("zh"))
        val persisted = mutableListOf<String>()
        val repository = AppLocaleRepository(
            platform,
            persisted::add,
            { "zh" },
        )
        platform.locales = LocaleListCompat.getEmptyLocaleList()

        repository.synchronizeFromPlatform()

        assertEquals("auto", repository.currentLanguage)
        assertEquals("auto", persisted.last())
    }

    @Test
    fun migration_doesNotOverwriteExistingPlatformLocale() {
        val platform = FakeAppLocalePlatform(localeListForLanguage("zh"))
        val repository = AppLocaleRepository(platform, { }, { "en" })

        repository.migrateLegacyLanguage("en")

        assertEquals("zh", repository.currentLanguage)
        assertEquals("zh", languageForLocaleList(platform.locales))
    }

    @Test
    fun synchronizeUnchangedLanguage_doesNotWriteMirrorAgain() {
        val persisted = mutableListOf<String>()
        val repository = AppLocaleRepository(
            FakeAppLocalePlatform(),
            persisted::add,
            { "auto" },
        )

        repository.synchronizeFromPlatform()

        assertEquals(emptyList<String>(), persisted)
    }

    private class FakeAppLocalePlatform(
        var locales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList(),
    ) : AppLocalePlatform {
        override fun getApplicationLocales(): LocaleListCompat = locales

        override fun setApplicationLocales(locales: LocaleListCompat) {
            this.locales = locales
        }
    }
}
