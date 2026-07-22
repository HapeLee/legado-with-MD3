package io.legado.app.utils

import android.app.Application
import io.legado.app.exception.RegexTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class RegexExtensionsTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test(timeout = 10_000)
    fun `JS 替换脚本超时后终止执行`() {
        System.clearProperty(JS_STATE_KEY)
        try {
            assertThrows(RegexTimeoutException::class.java) {
                "text".replace(
                    regex = Regex("text"),
                    replacement = """
                        @js:
                        var count = 0;
                        while (true) {
                            java.lang.System.setProperty('$JS_STATE_KEY', String(++count));
                        }
                    """.trimIndent(),
                    timeout = 100,
                )
            }

            val countAfterReturn = System.getProperty(JS_STATE_KEY)?.toLongOrNull()
            assertTrue("JS 应在超时前进入循环", countAfterReturn != null && countAfterReturn > 0)
            Thread.sleep(250)
            assertEquals(countAfterReturn.toString(), System.getProperty(JS_STATE_KEY))
        } finally {
            System.clearProperty(JS_STATE_KEY)
        }
    }

    @Test
    fun `JS 替换脚本正常返回结果`() {
        val result = "text".replace(
            regex = Regex("text"),
            replacement = "@js:result.toUpperCase()",
            timeout = 1_000,
        )

        assertEquals("TEXT", result)
    }

    @Test
    fun `普通正则替换保留捕获组语义`() {
        val result = "text-123".replace(
            regex = Regex("([a-z]+)-(\\d+)"),
            replacement = "$2-$1",
            timeout = 1_000,
        )

        assertEquals("123-text", result)
    }

    @Test(timeout = 10_000)
    fun `灾难性正则回溯超时后终止执行`() {
        val input = CountingCharSequence("a".repeat(30_000).plus("!"))

        assertThrows(RegexTimeoutException::class.java) {
            input.replace(
                regex = Regex("(a+)+$"),
                replacement = "",
                timeout = 100,
            )
        }

        val readCountAfterReturn = input.readCount.get()
        assertTrue("正则应在超时前开始读取输入", readCountAfterReturn > 0)
        Thread.sleep(250)
        assertEquals(readCountAfterReturn, input.readCount.get())
    }

    private class CountingCharSequence(private val value: String) : CharSequence {

        val readCount = AtomicLong()

        override val length: Int get() = value.length

        override fun get(index: Int): Char {
            readCount.incrementAndGet()
            return value[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            value.subSequence(startIndex, endIndex)
    }

    private companion object {
        const val JS_STATE_KEY = "io.legado.app.utils.RegexExtensionsTest.jsState"
    }
}
