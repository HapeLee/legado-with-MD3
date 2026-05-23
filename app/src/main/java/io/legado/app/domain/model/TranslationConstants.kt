package io.legado.app.domain.model

object TranslationConstants {

    const val PROVIDER_OPENAI = "openai"
    const val PROVIDER_GOOGLE = "google"

    val providerDisplayNames = listOf("Google Translate", "OpenAI适配接口")
    val providerValues = listOf(PROVIDER_GOOGLE, PROVIDER_OPENAI)

    val targetLanguages = listOf(
        "zh" to "简体中文",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "ru" to "Русский",
        "ar" to "العربية"
    )

    const val DEFAULT_PROMPT =
        """You are a professional literary translator, please translate according to the following requirements:

1. Keep the original paragraph count and order unchanged
2. Maintain the literary style and tone of the original text
3. Do not summarize, condense, or omit any content
4. Only output the translation result, do not add comments or explanations
5. Keep name consistency across abbreviations/nicknames (e.g., Alexander → Alex → same name). Add nickname mapping to dictionary.

"""

    const val OUTPUT_FORMAT = """Output is divided into two parts:

**New** proper nouns, place names that need to be recorded for context, and the translation result.

Only select the most common and important terms (max 10) to include in the dictionary.

Output format as follows, IMPORTANT, **dictionary** part must begin with english word **[dictionary]**, MUST NOT start with any other words. **result** part must begin with english word **[result]**,  MUST NOT start with any other words:
<example>
[dictionary]
Jack -> 杰克
Harry Port -> 哈利波特

[result]
...
</example>
    """
}
