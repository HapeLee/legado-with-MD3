package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkUtilsMediaUrlTest {
    @Test
    fun `protocol relative media url gets an https scheme`() {
        assertEquals(
            "https://example.com/video.mp4",
            NetworkUtils.normalizeMediaUrl("//example.com/video.mp4"),
        )
        assertEquals(
            "https://example.com/video.mp4",
            NetworkUtils.normalizeMediaUrl("  //example.com/video.mp4  "),
        )
    }

    @Test
    fun `absolute and local media urls stay unchanged`() {
        listOf(
            "http://example.com/video.mp4",
            "https://example.com/video.mpd?token=1",
            "file:///cache/video.mp4",
            "content://media/external/video/1",
            "data:text/plain,local",
            "/cache/video.mp4",
        ).forEach { assertEquals(it, NetworkUtils.normalizeMediaUrl(it)) }
    }

    @Test
    fun `triple slash path is not treated as a protocol relative url`() {
        assertEquals(
            "///cache/video.mp4",
            NetworkUtils.normalizeMediaUrl("///cache/video.mp4"),
        )
    }
}
