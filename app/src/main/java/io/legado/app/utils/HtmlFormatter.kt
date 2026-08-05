package io.legado.app.utils

import io.legado.app.model.analyzeRule.AnalyzeUrl
import org.jsoup.Jsoup
import java.net.URL
import java.util.regex.Pattern

@Suppress("RegExpRedundantEscape")
object HtmlFormatter {
    private val nbspRegex = "(&nbsp;)+".toRegex()
    private val espRegex = "(&ensp;|&emsp;)".toRegex()
    private val noPrintRegex = "(&thinsp;|&zwnj;|&zwj;|\u2009|\u200C|\u200D)".toRegex()
    private val wrapHtmlRegex = "</?(?:div|p|br|hr|h\\d|article|dd|dl)[^>]*>".toRegex()
    private val commentRegex = "<!--[^>]*-->".toRegex() //注释
    private val notImgHtmlRegex = "</?(?!img)[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()
    private val otherHtmlRegex = "</?[a-zA-Z]+(?=[ >])[^<>]*>".toRegex()
    private val formatImagePattern = Pattern.compile(
        "<img[^>]*\\ssrc\\s*=\\s*['\"]([^'\"{>]*\\{(?:[^{}]|\\{[^}>]+\\})+\\})['\"][^>]*>|<img[^>]*\\s(?:data-src|src)\\s*=\\s*['\"]([^'\">]+)['\"][^>]*>|<img[^>]*\\sdata-[^=>]*=\\s*['\"]([^'\">]*)['\"][^>]*>",
        Pattern.CASE_INSENSITIVE
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
        val matcher = formatImagePattern.matcher(keepImgHtml)
        var appendPos = 0
        val sb = StringBuilder()
        while (matcher.find()) {
            var param = ""
            sb.append(
                keepImgHtml.substring(appendPos, matcher.start()), "<img src=\"${
                    NetworkUtils.getAbsoluteURL(
                        redirectUrl,
                        matcher.group(1)?.let {
                            val urlMatcher = AnalyzeUrl.paramPattern.matcher(it)
                            if (urlMatcher.find()) {
                                param = ',' + it.substring(urlMatcher.end())
                                it.substring(0, urlMatcher.start())
                            } else it
                        } ?: matcher.group(2) ?: matcher.group(3)!!
                    ) + param
                }\">"
            )
            appendPos = matcher.end()
        }
        if (appendPos < keepImgHtml.length) sb.append(
            keepImgHtml.substring(
                appendPos,
                keepImgHtml.length
            )
        )
        return sb.toString()
    }
}
