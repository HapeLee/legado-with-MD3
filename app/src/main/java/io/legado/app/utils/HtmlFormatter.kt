package io.legado.app.utils

import io.legado.app.model.analyzeRule.AnalyzeUrl
import org.jsoup.Jsoup
import java.net.URL

@Suppress("RegExpRedundantEscape")
object HtmlFormatter {
    private val nbspRegex = "(&nbsp;)+".toRegex()
    private val espRegex = "(&ensp;|&emsp;)".toRegex()
    private val noPrintRegex = "(&thinsp;|&zwnj;|&zwj;|\u2009|\u200C|\u200D)".toRegex()
    private val wrapHtmlRegex = "</?(?:div|p|br|hr|h\\d|article|dd|dl)[^>]*>".toRegex()
    private val commentRegex = "<!--[^>]*-->".toRegex() //注释
    private val notImgHtmlRegex = "</?(?!img)[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()
    private val otherHtmlRegex = "</?[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()

    // 字数统计专用：正文里携带的图片 Base64/SVG 源码不能计入字数。
    // 容器型媒体标签整体连内容一起删除(base64、SVG 路径数据都藏在标签内部)
    private val mediaBlockRegex = Regex(
        "(?is)<(script|style|svg|math|video|audio|canvas|picture|iframe|object|embed|figure|template)\\b[^>]*>.*?</\\1\\s*>"
    )
    // 未闭合的 <svg>(后面没有 </svg>): 删到结尾, 避免剩余矢量数据被当成正文
    private val unclosedSvgRegex = Regex("(?is)<svg\\b[^>]*>[\\s\\S]*$")
    // 自闭合/空元素标签(img 的 src 里就是整段 data:image;base64)
    private val voidMediaRegex = Regex(
        "(?is)<(?:img|br|hr|input|source|track|area|col|link|meta)\\b[^>]*/?>"
    )
    // 散落在属性之外的 data: URI 与 XML 声明/doctype
    private val dataUriRegex = Regex(
        "(?i)['\"(]?data:[a-z0-9.+-]+/[a-z0-9.+,-]+(?:;[a-z0-9.+-]+)?(?:;base64)?[^\\s'\"<>)]*['\")]?"
    )
    private val xmlDeclRegex = Regex("(?is)<\\?[\\s\\S]*?\\?>|<!doctype[^>]*>")
    // Markdown 图片/链接语法 ![alt](url)
    private val markdownMediaRegex = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")

    // 简介前缀与按钮 onclick 片段，供 formatReadableText 使用
    private val introPrefixRegex = Regex("^<(usehtml|useweb|md)>", RegexOption.IGNORE_CASE)
    private val onClickSuffixRegex = Regex("@onclick:[^<\\n]*", RegexOption.IGNORE_CASE)

