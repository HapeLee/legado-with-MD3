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
        assertTrue("http://127.0.0.1:1234".targetsLocalNetwork())
        assertTrue("http://169.254.10.10".targetsLocalNetwork())
    }

    @Test
    fun `public hosts do not target local network`() {
        assertFalse("https://api.deepseek.com".targetsLocalNetwork())
        assertFalse("https://api.openai.com/v1".targetsLocalNetwork())
        assertFalse("http://8.8.8.8".targetsLocalNetwork())
        // 172.32/16 已经不在 172.16/12 里
        assertFalse("http://172.32.0.1/v1".targetsLocalNetwork())
    }

    @Test
    fun `localhost and scheme-less urls target local network`() {
        assertTrue("http://localhost:11434/v1".targetsLocalNetwork())
        assertTrue("http://model.localhost".targetsLocalNetwork())
        assertTrue("192.168.50.1:17863".targetsLocalNetwork())
        assertTrue("192.168.50.1".targetsLocalNetwork())
    }

    @Test
    fun `urls with credentials still resolve the host`() {
        assertTrue("http://user:pass@192.168.50.1:8080/v1".targetsLocalNetwork())
        assertTrue("http://user:pass@model.localhost:8080/v1".targetsLocalNetwork())
    }

    @Test
    fun `ipv6 loopback and local addresses target local network`() {
        assertTrue("http://[::1]:8080/v1".targetsLocalNetwork())
        assertTrue("http://[fe80::1]:8080/v1".targetsLocalNetwork())
        assertTrue("http://[fd00::1]:8080/v1".targetsLocalNetwork())
        assertFalse("http://[2001:4860:4860::8888]:8080/v1".targetsLocalNetwork())
    }

    @Test
    fun `blank and malformed values are ignored`() {
        assertFalse("".targetsLocalNetwork())
        assertFalse("   ".targetsLocalNetwork())
    }
}
