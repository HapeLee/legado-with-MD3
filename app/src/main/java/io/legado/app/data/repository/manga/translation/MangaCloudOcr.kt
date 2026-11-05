package io.legado.app.data.repository.manga.translation

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.domain.gateway.MangaCloudOcrGateway
import io.legado.app.domain.model.manga.*
import io.legado.app.help.http.LocalNetworkBlockedNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** OCR only. Requests are not automatically retried, and never fall back to another paid provider. */
internal class MangaCloudOcr(
    private val context: Context,
    private val configs: MangaCloudOcrGateway,
    suppliedClient: OkHttpClient = OkHttpClient(),
    private val jobsUrl: String = PADDLE_JOBS_URL,
    private val pollMillis: Long = 1000,
    private val jobTimeoutMillis: Long = 180_000,
    private val permissionRequired: (String) -> Boolean = LocalNetworkBlockedNotifier::requestPermissionIfNeeded,
) {
    private val client = suppliedClient.newBuilder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private val publicClient = client.newBuilder().followRedirects(true).followSslRedirects(true).build()
    private val tokenLock = Mutex()
    private var tokenIdentity = ""
    private var token = ""
    private var tokenExpires = 0L

    suspend fun identity(provider: MangaCloudOcrProvider) = cloudOcrPublicIdentity(provider, configs.get(provider))

    suspend fun detect(bitmap: Bitmap, language: String, provider: MangaCloudOcrProvider): List<MangaTranslatedRegion> = withContext(Dispatchers.IO) {
        val config = configs.get(provider)
        require(config.apiKey.none(Char::isISOControl) && config.secretKey.none(Char::isISOControl)) { context.getString(R.string.manga_cloud_ocr_invalid_credentials) }
        require(provider == MangaCloudOcrProvider.PADDLE_SERVER || config.apiKey.isNotBlank()) { context.getString(R.string.manga_cloud_ocr_credentials_required) }
        require(provider !in setOf(MangaCloudOcrProvider.BAIDU, MangaCloudOcrProvider.TENCENT) || config.secretKey.isNotBlank()) {
            context.getString(R.string.manga_cloud_ocr_credentials_required)
        }
        val ratio = minOf(1f, 4096f / maxOf(bitmap.width, bitmap.height), sqrt(4_194_304f / (bitmap.width.toLong() * bitmap.height)))
        val image = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1), (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true) else bitmap
        try {
            var bytes = byteArrayOf()
            for (quality in listOf(92, 80, 65)) {
                bytes = ByteArrayOutputStream().use { out -> check(image.compress(Bitmap.CompressFormat.JPEG, quality, out)); out.toByteArray() }
                if (bytes.size <= 3 * 1024 * 1024) break
            }
            require(bytes.size <= 3 * 1024 * 1024) { context.getString(R.string.manga_cloud_ocr_image_too_large) }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val root = when (provider) {
                MangaCloudOcrProvider.BAIDU -> json(baiduOcrRequest(base64, language, baiduToken(config), config.accurate))
                MangaCloudOcrProvider.TENCENT -> json(tencentOcrRequest(base64, language, config, System.currentTimeMillis() / 1000))
                MangaCloudOcrProvider.PADDLE -> paddleJob(bytes, config)
                MangaCloudOcrProvider.PADDLE_SERVER -> {
                    val url = config.endpoint.trim().toHttpUrlOrNull()
                    require(url != null && url.username.isBlank() && url.password.isBlank() && url.query == null && url.fragment == null) {
                        context.getString(R.string.manga_cloud_ocr_endpoint_required)
                    }
                    val body = paddleOptions().apply { addProperty("file", base64); addProperty("fileType", 1) }
                    val request = Request.Builder().url(url).post(body.toString().toRequestBody(cloudOcrJson))
                    if (config.apiKey.isNotBlank()) {
                        require(config.authScheme in setOf("Bearer", "token"))
                        request.header("Authorization", "${config.authScheme} ${config.apiKey}")
                    }
                    json(request.build())
                }
            }
            cloudOcrRegions(provider, root, image.width, image.height)
        } finally { if (image !== bitmap) image.recycle() }
    }

    private suspend fun baiduToken(config: MangaCloudOcrConfig): String = tokenLock.withLock {
        val identity = MessageDigest.getInstance("SHA-256").digest("${config.apiKey}\u0000${config.secretKey}".toByteArray()).toList().toString()
        if (identity == tokenIdentity && tokenExpires > System.currentTimeMillis()) return@withLock token
        val root = json(Request.Builder().url("https://aip.baidubce.com/oauth/2.0/token").post(FormBody.Builder()
            .add("grant_type", "client_credentials").add("client_id", config.apiKey).add("client_secret", config.secretKey).build()).build())
        token = root.get("access_token")?.asString?.takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }
            ?: error(context.getString(R.string.manga_cloud_ocr_auth_failed))
        tokenIdentity = identity
        tokenExpires = System.currentTimeMillis() + ((root.get("expires_in")?.asLong ?: 300).coerceIn(120, 2_592_000) - 60) * 1000
        token
    }

    private fun paddleOptions() = JsonObject().apply {
        // Return coordinates in the uploaded image frame, with no document rotation or unwarping.
        addProperty("useDocOrientationClassify", false); addProperty("useDocUnwarping", false)
        addProperty("useTextlineOrientation", true); addProperty("visualize", false)
    }

    private suspend fun paddleJob(bytes: ByteArray, config: MangaCloudOcrConfig): JsonObject = try { withTimeout(jobTimeoutMillis) {
        require(config.model in setOf("PP-OCRv6", "PP-OCRv5", "PP-OCRv5-latin"))
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("model", config.model)
            .addFormDataPart("optionalPayload", paddleOptions().toString())
            .addFormDataPart("file", "manga.jpg", bytes.toRequestBody("image/jpeg".toMediaType())).build()
        fun authenticated(url: String) = Request.Builder().url(url).header("Authorization", "Bearer ${config.apiKey}")
        fun unwrap(root: JsonObject): JsonObject {
            root.get("code")?.let { if (it.asInt != 0) cloudOcrError(it.asString) }
            return root.getAsJsonObject("data") ?: error("OCR response missing data")
        }
        val submission = unwrap(json(authenticated(jobsUrl).post(body).build()))
        val id = submission.get("jobId")?.asString?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,200}")) } ?: error("OCR response missing jobId")
        var interval = pollMillis
        while (true) {
            val status = unwrap(json(authenticated("$jobsUrl/$id").get().build()))
            when (status.get("state")?.asString) {
                "done" -> {
                    val resultUrl = status.getAsJsonObject("resultUrl")?.get("jsonUrl")?.asString?.toHttpUrlOrNull()
                    require(resultUrl != null && (resultUrl.isHttps || resultUrl.host in setOf("localhost", "127.0.0.1"))) { "OCR result URL invalid" }
                    // Presigned result hosts receive no API credentials; never download preview images.
                    val lines = execute(Request.Builder().url(resultUrl).build(), publicClient).toString(Charsets.UTF_8)
                        .lineSequence().filter(String::isNotBlank).toList()
                    require(lines.size == 1) { "OCR image response must contain one result" }
                    return@withTimeout JsonParser.parseString(lines.single()).asJsonObject
                }
                "failed" -> error(context.getString(R.string.manga_cloud_ocr_job_failed))
                "pending", "running" -> { delay(interval); interval = (interval * 3 / 2).coerceAtMost(10_000) }
                else -> error("OCR response invalid job state")
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    } } catch (timeout: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        error(context.getString(R.string.manga_cloud_ocr_timeout))
    }

    private suspend fun json(request: Request): JsonObject = try {
        JsonParser.parseString(execute(request).toString(Charsets.UTF_8)).asJsonObject
    } catch (error: com.google.gson.JsonParseException) {
        error(context.getString(R.string.manga_cloud_ocr_invalid_response))
    }

    private suspend fun execute(request: Request, transport: OkHttpClient = client): ByteArray = suspendCancellableCoroutine { continuation ->
        if (permissionRequired(request.url.host)) {
            continuation.resumeWithException(MangaCloudOcrPermissionRequiredException(context.getString(R.string.local_network_permission_required)))
            return@suspendCancellableCoroutine
        }
        val call = transport.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException(context.getString(R.string.manga_cloud_ocr_network_failed)))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        check(it.isSuccessful) { "OCR service HTTP ${it.code}" }
                        ByteArrayOutputStream().use { out ->
                            it.body.byteStream().use { input ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val n = input.read(buffer); if (n < 0) break
                                    check(out.size() + n <= 8 * 1024 * 1024) { "OCR response too large" }
                                    out.write(buffer, 0, n)
                                }
                            }
                            out.toByteArray()
                        }
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }
}