    private val formatImagePattern = Regex(
        "<img[^>]*\\ssrc\\s*=\\s*['\"]([^'\"{>]*\\{(?:[^{}]|\\{[^}>]+\\})+\\})['\"][^>]*>|<img[^>]*\\s(?:data-src|src)\\s*=\\s*['\"]([^'\">]+)['\"][^>]*>|<img[^>]*\\sdata-[^=>]*=\\s*['\"]([^'\">]*)['\"][^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val indent1Regex = "\\s*\\n+\\s*".toRegex()
    private val indent2Regex = "^[\\n\\s]+".toRegex()
    private val lastRegex = "[\\n\\s]+$".toRegex()
    private const val PARAGRAPH_INDENT = "　　"

    //半角空白由 indent 正则处理, 这里去掉全角及特殊宽度空格
    private val blankChars = charArrayOf(
        ' ', '\t', '\u00a0', '\u2002', '\u2003', '\u2009', '\u3000'
    )

    //简介里重复书籍信息的字段, 整行丢弃
    private const val metaLabels =
        "书名|名称|书籍名称|作者|译者|分类|类别|类型|题材|状态|连载状态|字数|标签|关键字|来源|首发|" +
            "更新时间|最后更新|最近更新|最新章节|最新更新"

    //简介自身的标题, 只去标题保留正文
    private const val introLabels = "内容简介|作品简介|小说简介|内容介绍|简介|文案|摘要"
    private val metaLineRegex =
        "^(?:[【\\[(（](?:$metaLabels)[】\\])）]|(?:$metaLabels)\\s*[：:])\\s*.{0,40}$".toRegex()
    private val introLabelRegex =
        "^(?:[【\\[(（](?:$introLabels)[】\\])）]|(?:$introLabels)\\s*[：:])\\s*".toRegex()

    //聚合类书源常把服务/登录状态排版进简介, 统一是"图标 + 短标签：值"的整行, 标签词无法穷举
    private const val lineIcons = "(?:\\p{So}[\\uFE0F\\u200D\\s\\u200E]*)+"
    private val iconMetaLineRegex = "^$lineIcons[^：:\\s]{1,8}\\s*[：:]\\s*.{0,40}$".toRegex()

    //只有符号没有文字的分隔行/占位行
    private val decorationLineRegex = "^[^\\p{L}\\p{N}]+$".toRegex()

    fun format(html: String?, otherRegex: Regex = otherHtmlRegex): String =
        format(html, otherRegex, "　　")

    private fun format(html: String?, otherRegex: Regex, paragraphIndent: String): String {
        html ?: return ""
        return html.replace(nbspRegex, " ")
            .replace(espRegex, " ")
            .replace(noPrintRegex, "")
            .replace(wrapHtmlRegex, "\n")
            .replace(commentRegex, "")
            .replace(otherRegex, "")
            .replace(indent1Regex, "\n$paragraphIndent")
            .replace(indent2Regex, paragraphIndent)
            .replace(lastRegex, "")
    }

    /**
     * Formats untrusted HTML for plain-text UI surfaces.
     * Script and style elements must be removed with their contents before stripping tags.
     * 各来源的段首缩进宽度不一, 先清空再统一补两个全角空格。
     * 同时丢弃与书籍信息重复的字段行(书名/作者/最新章节等)。
     */
    fun formatDisplayText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val document = Jsoup.parseBodyFragment(html)
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        body.select("script, style, noscript").remove()
        return format(body.html(), otherHtmlRegex, "")
            .lineSequence()
            .map { it.trim(*blankChars) }
            .filterNot { it.isEmpty() || metaLineRegex.matches(it) }
            .map { it.replaceFirst(introLabelRegex, "").trim(*blankChars) }
            .filterNot { it.isEmpty() }
            .joinToString("\n") { PARAGRAPH_INDENT + it }
    }

    /**
     * Returns reader content as text for character counting.
     *
     * Cached chapters can retain image tags for rendering; their tag names, attributes, and
     * URLs are markup rather than readable content and must not contribute to the count.
     */
    fun textForWordCount(html: String): String {
        if (html.isBlank()) return ""
        val document = Jsoup.parseBodyFragment(html)
        document.outputSettings().prettyPrint(false)
        document.body().select("script, style, noscript").remove()
        return document.text()
    }

    /**
     * 书架/列表用的简介: 在 [formatDisplayText] 之上再丢掉书源排版进简介的状态面板,
     * 即"📡 当前服务：xxx"这类图标开头的整行, 以及纯符号的分隔行。
     * 详情页不做这一步 —— 那里是书源和用户交互的地方(登录提示等), 状态面板有用。
     */
    fun formatIntroText(html: String?): String {
        return formatDisplayText(html)
            .lineSequence()
            .map { it.trim(*blankChars) }
            .filterNot {
                it.isEmpty() || decorationLineRegex.matches(it) || iconMetaLineRegex.matches(it)
            }
            .joinToString("\n") { PARAGRAPH_INDENT + it }
    }

