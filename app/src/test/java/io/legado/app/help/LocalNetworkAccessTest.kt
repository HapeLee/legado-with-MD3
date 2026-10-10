package io.legado.app.help

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {

    @Test
    fun `private ipv4 addresses target local network`() {
        assertTrue("http://192.168.50.1:17863/v1".targetsLocalNetwork())
        assertTrue("http://10.0.2.2:8080/v1".targetsLocalNetwork())
        assertTrue("http://172.16.0.5/v1".targetsLocalNetwork())
        assertTrue("http://172.31.255.254/v1".targetsLocalNetwork())
        assertTrue("http://169.254.10.10".targetsLocalNetwork())
        // 100.64/10 运营商级 NAT 同样属于本地网络定义
        assertTrue("http://100.64.0.1/v1".targetsLocalNetwork())
        assertTrue("http://100.127.255.254/v1".targetsLocalNetwork())
    }

    @Test
    fun `public hosts do not target local network`() {
        assertFalse("https://api.deepseek.com".targetsLocalNetwork())
        assertFalse("https://api.openai.com/v1".targetsLocalNetwork())
        assertFalse("http://8.8.8.8".targetsLocalNetwork())
        // 172.32/16 已经不在 172.16/12 里
        assertFalse("http://172.32.0.1/v1".targetsLocalNetwork())
        // 100.63/16 与 100.128/16 都在 100.64/10 之外
        assertFalse("http://100.63.255.255/v1".targetsLocalNetwork())
        assertFalse("http://100.128.0.1/v1".targetsLocalNetwork())
    }

    @Test
    fun `loopback addresses do not target local network`() {
        // 回环接口不具备广播能力、流量不出设备，不在官方本地网络定义内，无需该权限
        assertFalse("http://127.0.0.1:1234".targetsLocalNetwork())
        assertFalse("http://localhost:11434/v1".targetsLocalNetwork())
        assertFalse("http://model.localhost".targetsLocalNetwork())
        assertFalse("http://[::1]:8080/v1".targetsLocalNetwork())
        assertFalse("127.0.0.1:8080".targetsLocalNetwork())
    }

    @Test
    fun `mdns and scheme-less urls target local network`() {
        assertTrue("http://ollama.local:11434/v1".targetsLocalNetwork())
        assertTrue("http://nas.local".targetsLocalNetwork())
        assertTrue("192.168.50.1:17863".targetsLocalNetwork())
        assertTrue("192.168.50.1".targetsLocalNetwork())
    }

    @Test
    fun `urls with credentials still resolve the host`() {
        assertTrue("http://user:pass@192.168.50.1:8080/v1".targetsLocalNetwork())
        assertTrue("http://user:pass@nas.local:8080/v1".targetsLocalNetwork())
    }

    @Test
    fun `ipv6 local addresses target local network`() {
        assertTrue("http://[fe80::1]:8080/v1".targetsLocalNetwork())
        assertTrue("http://[fd00::1]:8080/v1".targetsLocalNetwork())
        assertFalse("http://[2001:4860:4860::8888]:8080/v1".targetsLocalNetwork())
    }

    @Test
    fun `blank and malformed values are ignored`() {
        assertFalse("".targetsLocalNetwork())
        assertFalse("   ".targetsLocalNetwork())
    }

    @Test
    fun `permission is required from api 37 but not below`() {
        assertFalse(LocalNetworkAccess.isRequired(36))
        assertTrue(LocalNetworkAccess.isRequired(37))
        assertTrue(LocalNetworkAccess.isRequired(38))
    }
}
