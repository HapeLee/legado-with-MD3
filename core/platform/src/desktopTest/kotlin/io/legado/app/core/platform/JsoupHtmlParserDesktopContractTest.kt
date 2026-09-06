package io.legado.app.core.platform

class JsoupHtmlParserDesktopContractTest : HtmlParserContractTest() {
    override fun createParser(): HtmlParser = JsoupHtmlParser
}
