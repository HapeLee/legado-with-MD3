package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleSvgPolicyTest {

    private val plainSvg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
          <path d="M4 4h56v56H4z" fill="none" stroke="${'$'}{color}"/>
          <text x="32" y="32" font-family="asset://font">${'$'}{num}</text>
        </svg>
    """.trimIndent()

    @Test
    fun acceptsPlainTemplateAndCollectsPackageReferences() {
        BubbleSvgPolicy.validate(plainSvg)
        assertEquals(setOf("asset://font"), BubbleSvgPolicy.packageReferences(plainSvg))
    }

    @Test
    fun escapesTextForXml() {
        assertEquals(
            "&lt;1&gt;&amp;&quot;&apos;",
            BubbleSvgPolicy.escapeText("<1>&\"'"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsScript() {
        BubbleSvgPolicy.validate("<svg><script>alert(1)</script></svg>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEventHandler() {
        BubbleSvgPolicy.validate("<svg><rect onclick=\"x()\"/></svg>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExternalHref() {
        BubbleSvgPolicy.validate("<svg><image href=\"https://evil.example/a.png\"/></svg>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsXmlEntities() {
        BubbleSvgPolicy.validate("<!DOCTYPE svg [<!ENTITY x \"y\">]><svg/>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignObject() {
        BubbleSvgPolicy.validate("<svg><foreignObject/></svg>")
    }

    @Test
    fun allowsInlineDataImageAndFragmentReferences() {
        BubbleSvgPolicy.validate(
            "<svg><use href=\"#grad\"/><image href=\"data:image/png;base64,AAAA\"/></svg>"
        )
    }

    @Test
    fun packageReferencesIgnoreFragmentAndInlineData() {
        assertTrue(
            BubbleSvgPolicy.packageReferences(
                "<svg><use href=\"#g\"/><image href=\"data:image/png;base64,AA\"/></svg>"
            ).isEmpty()
        )
    }
}
