package io.legado.app.help.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateEndpointTest {

    @Test
    fun `持久化取值与设置项顺序保持一致`() {
        assertEquals(
            listOf("auto", "github", "mirror"),
            AppUpdateEndpoint.entries.map { it.value },
        )
    }

    @Test
    fun `未知或缺失的取值回落到自动`() {
        assertEquals(AppUpdateEndpoint.AUTO, AppUpdateEndpoint.of(null))
        assertEquals(AppUpdateEndpoint.AUTO, AppUpdateEndpoint.of(""))
        assertEquals(AppUpdateEndpoint.AUTO, AppUpdateEndpoint.of("cdn"))
        AppUpdateEndpoint.entries.forEach { endpoint ->
            assertEquals(endpoint, AppUpdateEndpoint.of(endpoint.value))
        }
    }

    @Test
    fun `各更新源按顺序给出清单地址`() {
        val repo = "owner/repo"
        val github = githubManifestUrl(repo, "official")

        assertEquals(
            listOf(github, UPDATE_MIRROR_PREFIX + github),
            AppUpdateEndpoint.AUTO.manifestUrls(repo, "official"),
        )
        assertEquals(
            listOf(github),
            AppUpdateEndpoint.GITHUB.manifestUrls(repo, "official"),
        )
        assertEquals(
            listOf(UPDATE_MIRROR_PREFIX + github),
            AppUpdateEndpoint.MIRROR.manifestUrls(repo, "official"),
        )
    }

    @Test
    fun `镜像模式不回落 GitHub API`() {
        assertFalse(AppUpdateEndpoint.MIRROR.usesGitHubApi)
        assertTrue(AppUpdateEndpoint.AUTO.usesGitHubApi)
        assertTrue(AppUpdateEndpoint.GITHUB.usesGitHubApi)
    }

    @Test
    fun `镜像地址重复包装保持幂等`() {
        val github = githubManifestUrl("owner/repo", "beta")

        assertEquals(mirrorUrl(github), mirrorUrl(mirrorUrl(github)))
    }

    @Test
    fun `清单地址按渠道拼接`() {
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/update-manifests/beta.json",
            githubManifestUrl("owner/repo", "beta"),
        )
    }
}
