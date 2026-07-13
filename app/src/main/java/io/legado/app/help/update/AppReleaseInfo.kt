package io.legado.app.help.update

import android.os.Build
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.exception.NoStackTraceException
import java.time.Instant

data class AppReleaseInfo(
    val appVariant: AppVariant,
    val createdAt: Long,
    val note: String,
    val name: String,
    val downloadUrl: String,
    val assetUrl: String,
    val versionName: String
)

enum class AppVariant {
    OFFICIAL,
    BETA_RELEASE,
    ALL,
    UNKNOWN;

}

@Keep
data class GithubRelease(
    val assets: List<Asset>?,
    val body: String?,
    @SerializedName("prerelease")
    val isPreRelease: Boolean,
    @SerializedName("tag_name")
    val tagName: String,
    val name: String?,
    @SerializedName("created_at")
    val createdAt: String?
) {
    fun gitReleaseToAppReleaseInfo(
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.asList()
    ): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")

        val version = tagName
        return selectCompatibleAssets(assets, supportedAbis)
            .map { it.assetToAppReleaseInfo(isPreRelease, body.orEmpty(), version) }
    }
}

private val releaseAbis = listOf(
    "arm64-v8a",
    "armeabi-v7a",
    "x86_64",
    "armeabi",
    "x86"
)

internal fun selectCompatibleAssets(
    assets: List<Asset>,
    supportedAbis: List<String>
): List<Asset> {
    val validAssets = assets.filter { it.isValid }

    supportedAbis.forEach { supportedAbi ->
        val matchingAssets = validAssets.filter { asset ->
            asset.releaseAbi.equals(supportedAbi, ignoreCase = true)
        }
        if (matchingAssets.isNotEmpty()) return matchingAssets
    }

    return validAssets.filter { it.releaseAbi == null }
}

private val Asset.releaseAbi: String?
    get() = releaseAbis.firstOrNull { name.contains(it, ignoreCase = true) }

@Keep
data class Asset(
    @SerializedName("browser_download_url")
    val apkUrl: String,
    @SerializedName("content_type")
    val contentType: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("download_count")
    val downloadCount: Int,
    val id: Int,
    val name: String,
    val state: String,
    val url: String
) {
    val isValid: Boolean
        get() = (contentType == "application/vnd.android.package-archive") && (state == "uploaded")

    fun assetToAppReleaseInfo(preRelease: Boolean, note: String, version: String): AppReleaseInfo {
        val instant = Instant.parse(createdAt)
        val timestamp: Long = instant.toEpochMilli()
        val appVariant = if (preRelease) AppVariant.BETA_RELEASE else AppVariant.OFFICIAL

        return AppReleaseInfo(
            appVariant = appVariant,
            createdAt = timestamp,
            note = note,
            name = name,
            downloadUrl = apkUrl,
            assetUrl = url,
            versionName = version
        )
    }
}
