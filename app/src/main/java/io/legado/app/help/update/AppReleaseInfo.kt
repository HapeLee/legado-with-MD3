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

/**
 * 同一 Release 并存 core 基础包与 comic 漫画增强版，它们的 ABI、versionCode 完全相同。
 * 更新只能挑自己这一份，否则 core 会被"升级"成 60+ MB 的 comic，comic 会被降级丢失本地引擎。
 *
 * [tag] 必须与发布资产名一致。comic 之前的产品标记是 `full`，改名后旧标记不再匹配：
 * 停在旧 full 版的用户需要手动下载一次 comic 包，之后应用内更新恢复。
 */
enum class AppProduct(val tag: String) {
    CORE("core"),
    COMIC("comic");

    companion object {
        /** 资产名里的产品标记形如 `legado-<version>-core-arm64-v8a.apk`。 */
        val current: AppProduct
            get() = if (io.legado.app.BuildConfig.MANGA_LOCAL_ENGINE) COMIC else CORE
    }
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
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.asList(),
        product: AppProduct = AppProduct.current
    ): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")

        val version = tagName
        return selectCompatibleAssets(assets, supportedAbis, product)
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
    supportedAbis: List<String>,
    product: AppProduct? = null
): List<Asset> {
    val validAssets = assets.filter { it.isValid }

    supportedAbis.forEach { supportedAbi ->
        val matchingAssets = validAssets.filter { asset ->
            asset.releaseAbi.equals(supportedAbi, ignoreCase = true)
        }
        if (matchingAssets.isNotEmpty()) return matchingAssets.ofProduct(product)
    }

    return validAssets.filter { it.releaseAbi == null }.ofProduct(product)
}

private val Asset.releaseAbi: String?
    get() = releaseAbis.firstOrNull { name.contains(it, ignoreCase = true) }

/** 单个包的旧 Release 没有产品标记，此时保持原行为，不把更新判成"没有新版本"。 */
private fun List<Asset>.ofProduct(product: AppProduct?): List<Asset> {
    if (product == null) return this
    val tagged = filter { it.releaseProduct != null }
    return if (tagged.isEmpty()) this else tagged.filter { it.releaseProduct == product }
}

private val Asset.releaseProduct: AppProduct?
    get() = AppProduct.entries.firstOrNull { name.contains("-${it.tag}-", ignoreCase = true) }

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
