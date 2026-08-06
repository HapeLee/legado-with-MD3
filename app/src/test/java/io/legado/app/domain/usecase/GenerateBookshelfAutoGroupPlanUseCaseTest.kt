package io.legado.app.domain.usecase

import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.BookshelfAutoGroupGateway
import io.legado.app.domain.gateway.BookshelfAutoGroupPromptGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.BookshelfAutoGroupApplyResult
import io.legado.app.domain.model.BookshelfAutoGroupBook
import io.legado.app.domain.model.BookshelfAutoGroupErrorReason
import io.legado.app.domain.model.BookshelfAutoGroupException
import io.legado.app.domain.model.BookshelfAutoGroupOptions
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupProgress
import io.legado.app.domain.model.BookshelfAutoGroupPromptText
import io.legado.app.domain.model.BookshelfAutoGroupSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateBookshelfAutoGroupPlanUseCaseTest {

    @Test
    fun `splits more than thirty books and merges responses in order`() = runBlocking {
        val source = BookshelfAutoGroupSource(
            books = (1..31).map { index -> book(index, name = "Book $index") },
            existingGroupNames = emptyList(),
        )
        val aiGateway = RecordingAiTextGateway(
            mutableListOf(
                responseForIds((1..30).map { "b$it" }),
                responseForIds(listOf("b31")),
            )
        )
        val useCase = useCase(source, aiGateway, maxInputChars = 100_000)
        val progress = mutableListOf<BookshelfAutoGroupProgress>()

        val preflight = useCase.preflight(source)
        val plan = useCase.generate(source, "") { progress += it }

        assertEquals(2, preflight.estimatedRequestCount)
        assertEquals(2, aiGateway.requests.size)
        assertEquals(listOf(1, 2), progress.map { it.currentBatch })
        assertEquals(31, plan.groups.single().books.size)
        assertFalse(aiGateway.requests.first().messages.last().content.contains("url-1"))
        assertTrue(aiGateway.requests.first().messages.last().content.contains("\"id\":\"b1\""))
        assertTrue(aiGateway.requests[1].messages.last().content.contains("Previously proposed groups"))
        assertTrue(aiGateway.requests[1].messages.last().content.contains("All"))
    }

    @Test
    fun `capacity failure happens before any ai request`() = runBlocking {
        val source = BookshelfAutoGroupSource(
            books = listOf(book(1, name = "X".repeat(2_000))),
            existingGroupNames = emptyList(),
        )
        val aiGateway = RecordingAiTextGateway(mutableListOf())
        val useCase = useCase(source, aiGateway, maxInputChars = 512)

        val error = runCatching { useCase.generate(source, "") }.exceptionOrNull()

        assertTrue(error is BookshelfAutoGroupException)
        assertEquals(
            BookshelfAutoGroupErrorReason.CapacityTooSmall,
            (error as BookshelfAutoGroupException).reason,
        )
        assertTrue(aiGateway.requests.isEmpty())
    }

    @Test
    fun `context token budget is conservatively checked with utf8 bytes`() = runBlocking {
        val source = BookshelfAutoGroupSource(
            books = listOf(book(1, name = "书".repeat(300))),
            existingGroupNames = emptyList(),
        )
        val aiGateway = RecordingAiTextGateway(mutableListOf())
        val useCase = useCase(
            source = source,
            aiGateway = aiGateway,
            maxInputChars = 100_000,
            contextWindow = 5_000,
        )

        val error = runCatching { useCase.generate(source, "") }.exceptionOrNull()

        assertTrue(error is BookshelfAutoGroupException)
        assertEquals(
            BookshelfAutoGroupErrorReason.CapacityTooSmall,
            (error as BookshelfAutoGroupException).reason,
        )
        assertTrue(aiGateway.requests.isEmpty())
    }

    @Test
    fun `uses dedicated task prompt and keeps mandatory output rules`() = runBlocking {
        val source = BookshelfAutoGroupSource(
            books = listOf(book(1, name = "Book")),
            existingGroupNames = emptyList(),
        )
        val aiGateway = RecordingAiTextGateway(
            mutableListOf(responseForIds(listOf("b1")))
        )
        val useCase = useCase(
            source = source,
            aiGateway = aiGateway,
            maxInputChars = 10_000,
            taskPrompt = "Use my preferred bookshelf taxonomy.",
        )

        useCase.generate(source, "")

        val systemPrompt = aiGateway.requests.single().messages.first().content
        assertTrue(systemPrompt.contains("Use my preferred bookshelf taxonomy."))
        assertTrue(systemPrompt.contains("Return JSON only"))
    }

    @Test
    fun `fast mode applies introduction temperature and disables reasoning`() = runBlocking {
        val source = BookshelfAutoGroupSource(
            books = listOf(book(1, name = "Book")),
            existingGroupNames = emptyList(),
        )
        val aiGateway = RecordingAiTextGateway(
            mutableListOf(
                """{"groups":[{"name":"All","books":[{"id":"b1","reason":"Fits"}]}]}"""
            )
        )
        val useCase = useCase(source, aiGateway, maxInputChars = 10_000)

        val plan = useCase.generate(
            source = source,
            groupingInstruction = "",
            options = BookshelfAutoGroupOptions(
                includeBookIntro = true,
            ),
        )

        val request = aiGateway.requests.single()
        val userPrompt = request.messages.last().content
        assertTrue(userPrompt.contains("\"intro\":\"Intro\""))
        assertTrue(userPrompt.contains("one short sentence"))
        assertEquals("Fits", plan.groups.single().books.single().reason)
        assertEquals(0.3f, request.params.temperature ?: error("temperature missing"), 0f)
        assertEquals(4_096, request.params.maxOutputTokens)
        assertEquals(AiReasoningLevel.OFF, request.params.reasoningLevel)
    }

    @Test
    fun `deep thinking mode removes temperature and uses high reasoning`() = runBlocking {
        val source = BookshelfAutoGroupSource(
            books = listOf(book(1, name = "Book")),
            existingGroupNames = emptyList(),
        )
        val aiGateway = RecordingAiTextGateway(
            mutableListOf(responseForIds(listOf("b1")))
        )
        val useCase = useCase(source, aiGateway, maxInputChars = 10_000)

        useCase.generate(
            source = source,
            groupingInstruction = "",
            options = BookshelfAutoGroupOptions(enableDeepThinking = true),
        )

        val params = aiGateway.requests.single().params
        assertEquals(null, params.temperature)
        assertEquals(AiReasoningLevel.HIGH, params.reasoningLevel)
        assertEquals(4_096, params.maxOutputTokens)
    }

    @Test
    fun `default options omit introductions but always request reasons`() = runBlocking {
        val source = BookshelfAutoGroupSource(
            books = listOf(book(1, name = "Book")),
            existingGroupNames = emptyList(),
        )
        val aiGateway = RecordingAiTextGateway(
            mutableListOf(
                """{"groups":[{"name":"All","books":[{"id":"b1","reason":"Unexpected"}]}]}"""
            )
        )
        val useCase = useCase(source, aiGateway, maxInputChars = 10_000)

        val plan = useCase.generate(source, "")

        val userPrompt = aiGateway.requests.single().messages.last().content
        assertFalse(userPrompt.contains("\"intro\""))
        assertTrue(userPrompt.contains("one short sentence"))
        assertEquals("Unexpected", plan.groups.single().books.single().reason)
    }

    @Test
    fun `localized prompt is used end to end and raw kind remains unchanged`() = runBlocking {
        val rawKind = "0.0分,都市,连载中,15小时前"
        val source = BookshelfAutoGroupSource(
            books = listOf(book(1, name = "测试书籍").copy(kind = rawKind)),
            existingGroupNames = emptyList(),
        )
        val aiGateway = RecordingAiTextGateway(
            mutableListOf(
                """{"groups":[{"name":"都市","books":[{"id":"b1","reason":"内容属于都市题材。"}]}]}"""
            )
        )
        val useCase = useCase(
            source = source,
            aiGateway = aiGateway,
            maxInputChars = 10_000,
            promptText = chinesePromptText(),
        )

        useCase.generate(source, "")

        val request = aiGateway.requests.single()
        assertTrue(request.messages.first().content.contains("所有输出内容使用简体中文"))
        assertTrue(request.messages.last().content.contains("为本批次的每本书生成内容分组方案"))
        assertTrue(request.messages.last().content.contains("\"kind\":\"$rawKind\""))
        assertFalse(request.messages.last().content.contains("Books:"))
    }

    private fun useCase(
        source: BookshelfAutoGroupSource,
        aiGateway: RecordingAiTextGateway,
        maxInputChars: Int,
        contextWindow: Int = 0,
        taskPrompt: String = "",
        promptText: BookshelfAutoGroupPromptText = englishPromptText(),
    ) = GenerateBookshelfAutoGroupPlanUseCase(
        gateway = object : BookshelfAutoGroupGateway {
            override suspend fun loadSource() = source
            override suspend fun applyPlan(plan: BookshelfAutoGroupPlan) =
                BookshelfAutoGroupApplyResult(0, 0, 0, 0)
        },
        promptGateway = object : BookshelfAutoGroupPromptGateway {
            override fun getPromptText() = promptText
        },
        aiProfileGateway = FakeAiProfileGateway(
            preset(maxInputChars, taskPrompt, contextWindow)
        ),
        aiTextGateway = aiGateway,
    )

    private fun preset(
        maxInputChars: Int,
        taskPrompt: String,
        contextWindow: Int,
    ): AiTaskPresetConfig {
        val provider = AiProviderConfig(
            id = "provider",
            name = "Provider",
            protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://example.test",
            apiKey = "key",
        )
        val model = AiModelConfig(
            id = "model",
            provider = provider,
            displayName = "Model",
            modelId = "model-id",
            contextWindow = contextWindow,
            maxOutputTokens = 4_096,
        )
        return AiTaskPresetConfig(
            id = "preset",
            taskType = AiTaskType.BOOKSHELF_AUTO_GROUP,
            name = "Preset",
            model = model,
            promptTemplate = taskPrompt,
            params = AiGenerationParams(maxOutputTokens = 4_096),
            runtimeOptions = AiTaskRuntimeOptions(maxInputChars = maxInputChars),
        )
    }

    private fun responseForIds(ids: List<String>): String = buildString {
        append("{\"groups\":[{\"name\":\"All\",\"books\":[")
        append(ids.joinToString(",") { "{\"id\":\"$it\"}" })
        append("]}]}")
    }

    private fun englishPromptText() = BookshelfAutoGroupPromptText(
        defaultSystemPrompt = "You are a bookshelf organization assistant.",
        mandatoryRules = "Return JSON only and group by content type.",
        generateTask = "Create a grouping plan for every book in this batch.",
        reviseTask = "Revise the plan for every book in this batch.",
        existingGroups = "Existing groups",
        noExistingGroups = "none",
        userRequirements = "User requirements",
        previouslyProposedGroups = "Previously proposed groups",
        reuseGroupNamesRule = "Reuse these names whenever they fit.",
        reasonRule = "Include exactly one short sentence as the reason for each book.",
        currentPlan = "Current plan for this batch",
        books = "Books",
        outputSchemaLabel = "Return this JSON shape only",
        outputSchema = "{\"groups\":[],\"ignoredBooks\":[]}",
    )

    private fun chinesePromptText() = englishPromptText().copy(
        defaultSystemPrompt = "你是书架整理助手。",
        mandatoryRules = "所有输出内容使用简体中文，并按照书籍内容类型分组。",
        generateTask = "为本批次的每本书生成内容分组方案。",
        reviseTask = "调整本批次每本书的内容分组方案。",
        existingGroups = "已有分组",
        noExistingGroups = "无",
        userRequirements = "用户要求",
        previouslyProposedGroups = "之前提出的内容分组",
        reuseGroupNamesRule = "内容含义匹配时复用这些名称。",
        reasonRule = "为每本书提供一句简短中文理由。",
        currentPlan = "本批次当前方案",
        books = "书籍",
        outputSchemaLabel = "只返回以下 JSON 结构",
        outputSchema = "{\"groups\":[],\"ignoredBooks\":[]}",
    )

    private fun book(index: Int, name: String) = BookshelfAutoGroupBook(
        bookUrl = "url-$index",
        name = name,
        author = "Author",
        intro = "Intro",
        kind = "Kind",
        currentGroupNames = emptyList(),
    )

    private class RecordingAiTextGateway(
        private val responses: MutableList<String>,
    ) : AiTextGateway {
        val requests = mutableListOf<AiGenerateRequest>()

        override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> {
            requests += request
            return Result.success(AiGenerateResponse(responses.removeAt(0)))
        }

        override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = emptyFlow()

        override suspend fun fetchModels(provider: AiProviderConfig) =
            Result.success(emptyList<AiAvailableModel>())
    }

    private class FakeAiProfileGateway(
        private val preset: AiTaskPresetConfig,
    ) : AiProfileGateway {
        override fun observeProviders(): Flow<List<AiProviderProfile>> = emptyFlow()
        override fun observeModels(): Flow<List<AiModelProfile>> = emptyFlow()
        override fun observePresets(): Flow<List<AiTaskPreset>> = emptyFlow()
        override suspend fun getProvider(id: String): AiProviderProfile? = null
        override suspend fun getModel(id: String): AiModelProfile? = null
        override suspend fun getTaskPreset(taskType: String) = preset
        override suspend fun getProviderApiKey(providerId: String) = ""
        override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile = error("unused")
        override suspend fun saveModel(draft: AiModelDraft): AiModelProfile = error("unused")
        override suspend fun importProviderModels(
            providerId: String,
            models: List<AiAvailableModel>,
        ): List<AiModelProfile> = error("unused")
        override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig = error("unused")
        override suspend fun saveDefaultChatProfile(draft: AiProfileDraft): AiTaskPresetConfig = error("unused")
        override suspend fun saveTaskPreset(
            taskType: String,
            promptTemplate: String,
            temperature: Float,
            maxOutputTokens: Int,
        ): AiTaskPresetConfig = error("unused")
        override suspend fun deleteProvider(providerId: String) = Unit
        override suspend fun deleteModel(modelId: String) = Unit
    }
}
