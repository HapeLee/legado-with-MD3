package io.legado.app.data.repository

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.gateway.CoverAlbumGateway
import io.legado.app.domain.model.CoverAlbum
import io.legado.app.domain.model.CoverAlbumImage
import io.legado.app.domain.model.CoverAlbumImageInput
import io.legado.app.domain.model.CoverAlbumSelection
import io.legado.app.help.config.AppConfigStore
import io.legado.app.utils.GSON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

class CoverAlbumRepository(
    private val context: Context,
    private val preferences: SettingsRepository,
) : CoverAlbumGateway {

    companion object {
        private const val FORMAT_VERSION = 2
        private const val INDEX_FILE_NAME = "albums.json"
        private const val LEGACY_DEFAULT_ALBUM_ID = "default-cover"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val rootDir = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "cover_albums",
    )
    private val indexFile = File(rootDir, INDEX_FILE_NAME)

    private val initialSelection = runBlocking {
        InitialSelection(
            albumId = preferences.getPreference(
                LocalPreferencesKeys.SELECTED_COVER_ALBUM_ID,
                "",
            ).first().ifBlank { null },
            lightAlbumId = preferences.getPreference(
                LocalPreferencesKeys.SELECTED_LIGHT_COVER_ALBUM_ID,
                "",
            ).first().ifBlank { null },
            darkAlbumId = preferences.getPreference(
                LocalPreferencesKeys.SELECTED_DARK_COVER_ALBUM_ID,
                "",
            ).first().ifBlank { null },
        )
    }

    private val _albums = MutableStateFlow(loadAlbums(initialSelection))
    override val albums: StateFlow<List<CoverAlbum>> = _albums.asStateFlow()

    private val _selection = MutableStateFlow(
        CoverAlbumSelection(
            albumId = initialSelection.albumId
                ?: initialSelection.lightAlbumId
                ?: initialSelection.darkAlbumId,
        )
    )
    override val selection: StateFlow<CoverAlbumSelection> = _selection.asStateFlow()

    init {
        scope.launch {
            migrateLegacyCoversIfNeeded()
        }
    }

    override fun selectedImagePaths(isDark: Boolean): List<String> {
        return _albums.value
            .firstOrNull { it.id == _selection.value.albumId }
            ?.let { if (isDark) it.darkImages else it.lightImages }
            ?.map { it.path }
            .orEmpty()
    }

    override suspend fun createAlbum(name: String): String = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val album = CoverAlbum(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                lightImages = emptyList(),
                darkImages = emptyList(),
            )
            replaceAlbums(_albums.value + album)
            album.id
        }
    }

    override suspend fun importAlbum(
        name: String,
        lightImages: List<CoverAlbumImageInput>,
        darkImages: List<CoverAlbumImageInput>,
    ): String = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val albumId = UUID.randomUUID().toString()
            val importedLightImages = copyImages(albumId, lightImages)
            val importedDarkImages = copyImages(albumId, darkImages)
            val album = CoverAlbum(
                id = albumId,
                name = name.trim(),
                lightImages = importedLightImages,
                darkImages = importedDarkImages,
            )
            runCatching {
                replaceAlbums(_albums.value + album)
            }.onFailure {
                File(rootDir, albumId).deleteRecursively()
            }.getOrThrow()
            albumId
        }
    }

    override suspend fun renameAlbum(
        albumId: String,
        name: String,
    ) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            replaceAlbums(
                _albums.value.map { album ->
                    if (album.id == albumId) album.copy(name = name.trim()) else album
                }
            )
        }
    }

    override suspend fun deleteAlbum(albumId: String) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val updatedAlbums = _albums.value.filterNot { it.id == albumId }
            replaceAlbums(updatedAlbums)
            if (_selection.value.albumId == albumId) {
                persistSelection(albumId = null)
            }
            File(rootDir, albumId).deleteRecursively()
            syncLegacyCoverPaths()
        }
    }

    override suspend fun addImages(
        albumId: String,
        isDark: Boolean,
        images: List<CoverAlbumImageInput>,
    ) = withContext(Dispatchers.IO) {
        if (images.isEmpty()) return@withContext
        operationMutex.withLock {
            val album = _albums.value.firstOrNull { it.id == albumId } ?: return@withLock
            val imported = copyImages(albumId, images)
            replaceAlbums(
                _albums.value.map {
                    if (it.id != albumId) {
                        it
                    } else if (isDark) {
                        it.copy(
                            darkImages = (it.darkImages + imported).distinctBy { image -> image.id }
                        )
                    } else {
                        it.copy(
                            lightImages = (it.lightImages + imported)
                                .distinctBy { image -> image.id }
                        )
                    }
                }
            )
            syncLegacyCoverPaths()
        }
    }

    override suspend fun removeImage(
        albumId: String,
        isDark: Boolean,
        imageId: String,
    ) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val album = _albums.value.firstOrNull { it.id == albumId } ?: return@withLock
            val images = if (isDark) album.darkImages else album.lightImages
            val image = images.firstOrNull { it.id == imageId } ?: return@withLock
            replaceAlbums(
                _albums.value.map {
                    if (it.id == albumId) {
                        if (isDark) {
                            it.copy(
                                darkImages = it.darkImages.filterNot { item -> item.id == imageId }
                            )
                        } else {
                            it.copy(
                                lightImages = it.lightImages.filterNot { item -> item.id == imageId }
                            )
                        }
                    } else {
                        it
                    }
                }
            )
            val updatedAlbum = _albums.value.first { it.id == albumId }
            val stillUsed = (updatedAlbum.lightImages + updatedAlbum.darkImages)
                .any { it.path == image.path }
            if (!stillUsed) File(image.path).delete()
            syncLegacyCoverPaths()
        }
    }

    override suspend fun selectAlbum(albumId: String?) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            if (albumId != null && _albums.value.none { it.id == albumId }) return@withLock
            persistSelection(albumId)
            syncLegacyCoverPaths()
        }
    }

    private suspend fun migrateLegacyCoversIfNeeded() {
        operationMutex.withLock {
            if (_selection.value.albumId != null &&
                _albums.value.none { it.id == _selection.value.albumId }
            ) {
                persistSelection(albumId = null)
            }
            val migrated = preferences.getPreference(
                LocalPreferencesKeys.COVER_ALBUM_MIGRATED,
                false,
            ).first()
            val legacyLight = AppConfigStore.getString(PreferKey.defaultCover)
                .orEmpty()
                .toExistingFiles()
            val legacyDark = AppConfigStore.getString(PreferKey.defaultCoverDark)
                .orEmpty()
                .toExistingFiles()
            val needsRecovery = _albums.value.isEmpty() &&
                _selection.value.albumId == null &&
                (legacyLight.isNotEmpty() || legacyDark.isNotEmpty())
            if (migrated && !needsRecovery) {
                preferences.updatePreference(
                    LocalPreferencesKeys.SELECTED_COVER_ALBUM_ID,
                    _selection.value.albumId.orEmpty(),
                )
                syncLegacyCoverPaths()
                return
            }

            var updatedAlbums = _albums.value
            if (legacyLight.isNotEmpty() || legacyDark.isNotEmpty()) {
                val existing = updatedAlbums.firstOrNull { it.id == LEGACY_DEFAULT_ALBUM_ID }
                val album = CoverAlbum(
                    id = LEGACY_DEFAULT_ALBUM_ID,
                    name = existing?.name ?: context.getString(R.string.default_cover),
                    lightImages = (
                        existing?.lightImages.orEmpty() +
                            copyImages(LEGACY_DEFAULT_ALBUM_ID, legacyLight.toInputs())
                        ).distinctBy { it.id },
                    darkImages = (
                        existing?.darkImages.orEmpty() +
                            copyImages(LEGACY_DEFAULT_ALBUM_ID, legacyDark.toInputs())
                        ).distinctBy { it.id },
                )
                updatedAlbums = updatedAlbums.filterNot { it.id == album.id } + album
                persistSelection(album.id)
            }
            if (updatedAlbums != _albums.value) {
                replaceAlbums(updatedAlbums)
            }
            preferences.updatePreference(LocalPreferencesKeys.COVER_ALBUM_MIGRATED, true)
            syncLegacyCoverPaths()
        }
    }

    private suspend fun persistSelection(albumId: String?) {
        _selection.value = CoverAlbumSelection(albumId = albumId)
        preferences.updatePreference(
            LocalPreferencesKeys.SELECTED_COVER_ALBUM_ID,
            albumId.orEmpty(),
        )
    }

    private fun syncLegacyCoverPaths() {
        AppConfigStore.putAll(
            mapOf(
                PreferKey.defaultCover to selectedImagePaths(isDark = false).joinToString(","),
                PreferKey.defaultCoverDark to selectedImagePaths(isDark = true).joinToString(","),
            )
        )
    }

    private fun copyImages(
        albumId: String,
        inputs: List<CoverAlbumImageInput>,
    ): List<CoverAlbumImage> {
        if (inputs.isEmpty()) return emptyList()
        val albumDir = File(rootDir, albumId).apply { mkdirs() }
        return inputs.mapNotNull { input ->
            runCatching {
                val extension = input.displayName
                    .substringAfterLast('.', "img")
                    .lowercase()
                    .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                    ?: "img"
                val tempFile = File(albumDir, ".${UUID.randomUUID()}.tmp")
                val digest = MessageDigest.getInstance("SHA-256")
                input.openStream().use { source ->
                    DigestInputStream(source, digest).use { digestInput ->
                        FileOutputStream(tempFile).use { output ->
                            digestInput.copyTo(output)
                        }
                    }
                }
                val id = digest.digest().joinToString("") { "%02x".format(it) }
                val target = File(albumDir, "$id.$extension")
                if (target.exists()) {
                    tempFile.delete()
                } else if (!tempFile.renameTo(target)) {
                    tempFile.copyTo(target, overwrite = false)
                    tempFile.delete()
                }
                CoverAlbumImage(
                    id = target.name,
                    fileName = target.name,
                    path = target.absolutePath,
                )
            }.onFailure {
                AppLog.put("导入封面图集图片失败\n${it.localizedMessage}", it)
            }.getOrNull()
        }
    }

    private fun replaceAlbums(albums: List<CoverAlbum>) {
        persistAlbums(albums)
        _albums.value = albums
    }

    private fun persistAlbums(albums: List<CoverAlbum>) {
        rootDir.mkdirs()
        val stored = StoredCoverAlbumIndex(
            formatVersion = FORMAT_VERSION,
            albums = albums.map { album ->
                StoredCoverAlbum(
                    id = album.id,
                    name = album.name,
                    lightImages = album.lightImages.map { it.fileName },
                    darkImages = album.darkImages.map { it.fileName },
                )
            },
        )
        val temp = File(rootDir, "$INDEX_FILE_NAME.tmp")
        FileOutputStream(temp).bufferedWriter().use { writer ->
            GSON.toJson(stored, writer)
        }
        runCatching {
            java.nio.file.Files.move(
                temp.toPath(),
                indexFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            java.nio.file.Files.move(
                temp.toPath(),
                indexFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun loadAlbums(selection: InitialSelection): List<CoverAlbum> {
        if (!indexFile.isFile) return emptyList()
        return runCatching {
            val stored = indexFile.bufferedReader().use {
                GSON.fromJson(it, StoredCoverAlbumIndex::class.java)
            }
            var albums = stored.albums.map { album ->
                val legacyImages = album.images.mapExistingImages(album.id)
                val lightFileNames = if (stored.formatVersion >= FORMAT_VERSION) {
                    album.lightImages
                } else if (album.id == selection.darkAlbumId &&
                    album.id != selection.lightAlbumId
                ) {
                    emptyList()
                } else {
                    album.images
                }
                val darkFileNames = if (stored.formatVersion >= FORMAT_VERSION) {
                    album.darkImages
                } else if (album.id == selection.darkAlbumId) {
                    album.images
                } else {
                    emptyList()
                }
                CoverAlbum(
                    id = album.id,
                    name = album.name,
                    lightImages = if (stored.formatVersion >= FORMAT_VERSION) {
                        lightFileNames.mapExistingImages(album.id)
                    } else if (lightFileNames == album.images) {
                        legacyImages
                    } else {
                        emptyList()
                    },
                    darkImages = if (stored.formatVersion >= FORMAT_VERSION) {
                        darkFileNames.mapExistingImages(album.id)
                    } else if (darkFileNames == album.images) {
                        legacyImages
                    } else {
                        emptyList()
                    },
                )
            }
            if (stored.formatVersion < FORMAT_VERSION &&
                selection.lightAlbumId != null &&
                selection.darkAlbumId != null &&
                selection.lightAlbumId != selection.darkAlbumId
            ) {
                val lightAlbum = albums.firstOrNull { it.id == selection.lightAlbumId }
                val darkAlbum = albums.firstOrNull { it.id == selection.darkAlbumId }
                if (lightAlbum != null && darkAlbum != null) {
                    val copiedDarkImages = copyImages(
                        albumId = lightAlbum.id,
                        inputs = darkAlbum.darkImages.map { image ->
                            CoverAlbumImageInput(image.fileName) { File(image.path).inputStream() }
                        },
                    )
                    albums = albums.map { album ->
                        if (album.id == lightAlbum.id) {
                            album.copy(darkImages = copiedDarkImages)
                        } else {
                            album
                        }
                    }
                }
            }
            if (stored.formatVersion < FORMAT_VERSION) {
                persistAlbums(albums)
            }
            albums
        }.onFailure {
            AppLog.put("读取封面图集失败\n${it.localizedMessage}", it)
        }.getOrDefault(emptyList())
    }

    private fun List<String>.mapExistingImages(albumId: String): List<CoverAlbumImage> =
        mapNotNull { fileName ->
            val file = File(File(rootDir, albumId), fileName)
            if (!file.isFile) return@mapNotNull null
            CoverAlbumImage(
                id = file.name,
                fileName = file.name,
                path = file.absolutePath,
            )
        }

    private fun String.toExistingFiles(): List<File> =
        split(",")
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(::File)
            .filter(File::isFile)
            .toList()

    private fun List<File>.toInputs(): List<CoverAlbumImageInput> = map { file ->
        CoverAlbumImageInput(file.name) { file.inputStream() }
    }

    private data class InitialSelection(
        val albumId: String?,
        val lightAlbumId: String?,
        val darkAlbumId: String?,
    )

    @Keep
    private data class StoredCoverAlbumIndex(
        @SerializedName("formatVersion")
        val formatVersion: Int = FORMAT_VERSION,
        @SerializedName("albums")
        val albums: List<StoredCoverAlbum> = emptyList(),
    )

    @Keep
    private data class StoredCoverAlbum(
        @SerializedName("id")
        val id: String = "",
        @SerializedName("name")
        val name: String = "",
        @SerializedName("lightImages")
        val lightImages: List<String> = emptyList(),
        @SerializedName("darkImages")
        val darkImages: List<String> = emptyList(),
        @SerializedName("images")
        val images: List<String> = emptyList(),
    )
}
