package io.legato.kazusa.help.update

import android.os.Build
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legato.kazusa.exception.NoStackTraceException
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

    fun isBeta(): Boolean {
        return this == BETA_RELEASE
    }

}

@Keep
data class GithubRelease(
    val assets: List<Asset>?,
    val body: String,
    @SerializedName("prerelease")
    val isPreRelease: Boolean,
    @SerializedName("tag_name")
    val tagName: String,
    val name: String?,
    @SerializedName("created_at")
    val createdAt: String?
) {
    fun gitReleaseToAppReleaseInfo(): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")

        val version = tagName
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        val abiSuffix = when {
            abi.contains("arm64") -> "arm64-v8a"
            abi.contains("armeabi") -> "armeabi-v7a"
            else -> ""
        }

        return assets
            .filter { it.isValid }
            .filter { asset ->
                abiSuffix.isEmpty() || asset.name.contains(abiSuffix, ignoreCase = true)
            }
            .map { it.assetToAppReleaseInfo(isPreRelease, body, version) }
    }
}

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