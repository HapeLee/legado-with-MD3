package io.legado.app.model.analyzeRule

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AnalyzeUrlPagingTest {
    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun pageExpressionsMatchTheActualUrlParser() {
        val rules = mapOf(
            "https://example.test/list?page={{page}}" to "https://example.test/list?page=3",
            "https://example.test/list?page={{ page }}" to "https://example.test/list?page=3",
            "https://example.test/list?offset={{(page - 1) * 20}}" to "https://example.test/list?offset=40",
            "@js: 'https://example.test/list?page=' + page" to "https://example.test/list?page=3",
            "<js>'https://example.test/list?page=' + page</js>" to "https://example.test/list?page=3",
            "https://example.test/list<1,2,3>" to "https://example.test/list3",
        )
        rules.forEach { (rule, expected) ->
            assertTrue(rule, AnalyzeUrl.supportsPageSelection(rule))
            assertEquals(rule, expected, AnalyzeUrl(rule, page = 3).url)
        }
    }

    @Test
    fun inspectionDoesNotExecuteSourceCode() {
        assertTrue(AnalyzeUrl.supportsPageSelection("@js: throw new Error('must not execute'); page"))
        assertTrue(AnalyzeUrl.supportsPageSelection("https://example.test/{{java.get('remote') + page}}"))
        assertTrue(AnalyzeUrl.supportsPageSelection("@js: `https://example.test/list?page=${'$'}{page}`"))
    }

    @Test
    fun pageTextAndLocalVariablesDoNotEnablePageSelection() {
        val rules = listOf(
            "https://example.test/list?page=1",
            "https://example.test/{{'page'}}",
            "@js: /* page */ 'https://example.test/list'",
            "@js: 'https://example.test/page'",
            "https://example.test/{{book.page}}",
            "https://example.test/{{({page: 1}).page}}",
            "https://example.test/{{(function(page) { return page; })(1)}}",
            "@js: var page = 1; 'https://example.test/list?page=' + page",
        )
        rules.forEach { assertFalse(it, AnalyzeUrl.supportsPageSelection(it)) }
        assertFalse(AnalyzeUrl.supportsPageSelection(null))
    }
}
