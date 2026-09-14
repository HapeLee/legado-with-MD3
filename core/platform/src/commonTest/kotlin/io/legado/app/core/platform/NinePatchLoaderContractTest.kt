package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [NinePatchLoader] 注入点契约测试（M1-3p）。
 *
 * 只钉住 **provider 语义**与**路径透传**：未注入时一律 `null`（同 [MimeTypeResolver] 的回落语义，
 * 不是抛异常）、安装/卸载同步、`load` 的入参原样交给实现。九宫格解码本身由 `:app` 侧
 * `AndroidPlatformCapabilities.ninePatchLoader()` 委托 `BitmapFactory`/`NinePatch`，
 * 属平台实现，不在此重复覆盖（desktop 上根本构造不出 `NinePatchDrawable`）。
 */
class NinePatchLoaderContractTest {

    /** 哨兵 payload：契约的返回类型是不透明的 `Any?`，测试用任意对象即可。 */
    private val sentinel = Any()

    @Test
    fun uninstalledFallsBackToNull() {
        NinePatchLoaderProvider.uninstall()
        assertFalse(NinePatchLoaderProvider.isInstalled)
        // 「这个平台没有九宫格」是正常状态：回落成 null，调用方按原路径加载，**不抛异常**。
        assertNull(NinePatchLoaderProvider.current.load("/tmp/bg.9.png"))
    }

    @Test
    fun installedReturnsDelegateAndForwardsPathVerbatim() {
        val loader = RecordingLoader()
        NinePatchLoaderProvider.install(loader)
        try {
            assertTrue(NinePatchLoaderProvider.isInstalled)
            val payload = NinePatchLoaderProvider.current.load("/tmp/case/混合 空格.9.png")
            assertEquals(listOf("/tmp/case/混合 空格.9.png"), loader.paths)
            assertSame(sentinel, payload)
        } finally {
            NinePatchLoaderProvider.uninstall()
        }
    }

    @Test
    fun uninstallClearsDelegate() {
        NinePatchLoaderProvider.install(RecordingLoader())
        NinePatchLoaderProvider.uninstall()
        assertFalse(NinePatchLoaderProvider.isInstalled)
        assertNull(NinePatchLoaderProvider.current.load("/tmp/bg.9.png"))
    }

    /** 记录入参并回哨兵 payload 的替身。 */
    private inner class RecordingLoader : NinePatchLoader {
        val paths = mutableListOf<String>()
        override fun load(path: String): Any? {
            paths += path
            return sentinel
        }
    }
}
