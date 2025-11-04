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
import kotlinx.coroutines.CoroutineScope

object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private val checkVariant: AppVariant
        get() = when (AppConfig.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            "all_version" -> AppVariant.ALL
            else -> AppConst.appInfo.appVariant
        }

    private suspend fun getLatestRelease(): List<AppReleaseInfo> {
        val url = if (checkVariant == AppVariant.OFFICIAL)
            "https://api.github.com/repos/HapeLee/legado-with-MD3/releases/latest"
        else
            "https://api.github.com/repos/HapeLee/legado-with-MD3/releases"

        val res = okHttpClient.newCallResponse { url(url) }
        if (!res.isSuccessful) throw NoStackTraceException("获取新版本出错(${res.code})")

        val body = res.body.text()
        if (body.isBlank()) throw NoStackTraceException("获取新版本出错")

        return when (checkVariant) {
            AppVariant.BETA_RELEASE -> {
                val releases = GSON.fromJsonArray<GithubRelease>(body)
                    .getOrElse { throw NoStackTraceException("解析失败 ${it.localizedMessage}") }

                releases.filter { it.isPreRelease }
                    .flatMap { it.gitReleaseToAppReleaseInfo() }
                    .sortedByDescending { it.createdAt }
            }

            AppVariant.OFFICIAL -> {
                val release = GSON.fromJsonObject<GithubRelease>(body)
                    .getOrElse { throw NoStackTraceException("解析失败 ${it.localizedMessage}") }

                release.gitReleaseToAppReleaseInfo()
                    .sortedByDescending { it.createdAt }
            }

            AppVariant.ALL -> {
                val releases = GSON.fromJsonArray<GithubRelease>(body)
                    .getOrElse { throw NoStackTraceException("解析失败 ${it.localizedMessage}") }

                releases.flatMap { it.gitReleaseToAppReleaseInfo() }
                    .sortedByDescending { it.createdAt }
            }

            else -> emptyList()
        }
    }

    suspend fun getReleaseByTag(tag: String): AppUpdate.UpdateInfo? {
        val url = "https://api.github.com/repos/HapeLee/legado-with-MD3/releases/tags/$tag"
        val res = okHttpClient.newCallResponse { url(url) }
        if (!res.isSuccessful) return null

        val body = res.body.text()
        val release = GSON.fromJsonObject<GithubRelease>(body).getOrElse { return null }
        val info = release.gitReleaseToAppReleaseInfo().firstOrNull() ?: return null

        return AppUpdate.UpdateInfo(
            tagName = info.versionName,
            updateLog = info.note,
            downloadUrl = info.downloadUrl,
            fileName = info.name
        )
    }

    override fun check(scope: CoroutineScope): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            val currentVersion = AppConst.appInfo.versionName
            val releases = getLatestRelease()

            val filtered = if (checkVariant == AppVariant.ALL) {
                releases
            } else {
                releases.filter { it.appVariant == checkVariant }
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
                val regex = Regex("""(\d+)\.(\d+)\.(\d+)(?:-([\w.]+))?""")
                val match = regex.matchEntire(version)
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