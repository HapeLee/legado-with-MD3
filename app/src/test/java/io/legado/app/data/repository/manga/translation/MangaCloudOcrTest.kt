package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import com.google.gson.JsonParser
import io.legado.app.domain.gateway.MangaCloudOcrGateway
import io.legado.app.domain.model.manga.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MangaCloudOcrTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val baidu = """{"words_result":[{"words":"こんにちは","location":{"left":10,"top":20,"width":30,"height":12},"vertexes_location":[{"x":10,"y":20},{"x":40,"y":21},{"x":39,"y":32},{"x":9,"y":31}]}]}"""
    private val paddle = """{"errorCode":0,"result":{"ocrResults":[{"prunedResult":{"rec_texts":["こんにちは"],"rec_polys":[[[10,20],[40,21],[39,32],[9,31]]]}}]}}"""
    private fun parsed(text: String) = JsonParser.parseString(text).asJsonObject
    private class Configs : MangaCloudOcrGateway {
        override fun observe(provider: MangaCloudOcrProvider) = flow { emit(get(provider)) }
        var config = MangaCloudOcrConfig(apiKey = "fake-id", secretKey = "fake-secret", endpoint = "https://paddle.invalid/ocr")
        override suspend fun get(provider: MangaCloudOcrProvider) = config
        override suspend fun update(provider: MangaCloudOcrProvider, transform: (MangaCloudOcrConfig) -> MangaCloudOcrConfig) { config = transform(config) }
    }
    private fun client(block: (Request) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(block(chain.request()).toResponseBody()).build()
    }.build()
    private fun payload(request: Request) = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
    private suspend fun detect(engine: MangaCloudOcr, provider: MangaCloudOcrProvider): List<MangaTranslatedRegion> {
        val image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        return try { engine.detect(image, "ja", provider) } finally { image.recycle() }
    }

    @Test fun `Baidu requests location endpoint and caches token by credential pair`() = runBlocking {
        val requests = mutableListOf<Request>()
        val configs = Configs()
        val engine = MangaCloudOcr(app, configs, client { request ->
            requests += request
            if (request.url.encodedPath.endsWith("token")) """{"access_token":"fake-token","expires_in":3600}""" else baidu
        })
        repeat(2) { assertEquals("こんにちは", detect(engine, MangaCloudOcrProvider.BAIDU).single().original) }
        assertEquals(3, requests.size)
        val request = requests.last()
        assertTrue(request.url.encodedPath.endsWith("/accurate"))
        assertEquals("fake-token", request.url.queryParameter("access_token"))
        assertTrue(payload(request).contains("language_type=JAP"))
        assertTrue(payload(request).contains("vertexes_location=true"))
        assertFalse(request.url.toString().contains("fake-secret"))
        configs.config = configs.config.copy(secretKey = "changed")
        detect(engine, MangaCloudOcrProvider.BAIDU)
        assertEquals(2, requests.count { it.url.encodedPath.endsWith("token") })
    }

    @Test fun `provider geometry preserves original line text and slant`() {
        val a = cloudOcrRegions(MangaCloudOcrProvider.BAIDU, parsed(baidu), 100, 100).single()
        val b = cloudOcrRegions(MangaCloudOcrProvider.PADDLE, parsed(paddle), 100, 100).single()
        assertEquals(a, b)
        assertEquals(90f, a.textBox.left)
        assertEquals(210f, a.sourceLines.single().quad!!.points[1].y)
        val tencent = parsed("""{"Response":{"TextDetections":[{"DetectedText":"こんにちは","Polygon":[{"X":10,"Y":20},{"X":40,"Y":21},{"X":39,"Y":32},{"X":9,"Y":31}]}]}}""")
        assertEquals(a, cloudOcrRegions(MangaCloudOcrProvider.TENCENT, tencent, 100, 100).single())
    }

    @Test fun `valid empty responses differ from provider errors and missing coordinates`() {
        assertTrue(cloudOcrRegions(MangaCloudOcrProvider.BAIDU, parsed("""{"words_result":[]}"""), 100, 100).isEmpty())
        for ((provider, json) in listOf(MangaCloudOcrProvider.BAIDU to """{"error_code":110,"error_msg":"sensitive-details"}""",
            MangaCloudOcrProvider.TENCENT to """{"Response":{"Error":{"Code":"AuthFailure","Message":"sensitive-details"}}}""",
            MangaCloudOcrProvider.PADDLE to """{"errorCode":500,"errorMsg":"sensitive-details"}""")) {
            val error = runCatching { cloudOcrRegions(provider, parsed(json), 100, 100) }.exceptionOrNull()!!
            assertFalse(error.message.orEmpty().contains("sensitive-details"))
        }
        assertTrue(runCatching { cloudOcrRegions(MangaCloudOcrProvider.BAIDU, parsed("{}"), 100, 100) }.isFailure)
        assertTrue(runCatching { cloudOcrRegions(MangaCloudOcrProvider.BAIDU, parsed("""{"words_result":[{"words":"text"}]}"""), 100, 100) }.isFailure)
        val mismatch = parsed(paddle); mismatch.getAsJsonObject("result").getAsJsonArray("ocrResults")[0].asJsonObject.getAsJsonObject("prunedResult").getAsJsonArray("rec_polys").remove(0)
        assertTrue(runCatching { cloudOcrRegions(MangaCloudOcrProvider.PADDLE, mismatch, 100, 100) }.isFailure)
    }

    @Test fun `official Paddle submits once polls and fetches JSONL without leaking token to result host`() = runBlocking {
        val requests = mutableListOf<Request>()
        var polls = 0
        val engine = MangaCloudOcr(app, Configs(), client { request ->
            requests += request
            when {
                request.method == "POST" -> """{"code":0,"data":{"jobId":"fixture-job"}}"""
                request.url.host == "results.invalid" -> paddle + "\n"
                polls++ == 0 -> """{"code":0,"data":{"state":"running"}}"""
                else -> """{"code":0,"data":{"state":"done","resultUrl":{"jsonUrl":"https://results.invalid/result.jsonl?signature=fixture"}}}"""
            }
        }, jobsUrl = "https://paddle.invalid/jobs", pollMillis = 1)
        assertEquals("こんにちは", detect(engine, MangaCloudOcrProvider.PADDLE).single().original)
        assertEquals(1, requests.count { it.method == "POST" })
        assertTrue(payload(requests.first()).contains("PP-OCRv6"))
        assertTrue(payload(requests.first()).contains("useDocUnwarping"))
        assertEquals("Bearer fake-id", requests.first().header("Authorization"))
        assertNull(requests.last().header("Authorization"))
        assertEquals(4, requests.size)
    }

    @Test fun `Paddle job cancellation stops polling without resubmitting or falling back`() = runBlocking {
        val submitted = CompletableDeferred<Unit>()
        var posts = 0
        val engine = MangaCloudOcr(app, Configs(), client { request ->
            if (request.method == "POST") { posts++; submitted.complete(Unit); """{"code":0,"data":{"jobId":"fixture-job"}}""" }
            else """{"code":0,"data":{"state":"running"}}"""
        }, jobsUrl = "https://paddle.invalid/jobs", pollMillis = 1000)
        val task = launch { detect(engine, MangaCloudOcrProvider.PADDLE) }
        submitted.await(); task.cancelAndJoin()
        assertEquals(1, posts)
        assertTrue(task.isCancelled)
    }

    @Test fun `Paddle poll timeout is a visible failure rather than user cancellation`() = runBlocking {
        val engine = MangaCloudOcr(app, Configs(), client { request ->
            if (request.method == "POST") """{"code":0,"data":{"jobId":"fixture-job"}}"""
            else """{"code":0,"data":{"state":"running"}}"""
        }, jobsUrl = "https://paddle.invalid/jobs", pollMillis = 1000, jobTimeoutMillis = 100)
        val error = runCatching { detect(engine, MangaCloudOcrProvider.PADDLE) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertFalse(error is CancellationException)
    }

    @Test fun `Paddle synchronous service uses requested endpoint scheme and returns geometry`() = runBlocking {
        val configs = Configs().apply { config = config.copy(authScheme = "token") }
        val engine = MangaCloudOcr(app, configs, client { request ->
            assertEquals("/ocr", request.url.encodedPath)
            assertEquals("token fake-id", request.header("Authorization"))
            assertFalse(parsed(payload(request))["useDocUnwarping"].asBoolean)
            assertEquals(1, parsed(payload(request))["fileType"].asInt)
            paddle
        })
        assertNotNull(detect(engine, MangaCloudOcrProvider.PADDLE_SERVER).single().sourceLines.single().quad)
    }

    @Test fun `local permission is requested before HTTP and explicit retry uses the same endpoint`() = runBlocking {
        val configs = Configs().apply { config = config.copy(endpoint = "http://192.168.50.7:8080/ocr") }
        var blocked = true
        var calls = 0
        val checks = mutableListOf<String>()
        val engine = MangaCloudOcr(app, configs, client { calls++; paddle }, permissionRequired = { host -> checks += host; blocked })
        assertTrue(runCatching { detect(engine, MangaCloudOcrProvider.PADDLE_SERVER) }.exceptionOrNull() is MangaCloudOcrPermissionRequiredException)
        assertEquals(0, calls)
        blocked = false
        assertEquals("こんにちは", detect(engine, MangaCloudOcrProvider.PADDLE_SERVER).single().original)
        assertEquals(1, calls)
        assertEquals(listOf("192.168.50.7", "192.168.50.7"), checks)
    }

    @Test fun `missing credentials fail before any HTTP request`() = runBlocking {
        var calls = 0
        val configs = Configs().apply { config = MangaCloudOcrConfig() }
        val engine = MangaCloudOcr(app, configs, client { calls++; error("No network expected") })
        for (provider in listOf(MangaCloudOcrProvider.BAIDU, MangaCloudOcrProvider.TENCENT, MangaCloudOcrProvider.PADDLE)) {
            assertTrue(runCatching { detect(engine, provider) }.isFailure)
        }
        assertEquals(0, calls)
    }

    @Test fun `configuration observers receive committed changes from another editor`() = runBlocking {
        val root = Files.createTempDirectory("cloud-ocr-observe").toFile()
        val repo = MangaCloudOcrConfigRepository(app, root.resolve("config.json"), encrypt = { it }, decrypt = { it })
        val initialized = CompletableDeferred<Unit>()
        val observed = async(Dispatchers.Default) {
            repo.observe(MangaCloudOcrProvider.BAIDU).onEach { initialized.complete(Unit) }.drop(1).first()
        }
        try {
            withTimeout(5000) { initialized.await() }
            repo.update(MangaCloudOcrProvider.BAIDU) { it.copy(apiKey = "committed-key") }
            assertEquals("committed-key", withTimeout(5000) { observed.await() }.apiKey)
        } finally { observed.cancelAndJoin(); root.deleteRecursively() }
    }

    @Test fun `draft merge permits clearing one field while preserving newer connection fields`() {
        val base = MangaCloudOcrConfig(apiKey = "old", secretKey = "old-secret")
        val latest = base.copy(secretKey = "new-secret", endpoint = "https://new.invalid/ocr", model = "PP-OCRv5")
        val merged = latest.mergeDraft(base, base.copy(apiKey = "", accurate = false))
        assertEquals("", merged.apiKey)
        assertFalse(merged.accurate)
        assertEquals(latest.secretKey, merged.secretKey)
        assertEquals(latest.endpoint, merged.endpoint)
        assertEquals(latest.model, merged.model)
    }

    @Test fun `global credentials are encrypted on disk preserve other providers and survive recreation`() = runBlocking {
        val root = Files.createTempDirectory("cloud-ocr-config").toFile()
        val file = root.resolve("config.json")
        fun repository() = MangaCloudOcrConfigRepository(app, file,
            encrypt = { "test:" + Base64.getEncoder().encodeToString(it.toByteArray()) },
            decrypt = { String(Base64.getDecoder().decode(it.removePrefix("test:"))) })
        try {
            val repo = repository()
            repo.update(MangaCloudOcrProvider.BAIDU) { it.copy(apiKey = "baidu-credential", secretKey = "baidu-secret") }
            repo.update(MangaCloudOcrProvider.TENCENT) { it.copy(apiKey = "tencent-credential", secretKey = "tencent-secret") }
            assertFalse(file.readText().contains("baidu-credential")); assertFalse(file.readText().contains("tencent-secret"))
            assertEquals("baidu-credential", repository().get(MangaCloudOcrProvider.BAIDU).apiKey)
            assertEquals(file.readText(), "tencent-secret", repository().get(MangaCloudOcrProvider.TENCENT).secretKey)
            repo.update(MangaCloudOcrProvider.BAIDU) { it.copy(apiKey = "", secretKey = "") }
            assertEquals("", repository().get(MangaCloudOcrProvider.BAIDU).apiKey)
            assertEquals("tencent-secret", repository().get(MangaCloudOcrProvider.TENCENT).secretKey)
            assertFalse(MangaCloudOcrConfig(apiKey = "sensitive").toString().contains("sensitive"))
        } finally { root.deleteRecursively() }
    }
}
