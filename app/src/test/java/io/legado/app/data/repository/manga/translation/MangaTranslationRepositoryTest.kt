package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.BitmapFactory.Options
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.*
import io.legado.app.domain.model.*
import io.legado.app.domain.model.manga.*
import io.legado.app.domain.reader.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaTranslationRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var original: File
    private lateinit var page: MangaTranslationPage
    private lateinit var repository: MangaTranslationRepository
    private var calls = 0
    private var edits = 0
    private lateinit var reopen: () -> MangaTranslationRepository
    private var responseText = """{"regions":[{"id":"r0","original":"hello","translation":"Hi","textBox":{"left":100,"top":100,"right":900,"bottom":700},"layoutBox":{"left":100,"top":100,"right":900,"bottom":700},"typesetting":{"lines":["Hi"],"fontSize":30}}],"terms":{"Alice":"爱丽丝"}}"""
    private val inputSizes = mutableListOf<Pair<Int, Int>>()
    private val requestContexts = mutableListOf<String>()
    private val reasoningLevels = mutableListOf<AiReasoningLevel>()
    private val requests = mutableListOf<AiGenerateRequest>()
    private var textInputSize: Pair<Int, Int>? = null
    private var cloudInputSize: Pair<Int, Int>? = null
    private var paddleOptions: MangaOcrOptions? = null
    private var detectedRegions = emptyList<MangaTranslatedRegion>()
    private var bubbleDetections = emptyList<MangaBubbleDetection>()
    private val paddleEngines = mutableListOf<MangaLocalOcrEngine>()
    private var cloudCalls = 0
    private var cloudFailure = false
    private var cloudVersion = "endpoint-v1"
    private var cloudProvider: MangaCloudOcrProvider? = null
    private var paddleCalls = 0
    private var paddleMissing = false
    private var paddleNativeFailure = false
    private var respond: ((AiGenerateRequest) -> String)? = null
    private var bubbleCalls = 0
    private var bubbleFailure = false
    private var inpaintAvailable = true
    private var inpaintFailure = false
    private var inpaintCalls = 0

    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        original = File.createTempFile("translation-test-", ".png", app.cacheDir)
        saveOriginal(Color.WHITE)
        page = MangaTranslationPage("page", UUID.randomUUID().toString(), original.toURI().toString(), "chapter", null)
        val model = AiModelConfig("model", AiProviderConfig("provider", "Test", AiProtocol.OPENAI_CHAT_COMPLETIONS, "https://example.invalid/v1", "unused"), "Test", "test",
            defaultParams = AiGenerationParams(reasoningLevel = AiReasoningLevel.HIGH), capabilities = setOf(AiCapability.REASONING, AiCapability.VISION))
        val preset = AiTaskPresetConfig("preset", AiTaskType.TRANSLATE_CHAPTER, "Test", model, "")
        val profiles = Proxy.newProxyInstance(AiProfileGateway::class.java.classLoader, arrayOf(AiProfileGateway::class.java)) { _, method, args ->
            when (method.name) { "getTaskPreset" -> preset; "getModelConfig" -> if (args[0] == "image") model.copy(id = "image", capabilities = setOf(AiCapability.IMAGE_EDITING)) else if (args[0] == "ocr") model.copy(id = "ocr") else if (args[0] == "text-only") model.copy(id = "text-only", capabilities = emptySet()) else model; else -> null }
        } as AiProfileGateway
        val ai = object : AiTextGateway {
            override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> = error("Comic analysis must stream")
            override fun generateStream(request: AiGenerateRequest) = flow {
                calls++
                requests += request
                reasoningLevels += request.params.reasoningLevel
                assertEquals(120_000L, request.timeoutMillis)
                request.messages.last().images.singleOrNull()?.let { image ->
                    assertTrue(image.mimeType in setOf("image/jpeg", "image/png"))
                    val bytes = android.util.Base64.decode(image.base64Data, android.util.Base64.DEFAULT)
                    val dimensions = Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
                    inputSizes += dimensions.outWidth to dimensions.outHeight
                }
                requestContexts += request.messages.last().content
                emit(AiStreamEvent.Reasoning("Provider reasoning"))
                val response = respond?.invoke(request) ?: responseText
                val split = response.length / 2
                emit(AiStreamEvent.Content(response.take(split)))
                emit(AiStreamEvent.Content(response.drop(split)))
            }
            override suspend fun fetchModels(provider: AiProviderConfig) = Result.success(emptyList<AiAvailableModel>())
        }
        // Real BitmapRegionDecoder JNI is not safe in Windows Robolectric. This exercises the
        // same decoder contract while leaving actual native integration to device tests.
        reopen = { MangaTranslationRepository(app, db, profiles, ai, imageEditor = { input, _, _, _ ->
            edits++
            input.copy(Bitmap.Config.ARGB_8888, true)
        }, textDetector = { input, _ -> textInputSize = input.width to input.height; detectedRegions }, bubbleDetector = {
            bubbleCalls++
            if (bubbleFailure) throw UnsatisfiedLinkError("fixture native load failure")
            bubbleDetections
        }, paddleDetector = { _, _, engine, options ->
            paddleCalls++; paddleEngines += engine; paddleOptions = options
            if (paddleNativeFailure) throw UnsatisfiedLinkError("fixture native load failure")
            check(!paddleMissing) { "Model not downloaded" }
            detectedRegions
        }, cloudIdentity = { cloudVersion }, cloudDetector = { input, _, provider ->
            cloudInputSize = input.width to input.height
            cloudCalls++; cloudProvider = provider
            check(!cloudFailure) { "Cloud OCR fixture failure" }
            detectedRegions
        }, inpaintReady = { check(inpaintAvailable) { "Inpainting model missing" } }, inpaint = { bitmap, _, _ ->
            inpaintCalls++
            check(!inpaintFailure) { "Inpainting failed" }
            bitmap.eraseColor(Color.LTGRAY) // Deliberately alters outside the mask; repository must contain it.
        }) { file ->
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            object : MangaRegionDecoder<Bitmap> {
                override val imageSize = MangaImageSize(bitmap.width, bitmap.height)
                override suspend fun decodeRegion(region: MangaImageRegion, sampleSize: Int): Bitmap {
                    val cropped = Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height).copy(Bitmap.Config.ARGB_8888, true)
                    return if (sampleSize == 1) cropped else Bitmap.createScaledBitmap(cropped,
                        (region.width / sampleSize).coerceAtLeast(1), (region.height / sampleSize).coerceAtLeast(1), true).also { cropped.recycle() }
                }
                override fun close() { bitmap.recycle() }
            }
        } }
        repository = reopen()
        runBlocking { repository.updateGlobal { MangaTranslationGlobalSettings(defaults = MangaTranslationSettings(retryCount = 1)) } }
    }

    @Test fun `cleared page stays unpublished after reopening until translated again`() = runBlocking {
        repository.translate(page, false) { }
        assertNotNull(repository.cached(page))
        repository.clearPage(page)
        assertNull(repository.cached(page))
        repository = reopen()
        assertNull(repository.cached(page))
        repository.translate(page, false) { }
        assertNotNull(repository.cached(page))
    }

    @Test fun `retry settings default to three persist and do not invalidate published translation`() = runBlocking {
        assertEquals(3, MangaTranslationSettings().retryCount)
        val saved = repository.translate(page, progress = {})
        repository.updateGlobal { it.copy(defaults = it.defaults.copy(retryCount = 5)) }
        assertEquals(5, reopen().settings(page.bookUrl).first().retryCount)
        assertEquals(saved.imagePath, repository.cached(page)!!.imagePath)
        repository.update(page.bookUrl) { it.copy(retryCount = 0) }
        assertEquals(0, reopen().settings(page.bookUrl).first().retryCount)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.update(page.bookUrl) { it.copy(retryCount = 11) } } }
        assertEquals(1, calls)
    }

    @Test fun `three retries permit four AI attempts and zero disables response correction`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(retryCount = 3) }
        respond = { if (calls < 4) authoredResponse("") else authoredResponse() }
        assertTrue(File(repository.translate(page, progress = {}).imagePath).exists())
        assertEquals(4, calls)
        repository.update(page.bookUrl) { it.copy(retryCount = 0) }
        respond = { authoredResponse("") }
        assertTrue(runCatching { repository.translate(page, force = true, progress = {}) }.isFailure)
        assertEquals(5, calls)
    }

    @Test fun `transport and invalid responses share one retry allowance`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(retryCount = 2) }
        respond = { when (calls) { 1 -> throw java.io.IOException("temporary")
            else -> authoredResponse("") } }
        assertTrue(runCatching { repository.translate(page, progress = {}) }.isFailure)
        assertEquals(3, calls)
    }

    @Test fun `cloud OCR translation retries invalid text without resubmitting OCR`() = runBlocking {
        detectedRegions = listOf(MangaTranslatedRegion("r0", "hello", "hello", MangaTextBox(100f, 100f, 900f, 700f)))
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.CLOUD, retryCount = 3) }
        respond = { if (calls < 3) """{"translations":[]}""" else """{"translations":[{"id":"r0","translation":"Hi"}]}""" }
        assertTrue(File(repository.translate(page, progress = {}).imagePath).exists())
        assertEquals(3, calls)
        assertEquals(1, cloudCalls)
        assertTrue(requests.last().messages.last().content.startsWith("TRANSLATION CORRECTION ONLY"))
        assertTrue(requests.all { it.messages.all { message -> message.images.isEmpty() } })
    }

    @Test fun `independent AI OCR uses vision model then text only translation model`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(ocrModelId = "ocr") }
        respond = { request ->
            if (request.messages.first().content.contains("COMPLETE translation and typesetting plan")) authoredResponse()
            else if (request.model.id == "ocr") """{"regions":[{"id":"r0","original":"hello","textBox":{"left":100,"top":100,"right":900,"bottom":700}}]}"""
            else """{"translations":[{"id":"r0","translation":"Hi"}]}"""
        }
        val result = repository.translate(page, progress = {})
        assertTrue(File(result.imagePath).exists())
        assertEquals(listOf("ocr", "model", "ocr"), requests.map { it.model.id })
        assertTrue(requests[0].messages.last().images.isNotEmpty())
        assertTrue(requests[1].messages.last().images.isEmpty())
        assertTrue(requests[1].messages.last().content.contains("hello"))
        repository.recompose(page, progress = {})
        assertEquals(3, calls)
    }

    @Test fun `switching local OCR engine routes the selected runtime and changes analysis identity`() = runBlocking {
        detectedRegions = listOf(MangaTranslatedRegion("r0", "hello", "hello", MangaTextBox(100f, 100f, 900f, 700f)))
        responseText = """{"translations":[{"id":"r0","translation":"Hi"}]}"""
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL) }
        repository.translate(page, progress = {})
        assertEquals(0, paddleCalls)
        repository.update(page.bookUrl) { it.copy(localOcrEngine = MangaLocalOcrEngine.PADDLE_V5) }
        repository.translate(page, progress = {})
        assertEquals(1, paddleCalls); assertEquals(2, calls)
        assertTrue(requests.all { request -> request.messages.all { it.images.isEmpty() } })
        repository.translate(page, progress = {})
        assertEquals(1, paddleCalls); assertEquals(2, calls)
        for (engine in listOf(MangaLocalOcrEngine.PADDLE_V6_SMALL, MangaLocalOcrEngine.PADDLE_V6_MEDIUM, MangaLocalOcrEngine.PADDLE_V6_MANGA)) {
            val previousImage = repository.cached(page)!!.imagePath
            repository.update(page.bookUrl) { it.copy(localOcrEngine = engine) }
            assertEquals(previousImage, repository.cached(page)!!.imagePath)
            repository.translate(page, progress = {})
            assertEquals(engine, paddleEngines.last())
            repository.translate(page, progress = {})
        }
        assertEquals(4, paddleCalls); assertEquals(5, calls)
        val restored = reopen()
        assertEquals(MangaLocalOcrEngine.PADDLE_V6_MANGA, restored.settings(page.bookUrl).first().localOcrEngine)
    }

    @Test fun `manga recognizer rejects non Japanese before paid translation`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL, localOcrEngine = MangaLocalOcrEngine.PADDLE_V6_MANGA, sourceLanguage = "en") }
        assertTrue(runCatching { repository.translate(page, progress = {}) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, paddleCalls); assertEquals(0, calls)
    }

    @Test fun `cloud OCR uses selected provider sends only text reuses anchors and keys by public configuration`() = runBlocking {
        detectedRegions = listOf(MangaTranslatedRegion("r0", "hello", "hello", MangaTextBox(100f, 100f, 900f, 700f)))
        responseText = """{"translations":[{"id":"r0","translation":"Hi"}]}"""
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.CLOUD, cloudOcrProvider = MangaCloudOcrProvider.TENCENT) }
        repository.translate(page, progress = {})
        assertEquals(MangaCloudOcrProvider.TENCENT, cloudProvider)
        assertEquals(1, cloudCalls); assertEquals(1, calls)
        assertTrue(requests.all { request -> request.messages.all { it.images.isEmpty() } })
        repository.translate(page, progress = {})
        repository.recompose(page) {}
        assertEquals(1, cloudCalls); assertEquals(1, calls)
        cloudVersion = "endpoint-v2"
        repository.translate(page, progress = {})
        assertEquals(2, cloudCalls); assertEquals(2, calls)
        val previous = repository.cached(page)!!.imagePath
        repository.update(page.bookUrl) { it.copy(cloudOcrProvider = MangaCloudOcrProvider.PADDLE) }
        assertEquals(previous, repository.cached(page)!!.imagePath)
        cloudFailure = true
        assertTrue(runCatching { repository.translate(page, force = true, progress = {}) }.isFailure)
        assertEquals(2, calls)
        assertEquals(previous, repository.cached(page)!!.imagePath)
    }

    @Test fun `OCR tuning reaches runtime persists and reuses saved anchors without clearing the published image`() = runBlocking {
        detectedRegions = listOf(MangaTranslatedRegion("r0", "hello", "hello", MangaTextBox(100f, 100f, 900f, 700f)))
        responseText = """{"translations":[{"id":"r0","translation":"Hi"}]}"""
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL, localOcrEngine = MangaLocalOcrEngine.PADDLE_V5) }
        val old = repository.translate(page, progress = {})
        val options = MangaOcrOptions(detectionProfile = MangaOcrDetectionProfile.ACCURATE, probabilityThreshold = .4f, upscale2x = true)
        repository.update(page.bookUrl) { it.copy(ocrOptions = options) }
        assertEquals(old.imagePath, repository.cached(page)!!.imagePath)
        repository.recompose(page) {}
        assertEquals(1, calls); assertEquals(1, paddleCalls)
        repository.translate(page, progress = {})
        assertEquals(options, paddleOptions)
        assertEquals(2, calls); assertEquals(2, paddleCalls)
        repository.translate(page, progress = {})
        assertEquals(2, calls)
        assertEquals(options, reopen().settings(page.bookUrl).first().ocrOptions)
        assertTrue(runCatching { repository.update(page.bookUrl) { it.copy(ocrOptions = options.copy(unclipRatio = Float.NaN)) } }.isFailure)
        assertEquals(options, repository.settings(page.bookUrl).first().ocrOptions)
    }

    @Test fun `image preprocessing applies to local and cloud OCR but detector tuning does not invalidate those routes`() = runBlocking {
        detectedRegions = listOf(MangaTranslatedRegion("r0", "hello", "hello", MangaTextBox(100f, 100f, 900f, 700f)))
        responseText = """{"translations":[{"id":"r0","translation":"Hi"}]}"""
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL, ocrOptions = MangaOcrOptions(upscale2x = true)) }
        repository.translate(page, progress = {})
        assertEquals(128 to 192, textInputSize)
        repository.update(page.bookUrl) { it.copy(ocrOptions = it.ocrOptions.copy(detectionProfile = MangaOcrDetectionProfile.ACCURATE, unclipRatio = 2f)) }
        repository.translate(page, progress = {})
        assertEquals(1, calls)
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.CLOUD) }
        repository.translate(page, progress = {})
        assertEquals(128 to 192, cloudInputSize)
        assertEquals(2, calls)
    }

    @Test fun `missing local model fails before paid translation and preserves published image`() = runBlocking {
        val prior = repository.translate(page, progress = {})
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL, localOcrEngine = MangaLocalOcrEngine.PADDLE_V5) }
        paddleMissing = true
        assertTrue(runCatching { repository.translate(page, force = true, progress = {}) }.isFailure)
        assertEquals(1, calls)
        assertEquals(prior.imagePath, repository.cached(page)?.imagePath)
        paddleMissing = false; paddleNativeFailure = true
        assertTrue(runCatching { repository.translate(page, force = true, progress = {}) }.exceptionOrNull() is IllegalStateException)
        assertEquals(1, calls)
    }

    @Test fun `explicit AI OCR rejects a model without vision before any paid request`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(ocrModelId = "text-only") }
        assertTrue(runCatching { repository.translate(page, progress = {}) }.isFailure)
        assertEquals(0, calls)
    }

    @After fun cleanup() { db.close(); original.delete() }

    private fun saveOriginal(color: Int, width: Int = 64, height: Int = 96) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun `local geometry hints are sent once and recomposition reuses anchors without model or paid requests`() = runBlocking {
        bubbleDetections = listOf(MangaBubbleDetection(MangaShapeRect(4, 4, 60, 92), .9f, 0))
        responseText = """{"regions":[{"id":"a0","original":"hello","translation":"Hi","sourceDirection":"HORIZONTAL_ROWS","typesetting":{"lines":["Hi"]}}]}"""
        repository.translate(page, progress = {})
        assertTrue(requestContexts.single().contains("Anchor data (original pixels)"))
        assertEquals(1, bubbleCalls)
        repository.update(page.bookUrl) { it.copy(fontScale = .8f) }
        val result = repository.recompose(page, progress = {})
        assertEquals(1, calls)
        assertEquals(1, bubbleCalls)
        assertEquals(result, reopen().cached(page))
    }

    @Test fun `local OCR detector failure retains publication without sending another paid request`() = runBlocking {
        val previous = repository.translate(page, progress = {})
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL) }
        bubbleFailure = true
        try { repository.translate(page, force = true, progress = {}); fail("Native capability failure must surface") }
        catch (failure: IllegalStateException) { assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is UnsatisfiedLinkError }) }
        assertEquals(1, calls)
        assertEquals(previous, repository.cached(page))
    }

    @Test fun `composition failure preserves analysis so a normal retry does not pay for translation twice`() = runBlocking {
        val failure = runCatching { repository.translate(page, progress = {
            if (it.stage == MangaTranslationStage.COMPOSING) error("fixture composition interruption")
        }) }
        assertTrue(failure.isFailure)
        assertEquals(1, calls)
        assertNull(repository.cached(page))
        val result = repository.translate(page, progress = {})
        assertTrue(File(result.imagePath).isFile)
        assertEquals(1, calls)
    }

    @Test fun `genuinely oversized local translation reports region and reason while preserving prior published image`() = runBlocking {
        val previous = repository.translate(page, progress = {})
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL) }
        detectedRegions = listOf(MangaTranslatedRegion("r0", "hello", "hello", MangaTextBox(100f, 100f, 900f, 700f)))
        responseText = """{"translations":[{"id":"r0","translation":"${"字".repeat(5000)}"}]}"""
        val failure = runCatching { repository.translate(page, force = true, progress = {}) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty(), failure.message.orEmpty().contains("NO_FIT"))
        assertTrue(generateSequence(failure) { it.cause }.any { it is MangaTextLayoutException })
        assertEquals(previous.imagePath, repository.cached(page)?.imagePath)
    }

    private fun assertRequestFrameMatchesImage(index: Int) {
        val dimensions = inputSizes[index]
        assertTrue(requests[index].messages.last().content.contains("\"width\":${dimensions.first}"))
        assertTrue(requests[index].messages.last().content.contains("\"height\":${dimensions.second}"))
    }

    @Test fun `local detection sends text only even to vision models and recomposes without AI`() = runBlocking {
        val box = MangaTextBox(100f, 100f, 900f, 700f)
        detectedRegions = listOf(MangaTranslatedRegion("local", "Hello", "Hello", box))
        responseText = """{"translations":[{"id":"local","translation":"Hi"}]}"""
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL) }
        val first = repository.translate(page, progress = {})
        val request = requests.single()
        assertTrue(request.model.capabilities.contains(AiCapability.VISION))
        assertTrue(request.messages.all { it.images.isEmpty() })
        val blocks = kotlinx.serialization.json.Json.parseToJsonElement(request.messages.last().content.substringAfter("Source text blocks: "))
        assertEquals("""[{"id":"local","original":"Hello"}]""", blocks.toString())
        assertFalse(request.messages.last().content.contains("textBox"))
        assertFalse(request.messages.last().content.contains("Attached image frame"))
        val metadata = File(first.imagePath + ".metadata.json").readText()
        assertTrue(metadata, metadata.contains("\"original\":\"Hello\""))
        assertTrue(metadata, metadata.contains("\"translation\":\"Hi\""))
        repository.update(page.bookUrl) { it.copy(fontScale = 0.75f, composition = MangaComposition.PATCH) }
        repository.recompose(page, progress = {})
        assertEquals(1, calls)
    }

    @Test fun `local empty detection skips the text service`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL) }
        val result = repository.translate(page, progress = {})
        assertEquals(0, calls)
        assertEquals(result, repository.cached(page))
    }

    @Test fun `invalid local translation retains the last published image`() = runBlocking {
        val first = repository.translate(page, progress = {})
        detectedRegions = listOf(MangaTranslatedRegion("local", "Hello", "Hello", MangaTextBox(100f, 100f, 900f, 700f)))
        responseText = """{"translations":[{"id":"wrong","translation":"Hi"}]}"""
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL) }
        try { repository.translate(page, progress = {}); fail("Invalid IDs must fail") } catch (_: IllegalArgumentException) { }
        assertEquals(first, repository.cached(page))
    }

    @Test fun `raster cache and local rerender reuse paid analysis while source changes invalidate it`() = runBlocking {
        val first = repository.translate(page, progress = {})
        assertEquals(first, repository.cached(page))
        assertEquals(1, calls)
        val translated = BitmapFactory.decodeFile(first.imagePath)
        assertEquals(64, translated.width); assertEquals(96, translated.height); translated.recycle()
        repository.update(page.bookUrl) { it.copy(fontScale = 0.75f) }
        val rerender = repository.translate(page, progress = {})
        assertNotEquals(first.imagePath, rerender.imagePath)
        assertEquals(1, calls)
        saveOriginal(Color.YELLOW)
        assertNull(repository.cached(page))
        repository.translate(page, progress = {})
        assertEquals(2, calls)
    }

    @Test fun `forced translation publishes a new immutable image and cache follows the latest revision`() = runBlocking {
        val first = repository.translate(page, progress = {})
        val second = repository.translate(page, force = true, progress = {})
        assertNotEquals(first.imagePath, second.imagePath)
        assertTrue(File(first.imagePath).exists())
        val publication = File(first.imagePath).parentFile!!.listFiles()!!.single { it.name.endsWith(".published.json") }.readText()
        assertTrue(publication, publication.contains(File(second.imagePath).name))
        assertEquals(second, repository.cached(page))
        assertEquals(2, calls)
    }

    @Test fun `global defaults are inherited live and per book automatic stays independent`() = runBlocking {
        val other = page.bookUrl + "-inherited"
        repository.updateGlobal { it.copy(automatic = true, defaults = it.defaults.copy(targetLanguage = "fr", sourceLanguage = "en", modelId = "ocr", automatic = true)) }
        assertTrue(repository.useGlobalSettings(page.bookUrl).first())
        assertEquals("fr", repository.settings(page.bookUrl).first().targetLanguage)
        assertTrue(repository.settings(page.bookUrl).first().automatic)
        assertFalse(repository.globalSettings().first().defaults.automatic)
        repository.update(page.bookUrl) { it.copy(automatic = true) }
        assertTrue(repository.useGlobalSettings(page.bookUrl).first())
        repository.updateGlobal { it.copy(defaults = it.defaults.copy(targetLanguage = "ko")) }
        assertEquals("ko", repository.settings(page.bookUrl).first().targetLanguage)
        assertTrue(repository.settings(page.bookUrl).first().automatic)
        assertTrue(repository.settings(other).first().automatic)
        val restored = reopen()
        assertEquals("ko", restored.settings(other).first().targetLanguage)
        assertTrue(restored.useGlobalSettings(page.bookUrl).first())
        assertEquals(0, calls)
    }

    @Test fun `custom comic settings persist separately and return after switching back from global`() = runBlocking {
        repository.updateGlobal { it.copy(defaults = it.defaults.copy(targetLanguage = "fr", fontScale = .75f)) }
        repository.setUseGlobalSettings(page.bookUrl, false)
        assertEquals("fr", repository.settings(page.bookUrl).first().targetLanguage)
        repository.update(page.bookUrl) { it.copy(targetLanguage = "ja", modelId = "ocr") }
        repository.updateGlobal { it.copy(defaults = it.defaults.copy(targetLanguage = "en")) }
        assertEquals("ja", repository.settings(page.bookUrl).first().targetLanguage)
        repository.setUseGlobalSettings(page.bookUrl, true)
        assertEquals("en", repository.settings(page.bookUrl).first().targetLanguage)
        repository.update(page.bookUrl) { it.copy(automatic = true) }
        repository.setUseGlobalSettings(page.bookUrl, false)
        val restored = reopen()
        assertFalse(restored.useGlobalSettings(page.bookUrl).first())
        assertEquals("ja", restored.settings(page.bookUrl).first().targetLanguage)
        assertEquals("ocr", restored.settings(page.bookUrl).first().modelId)
        assertTrue(restored.settings(page.bookUrl).first().automatic)
        assertEquals(0, calls)
    }

    @Test fun `translation uses inherited model and reasoning while global edits preserve published raster`() = runBlocking {
        repository.updateGlobal { it.copy(defaults = it.defaults.copy(modelId = "ocr", reasoningLevel = AiReasoningLevel.LOW.storageValue)) }
        val translated = repository.translate(page, false) {}
        assertEquals("ocr", requests.single().model.id)
        assertEquals(AiReasoningLevel.LOW, reasoningLevels.single())
        repository.updateGlobal { it.copy(defaults = it.defaults.copy(fontScale = .75f, targetLanguage = "fr")) }
        assertEquals(translated, repository.cached(page))
        val result = repository.recompose(page) {}
        assertTrue(File(result.imagePath).isFile)
        assertEquals(1, calls)
        assertEquals("fr", reopen().settings(page.bookUrl).first().targetLanguage)
    }

    @Test fun `invalid global defaults are rejected without replacing saved defaults`() = runBlocking {
        val before = repository.globalSettings().first()
        for (bad in listOf(before.defaults.copy(fontScale = 5f), before.defaults.copy(targetLanguage = ""), before.defaults.copy(reasoningLevel = "not-a-level"))) {
            val failure = runCatching { repository.updateGlobal { it.copy(defaults = bad) } }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(before, repository.globalSettings().first())
        }
    }

    @Test fun `book can override either global automatic default across recreation`() = runBlocking {
        repository.updateGlobal { it.copy(automatic = true) }
        assertTrue(repository.settings(page.bookUrl).first().automatic)
        repository.update(page.bookUrl) { it.copy(automatic = false) }
        assertFalse(reopen().settings(page.bookUrl).first().automatic)
        repository.updateGlobal { it.copy(automatic = false) }
        repository.update(page.bookUrl) { it.copy(automatic = true) }
        assertTrue(reopen().settings(page.bookUrl).first().automatic)
    }

    @Test fun `automatic master and independent book switches persist without starting translation`() = runBlocking {
        val other = page.bookUrl + "-other"
        repository.updateGlobal { it.copy(automatic = false) }
        assertFalse(repository.settings(page.bookUrl).first().automatic)
        repository.update(page.bookUrl) { it.copy(automatic = true) }
        repository.updateGlobal { it.copy(automatic = true) }
        val restored = reopen()
        assertTrue(restored.globalSettings().first().automatic)
        assertTrue(restored.settings(page.bookUrl).first().automatic)
        assertTrue(restored.settings(other).first().automatic)
        restored.updateGlobal { it.copy(automatic = false) }
        assertTrue(restored.settings(page.bookUrl).first().automatic)
        assertFalse(reopen().globalSettings().first().automatic)
        assertEquals(0, calls)
    }

    @Test fun `changing the automatic book switch during manual recompose does not invalidate its result`() = runBlocking {
        repository.translate(page, false) {}
        var changed = false
        val result = repository.recompose(page) { progress ->
            if (!changed && progress.stage == MangaTranslationStage.COMPOSING) {
                changed = true
                runBlocking { repository.update(page.bookUrl) { it.copy(automatic = true) } }
            }
        }
        assertTrue(changed)
        assertTrue(File(result.imagePath).isFile)
        assertEquals(1, calls)
    }

    @Test fun `last published page survives every settings change and repository recreation without AI`() = runBlocking {
        val first = repository.translate(page, progress = {})
        repository.update(page.bookUrl) { it.copy(fontScale = 1.5f, composition = MangaComposition.AI_PAGE,
            modelId = "removed", imageModelId = "", targetLanguage = "fr") }
        assertEquals(first, repository.cached(page))
        repository = reopen()
        assertEquals(first, repository.cached(page.copy(key = "new-reader-slot")))
        assertEquals(1, calls)
        assertEquals(0, edits)
    }

    @Test fun `per-book reasoning choice persists controls streamed requests and preserves published image`() = runBlocking {
        val updates = mutableListOf<MangaTranslationProgress>()
        val first = repository.translate(page, progress = updates::add)
        assertEquals(AiReasoningLevel.OFF, reasoningLevels.single()) // Disabled by default.
        val trace = updates.mapNotNull { it.ai }.last()
        assertEquals("Provider reasoning", trace.reasoning)
        assertFalse(trace.running)
        assertEquals(responseText.length, trace.outputCharacters)
        repository.update(page.bookUrl) { it.copy(reasoningLevel = AiReasoningLevel.LOW.storageValue) }
        assertEquals(first, repository.cached(page))
        repository = reopen()
        repository.translate(page, progress = {})
        assertEquals(listOf(AiReasoningLevel.OFF, AiReasoningLevel.LOW), reasoningLevels)
        repository.translate(page, progress = {})
        assertEquals(2, calls)
        repository.update(page.bookUrl) { it.copy(reasoningLevel = AiReasoningLevel.OFF.storageValue) }
        repository.recompose(page, progress = {})
        assertEquals(2, calls) // Recomposition must ignore the new reasoning choice.
        repository.translate(page, force = true, progress = {})
        assertEquals(AiReasoningLevel.OFF, reasoningLevels.last())
    }

    @Test fun `normal high-resolution page uses one bounded analysis request across multiple render tiles`() = runBlocking {
        saveOriginal(Color.WHITE, 1800, 2700)
        assertTrue(mangaTranslationBands(1800, 2700).size > 1)
        val phases = mutableListOf<MangaTranslationProgress>()
        val result = repository.translate(page, progress = phases::add)
        assertEquals(1, calls)
        assertEquals(listOf(1), phases.filter { it.stage == MangaTranslationStage.ANALYZING }.map { it.total }.distinct())
        assertTrue(inputSizes.single().let { (w, h) -> w.toLong() * h <= 2 * 1024 * 1024 && h < 2700 })
        assertRequestFrameMatchesImage(0)
        val dimensions = Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(result.imagePath, dimensions)
        assertEquals(1800, dimensions.outWidth); assertEquals(2700, dimensions.outHeight)
        repository.update(page.bookUrl) { it.copy(fontScale = 0.75f) }
        repository.translate(page, progress = {})
        assertEquals(1, calls)
    }

    @Test fun `AI background repair runs once for a normal page instead of once per render tile`() = runBlocking {
        saveOriginal(Color.WHITE, 1800, 2700)
        repository.update(page.bookUrl) { it.copy(composition = MangaComposition.AI_REPAIR, imageModelId = "image") }
        repository.translate(page, progress = {})
        assertEquals(1, calls); assertEquals(1, edits)
    }

    @Test fun `AI whole-page editing remains a single request while PNG output is tiled`() = runBlocking {
        saveOriginal(Color.WHITE, 1800, 2700)
        repository.update(page.bookUrl) { it.copy(composition = MangaComposition.AI_PAGE, imageModelId = "image") }
        val output = repository.translate(page, progress = {})
        assertEquals(0, calls); assertEquals(1, edits)
        val dimensions = Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output.imagePath, dimensions)
        assertEquals(1800, dimensions.outWidth); assertEquals(2700, dimensions.outHeight)
    }

    @Test fun `complete lettering layout is identical on both sides of a render boundary`() = runBlocking {
        saveOriginal(Color.WHITE, 800, 2048)
        val box = MangaTextBox(125f, 625f, 875f, 875f)
        val region = MangaTranslatedRegion("r0", "hello", "A comic line with several words. ".repeat(7), box, sourceFontSize = 30f)
        val plan = MangaTranslationPlan(listOf(region))
        detectedRegions = plan.regions
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL) }
        responseText = MangaTranslationPlan.json.encodeToString(mapOf("translations" to plan.regions.map { mapOf("id" to it.id, "translation" to it.translation) }))
        val reference = BitmapFactory.decodeFile(original.absolutePath).copy(Bitmap.Config.ARGB_8888, true)
        val measured = MangaPageComposer.prepare(reference, plan)
        MangaPageComposer.erase(reference, measured); MangaPageComposer.draw(reference, measured, false, 1f)
        val output = repository.translate(page, progress = {})
        val actual = BitmapFactory.decodeFile(output.imagePath)
        try {
            val expectedPixels = IntArray(800 * 2048); val actualPixels = IntArray(expectedPixels.size)
            reference.getPixels(expectedPixels, 0, 800, 0, 0, 800, 2048)
            actual.getPixels(actualPixels, 0, 800, 0, 0, 800, 2048)
            assertArrayEquals(expectedPixels, actualPixels)
            assertEquals(1, calls)
        } finally { reference.recycle(); actual.recycle() }
    }

    @Test fun `long strip requests receive preceding dialogue as context`() = runBlocking {
        saveOriginal(Color.WHITE, 512, 6000)
        repository.update(page.bookUrl) { it.copy(sourceLanguage = "und") }
        repository.translate(page, progress = {})
        assertTrue(calls > 1)
        assertTrue(requestContexts.drop(1).all { it.contains("Previous dialogue") && it.contains("hello") })
        requests.indices.forEach(::assertRequestFrameMatchesImage)
    }

    @Test fun `lettering clipped by an internal analysis edge fails instead of publishing a truncated translation`() = runBlocking {
        saveOriginal(Color.WHITE, 512, 6000)
        repository.update(page.bookUrl) { it.copy(sourceLanguage = "und") }
        responseText = """{"regions":[{"id":"r0","original":"clipped","translation":"text","typesetting":{"lines":["text"],"fontSize":30},"textBox":{"left":100,"top":0,"right":900,"bottom":800},"layoutBox":{"left":100,"top":0,"right":900,"bottom":800}}]}"""
        try { repository.translate(page, progress = {}); fail("Expected a clipped-region error") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("internal analysis edge")) }
        assertNull(repository.cached(page))
    }

    @Test fun `manual terminology overrides learned names and invalidates analysis`() = runBlocking {
        val previous = repository.translate(page, progress = {})
        assertEquals("爱丽丝", repository.glossary(page.bookUrl)["Alice"])
        repository.setGlossary(page.bookUrl, mapOf("Alice" to "艾莉丝"))
        assertEquals(previous, repository.cached(page))
        repository.translate(page, progress = {})
        assertEquals("艾莉丝", repository.glossary(page.bookUrl)["Alice"])
        assertEquals(2, calls)
        repository.clear(page.bookUrl)
        assertNull(repository.cached(page))
        assertEquals("艾莉丝", repository.glossary(page.bookUrl)["Alice"])
    }

    @Test fun `local recompose restores saved anchors after restart and ignores changed AI configuration`() = runBlocking {
        val first = repository.translate(page, progress = {})
        assertTrue(first.canRecompose)
        repository.update(page.bookUrl) { it.copy(composition = MangaComposition.PATCH, fontScale = 0.75f,
            modelId = "removed", targetLanguage = "fr", locator = MangaLocator.LOCAL) }
        // Published metadata is self-contained; analysis intermediates can be regenerated or lost.
        File(first.imagePath).parentFile!!.listFiles()!!.filter { it.name.contains("page2") || it.name.contains("analysis-bands") }.forEach { it.delete() }
        repository = reopen()
        val second = repository.recompose(page, progress = {})
        assertEquals(1, calls); assertEquals(0, edits)
        assertNotEquals(first.imagePath, second.imagePath)
        assertTrue(File(first.imagePath).exists())
        repository = reopen()
        assertEquals(second, repository.cached(page))
    }

    @Test fun `legacy published anchors upgrade locally once without changing translation or calling AI`() = runBlocking {
        val first = repository.translate(page, progress = {})
        val publication = File(first.imagePath).parentFile!!.listFiles()!!.single { it.name.endsWith(".published.json") }
        val legacy = publication.readText().replace("\"localGeometryVersion\":${MangaPageComposer.GEOMETRY_VERSION}", "\"localGeometryVersion\":0")
        assertTrue(legacy.contains("\"localGeometryVersion\":0"))
        publication.writeText(legacy)
        File(first.imagePath).parentFile!!.listFiles()!!.filter { it.name.contains("page2") || it.name.contains("analysis-bands") }.forEach { it.delete() }
        repository.update(page.bookUrl) { it.copy(modelId = "removed", composition = MangaComposition.PATCH) }
        repository = reopen()
        val upgraded = repository.recompose(page, progress = {})
        val snapshot = publication.readText()
        assertTrue(snapshot, snapshot.contains("\"localGeometryVersion\":${MangaPageComposer.GEOMETRY_VERSION}"))
        assertTrue(snapshot.contains("\"original\":\"hello\"")); assertTrue(snapshot.contains("\"translation\":\"Hi\""))
        val again = repository.recompose(page, progress = {})
        assertArrayEquals(File(upgraded.imagePath).readBytes(), File(again.imagePath).readBytes())
        assertEquals(1, calls); assertEquals(0, edits)
        assertTrue(File(first.imagePath).exists())
    }

    @Test fun `v1 offset capsule anchors recompose through full image overlays without AI`() = runBlocking {
        val bitmap = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        Canvas(bitmap).apply {
            val capsule = RectF(70f, 35f, 230f, 465f)
            drawRoundRect(capsule, 80f, 80f, Paint().apply { color = Color.WHITE })
            drawRoundRect(capsule, 80f, 80f, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f })
            for (y in listOf(95, 155, 215, 275, 335, 395)) drawRect(108f, y.toFloat(), 160f, y + 18f, Paint().apply { color = Color.BLACK })
        }
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val offset = MangaTextBox(500f, 340f, 815f, 640f)
        val source = MangaTranslatedRegion("r0", "Several horizontal lines of source dialogue", "Translated dialogue", offset)
        detectedRegions = listOf(source)
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL) }
        responseText = """{"translations":[{"id":"r0","translation":"Translated dialogue"}]}"""
        val first = repository.translate(page, progress = {})
        val publication = File(first.imagePath).parentFile!!.listFiles()!!.single { it.name.endsWith(".published.json") }
        val json = MangaTranslationPlan.json
        val snapshot = json.parseToJsonElement(publication.readText()).jsonObject
        val chunk = snapshot.getValue("chunks").jsonArray.single().jsonObject
        val legacy = json.parseToJsonElement(json.encodeToString(MangaTranslationPlan(listOf(source.copy(sourceFontSize = 40f)), localGeometryVersion = 1)))
        publication.writeText(JsonObject(snapshot + ("chunks" to JsonArray(listOf(JsonObject(chunk + ("plan" to legacy)))))).toString())
        repository.update(page.bookUrl) { it.copy(modelId = "removed", composition = MangaComposition.SMART) }
        repository = reopen()
        val updated = repository.recompose(page, progress = {})
        val result = BitmapFactory.decodeFile(updated.imagePath)
        assertEquals(Color.WHITE, result.getPixel(112, 103))
        assertEquals(Color.WHITE, result.getPixel(112, 403))
        assertEquals(Color.BLACK, result.getPixel(70, 250))
        result.recycle()
        val saved = json.parseToJsonElement(publication.readText()).jsonObject.getValue("chunks").jsonArray.single().jsonObject.getValue("plan")
        val measured = json.decodeFromJsonElement(MangaTranslationPlan.serializer(), saved)
        assertEquals(MangaPageComposer.GEOMETRY_VERSION, measured.localGeometryVersion)
        assertEquals(offset, measured.regions.single().recognitionBox)
        assertEquals(source.original, measured.regions.single().original)
        assertEquals(source.translation, measured.regions.single().translation)
        assertEquals(1, calls); assertEquals(0, edits)
        assertTrue(File(first.imagePath).exists())
    }

    @Test fun `failed recomposition preserves last publication and missing anchors never trigger AI`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(composition = MangaComposition.AI_PAGE, imageModelId = "image") }
        val first = repository.translate(page, progress = {})
        assertFalse(first.canRecompose)
        repository.update(page.bookUrl) { it.copy(composition = MangaComposition.PATCH) }
        try { repository.recompose(page, progress = {}); fail("Expected missing anchors") }
        catch (_: IllegalArgumentException) { }
        assertEquals(first, repository.cached(page))
        assertEquals(0, calls); assertEquals(1, edits)
        saveOriginal(Color.YELLOW)
        assertNull(repository.cached(page))
        try { repository.recompose(page, progress = {}); fail("Expected missing anchors for new source") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, calls); assertEquals(1, edits)
    }

    @Test fun `overlapping indistinguishable source regions never replace the last publication`() = runBlocking {
        val first = repository.translate(page, progress = {})
        val box = MangaTextBox(100f, 100f, 900f, 700f)
        val a = MangaTranslatedRegion("a", "First source", "First translation", box,
            typesetting = MangaAiTypesetting(listOf("First translation"), 30f))
        responseText = MangaTranslationPlan.json.encodeToString(MangaTranslationPlan(listOf(a, a.copy(id = "b", original = "Second source", translation = "Second translation", typesetting = MangaAiTypesetting(listOf("Second translation"), 30f)))))
        try { repository.translate(page, force = true, progress = {}); fail("Ambiguous overlap must fail") }
        catch (expected: IllegalArgumentException) {
            assertEquals(RuntimeEnvironment.getApplication().getString(io.legado.app.R.string.manga_translation_layout_overlap), expected.message)
        }
        assertEquals(first, repository.cached(page))
        assertTrue(File(first.imagePath).exists())
    }

    @Test fun `v2 oversized font baseline is remeasured locally without AI`() = runBlocking {
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).drawText("HELLO", 60f, 150f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 40f })
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val region = MangaTranslatedRegion("r0", "HELLO", "国", MangaTextBox(110f, 230f, 430f, 310f))
        detectedRegions = listOf(region)
        repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL) }
        responseText = """{"translations":[{"id":"r0","translation":"国"}]}"""
        val first = repository.translate(page, progress = {})
        val publication = File(first.imagePath).parentFile!!.listFiles()!!.single { it.name.endsWith(".published.json") }
        val json = MangaTranslationPlan.json
        val snapshot = json.parseToJsonElement(publication.readText()).jsonObject
        val chunk = snapshot.getValue("chunks").jsonArray.single().jsonObject
        val old = json.parseToJsonElement(json.encodeToString(MangaTranslationPlan(listOf(region.copy(sourceFontSize = 200f)), localGeometryVersion = 2)))
        publication.writeText(JsonObject(snapshot + ("chunks" to JsonArray(listOf(JsonObject(chunk + ("plan" to old)))))).toString())
        repository.update(page.bookUrl) { it.copy(modelId = "removed", fontScale = 1f) }
        repository = reopen()
        repository.recompose(page, progress = {})
        val measuredJson = json.parseToJsonElement(publication.readText()).jsonObject.getValue("chunks").jsonArray.single().jsonObject.getValue("plan")
        val measured = json.decodeFromJsonElement(MangaTranslationPlan.serializer(), measuredJson)
        val pixels = measured.regions.single().sourceFontSize!! * 500 / 1000
        assertTrue("Old 100px baseline remains $pixels", pixels in 24f..38f)
        assertEquals(MangaPageComposer.GEOMETRY_VERSION, measured.localGeometryVersion)
        assertEquals(1, calls); assertEquals(0, edits)
    }

    @Test fun `font changes reuse paid AI background repair and explicit retry bypasses it`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(composition = MangaComposition.AI_REPAIR, imageModelId = "image") }
        repository.translate(page, progress = {})
        repository.update(page.bookUrl) { it.copy(fontScale = 0.75f) }
        repository.translate(page, progress = {})
        assertEquals(1, calls)
        assertEquals(1, edits)
        repository.translate(page, force = true, progress = {})
        assertEquals(2, calls)
        assertEquals(2, edits)
    }

    @Test fun `deleted automatic names are not relearned until manually restored`() = runBlocking {
        repository.translate(page, progress = {})
        repository.setGlossary(page.bookUrl, emptyMap())
        repository.translate(page, progress = {})
        assertFalse(repository.glossary(page.bookUrl).containsKey("Alice"))
    }
    @Test fun `failed text translation retries and target model changes reuse independently cached vision`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(ocrModelId = "ocr", modelId = "text-only") }
        var failTranslation = true
        respond = { request -> when {
            request.messages.first().content.contains("COMPLETE translation and typesetting plan") -> authoredResponse("\"好\"", "cached source")
            request.model.id == "ocr" -> """{"regions":[{"id":"r0","original":"cached source","textBox":{"left":100,"top":100,"right":900,"bottom":700}}]}"""
            failTranslation -> error("text service fixture failure")
            else -> """{"translations":[{"id":"r0","translation":"好"}]}"""
        } }
        assertTrue(runCatching { repository.translate(page, progress = {}) }.isFailure)
        assertEquals(2, calls)
        assertNull(repository.cached(page))
        failTranslation = false
        repository = reopen()
        repository.translate(page, progress = {})
        assertEquals(4, calls)
        assertEquals(1, requests.count { it.messages.first().content.startsWith("Recognize comic lettering only") })
        repository.update(page.bookUrl) { it.copy(targetLanguage = "en", modelId = "") }
        repository.translate(page, progress = {})
        assertEquals(6, calls)
        assertEquals(1, requests.count { it.messages.first().content.startsWith("Recognize comic lettering only") })
        assertTrue(requests.last().messages.last().content.contains("cached source"))
    }

    @Test fun `local short translation removes bottom strokes and word tails through grouping delta PNG and recompose`() = runBlocking {
        saveOriginal(Color.WHITE, 400, 200)
        val image = BitmapFactory.decodeFile(original.absolutePath).copy(Bitmap.Config.ARGB_8888, true)
        try {
            Canvas(image).apply {
                val ink = Paint().apply { color = Color.BLACK }
                drawRect(80f, 50f, 95f, 75f, ink)
                drawRect(90f, 79f, 96f, 82f, ink)
                drawRect(210f, 50f, 225f, 75f, ink)
                drawRect(20f, 40f, 25f, 90f, Paint().apply { color = Color.RED })
            }
            original.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
        fun box(l: Int, t: Int, r: Int, b: Int) = MangaTextBox(l * 2.5f, t * 5f, r * 2.5f, b * 5f)
        val line = box(80, 50, 96, 75)
        detectedRegions = listOf(MangaTranslatedRegion("r0", "LONG ORIGINAL", "LONG ORIGINAL", box(80, 50, 230, 84),
            eraseBoxes = listOf(line), sourceLines = listOf(MangaSourceLine("LONG ORIGINAL", line))))
        responseText = """{"translations":[{"id":"r0","translation":"短"}]}"""
        for (composition in listOf(MangaComposition.SMART, MangaComposition.PATCH)) {
            repository.update(page.bookUrl) { it.copy(locator = MangaLocator.LOCAL, composition = composition) }
            val output = repository.translate(page, progress = {})
            val result = BitmapFactory.decodeFile(output.imagePath)
            try {
                assertEquals(Color.WHITE, result.getPixel(92, 80))
                assertEquals(Color.WHITE, result.getPixel(215, 65))
                assertEquals(Color.RED, result.getPixel(22, 60))
            } finally { result.recycle() }
        }
        assertEquals(1, calls)
        repository = reopen()
        val recomposed = repository.recompose(page, progress = {})
        val rendered = BitmapFactory.decodeFile(recomposed.imagePath)
        try { assertEquals(Color.WHITE, rendered.getPixel(215, 65)) } finally { rendered.recycle() }
        assertEquals(1, calls)
    }

    private fun authoredResponse(lines: String = "\"Hi\"", originalText: String = "hello") =
        """{"frame":{"id":"page","width":64,"height":96},"regions":[{"id":"r0","original":"$originalText","textBox":{"left":100,"top":100,"right":900,"bottom":700},"layoutBox":{"left":200,"top":300,"right":800,"bottom":900},"typesetting":{"lines":[$lines],"fontSize":120,"align":"LEFT","verticalAlign":"TOP"}}]}"""

    @Test fun `legacy AI coordinate failures are corrected once with source and translation preserved`() = runBlocking {
        val good = responseText
        respond = { request -> if (request.messages.last().content.contains("CORRECTION ONLY")) good
            else good.replace("\"bottom\":700", "\"bottom\":1700") }
        val result = repository.translate(page, progress = {})
        assertTrue(File(result.imagePath).isFile)
        assertEquals(2, calls)
        assertEquals(requests.first().messages, requests.last().messages.take(2))
        assertEquals(requests.first().model, requests.last().model)
    }

    @Test fun `AI layout missing one anchor is completed once without discarding the first translation`() = runBlocking {
        bubbleDetections = anchoredDetections()
        respond = { request -> if (request.messages.last().content.contains("CORRECTION ONLY")) anchoredResponse()
            else """{"regions":[{"id":"a0","original":"first","typesetting":{"lines":["第一"]}}]}""" }
        val result = repository.translate(page, progress = {})
        assertTrue(File(result.imagePath).isFile)
        assertEquals(2, calls)
    }

    @Test fun `AI authored whole page falls back to one lossless vision request if optional detection is unavailable`() = runBlocking {
        bubbleFailure = true
        responseText = authoredResponse()
        val phases = mutableListOf<MangaTranslationStage>()
        val first = repository.translate(page) { phases += it.stage }
        assertEquals(1, calls); assertEquals(1, bubbleCalls); assertEquals(0, paddleCalls)
        assertEquals("image/png", requests.single().messages.last().images.single().mimeType)
        assertTrue(requests.single().messages.first().content.contains("COMPLETE translation and typesetting plan"))
        assertTrue(MangaTranslationStage.PLANNING in phases)
        val saved = File(first.imagePath).parentFile!!.listFiles()!!.single { it.name.endsWith(".published.json") }.readText()
        assertTrue(saved.contains("typesetting") && saved.contains("LEFT"))
        repository.update(page.bookUrl) { it.copy(fontScale = .75f) }
        repository = reopen()
        repository.recompose(page, progress = {})
        assertEquals(1, calls); assertEquals(1, bubbleCalls)
        assertNotNull(repository.cached(page))
    }

    @Test fun `separate OCR and text models retain their roles and cache the text before visual layout`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(ocrModelId = "ocr", modelId = "text-only") }
        var layoutFailure = true
        respond = { request -> when {
            request.messages.first().content.contains("COMPLETE translation and typesetting plan") -> {
                if (layoutFailure) error("layout fixture failure")
                authoredResponse("\"你好\"", "原文")
            }
            request.model.id == "ocr" -> """{"regions":[{"id":"r0","original":"原文","textBox":{"left":100,"top":100,"right":900,"bottom":700}}]}"""
            else -> """{"translations":[{"id":"r0","translation":"你好"}]}"""
        } }
        assertTrue(runCatching { repository.translate(page, progress = {}) }.isFailure)
        assertEquals(listOf("ocr", "text-only", "ocr"), requests.map { it.model.id })
        assertEquals(listOf(1, 0, 1), requests.map { it.messages.last().images.size })
        layoutFailure = false
        repository = reopen()
        val result = repository.translate(page, progress = {})
        assertEquals(4, calls); assertTrue(File(result.imagePath).exists())
        assertEquals("ocr", requests.last().model.id)
        repository.update(page.bookUrl) { it.copy(fontScale = 1.25f) }
        repository.recompose(page, progress = {})
        assertEquals(4, calls)
        assertEquals(2, bubbleCalls)
    }

    @Test fun `missing AI layout never falls back to old geometry or removes the published image`() = runBlocking {
        val first = repository.translate(page, progress = {})
        responseText = responseText.replace(",\"typesetting\":{\"lines\":[\"Hi\"],\"fontSize\":30}", "")
        val error = runCatching { repository.translate(page, force = true, progress = {}) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("排版") || error.message.orEmpty().contains("typesetting"))
        assertEquals(3, calls) // Prior success plus one invalid plan and one bounded correction.
        assertEquals(first.imagePath, repository.cached(page)!!.imagePath)
    }

    @Test fun `empty AI rows request one complete correction instead of discarding recognized text`() = runBlocking {
        respond = { request ->
            if (request.messages.last().content.startsWith("LAYOUT CORRECTION ONLY")) authoredResponse()
            else authoredResponse("")
        }
        val result = repository.translate(page, progress = {})
        assertTrue(File(result.imagePath).exists())
        assertEquals(2, calls)
        assertTrue(requests.last().messages.last().content.contains("Empty or blank typesetting.lines"))
        assertEquals(requests.first().messages, requests.last().messages.take(2))
    }

    @Test fun `AI out of range layout is corrected once using the same image model and completed text`() = runBlocking {
        respond = { request ->
            if (request.messages.last().content.startsWith("COORDINATE CORRECTION ONLY")) authoredResponse()
            else authoredResponse().replace("\"bottom\":900", "\"bottom\":1900")
        }
        val result = repository.translate(page, progress = {})
        assertTrue(File(result.imagePath).exists())
        assertEquals(2, calls); assertEquals(1, bubbleCalls)
        val repair = requests.last()
        assertEquals(requests.first().model, repair.model)
        assertEquals(requests.first().params, repair.params)
        assertEquals(requests.first().messages, repair.messages.take(2))
        assertEquals(AiMessageRole.ASSISTANT, repair.messages[2].role)
        assertTrue(repair.messages[2].content.contains("1900"))
        assertTrue(repair.messages.last().content.contains("r0.layoutBox"))
        assertTrue(repair.messages.last().content.contains("pixelY/96"))
        repository.recompose(page, progress = {})
        assertEquals(2, calls)
    }

    @Test fun `persistent coordinate failures stop after one correction and preserve published translation`() = runBlocking {
        val first = repository.translate(page, progress = {})
        responseText = authoredResponse().replace("\"bottom\":700", "\"bottom\":1700")
        val failure = runCatching { repository.translate(page, force = true, progress = {}) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("r0.textBox"))
        assertTrue(failure.message.orEmpty().contains("1700"))
        assertEquals(3, calls)
        assertEquals(first.imagePath, repository.cached(page)!!.imagePath)
    }

    @Test fun `coordinate correction cannot silently retranslate or discard text`() = runBlocking {
        val first = repository.translate(page, progress = {})
        respond = { request ->
            if (request.messages.last().content.startsWith("COORDINATE CORRECTION ONLY")) authoredResponse("\"Bye\"")
            else authoredResponse().replace("\"bottom\":900", "\"bottom\":1900")
        }
        val failure = runCatching { repository.translate(page, force = true, progress = {}) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("changed text"))
        assertEquals(3, calls)
        assertEquals(first.imagePath, repository.cached(page)!!.imagePath)
    }

    @Test fun `separate models correct only the visual layout without repeating OCR or translation`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(ocrModelId = "ocr", modelId = "text-only") }
        respond = { request -> when {
            request.messages.last().content.startsWith("COORDINATE CORRECTION ONLY") -> authoredResponse("\"你好\"", "原文")
            request.messages.first().content.contains("COMPLETE translation and typesetting plan") ->
                authoredResponse("\"你好\"", "原文").replace("\"bottom\":900", "\"bottom\":1900")
            request.model.id == "ocr" -> """{"regions":[{"id":"r0","original":"原文","textBox":{"left":100,"top":100,"right":900,"bottom":700}}]}"""
            else -> """{"translations":[{"id":"r0","translation":"你好"}]}"""
        } }
        val result = repository.translate(page, progress = {})
        assertTrue(File(result.imagePath).exists())
        assertEquals(listOf("ocr", "text-only", "ocr", "ocr"), requests.map { it.model.id })
        assertEquals(1, bubbleCalls)
    }

    @Test fun `oversized invalid plan is not resent as correction input`() = runBlocking {
        responseText = authoredResponse().replace("\"bottom\":900", "\"bottom\":1900")
            .replaceFirst("{", "{\"unused\":\"${"x".repeat(128 * 1024)}\",")
        val failure = runCatching { repository.translate(page, progress = {}) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("r0.layoutBox"))
        assertEquals(1, calls)
    }

    @Test fun `cancelling coordinate correction preserves cancellation and published translation`() = runBlocking {
        val first = repository.translate(page, progress = {})
        respond = { request ->
            if (request.messages.last().content.startsWith("COORDINATE CORRECTION ONLY")) throw kotlinx.coroutines.CancellationException("cancel correction")
            authoredResponse().replace("\"bottom\":900", "\"bottom\":1900")
        }
        val failure = runCatching { repository.translate(page, force = true, progress = {}) }.exceptionOrNull()
        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertEquals(3, calls)
        assertEquals(first.imagePath, repository.cached(page)!!.imagePath)
    }

    private fun anchoredDetections() = listOf(
        MangaBubbleDetection(MangaShapeRect(8, 10, 28, 45), .95f, 1),
        MangaBubbleDetection(MangaShapeRect(35, 48, 58, 85), .95f, 1))

    private fun anchoredResponse() = """{"frame":{"id":"page","width":64,"height":96},"regions":[{"id":"a0","original":"first","typesetting":{"lines":["第一"],"fontScale":1}},{"id":"a1","original":"second","typesetting":{"lines":["第二"],"fontScale":1}}],"flows":[{"id":"f","regionIds":["a0","a1"]}]}"""

    @Test fun `touching AI sources share one repair while keeping their separate translations`() = runBlocking {
        val image = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            Canvas(image).apply {
                val paint = Paint().apply { color = Color.BLACK }
                drawRect(8f, 30f, 20f, 48f, paint); drawRect(31f, 30f, 43f, 48f, paint)
            }
            original.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
        val plan = MangaTranslationPlan(listOf(
            MangaTranslatedRegion("r0", "甲", "第一", MangaTextBox(80f, 150f, 400f, 800f), vertical = true,
                typesetting = MangaAiTypesetting(listOf("第一"), 100f, vertical = true)),
            MangaTranslatedRegion("r1", "乙", "第二", MangaTextBox(400f, 150f, 800f, 800f), vertical = true,
                typesetting = MangaAiTypesetting(listOf("第二"), 100f, vertical = true))))
        responseText = MangaTranslationPlan.json.encodeToString(plan)
        repository.update(page.bookUrl) { it.copy(inpaintEngine = MangaInpaintEngine.MI_GAN) }
        val result = repository.translate(page, progress = {})
        assertEquals(1, inpaintCalls)
        val snapshot = File(result.imagePath).parentFile!!.listFiles()!!.single { it.name.endsWith(".published.json") }.readText()
        assertTrue(snapshot.contains("第一") && snapshot.contains("第二"))
        repository.update(page.bookUrl) { it.copy(fontScale = 1.2f) }
        repository.recompose(page, progress = {})
        assertEquals(1, inpaintCalls); assertEquals(1, calls)
    }

    @Test fun `adjacent cached AI layouts from both manga pages survive original pixel projection`() = runBlocking {
        val examples = listOf(
            1808 to listOf(MangaTextBox(765.197f, 364.3289f, 806.23975f, 458.45438f),
                MangaTextBox(806.2479f, 407.32715f, 874.3842f, 504.35794f)),
            1818 to listOf(MangaTextBox(551.44855f, 496.23767f, 635.3909f, 685.2174f),
                MangaTextBox(508.6502f, 555.3681f, 551.4403f, 678.26086f)))
        for ((height, boxes) in examples) {
            saveOriginal(Color.WHITE, 1280, height)
            val plan = MangaTranslationPlan(boxes.mapIndexed { i, box ->
                MangaTranslatedRegion("r$i", "原文$i", "译文$i", box, vertical = true,
                    typesetting = MangaAiTypesetting(listOf("译文$i"), 14f, vertical = true))
            })
            assertFalse(MangaPageComposer.hasLayoutOverlap(plan))
            responseText = MangaTranslationPlan.json.encodeToString(plan)
            repository.update(page.bookUrl) { it.copy(composition = MangaComposition.SMART) }
            val translated = repository.translate(page, force = true, progress = {})
            assertTrue(File(translated.imagePath).isFile)
            val paidCalls = calls
            repository.update(page.bookUrl) { it.copy(composition = MangaComposition.PATCH) }
            assertTrue(File(repository.recompose(page, progress = {}).imagePath).isFile)
            assertEquals(paidCalls, calls)
        }
    }

    @Test fun `AI region editing obtains the same mandatory plan before calling the image editor`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(composition = MangaComposition.AI_REGION, imageModelId = "image") }
        val result = repository.translate(page, progress = {})
        assertTrue(File(result.imagePath).isFile)
        assertEquals(1, calls); assertEquals(1, edits)
        assertTrue(requests.single().messages.first().content.contains("COMPLETE translation and typesetting plan"))
        val snapshot = File(result.imagePath).parentFile!!.listFiles()!!.single { it.name.endsWith(".published.json") }.readText()
        assertTrue(snapshot.contains("typesetting"))
    }

    @Test fun `numbered crops bind separate source positions in one request and survive cached recomposition`() = runBlocking {
        bubbleDetections = anchoredDetections()
        responseText = anchoredResponse()
        val first = repository.translate(page, progress = {})
        assertEquals(1, calls); assertEquals(1, bubbleCalls); assertEquals(0, paddleCalls)
        assertEquals(2, requests.single().messages.last().images.size)
        assertTrue(requests.single().messages.first().content.contains("NUMBERED CROP SHEET"))
        assertTrue(requests.single().messages.last().content.contains("widthPx"))
        val snapshot = File(first.imagePath).parentFile!!.listFiles()!!.single { it.name.endsWith(".published.json") }.readText()
        assertTrue(snapshot.contains("a0") && snapshot.contains("a1") && snapshot.contains("regionIds"))
        repository.update(page.bookUrl) { it.copy(fontScale = 1.2f, composition = MangaComposition.PATCH) }
        repository = reopen()
        val second = repository.recompose(page, progress = {})
        assertTrue(File(second.imagePath).isFile)
        assertEquals(1, calls); assertEquals(1, bubbleCalls)
    }

    @Test fun `omitting a numbered crop cannot publish a partial translation over a prior success`() = runBlocking {
        val previous = repository.translate(page, progress = {})
        bubbleDetections = anchoredDetections()
        responseText = """{"regions":[{"id":"a0","original":"first","typesetting":{"lines":["第一"]}}]}"""
        val failure = runCatching { repository.translate(page, force = true, progress = {}) }.exceptionOrNull()
        assertTrue(failure!!.message.orEmpty().contains("Missing AI anchor IDs"))
        assertEquals(previous.imagePath, repository.cached(page)!!.imagePath)
        assertEquals(3, calls)
    }

    @Test fun `separate OCR and translation models share numbered anchors through all three stages`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(ocrModelId = "ocr", modelId = "text-only") }
        bubbleDetections = anchoredDetections()
        respond = { request -> when {
            request.messages.first().content.startsWith("Recognize comic text only") ->
                """{"regions":[{"id":"a0","original":"first"},{"id":"a1","original":"second"}],"flows":[{"id":"f","regionIds":["a0","a1"]}]}"""
            request.model.id == "text-only" -> """{"translations":[{"id":"a0","translation":"第一"},{"id":"a1","translation":"第二"}]}"""
            else -> anchoredResponse()
        } }
        val result = repository.translate(page, progress = {})
        assertTrue(File(result.imagePath).isFile)
        assertEquals(listOf("ocr", "text-only", "ocr"), requests.map { it.model.id })
        assertEquals(listOf(2, 0, 2), requests.map { it.messages.last().images.size })
        repository.recompose(page, progress = {})
        assertEquals(3, calls); assertEquals(1, bubbleCalls)
    }

    @Test fun `independent AI OCR corrects invalid coordinates before text translation`() = runBlocking {
        repository.update(page.bookUrl) { it.copy(ocrModelId = "ocr") }
        val source = """{"regions":[{"id":"r0","original":"hello","textBox":{"left":100,"top":100,"right":900,"bottom":700}}]}"""
        respond = { request -> when {
            request.messages.first().content.contains("COMPLETE translation and typesetting plan") -> authoredResponse()
            request.model.id == "ocr" -> if (request.messages.last().content.contains("CORRECTION ONLY")) source else source.replace("700", "1700")
            else -> """{"translations":[{"id":"r0","translation":"Hi"}]}"""
        } }
        assertTrue(File(repository.translate(page, progress = {}).imagePath).isFile)
        assertEquals(listOf("ocr", "ocr", "model", "ocr"), requests.map { it.model.id })
    }

    @Test fun `overlapping numbered AI candidates render and recompose without a second paid analysis`() = runBlocking {
        bubbleDetections = listOf(
            MangaBubbleDetection(MangaShapeRect(8, 10, 38, 48), .95f, 1),
            MangaBubbleDetection(MangaShapeRect(30, 40, 58, 85), .95f, 1))
        responseText = anchoredResponse()
        val first = repository.translate(page, progress = {})
        assertTrue(File(first.imagePath).isFile)
        repository.update(page.bookUrl) { it.copy(fontScale = 1.25f) }
        repository = reopen()
        assertTrue(File(repository.recompose(page, progress = {}).imagePath).isFile)
        assertEquals(1, calls)
    }

    private fun drawInpaintSource() {
        responseText = responseText.replace("\"fontSize\":30", "\"fontSize\":140")
        val bitmap = BitmapFactory.decodeFile(original.absolutePath).copy(Bitmap.Config.ARGB_8888, true)
        try {
            Canvas(bitmap).drawText("HELLO", 9f, 35f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f })
            original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @Test fun `background cache follows engine and source mask but not font multiplier or translated layout`() = runBlocking {
        drawInpaintSource()
        repository.update(page.bookUrl) { it.copy(inpaintEngine = MangaInpaintEngine.MI_GAN) }
        val first = repository.translate(page, progress = {})
        assertEquals(1, calls); assertEquals(1, inpaintCalls)
        val background = File(first.imagePath).parentFile!!.listFiles()!!.single { it.name.startsWith("inpaint-") }
        BitmapFactory.decodeFile(background.absolutePath).let { bmp -> try { assertEquals(Color.WHITE, bmp.getPixel(0, 0)) } finally { bmp.recycle() } }
        repository.update(page.bookUrl) { it.copy(fontScale = 1.25f) }
        repository = reopen()
        repository.recompose(page, progress = {})
        assertEquals(1, inpaintCalls); assertEquals(1, calls)
        repository.update(page.bookUrl) { it.copy(inpaintEngine = MangaInpaintEngine.LAMA) }
        repository.recompose(page, progress = {})
        assertEquals(2, inpaintCalls); assertEquals(1, calls)
        assertEquals(MangaInpaintEngine.LAMA, repository.settings(page.bookUrl).first().inpaintEngine)
    }

    @Test fun `missing inpaint weights fail before paid analysis and keep the published image`() = runBlocking {
        val previous = repository.translate(page, progress = {})
        repository.update(page.bookUrl) { it.copy(inpaintEngine = MangaInpaintEngine.MI_GAN) }
        inpaintAvailable = false
        assertTrue(runCatching { repository.translate(page, progress = {}) }.isFailure)
        assertEquals(1, calls); assertEquals(0, inpaintCalls)
        assertEquals(previous.imagePath, repository.cached(page)!!.imagePath)
    }

    @Test fun `inpaint failure cannot replace a previously published translation`() = runBlocking {
        drawInpaintSource()
        val previous = repository.translate(page, progress = {})
        repository.update(page.bookUrl) { it.copy(inpaintEngine = MangaInpaintEngine.MI_GAN) }
        inpaintFailure = true
        assertTrue(runCatching { repository.translate(page, force = true, progress = {}) }.isFailure)
        assertEquals(1, inpaintCalls)
        assertEquals(previous.imagePath, repository.cached(page)!!.imagePath)
    }

}
