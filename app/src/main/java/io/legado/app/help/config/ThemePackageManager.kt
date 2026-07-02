package io.legado.app.help.config

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.Keep
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.domain.model.CoverAlbumImageInput
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ThemePackageManager(
    private val context: Context,
    private val coverAlbumUseCase: CoverAlbumUseCase,
) {

    companion object {
        const val FILE_EXTENSION = "zip"
        private const val FORMAT_VERSION = 1
        private const val MANIFEST_PATH = "manifest.json"
        private const val MAX_ENTRY_COUNT = 4_096
        private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

        private const val ASSET_BACKGROUND_LIGHT = "background.light"
        private const val ASSET_BACKGROUND_DARK = "background.dark"
        private const val ASSET_NAV_HOME = "navigation.home"
        private const val ASSET_NAV_BOOKSHELF = "navigation.bookshelf"
        private const val ASSET_NAV_EXPLORE = "navigation.explore"
        private const val ASSET_NAV_RSS = "navigation.rss"
        private const val ASSET_NAV_MY = "navigation.my"
        private const val ASSET_FONT = "font.app"
    }

    suspend fun exportPackage(
        uri: Uri,
        themeName: String? = null,
        themeData: ThemeExportData? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runSuspendCatching {
            val rawConfig = themeData ?: ThemeImportExport.exportFromCurrent(
                includeEmbeddedAssets = false
            )
            context.contentResolver.openOutputStream(uri)?.use { output ->
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    val assetEntries = exportAssets(zip, rawConfig)
                    val coverData = exportCoverAlbums(
                        zip = zip,
                        preferredAlbumId = rawConfig.selectedCoverAlbumId,
                    )
                    val manifest = ThemePackageManifest(
                        formatVersion = FORMAT_VERSION,
                        name = themeName,
                        config = rawConfig.toPortableConfig(),
                        assets = assetEntries,
                        coverAlbums = coverData.albums,
                        coverSelection = coverData.selection,
                    )
                    zip.writeEntry(
                        MANIFEST_PATH,
                        GSON.toJson(manifest).byteInputStream(),
                    )
                }
            } ?: error("无法创建主题包")
        }
    }

    suspend fun importPackage(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runSuspendCatching {
            val tempRoot = File(context.cacheDir, "theme_import/${UUID.randomUUID()}")
            val importedAlbumIds = mutableListOf<String>()
            val copiedAssets = mutableListOf<File>()
            try {
                extractPackage(uri, tempRoot)
                val manifestFile = resolvePackageFile(tempRoot, MANIFEST_PATH)
                val manifestJson = manifestFile.readText()
                val manifestRoot = JsonParser.parseString(manifestJson).asJsonObject
                require(manifestRoot.has("formatVersion") && manifestRoot.has("config")) {
                    "主题包清单不完整"
                }
                val manifest = GSON.fromJson(manifestJson, ThemePackageManifest::class.java)
                require(manifest.formatVersion == FORMAT_VERSION) {
                    "不支持的主题包版本: ${manifest.formatVersion}"
                }

                val localAssets = importAssets(
                    root = tempRoot,
                    entries = manifest.assets,
                    copiedFiles = copiedAssets,
                )
                val albumIdMap = importCoverAlbums(
                    root = tempRoot,
                    albums = manifest.coverAlbums,
                    importedIds = importedAlbumIds,
                )
                val selectedAlbumId = resolveAlbumRef(
                    manifest.coverSelection.albumRef,
                    albumIdMap,
                )
                ThemeImportExport.applyToThemeConfig(
                    manifest.config.copy(
                        bgImageLight = localAssets[ASSET_BACKGROUND_LIGHT],
                        bgImageDark = localAssets[ASSET_BACKGROUND_DARK],
                        navIconHome = localAssets[ASSET_NAV_HOME].orEmpty(),
                        navIconBookshelf = localAssets[ASSET_NAV_BOOKSHELF].orEmpty(),
                        navIconExplore = localAssets[ASSET_NAV_EXPLORE].orEmpty(),
                        navIconRss = localAssets[ASSET_NAV_RSS].orEmpty(),
                        navIconMy = localAssets[ASSET_NAV_MY].orEmpty(),
                        appFontPath = localAssets[ASSET_FONT],
                        coverDefaultImage = "",
                        coverDefaultImageDark = "",
                        assets = null,
                    )
                )
                coverAlbumUseCase.selectAlbum(selectedAlbumId)
                saveImportedTheme(
                    name = importedThemeName(uri, manifest.name),
                    selectedCoverAlbumId = selectedAlbumId,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                importedAlbumIds.forEach { id ->
                    runCatching { coverAlbumUseCase.deleteAlbum(id) }
                }
                copiedAssets.forEach(File::delete)
                throw error
            } finally {
                tempRoot.deleteRecursively()
            }
        }
    }

    suspend fun importLegacyJson(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runSuspendCatching {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: error("无法读取旧主题配置")
            val appliedAssets = ThemeImportExport.importFromJsonWithAssets(json)
                ?: error("旧主题配置格式无效或字段已被混淆")
            val name = importedThemeName(uri)
            val selectedCoverAlbumId = importLegacyCoverAlbums(
                albumName = name,
                appliedAssets = appliedAssets,
            )
            saveImportedTheme(
                name = name,
                selectedCoverAlbumId = selectedCoverAlbumId,
            )
        }
    }

    fun saveTheme(
        name: String,
        data: ThemeExportData = ThemeImportExport.exportFromCurrent(),
    ): SavedTheme {
        val selectedCoverAlbumId = data.selectedCoverAlbumId
            ?: coverAlbumUseCase.selection.value.albumId
        return ThemeImportExport.saveCurrentAsTheme(
            name = name,
            data = data.copy(selectedCoverAlbumId = selectedCoverAlbumId),
        )
    }

    suspend fun applySavedTheme(theme: SavedTheme): Result<Unit> =
        withContext(Dispatchers.IO) {
            runSuspendCatching {
                val savedAlbumId = theme.data.selectedCoverAlbumId
                    ?.takeIf { id -> coverAlbumUseCase.albums.value.any { it.id == id } }
                val appliedAssets = ThemeImportExport.applyToThemeConfig(
                    data = theme.data,
                    applyEmbeddedCoverAssets = savedAlbumId == null,
                )
                val selectedCoverAlbumId = if (savedAlbumId != null) {
                    coverAlbumUseCase.selectAlbum(savedAlbumId)
                    savedAlbumId
                } else {
                    importLegacyCoverAlbums(
                        albumName = theme.name,
                        appliedAssets = appliedAssets,
                    )
                }
                if (selectedCoverAlbumId != theme.data.selectedCoverAlbumId) {
                    ThemeImportExport.saveCurrentAsTheme(
                        name = theme.name,
                        data = theme.data.copy(
                            selectedCoverAlbumId = selectedCoverAlbumId,
                        ),
                    )
                }
            }
        }

    private fun exportAssets(
        zip: ZipOutputStream,
        config: ThemeExportData,
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val sources = listOf(
            AssetSource(ASSET_BACKGROUND_LIGHT, config.bgImageLight, "assets/background/light"),
            AssetSource(ASSET_BACKGROUND_DARK, config.bgImageDark, "assets/background/dark"),
            AssetSource(ASSET_NAV_HOME, config.navIconHome, "assets/navigation/home"),
            AssetSource(
                ASSET_NAV_BOOKSHELF,
                config.navIconBookshelf,
                "assets/navigation/bookshelf",
            ),
            AssetSource(ASSET_NAV_EXPLORE, config.navIconExplore, "assets/navigation/explore"),
            AssetSource(ASSET_NAV_RSS, config.navIconRss, "assets/navigation/rss"),
            AssetSource(ASSET_NAV_MY, config.navIconMy, "assets/navigation/my"),
            AssetSource(ASSET_FONT, config.appFontPath, "assets/fonts/app"),
        )
        sources.forEach { source ->
            val path = source.sourcePath?.takeIf(String::isNotBlank) ?: return@forEach
            val extension = sourceExtension(path, source.key)
            val entryPath = "${source.entryBase}.$extension"
            openSource(path).use { input ->
                zip.writeEntry(entryPath, input)
            }
            result[source.key] = entryPath
        }
        return result
    }

    private fun exportCoverAlbums(
        zip: ZipOutputStream,
        preferredAlbumId: String?,
    ): ExportedCoverData {
        val albumsById = coverAlbumUseCase.albums.value.associateBy { it.id }
        val selectedAlbumId = preferredAlbumId
            ?.takeIf(albumsById::containsKey)
            ?: coverAlbumUseCase.selection.value.albumId
        val selectedIds = listOfNotNull(selectedAlbumId)
        val refById = selectedIds.associateWith { "album_0" }
        val exportedAlbums = selectedIds.mapNotNull { albumId ->
            val album = albumsById[albumId] ?: return@mapNotNull null
            val albumRef = refById.getValue(albumId)
            fun exportImages(
                group: String,
                images: List<io.legado.app.domain.model.CoverAlbumImage>,
            ) = images.mapIndexed { index, image ->
                val file = File(image.path)
                require(file.isFile) { "图集图片不存在: ${image.path}" }
                val extension = file.extension
                    .lowercase()
                    .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                    ?: "img"
                val entryPath = "cover-albums/$albumRef/$group/image_$index.$extension"
                file.inputStream().use { input ->
                    zip.writeEntry(entryPath, input)
                }
                ThemePackageCoverImage(path = entryPath)
            }
            ThemePackageCoverAlbum(
                ref = albumRef,
                name = album.name,
                lightImages = exportImages("light", album.lightImages),
                darkImages = exportImages("dark", album.darkImages),
            )
        }
        return ExportedCoverData(
            albums = exportedAlbums,
            selection = ThemePackageCoverSelection(
                albumRef = selectedAlbumId?.let(refById::get),
            ),
        )
    }

    private fun importAssets(
        root: File,
        entries: Map<String, String>,
        copiedFiles: MutableList<File>,
    ): Map<String, String> {
        return entries.mapNotNull { (key, entryPath) ->
            val source = resolvePackageFile(root, entryPath)
            require(source.isFile) { "主题资源不存在: $entryPath" }
            val extension = source.extension
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                ?: "asset"
            val targetDir = when (key) {
                ASSET_BACKGROUND_LIGHT, ASSET_BACKGROUND_DARK -> {
                    File(context.getExternalFilesDir(null) ?: context.filesDir, "theme_assets")
                }

                ASSET_NAV_HOME, ASSET_NAV_BOOKSHELF, ASSET_NAV_EXPLORE,
                ASSET_NAV_RSS, ASSET_NAV_MY -> File(context.filesDir, "nav_icons")

                ASSET_FONT -> File(context.filesDir, "fonts")
                else -> return@mapNotNull null
            }.apply { mkdirs() }
            val target = File(
                targetDir,
                "theme_${key.substringAfterLast('.')}_${UUID.randomUUID()}.$extension",
            )
            source.copyTo(target)
            copiedFiles += target
            key to target.absolutePath
        }.toMap()
    }

    private suspend fun importCoverAlbums(
        root: File,
        albums: List<ThemePackageCoverAlbum>,
        importedIds: MutableList<String>,
    ): Map<String, String> {
        val existingNames = coverAlbumUseCase.albums.value.mapTo(mutableSetOf()) { it.name }
        val result = mutableMapOf<String, String>()
        val albumRefs = mutableSetOf<String>()
        albums.forEach { album ->
            require(album.ref.isNotBlank()) { "主题包图集标识为空" }
            require(albumRefs.add(album.ref)) { "主题包图集标识重复: ${album.ref}" }
        }
        albums.forEach { album ->
            fun toInputs(images: List<ThemePackageCoverImage>) = images.map { image ->
                val source = resolvePackageFile(root, image.path)
                require(source.isFile) { "图集图片不存在: ${image.path}" }
                CoverAlbumImageInput(
                    displayName = source.name,
                    openStream = source::inputStream,
                )
            }
            val importedName = uniqueImportedAlbumName(album.name, existingNames)
            val albumId = coverAlbumUseCase.importAlbum(
                name = importedName,
                lightImages = toInputs(album.lightImages),
                darkImages = toInputs(album.darkImages),
            )
            importedIds += albumId
            result[album.ref] = albumId
            existingNames += importedName
        }
        return result
    }

    private suspend fun importLegacyCoverAlbums(
        albumName: String,
        appliedAssets: AppliedThemeAssets,
    ): String? {
        val importedIds = mutableListOf<String>()
        try {
            val lightFiles = appliedAssets.lightCoverPaths.toExistingFiles()
            val darkFiles = appliedAssets.darkCoverPaths.toExistingFiles()
            val albumId = if (lightFiles.isNotEmpty() || darkFiles.isNotEmpty()) {
                val existingNames = coverAlbumUseCase.albums.value.mapTo(mutableSetOf()) { it.name }
                val name = uniqueImportedAlbumName(
                    albumName,
                    existingNames,
                )
                coverAlbumUseCase.importAlbum(
                    name = name,
                    lightImages = lightFiles.toInputs(),
                    darkImages = darkFiles.toInputs(),
                ).also {
                    importedIds += it
                }
            } else {
                null
            }
            coverAlbumUseCase.selectAlbum(albumId)
            return albumId
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            importedIds.forEach { id ->
                runCatching { coverAlbumUseCase.deleteAlbum(id) }
            }
            throw error
        }
    }

    private fun saveImportedTheme(
        name: String,
        selectedCoverAlbumId: String?,
    ) {
        saveTheme(
            name = ThemeImportExport.uniqueSavedThemeName(name),
            data = ThemeImportExport.exportFromCurrent().copy(
                selectedCoverAlbumId = selectedCoverAlbumId,
            ),
        )
    }

    private fun importedThemeName(
        uri: Uri,
        packageName: String? = null,
    ): String {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val rawName = packageName
            ?.takeIf(String::isNotBlank)
            ?: displayName?.substringBeforeLast('.')
            ?: uri.lastPathSegment?.substringBeforeLast('.')
            ?: context.getString(R.string.import_theme)
        return rawName
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .trim('.')
            .ifBlank { context.getString(R.string.import_theme) }
    }

    private fun extractPackage(uri: Uri, root: File) {
        root.mkdirs()
        var entryCount = 0
        var totalBytes = 0L
        val entryNames = mutableSetOf<String>()
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法读取主题包")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRY_COUNT) { "主题包文件数量过多" }
                require(entryNames.add(entry.name.removeSuffix("/"))) {
                    "主题包包含重复路径: ${entry.name}"
                }
                val target = resolvePackageFile(root, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        totalBytes += zip.copyLimitedTo(
                            output = output,
                            maxEntryBytes = MAX_ENTRY_BYTES,
                            currentTotalBytes = totalBytes,
                        )
                    }
                    require(totalBytes <= MAX_TOTAL_BYTES) { "主题包解压后体积过大" }
                }
                zip.closeEntry()
            }
        }
        require(entryCount > 0) { "主题包为空" }
    }

    private fun resolvePackageFile(root: File, relativePath: String): File {
        val normalizedPath = relativePath.removeSuffix("/")
        require(normalizedPath.isNotBlank()) { "主题包包含空路径" }
        require(!normalizedPath.startsWith("/") && '\\' !in normalizedPath) {
            "主题包路径无效: $relativePath"
        }
        require(normalizedPath.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "主题包路径无效: $relativePath"
        }
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, normalizedPath).canonicalFile
        require(target.path.startsWith(canonicalRoot.path + File.separator)) {
            "主题包路径越界: $relativePath"
        }
        return target
    }

    private fun resolveAlbumRef(
        albumRef: String?,
        albumIdMap: Map<String, String>,
    ): String? {
        if (albumRef == null) return null
        return requireNotNull(albumIdMap[albumRef]) {
            "主题包引用了不存在的图集: $albumRef"
        }
    }

    private fun uniqueImportedAlbumName(
        originalName: String,
        existingNames: Set<String>,
    ): String {
        val original = originalName.ifBlank { context.getString(R.string.cover_albums) }
        if (original !in existingNames) return original
        val baseName = context.getString(
            R.string.cover_album_imported_name,
            original,
        )
        if (baseName !in existingNames) return baseName
        var index = 2
        while ("$baseName $index" in existingNames) index++
        return "$baseName $index"
    }

    private fun ThemeExportData.toPortableConfig() = copy(
        bgImageLight = null,
        bgImageDark = null,
        navIconHome = "",
        navIconBookshelf = "",
        navIconExplore = "",
        navIconRss = "",
        navIconMy = "",
        appFontPath = null,
        coverDefaultImage = "",
        coverDefaultImageDark = "",
        selectedCoverAlbumId = null,
        assets = null,
    )

    private fun openSource(path: String): InputStream {
        return if (path.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(path))
                ?: error("无法读取主题资源")
        } else {
            File(path).inputStream()
        }
    }

    private fun sourceExtension(path: String, key: String): String {
        val candidate = Uri.parse(path).lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        return candidate ?: if (key == ASSET_FONT) "ttf" else "img"
    }

    private fun ZipOutputStream.writeEntry(path: String, input: InputStream) {
        putNextEntry(ZipEntry(path))
        input.use { it.copyTo(this) }
        closeEntry()
    }

    private fun InputStream.copyLimitedTo(
        output: OutputStream,
        maxEntryBytes: Long,
        currentTotalBytes: Long,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entryBytes = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            entryBytes += read
            require(entryBytes <= maxEntryBytes) { "主题包单个文件过大" }
            require(currentTotalBytes + entryBytes <= MAX_TOTAL_BYTES) {
                "主题包解压后体积过大"
            }
            output.write(buffer, 0, read)
        }
        return entryBytes
    }

    private fun List<String>.toExistingFiles(): List<File> =
        map(String::trim)
            .filter(String::isNotEmpty)
            .map(::File)
            .filter(File::isFile)

    private fun List<File>.toInputs(): List<CoverAlbumImageInput> = map { file ->
        CoverAlbumImageInput(
            displayName = file.name,
            openStream = file::inputStream,
        )
    }

    private suspend inline fun <T> runSuspendCatching(
        crossinline block: suspend () -> T,
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private data class AssetSource(
        val key: String,
        val sourcePath: String?,
        val entryBase: String,
    )

    private data class ExportedCoverData(
        val albums: List<ThemePackageCoverAlbum>,
        val selection: ThemePackageCoverSelection,
    )
}

@Keep
data class ThemePackageManifest(
    @SerializedName("formatVersion")
    val formatVersion: Int = 1,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("config")
    val config: ThemeExportData = ThemeExportData(),
    @SerializedName("assets")
    val assets: Map<String, String> = emptyMap(),
    @SerializedName("coverAlbums")
    val coverAlbums: List<ThemePackageCoverAlbum> = emptyList(),
    @SerializedName("coverSelection")
    val coverSelection: ThemePackageCoverSelection = ThemePackageCoverSelection(),
)

@Keep
data class ThemePackageCoverAlbum(
    @SerializedName("ref")
    val ref: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("lightImages")
    val lightImages: List<ThemePackageCoverImage> = emptyList(),
    @SerializedName("darkImages")
    val darkImages: List<ThemePackageCoverImage> = emptyList(),
)

@Keep
data class ThemePackageCoverImage(
    @SerializedName("path")
    val path: String = "",
)

@Keep
data class ThemePackageCoverSelection(
    @SerializedName("albumRef")
    val albumRef: String? = null,
)
