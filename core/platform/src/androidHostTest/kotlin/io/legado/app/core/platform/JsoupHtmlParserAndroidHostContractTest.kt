package io.legado.app.core.platform

class JsoupHtmlParserAndroidHostContractTest : HtmlParserContractTest() {
    override fun createParser(): HtmlParser = JsoupHtmlParser
}
