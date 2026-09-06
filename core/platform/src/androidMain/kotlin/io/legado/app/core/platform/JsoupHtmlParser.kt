package io.legado.app.core.platform

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * jsoup 实现：委托 `org.jsoup.Jsoup`（1.16.2，AGENTS.md 锁定版本）。
 * androidMain 与 desktopMain 都是 JVM、都能用同一份 jsoup，故实现相同。
 *
 * 不做任何规范化/清洗：共享层拿到的就是 jsoup 的原生解析结果，
 * 从而保证存量书源与正文清洗（[HtmlFormatter] 等）的行为零变化。
 */
object JsoupHtmlParser : HtmlParser {
    override fun parseBodyFragment(html: String): HtmlDocument =
        JsoupHtmlDocument(Jsoup.parseBodyFragment(html))
}

private class JsoupHtmlDocument(private val document: Document) : HtmlDocument {
    override fun body(): HtmlElement = JsoupHtmlElement(document.body())

    override fun setPrettyPrint(enabled: Boolean) {
        document.outputSettings().prettyPrint(enabled)
    }
}

private class JsoupHtmlElement(private val element: Element) : HtmlElement {
    override fun select(cssQuery: String): List<HtmlElement> =
        element.select(cssQuery).map(::JsoupHtmlElement)

    override fun remove() {
        element.remove()
    }

    override fun html(): String = element.html()
}
