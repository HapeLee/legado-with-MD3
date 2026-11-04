package io.legado.app.help.update

import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers
import okhttp3.Request
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private val otherSettingsGateway get() = org.koin.core.context.GlobalContext.get().get<io.legado.app.domain.gateway.OtherSettingsGateway>()

    private const val repoPath = "HapeLee/legado-with-MD3"
    private const val githubApiBaseUrl = "https://api.github.com/repos/$repoPath/releases"
    private const val manifestTimeoutMillis = 2500L
    private const val checkTimeoutMillis = 15000L

    private val checkVariant: AppVariant
        get() = when (otherSettingsGateway.currentSettings.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            "all_version" -> AppVariant.ALL
            else -> AppConst.appInfo.appVariant
        }

    private val endpoint: AppUpdateEndpoint
        get() = AppUpdateEndpoint.of(otherSettingsGateway.currentSettings.updateEndpoint)

    private data class ManifestFeed(
        val releases: List<GithubRelease>,
        val viaMirror: Boolean
    )

    private data class ManifestRelease(
        val release: GithubRelease,
        val viaMirror: Boolean
    )

    private data class ReleaseFeed(
        val releases: List<AppReleaseInfo>,
        val viaMirror: Boolean
    )

    private suspend fun getLatestRelease(variant: AppVariant): ReleaseFeed {
        val manifest = getManifestReleases(variant)
        if (manifest != null) {
            return ReleaseFeed(
                releases = manifest.releases
                    .flatMap { it.gitReleaseToAppReleaseInfo() }
                    .sortedByDescending { it.createdAt },
                viaMirror = manifest.viaMirror
            )
        }
        // 镜像模式只信任镜像清单：连不上时明确报错，不能静默当成“已是最新版本”。
        if (!endpoint.usesGitHubApi) {
            throw NoStackTraceException("无法获取更新信息，请检查网络或更换更新源")
        }
        return ReleaseFeed(
            releases = getApiReleases(variant)
                .flatMap { it.gitReleaseToAppReleaseInfo() }
                .sortedByDescending { it.createdAt },
            viaMirror = false
        )
    }

    private suspend fun getManifestReleases(variant: AppVariant): ManifestFeed? {
        val channels = when (variant) {
            AppVariant.OFFICIAL -> listOf("official" to false)
            AppVariant.BETA_RELEASE -> listOf("beta" to true)
            AppVariant.ALL -> listOf("official" to false, "beta" to true)
            else -> return null
        }
        val releases = mutableListOf<GithubRelease>()
        var viaMirror = false
        for ((channel, expectsPreRelease) in channels) {
            val release = getManifestRelease(channel, expectsPreRelease) ?: return null
            releases += release.release
            // 只要有一份清单来自镜像，安装包也走镜像，避免清单与下载落在两条链路上。
            viaMirror = viaMirror || release.viaMirror
        }
        return ManifestFeed(releases, viaMirror)
    }

    private suspend fun getManifestRelease(
        channel: String,
        expectsPreRelease: Boolean
    ): ManifestRelease? {
        for (address in endpoint.manifestUrls(repoPath, channel)) {
            val release = fetchManifest(address, expectsPreRelease) ?: continue
            return ManifestRelease(release, address.startsWith(UPDATE_MIRROR_PREFIX))
        }
        return null
    }

    private suspend fun fetchManifest(address: String, expectsPreRelease: Boolean): GithubRelease? {
        return try {
            withTimeoutOrNull(manifestTimeoutMillis) {
                val response = okHttpClient.newCallResponse {
                    url(address)
                }
                response.use {
                    if (!it.isSuccessful) return@withTimeoutOrNull null
                    val release = GSON.fromJsonObject<GithubRelease>(it.body.text()).getOrNull()
                        ?: return@withTimeoutOrNull null
                    release.takeIf {
                        it.isPreRelease == expectsPreRelease &&
                            it.assets.orEmpty().any { asset -> asset.isValid }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getApiReleases(variant: AppVariant): List<GithubRelease> {
        if (variant == AppVariant.ALL) {
            val releases = parseReleaseList(getGithubApiBody(githubApiBaseUrl))
            val latestOfficial = try {
                GSON.fromJsonObject<GithubRelease>(
                    getGithubApiBody("$githubApiBaseUrl/latest")
                ).getOrThrow()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            return (listOfNotNull(latestOfficial) + releases).distinctBy { it.tagName }
        }

        val url = if (variant == AppVariant.OFFICIAL) {
            "$githubApiBaseUrl/latest"
        } else {
            githubApiBaseUrl
        }
        val body = getGithubApiBody(url)
        return when (variant) {
            AppVariant.OFFICIAL -> listOf(
                GSON.fromJsonObject<GithubRelease>(body)
                    .getOrElse { throw NoStackTraceException("解析失败 ${it.localizedMessage}") }
            )
            AppVariant.BETA_RELEASE -> parseReleaseList(body)
                .filter { it.isPreRelease }
            else -> emptyList()
        }
    }

    private fun parseReleaseList(body: String): List<GithubRelease> {
        return GSON.fromJsonArray<GithubRelease>(body)
            .getOrElse { throw NoStackTraceException("解析失败 ${it.localizedMessage}") }
    }

    suspend fun getReleaseByTag(tag: String): AppUpdate.UpdateInfo? {
        val manifestRelease = findManifestRelease(tag)
        val release = manifestRelease?.release
            ?: (if (endpoint.usesGitHubApi) getApiReleaseByTag(tag) else null)
            ?: return null
        val info = release.gitReleaseToAppReleaseInfo().firstOrNull() ?: return null
        val viaMirror = manifestRelease?.viaMirror == true

        return AppUpdate.UpdateInfo(
            tagName = info.versionName,
            updateLog = info.note,
            downloadUrl = if (viaMirror) mirrorUrl(info.downloadUrl) else info.downloadUrl,
            fileName = info.name
        )
    }

    private suspend fun findManifestRelease(tag: String): ManifestRelease? {
        val channels = if (tag.contains("beta", ignoreCase = true)) {
            listOf("beta" to true, "official" to false)
        } else {
            listOf("official" to false, "beta" to true)
        }
        for ((channel, expectsPreRelease) in channels) {
            val release = getManifestRelease(channel, expectsPreRelease) ?: continue
            if (release.release.tagName == tag) return release
        }
        return null
    }

    private suspend fun getApiReleaseByTag(tag: String): GithubRelease? {
        val response = okHttpClient.newCallResponse {
            url("$githubApiBaseUrl/tags/$tag")
            addGithubApiHeaders()
        }
        response.use {
            if (it.code == 404) return null
            val body = it.body.text()
            if (!it.isSuccessful) throw githubApiException(it.code, it.headers, body)
            return GSON.fromJsonObject<GithubRelease>(body).getOrNull()
        }
    }

    private suspend fun getGithubApiBody(url: String): String {
        val response = okHttpClient.newCallResponse {
            url(url)
            addGithubApiHeaders()
        }
        response.use {
            val body = it.body.text()
            if (!it.isSuccessful) throw githubApiException(it.code, it.headers, body)
            if (body.isBlank()) throw NoStackTraceException("获取新版本出错")
            return body
        }
    }

    private fun Request.Builder.addGithubApiHeaders() {
        header("Accept", "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2026-03-10")
    }

    override fun check(scope: CoroutineScope): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) { checkNow() }
    }

    /**
     * 可直接 await 的版本检查：启动自动检查这类需要"检查完再决定下一步"的调用方用它，
     * 避免 [Coroutine] 回调在协程过快完成时被跳过的老问题。
     */
    suspend fun checkNow(): AppUpdate.UpdateInfo = try {
        withTimeout(checkTimeoutMillis) { resolveLatest() }
    } catch (_: TimeoutCancellationException) {
        // 超时本身也是 CancellationException，直接外抛会连带取消调用方的启动协程。
        throw NoStackTraceException("检查更新超时")
    }

    private suspend fun resolveLatest(): AppUpdate.UpdateInfo {
        val currentVersion = AppConst.appInfo.versionName
        val variant = checkVariant
        val feed = getLatestRelease(variant)
        val releases = feed.releases

        val filtered = if (variant == AppVariant.ALL) {
            releases
        } else {
            releases.filter { it.appVariant == variant }
        }

        val latest = filtered.firstOrNull { r ->
            try {
                r.versionName.versionCompare(currentVersion) > 0
            } catch (_: Exception) {
                false
            }
        } ?: throw NoStackTraceException("已是最新版本")

        return AppUpdate.UpdateInfo(
            latest.versionName,
            latest.note,
            // 清单来自镜像时安装包也走镜像，否则国内用户仍会卡在 GitHub 下载。
            if (feed.viaMirror) mirrorUrl(latest.downloadUrl) else latest.downloadUrl,
            latest.name
        )
    }

    private data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String? = null
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            if (major != other.major) return major - other.major
            if (minor != other.minor) return minor - other.minor
            if (patch != other.patch) return patch - other.patch

            if (preRelease == null && other.preRelease != null) return 1
            if (preRelease != null && other.preRelease == null) return -1
            if (preRelease == null && other.preRelease == null) return 0

            val aParts = preRelease!!.split(".")
            val bParts = other.preRelease!!.split(".")
            val maxLen = maxOf(aParts.size, bParts.size)

            for (i in 0 until maxLen) {
                val a = aParts.getOrNull(i)
                val b = bParts.getOrNull(i)
                if (a == null) return -1
                if (b == null) return 1

                val isANum = a.all { it.isDigit() }
                val isBNum = b.all { it.isDigit() }
                if (isANum && isBNum) {
                    val cmp = a.toInt().compareTo(b.toInt())
                    if (cmp != 0) return cmp
                } else {
                    val cmp = a.compareTo(b)
                    if (cmp != 0) return cmp
                }
            }
            return 0
        }

        companion object {
            fun parse(version: String): SemVer {
                val regex = Regex("""(\d+)\.(\d+)\.(\d+)(?:[-_]([\w.]+))?""")
                val match = regex.find(version)
                    ?: throw IllegalArgumentException("Invalid version: $version")
                val (maj, min, pat, pre) = match.destructured
                return SemVer(maj.toInt(), min.toInt(), pat.toInt(), pre.ifBlank { null })
            }
        }
    }

    fun String.versionCompare(other: String): Int {
        return SemVer.parse(this).compareTo(SemVer.parse(other))
    }
}

internal fun githubApiException(
    code: Int,
    headers: Headers,
    body: String,
    zoneId: ZoneId = ZoneId.systemDefault()
): NoStackTraceException {
    if (code == 403 || code == 429) {
        val retryAfter = headers["Retry-After"]?.toLongOrNull()
        if (retryAfter != null) {
            return NoStackTraceException("GitHub API 请求受限，请在 $retryAfter 秒后重试")
        }
        if (headers["X-RateLimit-Remaining"] == "0") {
            val resetAt = formatRateLimitReset(headers["X-RateLimit-Reset"], zoneId)
            val message = resetAt?.let { "GitHub API 请求额度已用完，请在 $it 后重试" }
                ?: "GitHub API 请求额度已用完，请稍后重试"
            return NoStackTraceException(message)
        }
        return NoStackTraceException("GitHub API 暂时限制请求，请稍后重试")
    }

    val detail = GSON.fromJsonObject<Map<String, String>>(body)
        .getOrNull()
        ?.get("message")
        ?.takeIf { it.isNotBlank() }
    return NoStackTraceException(
        detail?.let { "获取新版本出错($code): $it" }
            ?: "获取新版本出错($code)"
    )
}

private fun formatRateLimitReset(value: String?, zoneId: ZoneId): String? {
    val epochSeconds = value?.toLongOrNull() ?: return null
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochSecond(epochSeconds).atZone(zoneId))
}
