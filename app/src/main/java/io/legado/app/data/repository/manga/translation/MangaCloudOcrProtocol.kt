package io.legado.app.data.repository.manga.translation

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.domain.model.manga.*
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val PADDLE_JOBS_URL = "https://paddleocr.aistudio-app.com/api/v2/ocr/jobs"
internal val cloudOcrJson = "application/json; charset=utf-8".toMediaType()

internal fun cloudOcrPublicIdentity(provider: MangaCloudOcrProvider, config: MangaCloudOcrConfig): String =
    "$provider|${config.endpoint.trim()}|${config.region}|${config.accurate}|${config.model}|${config.authScheme}"

internal fun baiduOcrRequest(base64: String, language: String, token: String, accurate: Boolean): Request {
    val lang = mapOf("ja" to "JAP", "ko" to "KOR", "zh" to "CHN_ENG", "en" to "ENG", "ru" to "RUS")[language] ?: "auto_detect"
    return Request.Builder().url("https://aip.baidubce.com/rest/2.0/ocr/v1/${if (accurate) "accurate" else "general"}?access_token=$token")
        .post(FormBody.Builder().add("image", base64).add("language_type", lang)
            .add("detect_direction", "false").add("vertexes_location", "true").add("probability", "true").build()).build()
}

/** TC3 signs the exact UTF-8 payload and Content-Type sent over the wire. */
internal fun tencentOcrRequest(base64: String, language: String, config: MangaCloudOcrConfig, timestamp: Long): Request {
    val lang = mapOf("ja" to "jap", "ko" to "kor", "ru" to "rus", "zh" to "zh", "en" to "auto")[language] ?: "auto"
    val body = JsonObject().apply { addProperty("ImageBase64", base64); addProperty("LanguageType", lang) }.toString()
    val action = if (config.accurate) "GeneralAccurateOCR" else "GeneralBasicOCR"
    return Request.Builder().url("https://ocr.tencentcloudapi.com/")
        .header("Authorization", cloudOcrTc3Authorization(config.apiKey, config.secretKey, timestamp, "ocr.tencentcloudapi.com", "ocr", body))
        .header("X-TC-Action", action).header("X-TC-Version", "2018-11-19")
        .header("X-TC-Timestamp", timestamp.toString()).header("X-TC-Region", config.region.ifBlank { "ap-guangzhou" })
        .post(body.toRequestBody(cloudOcrJson)).build()
}

internal fun cloudOcrTc3Authorization(id: String, secret: String, timestamp: Long, host: String, service: String, body: String): String {
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    fun sha(text: String) = hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    fun hmac(key: ByteArray, text: String) = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(text.toByteArray(Charsets.UTF_8))
    }
    val date = Instant.ofEpochSecond(timestamp).atOffset(ZoneOffset.UTC).toLocalDate().toString()
    val headers = "content-type:application/json; charset=utf-8\nhost:$host\n"
    val canonical = "POST\n/\n\n$headers\ncontent-type;host\n${sha(body)}"
    val scope = "$date/$service/tc3_request"
    val sign = "TC3-HMAC-SHA256\n$timestamp\n$scope\n${sha(canonical)}"
    val key = hmac(hmac(hmac("TC3$secret".toByteArray(Charsets.UTF_8), date), service), "tc3_request")
    return "TC3-HMAC-SHA256 Credential=$id/$scope, SignedHeaders=content-type;host, Signature=${hex(hmac(key, sign))}"
}

internal fun cloudOcrError(code: String): Nothing = error("OCR service error (${code.filter { it.isLetterOrDigit() || it in "._-" }.take(80)})")

