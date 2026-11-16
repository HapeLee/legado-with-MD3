package io.legado.app.data.repository

import io.legado.app.data.entities.BookSource
import io.legado.app.domain.gateway.CheckSourceSettings
import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.exception.TocEmptyException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 书源校验的判定规则：设置归一化、失败分组映射、以及分组是否参与「失效」判定。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class BookSourceCheckRuleTest {

    @Test
    fun `normalized clamps zero timeout so check cannot fail instantly`() {
        val settings = CheckSourceSettings(timeoutMillis = 0).normalized()
        assertEquals(CheckSourceSettings.MIN_TIMEOUT_MILLIS, settings.timeoutMillis)
    }

    @Test
    fun `normalized clamps out of range values`() {
        val tooSmall = CheckSourceSettings(threadCount = 0).normalized()
        assertEquals(CheckSourceSettings.MIN_THREAD_COUNT, tooSmall.threadCount)

        val tooLarge = CheckSourceSettings(
            timeoutMillis = 10 * 60 * 1000L,
            threadCount = 1000,
        ).normalized()
        assertEquals(CheckSourceSettings.MAX_TIMEOUT_MILLIS, tooLarge.timeoutMillis)
        assertEquals(CheckSourceSettings.MAX_THREAD_COUNT, tooLarge.threadCount)
    }

    @Test
    fun `normalized keeps at least one search branch enabled`() {
        val bothOff = CheckSourceSettings(checkSearch = false, checkDiscovery = false).normalized()
        assertTrue(bothOff.checkSearch)

        val discoveryOnly = CheckSourceSettings(
            checkSearch = false,
            checkDiscovery = true,
        ).normalized()
        assertFalse(discoveryOnly.checkSearch)
        assertTrue(discoveryOnly.checkDiscovery)
    }

    @Test
    fun `timeout failure marks timeout group`() {
        val timeout = runCatching {
            runBlocking {
                withTimeout(1L) { delay(1_000L) }
            }
        }.exceptionOrNull()!!
        assertEquals(GROUP_TIMEOUT, checkFailureGroup(timeout))
    }

    @Test
    fun `no stack trace failure keeps the group it already carries`() {
        assertNull(checkFailureGroup(NoStackTraceException(GROUP_SEARCH_INVALID)))
        assertNull(checkFailureGroup(ContentEmptyException("正文为空")))
    }

    @Test
    fun `unexpected failure marks site invalid`() {
        assertEquals(GROUP_SITE_INVALID, checkFailureGroup(IllegalStateException("boom")))
    }

    @Test
    fun `book failure group depends on branch type`() {
        assertEquals("${TYPE_SEARCH}正文失效", bookFailureGroup(TYPE_SEARCH, ContentEmptyException("x")))
        assertEquals(
            "${TYPE_DISCOVERY}目录失效",
            bookFailureGroup(TYPE_DISCOVERY, TocEmptyException("x")),
        )
        assertNull(bookFailureGroup(TYPE_SEARCH, IllegalStateException("x")))
    }

    @Test
    fun `groups written by check are recognised as invalid groups`() {
        val source = BookSource(bookSourceUrl = "https://a.example", bookSourceName = "A")
        source.addGroup(GROUP_SEARCH_INVALID)
        source.addGroup(GROUP_TIMEOUT)
        assertEquals(
            setOf(GROUP_SEARCH_INVALID, GROUP_TIMEOUT),
            source.getInvalidGroupNames().split(",").map { it.trim() }.toSet(),
        )

        source.removeInvalidGroups()
        assertEquals("", source.getInvalidGroupNames())
    }

    /**
     * 与基线一致：空搜索规则只写分组提示，不参与「失效」判定。
     * 若要改成判失效，必须同时调整 [BookSource.getInvalidGroupNames] 的口径。
     */
    @Test
    fun `empty search rule group is not part of the invalid judgement`() {
        val source = BookSource(bookSourceUrl = "https://a.example", bookSourceName = "A")
        source.addGroup(GROUP_SEARCH_URL_EMPTY)
        assertEquals("", source.getInvalidGroupNames())
        assertTrue(source.bookSourceGroup!!.contains(GROUP_SEARCH_URL_EMPTY))
    }

    @Test
    fun `domain failure group is part of the invalid judgement`() {
        val source = BookSource(bookSourceUrl = "https://a.example", bookSourceName = "A")
        source.addGroup(GROUP_DOMAIN_INVALID)
        assertEquals(GROUP_DOMAIN_INVALID, source.getInvalidGroupNames())
    }

    @Test
    fun `domain probe port follows scheme when url omits it`() {
        assertEquals(443, resolveProbePort("https", -1))
        assertEquals(443, resolveProbePort("HTTPS", -1))
        assertEquals(80, resolveProbePort("http", -1))
        assertEquals(80, resolveProbePort(null, -1))
        assertEquals(8443, resolveProbePort("https", 8443))
        assertEquals(8080, resolveProbePort("http", 8080))
    }

    @Test
    fun `quick mode and domain check stay off by default`() {
        val settings = CheckSourceSettings()
        assertFalse(settings.quickMode)
        assertFalse(settings.checkDomain)
        val normalized = settings.normalized()
        assertFalse(normalized.quickMode)
        assertFalse(normalized.checkDomain)
    }
}
