package io.legado.app.debug

import android.content.res.AssetManager
import android.util.Base64
import org.json.JSONObject

internal object DebugParagraphReviewFixture {

    fun createTag(assets: AssetManager, fixtureId: String): String {
        require(fixtureId == FIXTURE_ID) { "unsupported paragraph review fixture" }
        val html = assets.open("debug-fixtures/$fixtureId.html").use { it.readBytes() }
        val htmlBase64 = Base64.encodeToString(html, Base64.NO_WRAP)
        val iconUrl = "data:image/svg+xml;base64," + Base64.encodeToString(
            ICON.toByteArray(),
            Base64.NO_WRAP,
        )
        val click = "$MARKER java.showBrowser('$URL', java.base64Decode('$htmlBase64'))"
        val imageOptions = JSONObject(mapOf("style" to "text", "click" to click)).toString()
        return "<img src=\"$iconUrl,$imageOptions\">"
    }

    private const val FIXTURE_ID = "paragraph-review-v1"
    private const val URL = "https://legado-debug.local/paragraph-reviews"
    const val MARKER = "/*LEGADO_DEBUG_PARAGRAPH_REVIEW_V1*/"
    private const val ICON = """<svg xmlns="http://www.w3.org/2000/svg" width="48" height="32" viewBox="0 0 48 32"><path fill="#4f6bed" d="M4 2h40a4 4 0 0 1 4 4v17a4 4 0 0 1-4 4H20l-8 5v-5H4a4 4 0 0 1-4-4V6a4 4 0 0 1 4-4z"/><text x="24" y="21" text-anchor="middle" font-family="sans-serif" font-size="16" font-weight="700" fill="white">3</text></svg>"""
}
