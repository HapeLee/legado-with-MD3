package io.legado.app.help.update

/**
 * 更新清单与安装包的获取渠道。
 *
 * - [AUTO]：先直连 GitHub，失败后换国内镜像；下载沿用命中的那条链路。
 * - [GITHUB]：只走 GitHub，请求不经过任何第三方。
 * - [MIRROR]：只走国内镜像，完全不回连 github.com，也不回落 GitHub API。
 */
internal enum class AppUpdateEndpoint(val value: String) {
    AUTO("auto"),
    GITHUB("github"),
    MIRROR("mirror");

    /** 按尝试顺序返回清单地址；顺序即优先级。 */
    fun manifestUrls(repoPath: String, channel: String): List<String> {
        val github = githubManifestUrl(repoPath, channel)
        return when (this) {
            AUTO -> listOf(github, mirrorUrl(github))
            GITHUB -> listOf(github)
            MIRROR -> listOf(mirrorUrl(github))
        }
    }

    /** 镜像模式不再回落 GitHub API：直连不通时再等一轮超时只会更慢。 */
    val usesGitHubApi: Boolean
        get() = this != MIRROR

    companion object {
        fun of(value: String?): AppUpdateEndpoint =
            entries.firstOrNull { it.value == value } ?: AUTO
    }
}

/**
 * GitHub 反代前缀。`ghfast.top` 只是当前默认值，第三方镜像随时可能失效；
 * 换镜像只需要改这一处，设置项保存的是语义值而不是域名。
 */
internal const val UPDATE_MIRROR_PREFIX = "https://ghfast.top/"

internal fun githubManifestUrl(repoPath: String, channel: String): String =
    "https://raw.githubusercontent.com/$repoPath/update-manifests/$channel.json"

/** 已带前缀时保持幂等，避免清单地址被重复包装。 */
internal fun mirrorUrl(url: String): String =
    if (url.startsWith(UPDATE_MIRROR_PREFIX)) url else UPDATE_MIRROR_PREFIX + url
