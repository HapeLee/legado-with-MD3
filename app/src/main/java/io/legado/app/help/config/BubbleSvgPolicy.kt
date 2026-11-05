package io.legado.app.help.config

import java.util.Locale

internal object BubbleSvgPolicy {
    fun escapeText(value: String): String = value.replace("&", "&amp;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private val eventHandlerPattern = Regex("""\son[a-z]+\s*=""", RegexOption.IGNORE_CASE)
    private val hrefPattern = Regex(
        """(?:href|xlink:href)\s*=\s*(["'])(.*?)\1""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val urlPattern = Regex(
        """url\s*\((.*?)\)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun validate(svg: String) {
        val lower = svg.lowercase(Locale.ROOT)
        require("<svg" in lower) { "bubble template is not SVG" }
        require("<!doctype" !in lower && "<!entity" !in lower) { "bubble SVG contains XML entities" }
        require("<?xml-stylesheet" !in lower) { "bubble SVG contains an external stylesheet" }
        require("<script" !in lower && "<foreignobject" !in lower) { "bubble SVG contains active content" }
        require(!eventHandlerPattern.containsMatchIn(svg)) { "bubble SVG contains event handlers" }
        for (match in hrefPattern.findAll(svg)) {
            requireSafeReference(match.groupValues[2].trim(), "external reference")
        }
        for (match in urlPattern.findAll(svg)) {
            requireSafeReference(match.groupValues[1].trim().trim('"', '\''), "external URL")
        }
    }

    fun packageReferences(svg: String): Set<String> {
        return buildSet {
            hrefPattern.findAll(svg).forEach { match ->
                match.groupValues[2].trim().takeIf(::isPackageReference)?.let(::add)
            }
            urlPattern.findAll(svg).forEach { match ->
                match.groupValues[1].trim().trim('"', '\'')
                    .takeIf(::isPackageReference)
                    ?.let(::add)
            }
        }
    }

    private fun requireSafeReference(value: String, description: String) {
        require(
            value.startsWith("#") ||
                value.startsWith("data:image/", ignoreCase = true) ||
                PackageResourcePolicy.isSafeReference(value)
        ) {
            "bubble SVG contains an $description"
        }
    }

    private fun isPackageReference(value: String): Boolean {
        return !value.startsWith("#") &&
            !value.startsWith("data:image/", ignoreCase = true) &&
            PackageResourcePolicy.isSafeReference(value)
    }
}
