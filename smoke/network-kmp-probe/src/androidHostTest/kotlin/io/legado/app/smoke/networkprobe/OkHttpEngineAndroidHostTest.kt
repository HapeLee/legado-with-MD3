package io.legado.app.smoke.networkprobe

import io.ktor.client.engine.okhttp.OkHttp
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * D3 PoC：证明 ktor-client-okhttp 引擎在 androidHost(JVM) target 可实例化。
 */
class OkHttpEngineAndroidHostTest {
    @Test
    fun `OkHttp engine instantiates on android host`() {
        val engine = OkHttp.create()
        assertNotNull(engine)
    }
}
