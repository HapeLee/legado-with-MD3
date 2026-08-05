package io.legado.app.ui.main.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class EllipsizeSourceNameTest {

    @Test
    fun `keeps names of four characters or fewer`() {
        assertEquals("", ellipsizeSourceName(""))
        assertEquals("知乎", ellipsizeSourceName("知乎"))
        assertEquals("机核网站", ellipsizeSourceName("机核网站"))
    }

    @Test
    fun `truncates to three characters plus ellipsis`() {
        assertEquals("虎扑步…", ellipsizeSourceName("虎扑步行街"))
        assertEquals("少数派…", ellipsizeSourceName("少数派日报"))
        assertEquals("abc…", ellipsizeSourceName("abcdefg"))
    }

    @Test
    fun `does not split surrogate pairs`() {
        // 4 个 code point（3 个 emoji + 1 个汉字）应原样保留
        assertEquals("😀😁😂新", ellipsizeSourceName("😀😁😂新"))
        // 5 个 code point 时截断到前 3 个 emoji，不能切出半个代理对
        assertEquals("😀😁😂…", ellipsizeSourceName("😀😁😂新闻"))
    }
}
