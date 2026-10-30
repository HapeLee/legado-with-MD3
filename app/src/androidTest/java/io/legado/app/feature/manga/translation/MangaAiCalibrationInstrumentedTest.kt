package io.legado.app.feature.manga.translation

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.repository.manga.translation.*
import io.legado.app.data.AppDatabase
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.MangaTranslationGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.*
import io.legado.app.domain.model.manga.*
import io.legado.app.ui.main.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Manual calibration; only run/pipeline modes use paid AI. Default suites skip before resolving services.
 * Keys stay in the app; exports contain explicitly supplied sample data and non-secret model metadata only.
 */
@RunWith(AndroidJUnit4::class)
class MangaAiCalibrationInstrumentedTest {
    @Test fun evaluateConfiguredModelOnPublicSample() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val mode = args.getString("mangaAiCalibration")
        assumeTrue("Manual calibration requires an explicit mode", mode in setOf("inventory", "prepare", "render", "recompose", "pipeline", "replay", "run"))
        val language = args.getString("mangaAiCalibrationLanguage") ?: "en"
        require(language in setOf("en", "ja", "ko")) { "Select en, ja or ko for this calibration corpus" }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val folder = File(app.cacheDir, "manga-ai-calibration").apply { mkdirs() }
        fun checkpoint(stage: String) = File(folder, "checkpoint.json").writeText(buildJsonObject {
            put("stage", stage); put("uptimeMillis", SystemClock.elapsedRealtime())
        }.toString())
        checkpoint("profiles")
        val json = MangaTranslationPlan.json
        if (mode == "recompose") {
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val source = File(folder, "source.png")
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, dimensions)
            val width = dimensions.outWidth; val height = dimensions.outHeight
            require(width > 0 && height > 0 && width.toLong() * height <= 4 * 1024 * 1024)
            val planText = File(folder, "replay-plan.json").readText()
            val plan = json.decodeFromString<MangaTranslationPlan>(planText).validate()
            val bookUrl = "manga-local-recompose-test:${UUID.randomUUID()}"
            fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
            val binding = json.parseToJsonElement(File(folder, "replay-metadata.json").readText()).jsonObject
            require(binding["sourceSha256"]?.jsonPrimitive?.content == hash(source.readBytes()) &&
                binding["planSha256"]?.jsonPrimitive?.content == hash(planText.toByteArray())) {
                "Cached plan binding does not match the supplied source image and plan"
            }
            val root = File(app.filesDir, "manga-translation").canonicalFile
            val temporaryBook = File(root, hash(bookUrl.toByteArray())).canonicalFile
            require(temporaryBook.parentFile == root && !temporaryBook.exists())
            val gateway = GlobalContext.get().get<MangaTranslationGateway>()
            val started = SystemClock.elapsedRealtime()
            var diagnostic: String? = null
            val progress: (MangaTranslationProgress) -> Unit = { phase ->
                check(phase.ai == null) { "Local recomposition must not request AI" }
                phase.diagnosticPath?.let { diagnostic = it }
            }
            try {
                gateway.update(bookUrl) { it.copy(composition = MangaComposition.SMART, geometryDiagnostics = true) }
                source.copyTo(File(temporaryBook, "recompose-source.png"))
                File(temporaryBook, "${hash(source.readBytes())}.published.json").writeText(buildJsonObject {
                    put("image", "recompose-source.png"); put("width", width); put("height", height)
                    put("chunks", buildJsonArray { add(buildJsonObject {
                        put("band", json.encodeToJsonElement(MangaTranslationBand.serializer(), MangaTranslationBand(0, height, 0, height)))
                        put("plan", json.parseToJsonElement(planText))
                    }) })
                }.toString())
                val page = MangaTranslationPage("recompose", bookUrl, source.toURI().toString(), "Local calibration", null)
                val smart = gateway.recompose(page, progress)
                File(smart.imagePath).copyTo(File(folder, "rendered-smart.png"), overwrite = true)
                gateway.update(bookUrl) { it.copy(composition = MangaComposition.PATCH) }
                val patch = gateway.recompose(page, progress)
                File(patch.imagePath).copyTo(File(folder, "rendered-patch.png"), overwrite = true)
                diagnostic?.let { File(it).copyTo(File(folder, "diagnostic.png"), overwrite = true) }
                File(folder, "summary.json").writeText(buildJsonObject {
                    put("mode", "recompose"); put("width", width); put("height", height); put("regions", plan.regions.size)
                    put("validPlan", true); put("externalRequestCount", 0); put("elapsedMillis", SystemClock.elapsedRealtime() - started)
                }.toString())
            } catch (failure: Exception) {
                File(folder, "summary.json").writeText(buildJsonObject {
                    put("mode", "recompose"); put("validPlan", false); put("failureType", failure.javaClass.simpleName)
                    if (failure is IllegalArgumentException) put("validationError", failure.message?.take(250))
                }.toString())
                throw AssertionError("Local calibration failed; see sanitized summary.json")
            } finally {
                gateway.clear(bookUrl)
                File(temporaryBook, "book.json").delete(); temporaryBook.delete()
            }
            return@runBlocking
        }
        if (mode == "render") {
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val plan = json.decodeFromString<MangaTranslationPlan>(File(folder, "plan.json").readText()).validate()
            val original = checkNotNull(BitmapFactory.decodeFile(File(folder, "source.png").absolutePath))
            try {
                for (patch in listOf(false, true)) {
                    val working = original.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                    try {
                        if (!patch) MangaPageComposer.erase(working, plan)
                        MangaPageComposer.draw(working, plan, patch, 1f)
                        File(folder, if (patch) "rendered-patch.png" else "rendered-smart.png").outputStream().use {
                            check(working.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally { working.recycle() }
                }
            } finally { original.recycle() }
            return@runBlocking
        }
        val profiles = GlobalContext.get().get<AiProfileGateway>()
        val models = profiles.observeModels().first().filter { it.enabled }
        File(folder, "inventory.json").writeText(buildJsonArray {
            models.forEach { profile ->
                val config = profiles.getModelConfig(profile.id) ?: return@forEach
                add(buildJsonObject {
                    put("profileId", profile.id); put("modelId", config.modelId)
                    put("protocol", config.provider.protocol); put("host", Uri.parse(config.provider.baseUrl).host)
                })
            }
        }.toString())
        if (mode == "inventory") return@runBlocking
        listOf("response.json", "normalized-response.json", "plan.json", "summary.json", "progress.json", "request-metadata.json", "anchors.json")
            .forEach { File(folder, it).delete() }

        // Some device vendors freeze even an active background instrumentation process.
        // A visible host keeps this manual call alive without changing power-policy settings.
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        val selected = args.getString("mangaAiCalibrationModel")
        val candidates = models.filter { if (selected != null) it.id == selected else it.modelId == "deepseek-flash" }
        require(candidates.size == 1) { "Select exactly one model profile using mangaAiCalibrationModel; see inventory.json" }
        val config = checkNotNull(profiles.getModelConfig(candidates.single().id))
        checkpoint("source")
        require(config.provider.protocol == AiProtocol.OPENAI_CHAT_COMPLETIONS) { "This comparison requires the configured OAI Chat Completions profile" }
        val source = File(folder, "source.png")
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, dimensions)
        val width = dimensions.outWidth; val height = dimensions.outHeight
        require(width > 0 && height > 0 && width.toLong() * height <= 4 * 1024 * 1024 && source.length() <= 8 * 1024 * 1024)
        val frame = MangaAiFrame("page", width, height)
        if (mode in setOf("pipeline", "replay")) {
            val replay = mode == "replay"
            val engine = args.getString("mangaAiCalibrationInpaint")?.let(MangaInpaintEngine::valueOf) ?: MangaInpaintEngine.LOCAL
            val inpaintRuntime = MangaLocalInpainter(app, MangaInpaintModelRepository(app, File(folder, "models")))
            val gateway: MangaTranslationGateway = if (!replay) {
                val live = GlobalContext.get().get<AiTextGateway>()
                var responseIndex = 0
                val captured = object : AiTextGateway by live {
                    override fun generateStream(request: AiGenerateRequest) = StringBuilder().let { content ->
                        val index = ++responseIndex
                        live.generateStream(request).onEach { event ->
                            if (event is AiStreamEvent.Content && content.length < 1024 * 1024)
                                content.append(event.text.take(1024 * 1024 - content.length))
                        }.onCompletion {
                            File(folder, "response-$index.json").writeText(content.toString())
                            File(folder, "response.json").writeText(content.toString())
                        }
                    }
                }
                MangaTranslationRepository(app, GlobalContext.get().get<AppDatabase>(), profiles, captured,
                    inpaintReady = inpaintRuntime::requireReady, inpaint = inpaintRuntime::erase)
            } else {
                val response = File(folder, "replay-plan.json").readText().trim().let {
                    if (it.startsWith("```")) it.substringAfter('\n').substringBeforeLast("```").trim() else it
                }
                MangaTranslationRepository(app, GlobalContext.get().get<AppDatabase>(), profiles, object : AiTextGateway {
                    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> = error("Replay uses streaming")
                    override fun generateStream(request: AiGenerateRequest) = flow {
                        val hintText = request.messages.last().content.substringAfter(" Anchor data (original pixels): ", "")
                        val hints = if (hintText.isBlank()) emptyMap() else json.parseToJsonElement(hintText).jsonArray
                            .associate { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject }
                        val saved = json.parseToJsonElement(response).jsonObject
                        val regions = saved.getValue("regions").jsonArray.map { element ->
                            val block = element.jsonObject
                            val hint = hints[block.getValue("id").jsonPrimitive.content] ?: return@map element
                            if (args.getString("mangaAiCalibrationReplayRaw") == "true") return@map element
                            val style = block["typesetting"]?.takeUnless { it == JsonNull }?.jsonObject ?: return@map element
                            if ("fontSize" !in style || "fontScale" in style) return@map element
                            val key = if (block["vertical"]?.jsonPrimitive?.boolean == true) "verticalFontPx" else "horizontalFontPx"
                            val factor = style.getValue("fontSize").jsonPrimitive.float * height / 1000 / hint.getValue(key).jsonPrimitive.float
                            JsonObject(block + ("typesetting" to JsonObject(style + ("fontScale" to JsonPrimitive(factor)))))
                        }
                        emit(AiStreamEvent.Content(JsonObject(saved + ("regions" to JsonArray(regions))).toString()))
                    }
                    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> = error("Replay does not fetch models")
                }, inpaintReady = inpaintRuntime::requireReady, inpaint = inpaintRuntime::erase)
            }
            val bookUrl = "manga-ai-calibration:${UUID.randomUUID()}"
            val hash = MessageDigest.getInstance("SHA-256").digest(bookUrl.toByteArray()).joinToString("") { "%02x".format(it) }
            val root = File(app.filesDir, "manga-translation").canonicalFile
            val temporaryBook = File(root, hash).canonicalFile
            require(temporaryBook.parentFile == root && !temporaryBook.exists())
            val started = SystemClock.elapsedRealtime()
            val requests = mutableSetOf<String>()
            var thinkingMillis = 0L
            var diagnostic: String? = null
            val progress: (MangaTranslationProgress) -> Unit = { phase ->
                phase.ai?.let { requests += it.requestId; thinkingMillis = it.thinkingMillis }
                phase.diagnosticPath?.let { diagnostic = it }
                File(folder, "progress.json").writeText(buildJsonObject {
                    put("elapsedMillis", SystemClock.elapsedRealtime() - started); put("stage", phase.stage.name)
                    put("outputCharacters", phase.ai?.outputCharacters); put("thinkingMillis", thinkingMillis)
                }.toString())
            }
            try {
                gateway.update(bookUrl) { it.copy(sourceLanguage = language, targetLanguage = "zh-CN", modelId = config.id,
                    locator = MangaLocator.AI,
                    inpaintEngine = engine,
                    reasoningLevel = "off", geometryDiagnostics = true) }
                val page = MangaTranslationPage("calibration", bookUrl, source.toURI().toString(), "Public calibration", null)
                val result = gateway.translate(page, force = true, progress = progress)
                File(result.imagePath).copyTo(File(folder, "rendered-smart.png"), overwrite = true)
                val snapshot = temporaryBook.listFiles().orEmpty().single { it.name.endsWith(".published.json") }
                File(folder, "snapshot.json").writeText(snapshot.readText())
                val saved = json.parseToJsonElement(snapshot.readText()).jsonObject
                val chunks = saved.getValue("chunks").jsonArray
                require(chunks.size == 1) { "This calibration comparison requires a single page frame" }
                val plan = chunks.single().jsonObject.getValue("plan")
                File(folder, "plan.json").writeText(plan.toString())
                File(folder, "binding.json").writeText(buildJsonObject {
                    fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
                    put("sourceSha256", hash(source.readBytes())); put("planSha256", hash(plan.toString().toByteArray()))
                }.toString())
                File(folder, "normalized-response.json").writeText(plan.toString())
                val decodedPlan = json.decodeFromJsonElement<MangaTranslationPlan>(plan)
                val original = checkNotNull(BitmapFactory.decodeFile(source.path))
                try {
                    val mask = MangaPageComposer.erasureMask(original, decodedPlan)
                    val renderedMask = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    try {
                        renderedMask.setPixels(IntArray(mask.size) { if (mask[it]) android.graphics.Color.WHITE else android.graphics.Color.BLACK }, 0, width, 0, 0, width, height)
                        File(folder, "erasure-mask.png").outputStream().use { check(renderedMask.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { renderedMask.recycle() }
                } finally { original.recycle() }
                diagnostic?.let { File(it).copyTo(File(folder, "diagnostic.png"), overwrite = true) }
                val initialRequests = requests.size
                gateway.update(bookUrl) { it.copy(composition = MangaComposition.PATCH) }
                val patch = gateway.recompose(page, progress)
                File(patch.imagePath).copyTo(File(folder, "rendered-patch.png"), overwrite = true)
                check(requests.size == initialRequests && initialRequests in 1..2) { "Recomposition must not issue another AI call; analysis allows at most one correction" }
                File(folder, "summary.json").writeText(buildJsonObject {
                    put("pipeline", true); put("modelId", config.modelId); put("width", width); put("height", height)
                    put("sourceLanguage", language)
                    put("aiTypesetting", true)
                    put("inpaintEngine", engine.name)
                    put("regions", decodedPlan.regions.size)
                    put("replay", replay); put("externalRequestCount", if (replay) 0 else requests.size)
                    put("reasoningLevel", "off"); put("thinkingMillis", thinkingMillis)
                    put("requestCount", requests.size); put("elapsedMillis", SystemClock.elapsedRealtime() - started)
                    put("validPlan", true)
                }.toString())
            } catch (failure: Exception) {
                File(folder, "summary.json").writeText(buildJsonObject {
                    put("pipeline", true); put("failureType", failure.javaClass.simpleName)
                    if (failure is IllegalArgumentException) put("validationError", failure.message?.take(300))
                }.toString())
                throw AssertionError("Calibration pipeline failed; see sanitized summary.json")
            } finally {
                gateway.clear(bookUrl)
                File(temporaryBook, "book.json").delete()
                temporaryBook.delete() // only succeeds when the verified, unique test directory is empty
            }
            return@runBlocking
        }
        val preparationStarted = SystemClock.elapsedRealtime()
        val anchorMode = args.getString("mangaAiCalibrationAnchors") == "true"
        var sheetImage: AiImageInput? = null
        val anchors = if (!anchorMode) emptyList() else {
            val original = checkNotNull(BitmapFactory.decodeFile(source.absolutePath))
            try {
                val detected = MangaBubbleDetector.detect(app, original)
                File(folder, "detections.json").writeText(buildJsonArray {
                    detected.forEach { d -> add(buildJsonObject {
                        put("kind", d.kind); put("confidence", d.confidence)
                        put("bounds", buildJsonArray { listOf(d.bounds.left, d.bounds.top, d.bounds.right, d.bounds.bottom).forEach { add(it) } })
                    }) }
                }.toString())
                mangaAiAnchors(original, detected).also { candidates ->
                    if (candidates.isNotEmpty()) {
                        val sheet = mangaAiAnchorSheet(original, candidates)
                        try {
                            val bytes = ByteArrayOutputStream().use { out ->
                                check(sheet.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)); out.toByteArray()
                            }
                            File(folder, "sheet.png").writeBytes(bytes)
                            sheetImage = AiImageInput("image/png", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        } finally { sheet.recycle() }
                    }
                }
            } finally { original.recycle() }
        }
        File(folder, "anchors.json").writeText(json.encodeToString(anchors))
        val localPreparationMillis = SystemClock.elapsedRealtime() - preparationStarted
        checkpoint("settings")
        val lastSettings = File(app.filesDir, "manga-translation").listFiles().orEmpty()
            .map { File(it, "book.json") }.filter { it.isFile }.maxByOrNull { it.lastModified() }?.let {
                json.parseToJsonElement(it.readText()).jsonObject["settings"]?.let { value -> json.decodeFromJsonElement<MangaTranslationSettings>(value) }
            }
        val level = args.getString("mangaAiCalibrationReasoning")?.let { AiReasoningLevel.fromStorage(it) }
            ?: lastSettings?.reasoningLevel?.let { AiReasoningLevel.fromStorage(it) } ?: AiReasoningLevel.AUTO
        val params = config.defaultParams.copy(
            reasoningLevel = if (level == AiReasoningLevel.AUTO) config.defaultParams.reasoningLevel else level,
            maxOutputTokens = (config.defaultParams.maxOutputTokens?.takeIf { it > 0 } ?: 16384).coerceAtMost(16384),
        )
        val pixelCoordinates = args.getString("mangaAiCalibrationCoordinates") == "pixels"
        val prompt = if (anchors.isNotEmpty()) MANGA_AI_ANCHORED_LAYOUT_PROMPT else if (!pixelCoordinates) MANGA_AI_LAYOUT_PROMPT else MANGA_AI_LAYOUT_PROMPT.lineSequence().map { line ->
            when {
                line.startsWith("Every box uses") -> "Every box uses ORIGINAL IMAGE PIXELS in the ENTIRE attached ${width}x${height} image. " +
                    "Origin top-left. All left/right values are physical pixel X in 0..$width, and all top/bottom values are physical pixel Y in 0..$height. " +
                    "The full image corners are (0,0) and ($width,$height). Do not normalize to 1000, 1024, 0..1 or balloon dimensions. The client alone converts pixels to its storage format."
                line.startsWith("For EVERY textBox") -> "For EVERY textBox, layoutBox and eraseBoxes entry, verify 0 <= left < right <= $width and 0 <= top < bottom <= $height. " +
                    "Each eraseBox must be contained by textBox; expand textBox to include all observed source ink when needed. Do not emit optional aiGeometry or glyph samples."
                line.startsWith("fontSize is") -> "fontSize is the typeface em size in ORIGINAL IMAGE PIXELS. It is not pt, sp, a percentage, a row count or a normalized value. " +
                    line.substringAfter("Choose a baseline", "").let { "Choose a baseline$it" }
                else -> line
            }
        }.joinToString("\n")
        val request = AiGenerateRequest(config, params = params, timeoutMillis = 120_000L,
            messages = listOf(AiMessage(AiMessageRole.SYSTEM, prompt), AiMessage(AiMessageRole.USER,
                "Source language: $language. Target language: zh-CN. Echo frame: ${json.encodeToString(frame)}. Glossary: {}. Previous dialogue (context only): []. " +
                    "Return complete source blocks whose SOURCE center Y is in [0.0,${if (pixelCoordinates) height.toFloat() else 1000f}), in the WHOLE image frame; other areas are context." +
                    if (anchors.isNotEmpty()) " Anchor data (original pixels): ${mangaAiAnchorHints(anchors, width, height)}" else "",
                images = listOfNotNull(AiImageInput("image/png", Base64.encodeToString(source.readBytes(), Base64.NO_WRAP)), sheetImage))))
        val started = SystemClock.elapsedRealtime()
        File(folder, "request-metadata.json").writeText(buildJsonObject {
            put("modelId", config.modelId); put("width", width); put("height", height)
            put("sourceLanguage", language)
            put("reasoningLevel", params.reasoningLevel.storageValue); put("maxOutputTokens", params.maxOutputTokens)
            put("coordinateSystem", if (pixelCoordinates) "pixels" else "normalized_1000")
            put("anchorCount", anchors.size); put("localPreparationMillis", localPreparationMillis)
        }.toString())
        if (mode == "prepare") return@runBlocking
        checkpoint("resolve-gateway")
        val gateway = GlobalContext.get().get<AiTextGateway>()
        var thinkingMillis = 0L
        var reasoningParameters: String? = null
        try {
            checkpoint("stream")
            // Exactly one call: retain even invalid raw geometry for a fair first-response comparison.
            var lastCheckpoint = 0L
            val response = mangaAiAnalysis(gateway, request,
                MangaTranslationProgress(MangaTranslationStage.PLANNING), { phase ->
                    phase.ai?.let { thinkingMillis = it.thinkingMillis; reasoningParameters = it.reasoningParameters }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastCheckpoint >= 1000 || phase.ai?.running == false) {
                        lastCheckpoint = now
                        File(folder, "progress.json").writeText(buildJsonObject {
                            put("elapsedMillis", now - started); put("thinkingMillis", thinkingMillis)
                            put("outputCharacters", phase.ai?.outputCharacters); put("running", phase.ai?.running)
                        }.toString())
                    }
                }, app::getString)
            File(folder, "response.json").writeText(response)
            val validation = runCatching {
                validateMangaAiFrame(response, frame)
                val normalized = if (anchors.isNotEmpty()) resolveMangaAiAnchors(response, anchors, frame = frame)
                    else if (!pixelCoordinates) response else normalizePixels(response, width, height)
                File(folder, "normalized-response.json").writeText(normalized)
                parseMangaAiTypesetting(normalized).also { File(folder, "plan.json").writeText(json.encodeToString(it)) }
            }
            File(folder, "summary.json").writeText(buildJsonObject {
                put("modelId", config.modelId); put("host", Uri.parse(config.provider.baseUrl).host)
                put("sourceLanguage", language)
                put("protocol", config.provider.protocol); put("width", width); put("height", height)
                put("reasoningLevel", params.reasoningLevel.storageValue); put("reasoningParameters", reasoningParameters)
                put("maxOutputTokens", params.maxOutputTokens); put("elapsedMillis", SystemClock.elapsedRealtime() - started)
                put("coordinateSystem", if (pixelCoordinates) "pixels" else "normalized_1000")
                put("anchorCount", anchors.size); put("localPreparationMillis", localPreparationMillis)
                put("thinkingMillis", thinkingMillis); put("outputCharacters", response.length)
                put("validPlan", validation.isSuccess); put("validationError", validation.exceptionOrNull()?.message?.take(300))
            }.toString())
        } catch (failure: Exception) {
            // Do not export exception text/stack traces: HTTP errors may contain private URLs.
            File(folder, "summary.json").writeText(buildJsonObject {
                put("modelId", config.modelId); put("elapsedMillis", SystemClock.elapsedRealtime() - started)
                put("failureType", failure.javaClass.simpleName)
                if (failure is AiHttpException) put("httpStatus", failure.statusCode)
            }.toString())
            throw AssertionError("Calibration request failed; see sanitized summary.json")
        }
    }

    private fun normalizePixels(response: String, width: Int, height: Int): String {
        val clean = response.trim().let { if (it.startsWith("```")) it.substringAfter('\n').substringBeforeLast("```").trim() else it }
        val root = MangaTranslationPlan.json.parseToJsonElement(clean).jsonObject
        fun box(value: JsonElement) = JsonObject(value.jsonObject.mapValues { (key, v) ->
            if (key in setOf("left", "right", "top", "bottom")) JsonPrimitive(v.jsonPrimitive.float * 1000 / if (key in setOf("left", "right")) width else height) else v
        })
        val regions = root.getValue("regions").jsonArray.map { element ->
            JsonObject(element.jsonObject.mapValues { (key, value) -> when (key) {
                "textBox", "layoutBox" -> box(value)
                "eraseBoxes" -> JsonArray(value.jsonArray.map(::box))
                "typesetting" -> JsonObject(value.jsonObject + ("fontSize" to JsonPrimitive(value.jsonObject.getValue("fontSize").jsonPrimitive.float * 1000 / height)))
                else -> value
            } })
        }
        return JsonObject(root + ("regions" to JsonArray(regions))).toString()
    }
}
