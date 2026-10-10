package io.legado.app.help.http

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkBlockedNotifierTest {

    @Test
    fun `reports connection failures to a private host without permission`() {
        assertTrue(
            LocalNetworkBlockedNotifier.shouldReport(
                host = "192.168.50.1",
                error = SocketTimeoutException("failed to connect after 15000ms"),
                permissionGranted = false
            )
        )
        assertTrue(
            LocalNetworkBlockedNotifier.shouldReport(
                host = "nas.local",
                error = ConnectException("Connection refused"),
                permissionGranted = false
            )
        )
        assertTrue(
            LocalNetworkBlockedNotifier.shouldReport(
                host = "100.64.0.1",
                error = SocketTimeoutException("timeout"),
                permissionGranted = false
            )
        )
    }

    @Test
    fun `never reports loopback hosts`() {
        listOf("127.0.0.1", "localhost", "::1").forEach { host ->
            assertFalse(
                LocalNetworkBlockedNotifier.shouldReport(
                    host = host,
                    error = ConnectException("Connection refused"),
                    permissionGranted = false
                )
            )
        }
    }

    @Test
    fun `never reports when permission is already granted`() {
        assertFalse(
            LocalNetworkBlockedNotifier.shouldReport(
                host = "192.168.50.1",
                error = SocketTimeoutException("timeout"),
                permissionGranted = true
            )
        )
    }

    @Test
    fun `never reports for public hosts`() {
        assertFalse(
            LocalNetworkBlockedNotifier.shouldReport(
                host = "api.deepseek.com",
                error = SocketTimeoutException("timeout"),
                permissionGranted = false
            )
        )
    }

    @Test
    fun `never reports unrelated failures`() {
        assertFalse(
            LocalNetworkBlockedNotifier.shouldReport(
                host = "192.168.50.1",
                error = UnknownHostException("dns failure"),
                permissionGranted = false
            )
        )
    }
}
