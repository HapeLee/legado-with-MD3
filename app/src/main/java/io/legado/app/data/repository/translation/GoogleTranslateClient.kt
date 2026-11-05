package io.legado.app.data.repository.translation

import androidx.annotation.Keep
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder

/**
 * Google's free gtx endpoint, shared by chapter translation and comic text translation.
 *
 * It has no API key, no id-aligned batch and it rewrites whitespace and punctuation, so callers
 * must send one short text per request and keep their own alignment. Source language stays
 * [AUTO]: comic recognition already decided what the lettering says.
 */
class GoogleTranslateClient {

    suspend fun translate(text: String, targetLanguage: String): Result<String> {
        val encoded = URLEncoder.encode(text, Charsets.UTF_8.name())
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLanguage" +
            "&dj=1&dt=t&ie=UTF-8&q=$encoded"
        val response = try {
            okHttpClient.newCallStrResponse { url(url) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return Result.failure(error)
        }
        if (!response.isSuccessful()) return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
        val translated = runCatching {
            GSON.fromJson(response.body, GoogleTranslateResponse::class.java)?.sentences?.mapNotNull { it.trans }?.joinToString("")
        }.getOrElse { return Result.failure(it) }.orEmpty()
        return if (translated.isNotEmpty()) Result.success(translated) else Result.failure(Exception("Empty translation result"))
    }

    private companion object {
        const val AUTO = "auto"
    }
}

@Keep
private data class GoogleTranslateResponse(val sentences: List<GoogleSentence>?)

@Keep
private data class GoogleSentence(val trans: String?)
