package io.legado.app.support

import com.google.gson.JsonParser

/** Shared inputs and reviewed main-branch goldens; no expected values come from the tested parser. */
object ReaderSemanticFixtures {
    fun read(name: String): String = checkNotNull(javaClass.getResourceAsStream("/reader/semantic/$name"))
        .bufferedReader().use { it.readText() }
    val html get() = read("compatibility.html")
    val css get() = read("compatibility.css")
    val expected get() = JsonParser.parseString(read("compatibility.expected.json")).asJsonObject
    val body get() = expected.get("semanticContent").asString
    val paragraphs get() = expected.getAsJsonArray("paragraphs").map { it.asString }
}
