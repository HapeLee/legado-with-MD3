package io.legado.app.help.config

data class RegexColorRule(
    var name: String,
    var pattern: String,
    var color: Int,
    var fontPath: String = ""
)
