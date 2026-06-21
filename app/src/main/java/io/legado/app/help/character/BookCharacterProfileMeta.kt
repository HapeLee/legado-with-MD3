package io.legado.app.help.character

import io.legado.app.data.entities.BookCharacter

object BookCharacterProfileMeta {

    private val ageLineRegex = Regex("""^\s*(?:年纪|年龄)\s*[:：]\s*(.*?)\s*$""")

    fun ageOf(character: BookCharacter): String {
        return ageFromAttributes(character.attributes)
    }

    fun ageFromAttributes(attributes: String): String {
        return attributes.lineSequence()
            .mapNotNull { line ->
                ageLineRegex.matchEntire(line)?.groupValues?.getOrNull(1)?.trim()
            }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .take(40)
    }

    fun attributesWithoutAge(attributes: String): String {
        return attributes.lineSequence()
            .filterNot { ageLineRegex.matches(it) }
            .joinToString("\n")
            .trim()
    }

    fun mergeAgeIntoAttributes(age: String, attributes: String): String {
        val cleanAge = sanitizeAge(age)
        val body = attributesWithoutAge(attributes)
        return when {
            cleanAge.isBlank() -> body
            body.isBlank() -> "年纪：$cleanAge"
            else -> "年纪：$cleanAge\n$body"
        }
    }

    fun sanitizeAge(value: String): String {
        return value
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(40)
    }
}
