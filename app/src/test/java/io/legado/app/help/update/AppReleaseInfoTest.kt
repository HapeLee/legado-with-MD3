package io.legado.app.help.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppReleaseInfoTest {

    @Test
    fun `falls back to next supported abi when preferred apk is missing`() {
        val selected = selectCompatibleAssets(
            assets = listOf(
                asset("legado-1.0.0-armeabi-v7a.apk"),
                asset("legado-1.0.0-x86.apk")
            ),
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )

        assertEquals(listOf("legado-1.0.0-armeabi-v7a.apk"), selected.map { it.name })
    }

    @Test
    fun `uses universal apk when no supported abi apk exists`() {
        val selected = selectCompatibleAssets(
            assets = listOf(
                asset("legado-1.0.0.apk"),
                asset("legado-1.0.0-x86.apk")
            ),
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )

        assertEquals(listOf("legado-1.0.0.apk"), selected.map { it.name })
    }

    @Test
    fun `does not select an incompatible abi when universal apk is missing`() {
        val selected = selectCompatibleAssets(
            assets = listOf(asset("legado-1.0.0-x86.apk")),
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )

        assertEquals(emptyList<Asset>(), selected)
    }

    @Test
    fun `prefers the highest priority supported abi`() {
        val selected = selectCompatibleAssets(
            assets = listOf(
                asset("legado-1.0.0.apk"),
                asset("legado-1.0.0-armeabi-v7a.apk"),
                asset("legado-1.0.0-arm64-v8a.apk")
            ),
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )

        assertEquals(listOf("legado-1.0.0-arm64-v8a.apk"), selected.map { it.name })
    }

    private fun asset(name: String) = Asset(
        apkUrl = "https://example.com/$name",
        contentType = "application/vnd.android.package-archive",
        createdAt = "2026-07-13T00:00:00Z",
        downloadCount = 0,
        id = name.hashCode(),
        name = name,
        state = "uploaded",
        url = "https://api.example.com/$name"
    )
}
