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
    fun `x86 does not match x86_64 apk`() {
        val selected = selectCompatibleAssets(
            assets = listOf(asset("legado-1.0.0-x86_64.apk")),
            supportedAbis = listOf("x86")
        )

        assertEquals(emptyList<Asset>(), selected)
    }

    @Test
    fun `armeabi does not match armeabi-v7a apk`() {
        val selected = selectCompatibleAssets(
            assets = listOf(asset("legado-1.0.0-armeabi-v7a.apk")),
            supportedAbis = listOf("armeabi")
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

    @Test
    fun `each product only picks its own apk from a dual release`() {
        val assets = listOf(
            asset("legado-1.0.0-core-arm64-v8a.apk"),
            asset("legado-1.0.0-core-universal.apk"),
            asset("legado-1.0.0-comic-arm64-v8a.apk"),
            asset("legado-1.0.0-comic-universal.apk")
        )

        assertEquals(
            listOf("legado-1.0.0-core-arm64-v8a.apk"),
            selectCompatibleAssets(assets, listOf("arm64-v8a"), AppProduct.CORE).map { it.name }
        )
        assertEquals(
            listOf("legado-1.0.0-comic-arm64-v8a.apk"),
            selectCompatibleAssets(assets, listOf("arm64-v8a"), AppProduct.COMIC).map { it.name }
        )
        assertEquals(
            listOf("legado-1.0.0-comic-universal.apk"),
            selectCompatibleAssets(listOf(asset("legado-1.0.0-comic-universal.apk")), listOf("arm64-v8a"), AppProduct.COMIC)
                .map { it.name }
        )
    }

    @Test
    fun `single apk release without product marker still updates every product`() {
        val assets = listOf(asset("legado-1.0.0-arm64-v8a.apk"))

        assertEquals(
            listOf("legado-1.0.0-arm64-v8a.apk"),
            selectCompatibleAssets(assets, listOf("arm64-v8a"), AppProduct.CORE).map { it.name }
        )
        assertEquals(
            listOf("legado-1.0.0-arm64-v8a.apk"),
            selectCompatibleAssets(assets, listOf("arm64-v8a"), AppProduct.COMIC).map { it.name }
        )
    }

    @Test
    fun `missing apk for the current product reports nothing instead of the other product`() {
        val assets = listOf(asset("legado-1.0.0-core-arm64-v8a.apk"))

        assertEquals(
            emptyList<Asset>(),
            selectCompatibleAssets(assets, listOf("arm64-v8a"), AppProduct.COMIC)
        )
    }

    /** comic 改名前的资产标记是 full，不能被当成 comic 的更新，否则会跨产品升级。 */
    @Test
    fun `legacy full assets are not offered to the comic product`() {
        val assets = listOf(
            asset("legado-1.0.0-core-arm64-v8a.apk"),
            asset("legado-1.0.0-full-arm64-v8a.apk")
        )

        assertEquals(
            emptyList<Asset>(),
            selectCompatibleAssets(assets, listOf("arm64-v8a"), AppProduct.COMIC)
        )
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
