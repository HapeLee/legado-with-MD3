package io.legado.app.help.config

import android.app.Application
import android.net.Uri
import io.legado.app.domain.gateway.CoverAlbumGateway
import io.legado.app.domain.gateway.ThemePackageSettingsGateway
import io.legado.app.domain.model.CoverAlbum
import io.legado.app.domain.model.CoverAlbumImage
import io.legado.app.domain.model.CoverAlbumImageInput
import io.legado.app.domain.model.CoverAlbumSelection
import io.legado.app.domain.model.settings.ThemeExportData
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.GSON
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ThemePackageManagerRollbackTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
    }

    @Test
    fun `旧JSON导入失败不覆盖当前资源并删除新建文件`() = runBlocking {
        val navDir = File(application.filesDir, "nav_icons").apply {
            deleteRecursively()
            mkdirs()
        }
        val fontDir = File(application.filesDir, "fonts").apply {
            deleteRecursively()
            mkdirs()
        }
        val oldNav = File(navDir, "home.png").apply { writeBytes("old-nav".toByteArray()) }
        val oldFont = File(fontDir, "theme_font.ttf").apply {
            writeBytes("old-font".toByteArray())
        }
        val oldSettings = ThemeExportData(
            appTheme = "old",
            navIconHome = oldNav.absolutePath,
            appFontPath = oldFont.absolutePath,
        )
        val settingsGateway = FailingThemeSettingsGateway(oldSettings)
        val coverGateway = FakeCoverAlbumGateway(File(application.cacheDir, "legacy-covers"))
        val manager = ThemePackageManager(
            context = application,
            coverAlbumUseCase = CoverAlbumUseCase(coverGateway),
            themePackageSettingsGateway = settingsGateway,
        )
        val legacyFile = File(application.cacheDir, "legacy-theme.json").apply {
            writeText(
                GSON.toJson(
                    ThemeExportData(
                        appTheme = "new",
                        assets = mapOf(
                            "navIconHome" to EncoderUtils.base64Encode("new-nav".toByteArray()),
                            "appFontPath" to EncoderUtils.base64Encode("new-font".toByteArray()),
                        ),
                    )
                )
            )
        }

        val result = manager.importLegacyJson(Uri.fromFile(legacyFile))

        assertTrue(result.isFailure)
        assertArrayEquals("old-nav".toByteArray(), oldNav.readBytes())
        assertArrayEquals("old-font".toByteArray(), oldFont.readBytes())
        assertEquals(listOf("home.png"), navDir.listFiles().orEmpty().map(File::getName))
        assertEquals(listOf("theme_font.ttf"), fontDir.listFiles().orEmpty().map(File::getName))
        assertEquals(oldSettings, settingsGateway.current)
    }

    @Test
    fun `应用已保存主题失败会清理本次复制的资源和封面图集`() = runBlocking {
        File(application.getExternalFilesDir(null), "theme_assets").deleteRecursively()
        val packageRoot = File(application.cacheDir, "saved-theme-package").apply {
            deleteRecursively()
            mkdirs()
        }
        File(packageRoot, "cover.png").writeBytes("cover".toByteArray())
        File(packageRoot, "background.png").writeBytes("background".toByteArray())
        val manifest = ThemePackageManifest(
            name = "saved",
            config = ThemeExportData(appTheme = "new"),
            assets = mapOf("background.light" to "background.png"),
            coverAlbums = listOf(
                ThemePackageCoverAlbum(
                    ref = "album-ref",
                    name = "Imported album",
                    lightImages = listOf(ThemePackageCoverImage(path = "cover.png")),
                )
            ),
            coverSelection = ThemePackageCoverSelection(albumRef = "album-ref"),
        )
        val oldSettings = ThemeExportData(appTheme = "old")
        val settingsGateway = FailingThemeSettingsGateway(oldSettings)
        val coverGateway = FakeCoverAlbumGateway(File(application.cacheDir, "saved-covers"))
        val manager = ThemePackageManager(
            context = application,
            coverAlbumUseCase = CoverAlbumUseCase(coverGateway),
            themePackageSettingsGateway = settingsGateway,
        )
        val theme = SavedTheme(
            name = "saved",
            data = ThemeExportData(),
            packageRootPath = packageRoot.absolutePath,
            packageManifest = manifest,
        )

        val result = manager.applySavedTheme(theme)

        assertTrue(result.isFailure)
        assertEquals(listOf("old-album"), coverGateway.albums.value.map { it.id })
        assertEquals("old-album", coverGateway.selection.value.albumId)
        assertEquals(1, coverGateway.deletedAlbumIds.size)
        assertFalse(File(coverGateway.rootDir, coverGateway.deletedAlbumIds.single()).exists())
        val copiedBackground = File(requireNotNull(settingsGateway.attempts.first().bgImageLight))
        assertFalse(copiedBackground.absolutePath.startsWith(packageRoot.absolutePath))
        assertFalse(copiedBackground.exists())
        assertTrue(
            File(application.getExternalFilesDir(null), "theme_assets")
                .listFiles()
                .orEmpty()
                .isEmpty()
        )
    }

    @Test
    fun `取消应用已保存主题也会删除本次导入的封面图集`() = runBlocking {
        val packageRoot = File(application.cacheDir, "cancelled-theme-package").apply {
            deleteRecursively()
            mkdirs()
        }
        File(packageRoot, "cover.png").writeBytes("cover".toByteArray())
        val manifest = ThemePackageManifest(
            name = "cancelled",
            config = ThemeExportData(appTheme = "new"),
            coverAlbums = listOf(
                ThemePackageCoverAlbum(
                    ref = "album-ref",
                    name = "Imported album",
                    lightImages = listOf(ThemePackageCoverImage(path = "cover.png")),
                )
            ),
            coverSelection = ThemePackageCoverSelection(albumRef = "album-ref"),
        )
        val settingsGateway = FailingThemeSettingsGateway(
            initial = ThemeExportData(appTheme = "old"),
            failure = { CancellationException("cancelled") },
        )
        val coverGateway = FakeCoverAlbumGateway(File(application.cacheDir, "cancelled-covers"))
        val manager = ThemePackageManager(
            context = application,
            coverAlbumUseCase = CoverAlbumUseCase(coverGateway),
            themePackageSettingsGateway = settingsGateway,
        )
        val theme = SavedTheme(
            name = "cancelled",
            data = ThemeExportData(),
            packageRootPath = packageRoot.absolutePath,
            packageManifest = manifest,
        )

        val error = runCatching { manager.applySavedTheme(theme) }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(listOf("old-album"), coverGateway.albums.value.map { it.id })
        assertEquals("old-album", coverGateway.selection.value.albumId)
        assertEquals(1, coverGateway.deletedAlbumIds.size)
        assertFalse(File(coverGateway.rootDir, coverGateway.deletedAlbumIds.single()).exists())
    }

    private class FailingThemeSettingsGateway(
        initial: ThemeExportData,
        private val failure: () -> Exception = { IOException("apply failed") },
    ) : ThemePackageSettingsGateway {
        val attempts = mutableListOf<ThemeExportData>()
        var current: ThemeExportData = initial
            private set

        override fun exportCurrent(): ThemeExportData = current

        override suspend fun applyAndAwait(data: ThemeExportData) {
            attempts += data
            throw failure()
        }
    }

    private class FakeCoverAlbumGateway(
        val rootDir: File,
    ) : CoverAlbumGateway {
        private var nextId = 0
        private val oldAlbum = CoverAlbum(
            id = "old-album",
            name = "Old album",
            lightImages = emptyList(),
            darkImages = emptyList(),
        )
        private val albumState = MutableStateFlow(listOf(oldAlbum))
        private val selectionState = MutableStateFlow(CoverAlbumSelection(oldAlbum.id))
        val deletedAlbumIds = mutableListOf<String>()

        override val albums: StateFlow<List<CoverAlbum>> = albumState
        override val selection: StateFlow<CoverAlbumSelection> = selectionState

        override fun selectedImagePaths(isDark: Boolean): List<String> = emptyList()

        override suspend fun createAlbum(name: String): String =
            importAlbum(name, emptyList(), emptyList())

        override suspend fun importAlbum(
            name: String,
            lightImages: List<CoverAlbumImageInput>,
            darkImages: List<CoverAlbumImageInput>,
        ): String {
            val id = "imported-${++nextId}"
            val albumRoot = File(rootDir, id).apply { mkdirs() }
            fun copyImages(inputs: List<CoverAlbumImageInput>, prefix: String) =
                inputs.mapIndexed { index, input ->
                    val target = File(albumRoot, "$prefix-$index-${input.displayName}")
                    input.openStream().use { source ->
                        target.outputStream().use(source::copyTo)
                    }
                    CoverAlbumImage(
                        id = "$prefix-$index",
                        fileName = input.displayName,
                        path = target.absolutePath,
                    )
                }
            albumState.value = albumState.value + CoverAlbum(
                id = id,
                name = name,
                lightImages = copyImages(lightImages, "light"),
                darkImages = copyImages(darkImages, "dark"),
            )
            return id
        }

        override suspend fun renameAlbum(albumId: String, name: String) = Unit

        override suspend fun deleteAlbum(albumId: String) {
            deletedAlbumIds += albumId
            albumState.value = albumState.value.filterNot { it.id == albumId }
            if (selectionState.value.albumId == albumId) {
                selectionState.value = CoverAlbumSelection()
            }
            File(rootDir, albumId).deleteRecursively()
        }

        override suspend fun addImages(
            albumId: String,
            isDark: Boolean,
            images: List<CoverAlbumImageInput>,
        ) = Unit

        override suspend fun removeImage(albumId: String, isDark: Boolean, imageId: String) = Unit

        override suspend fun selectAlbum(albumId: String?) {
            selectionState.value = CoverAlbumSelection(albumId)
        }
    }
}
