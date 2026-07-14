package io.legado.app.help.update

import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers
import okhttp3.Request
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private const val repoPath = "HapeLee/legado-with-MD3"
    private const val githubApiBaseUrl = "https://api.github.com/repos/$repoPath/releases"
    private const val updateManifestBaseUrl =
        "https://raw.githubusercontent.com/$repoPath/update-manifests"
    private const val manifestTimeoutMillis = 2500L

    private val checkVariant: AppVariant
        get() = when (AppConfig.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            "all_version" -> AppVariant.ALL
            else -> AppConst.appInfo.appVariant
        }

    private suspend fun getLatestRelease(variant: AppVariant): List<AppReleaseInfo> {
        val releases = getManifestReleases(variant) ?: getApiReleases(variant)
        return releases
            .flatMap { it.gitReleaseToAppReleaseInfo() }
            .sortedByDescending { it.createdAt }
    }

    private suspend fun getManifestReleases(variant: AppVariant): List<GithubRelease>? {
        val releases = when (variant) {
            AppVariant.OFFICIAL -> listOfNotNull(getManifestRelease(AppVariant.OFFICIAL))
            AppVariant.BETA_RELEASE -> listOfNotNull(getManifestRelease(AppVariant.BETA_RELEASE))
            AppVariant.ALL -> listOfNotNull(
                getManifestRelease(AppVariant.OFFICIAL),
                getManifestRelease(AppVariant.BETA_RELEASE)
            )
            else -> emptyList()
        }
        val expectedCount = if (variant == AppVariant.ALL) 2 else 1
        return releases.takeIf { it.size == expectedCount }
    }

    private suspend fun getManifestRelease(variant: AppVariant): GithubRelease? {
        val channel = when (variant) {
            AppVariant.OFFICIAL -> "official"
            AppVariant.BETA_RELEASE -> "beta"
            else -> return null
        }
        return try {
            withTimeoutOrNull(manifestTimeoutMillis) {
                val response = okHttpClient.newCallResponse {
                    url("$updateManifestBaseUrl/$channel.json")
                }
                response.use {
                    if (!it.isSuccessful) return@withTimeoutOrNull null
                    val release = GSON.fromJsonObject<GithubRelease>(it.body.text()).getOrNull()
                        ?: return@withTimeoutOrNull null
                    val expectedPreRelease = variant == AppVariant.BETA_RELEASE
                    release.takeIf {
                        it.isPreRelease == expectedPreRelease &&
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
        val release = manifestRelease ?: getApiReleaseByTag(tag) ?: return null
        val info = release.gitReleaseToAppReleaseInfo().firstOrNull() ?: return null

        return AppUpdate.UpdateInfo(
            tagName = info.versionName,
            updateLog = info.note,
            downloadUrl = info.downloadUrl,
            fileName = info.name
        )
    }

    private suspend fun findManifestRelease(tag: String): GithubRelease? {
        val variants = if (tag.contains("beta", ignoreCase = true)) {
            listOf(AppVariant.BETA_RELEASE, AppVariant.OFFICIAL)
        } else {
            listOf(AppVariant.OFFICIAL, AppVariant.BETA_RELEASE)
        }
        variants.forEach { variant ->
            getManifestRelease(variant)?.let { release ->
                if (release.tagName == tag) return release
            }
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
        return Coroutine.async(scope) {
            val currentVersion = AppConst.appInfo.versionName
            val variant = checkVariant
            val releases = getLatestRelease(variant)

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
            }

            if (latest != null) {
                return@async AppUpdate.UpdateInfo(
                    latest.versionName,
                    latest.note,
                    latest.downloadUrl,
                    latest.name
                )
            }

            throw NoStackTraceException("已是最新版本")
        }.timeout(10000)
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