    /**
     * 与 [formatIntroText] 同样清洗, 但压成单行摘要。
     * 列表/卡片只显示一两行并 ellipsis, 保留换行会让首段之后的内容被直接截断。
     */
    fun formatSummaryText(html: String?): String {
        return formatIntroText(html)
            .lineSequence()
            .map { it.trim(*blankChars) }
            .filterNot { it.isEmpty() }
            .joinToString(" ")
    }

    fun formatKeepImg(html: String?, redirectUrl: URL? = null): String {
        html ?: return ""
        val keepImgHtml = format(html, notImgHtmlRegex)

        //正则的“|”处于顶端而不处于（）中时，具有类似||的熔断效果，故以此机制简化原来的代码
        var appendPos = 0
        val sb = StringBuilder()
        for (m in formatImagePattern.findAll(keepImgHtml)) {
            var param = ""
            sb.append(
                keepImgHtml.substring(appendPos, m.range.first), "<img src=\"${
                    NetworkUtils.getAbsoluteURL(
                        redirectUrl,
                        m.groups[1]?.value?.let {
                            val urlMatch = AnalyzeUrl.paramPattern.find(it)
                            if (urlMatch != null) {
                                param = ',' + it.substring(urlMatch.range.last + 1)
                                it.substring(0, urlMatch.range.first)
                            } else it
                        } ?: m.groups[2]?.value ?: m.groups[3]!!.value
                    ) + param
                }\">"
            )
            appendPos = m.range.last + 1
        }
        if (appendPos < keepImgHtml.length) sb.append(
            keepImgHtml.substring(
                appendPos,
                keepImgHtml.length
            )
        )
        return sb.toString()
    }

    /**
     * 把书源简介渲染成与详情页观感一致的“可读纯文本”，用于
     * 不支持交互的只读卡片（如阅读页目录侧栏“信息”页）：
     * 1) 去掉 <usehtml>/<useweb>/<md> 前缀（与详情页 parseBookInfoIntro 同一处理，
     *    否则字面量前缀会被当未知标签丢弃但尾部残留调用代码）；
     * 2) 丢弃按钮里的 @onclick:JS 片段（详情页渲染成可点按钮，只读场景只保留按钮文字，
     *    如“💬 本书讨论”）；
     * 3) 不补段首缩进（formatDisplayText 会给每行加两个全角空格，只读卡片里显得
     *    每行缩进，详情页则没有）。
     */
    fun formatReadableText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        var body = html.trim()
        introPrefixRegex.find(body)?.let { m ->
            val lastLt = body.lastIndexOf('<')
            body = if (lastLt > m.range.last) {
                body.substring(m.range.last + 1, lastLt)
            } else {
                body.substring(m.range.last + 1)
            }
        }
        body = onClickSuffixRegex.replace(body, "")
        val document = Jsoup.parseBodyFragment(body)
        document.outputSettings().prettyPrint(false)
        document.select("script, style, noscript").remove()
        return format(document.body().html(), otherHtmlRegex, "")
    }

    /**
     * 统计正文真实可读字数。部分书源的 content 携带
     * `<img src="data:image/png;base64,...">`、内联 `<svg>...</svg>` 等富文本源码，
     * 直接取 content.length 会把整段 Base64/标签算进目录字数（几百字显示成几千字）。
     * 这里先剔除媒体标签及其内容、残留 data: URI 与 Markdown 图片语法，
     * 再按纯文本（解码实体、去空白）计算字符数。
     */
    fun countReadableTextLength(content: String?): Int {
        if (content.isNullOrEmpty()) return 0
        var text = mediaBlockRegex.replace(content, "")
        text = unclosedSvgRegex.replace(text, "")
        text = voidMediaRegex.replace(text, "")
        text = markdownMediaRegex.replace(text, "")
        text = xmlDeclRegex.replace(text, "")
        text = dataUriRegex.replace(text, "")
        // 剩余标签与 HTML 实体交给 Jsoup 按纯文本解出
        text = Jsoup.parse(text).text()
        return text.count { !it.isWhitespace() && it != '\u00a0' && it != '\u3000' }
    }
}
