package io.legado.app.smoke.networkprobe

import io.ktor.client.engine.okhttp.OkHttp
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * D3 PoC：证明 ktor-client-okhttp 引擎在 desktop(JVM) target 可实例化，
 * 即 HttpClient 契约的生产引擎路径在 desktop 可用。
 */
class OkHttpEngineDesktopTest {
    @Test
    fun `OkHttp engine instantiates on desktop`() {
        val engine = OkHttp.create()
        assertNotNull(engine)
    }
}