/** Empty arrays mean no text; missing arrays/coordinates mean a failed response, never no-text success. */
internal fun cloudOcrRegions(provider: MangaCloudOcrProvider, root: JsonObject, width: Int, height: Int): List<MangaTranslatedRegion> {
    fun requiredArray(obj: JsonObject, name: String): JsonArray = obj.getAsJsonArray(name) ?: error("OCR response missing $name")
    fun polygon(value: JsonElement?): FloatArray? {
        if (value == null || !value.isJsonArray) return null
        val p = value.asJsonArray
        if (p.size() != 4) return null
        return FloatArray(8) { i ->
            val point = p[i / 2]
            if (point.isJsonArray) point.asJsonArray[i % 2].asFloat
            else point.asJsonObject.get(if (i % 2 == 0) "x" else "y")?.asFloat
                ?: point.asJsonObject.get(if (i % 2 == 0) "X" else "Y").asFloat
        }
    }
    val lines = mutableListOf<Pair<String, FloatArray>>()
    when (provider) {
        MangaCloudOcrProvider.BAIDU -> {
            root.get("error_code")?.let { cloudOcrError(it.asString) }
            requiredArray(root, "words_result").forEach { value ->
                val item = value.asJsonObject
                val text = item.get("words")?.asString ?: error("OCR response missing text")
                val corners = polygon(item.get("vertexes_location")) ?: item.getAsJsonObject("location")?.let {
                    val x = it["left"].asFloat; val y = it["top"].asFloat
                    val r = x + it["width"].asFloat; val b = y + it["height"].asFloat
                    floatArrayOf(x, y, r, y, r, b, x, b)
                } ?: error("OCR response missing coordinates")
                lines += text to corners
            }
        }
        MangaCloudOcrProvider.TENCENT -> {
            val response = root.getAsJsonObject("Response") ?: error("OCR response missing Response")
            response.getAsJsonObject("Error")?.let { cloudOcrError(it["Code"].asString) }
            requiredArray(response, "TextDetections").forEach { value ->
                val item = value.asJsonObject
                lines += (item.get("DetectedText")?.asString ?: error("OCR response missing text")) to
                    (polygon(item.get("Polygon")) ?: item.getAsJsonObject("ItemPolygon")?.let {
                        val x = it["X"].asFloat; val y = it["Y"].asFloat
                        val r = x + it["Width"].asFloat; val b = y + it["Height"].asFloat
                        floatArrayOf(x, y, r, y, r, b, x, b)
                    } ?: error("OCR response missing coordinates"))
            }
        }
        MangaCloudOcrProvider.PADDLE, MangaCloudOcrProvider.PADDLE_SERVER -> {
            root.get("errorCode")?.let { if (it.asInt != 0) cloudOcrError(it.asString) }
            val result = root.getAsJsonObject("result") ?: error("OCR response missing result")
            val pages = requiredArray(result, "ocrResults")
            require(pages.size() == 1) { "OCR image response must contain exactly one page" }
            val item = pages[0].asJsonObject.getAsJsonObject("prunedResult") ?: error("OCR response missing prunedResult")
            val texts = requiredArray(item, "rec_texts")
            val polys = requiredArray(item, "rec_polys")
            require(texts.size() == polys.size()) { "OCR text/geometry mismatch" }
            texts.forEachIndexed { index, text -> lines += text.asString to (polygon(polys[index]) ?: error("OCR response invalid polygon")) }
        }
    }
    require(lines.size <= 256) { "Too many OCR regions" }
    return lines.mapIndexedNotNull { index, (text, corners) ->
        if (text.isBlank()) return@mapIndexedNotNull null
        require(corners.all(Float::isFinite)) { "OCR returned nonfinite coordinates" }
        val xs = (0..3).map { corners[it * 2] }; val ys = (0..3).map { corners[it * 2 + 1] }
        val box = MangaTextBox((xs.min() * 1000 / width).coerceIn(0f, 1000f), (ys.min() * 1000 / height).coerceIn(0f, 1000f),
            (xs.max() * 1000 / width).coerceIn(0f, 1000f), (ys.max() * 1000 / height).coerceIn(0f, 1000f)).also { it.validate() }
        val quad = mangaSourceQuad(corners, width, height)
        MangaTranslatedRegion("r$index", text, text, box, sourceLines = listOf(MangaSourceLine(text, box, quad)),
            vertical = (box.bottom - box.top) * height > (box.right - box.left) * width * 1.5f)
    }
}
