package io.legado.app.constant

@Suppress("RegExpRedundantEscape", "unused")
object AppPattern {
    val JS_PATTERN: Regex =
        Regex("<js>([\\w\\W]*?)</js>|@js:([\\w\\W]*)", RegexOption.IGNORE_CASE)
    val WebJS_PATTERN: Regex =
        Regex("@webjs:([\\w\\W]{5,})", RegexOption.IGNORE_CASE)
    val EXP_PATTERN: Regex = Regex("\\{\\{([\\w\\W]*?)\\}\\}")

    //匹配格式化后的图片格式
    val imgPattern: Regex = Regex("<img[^>]*src=\"([^\"]*(?:\"[^>]+\\})?)\"[^>]*>")

    //匹配自定义html格式字符串
    val useHtmlRegex = Regex("<usehtml>.*?</usehtml>", RegexOption.DOT_MATCHES_ALL) //.包含换行

    //匹配html字符串中的head
    val htmlHeadRegex = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)

    //dataURL图片类型
    val dataUriRegex = Regex("^data:.*?;base64,(.*)")
    //提取标题中的段评
    val imgRegex = Regex("(.*)((?:data|https?):[\\s\\S]+)$")
    //自定义图片样式
    val imgStyRegex = Regex("style[\"'\\s]*:\\s*[\"']([^\"']*)[\"']")
    //匹配章节信息中的字数
    val wordCountRegex = Regex("(?:^|字数[：:、]?|\\s+)([0-9万千百\\.]{1,6}字)")

    //正文不计入字数的字符
    val noWordCountRegex = Regex("[\\s\\u200B-\\u200F\\uFEFF]")

    //提取链接中的域名
    val domainRegex = Regex("^https?://([^:/]+)",RegexOption.IGNORE_CASE)

    val nameRegex = Regex("\\s+作\\s*者.*|\\s+\\S+\\s+著")
    val authorRegex = Regex("^\\s*作\\s*者[:：\\s]+|\\s+著")
    val fileNameRegex = Regex("[\\\\/:*?\"<>|.]")
    val fileNameRegex2 = Regex("[\\\\/:*?\"<>|]")
    val splitGroupRegex = Regex("[,;，；]")
    val titleNumPattern: Regex = Regex("(第)(.+?)(章)")

    //书源调试信息中的各种符号
    val debugMessageSymbolRegex = Regex("[⇒◇┌└≡]")

    //本地书籍支持类型
    val bookFileRegex = Regex(".*\\.(txt|epub|umd|pdf|mobi|azw3|azw)", RegexOption.IGNORE_CASE)
    //压缩文件支持类型
    val archiveFileRegex = Regex(".*\\.(zip|cbz|rar|7z)$", RegexOption.IGNORE_CASE)

    /**
     * 所有标点
     */
    val bdRegex = Regex("(\\p{P})+")

    /**
     * 换行
     */
    val rnRegex = Regex("[\\r\\n]")

    /**
     * 不发音段落判断
     */
    val notReadAloudRegex = Regex("^(\\s|\\p{C}|\\p{P}|\\p{Z}|\\p{S})+$")

    val xmlContentTypeRegex = "(application|text)/\\w*\\+?xml.*".toRegex()

    val semicolonRegex = ";".toRegex()

    val equalsRegex = "=".toRegex()

    val spaceRegex = "\\s+".toRegex()

    val regexCharRegex = "[{}()\\[\\].+*?^$\\\\|]".toRegex()

    val LFRegex = "\n".toRegex()
}
