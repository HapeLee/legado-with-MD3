package io.legado.app.ui.main.bookshelf.autoGroup

import android.app.Application
import android.os.Looper
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
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.BookshelfAutoGroupApplyResult
import io.legado.app.domain.model.BookshelfAutoGroupBook
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupPromptText
import io.legado.app.domain.model.BookshelfAutoGroupSource
import io.legado.app.domain.usecase.ApplyBookshelfAutoGroupPlanUseCase
import io.legado.app.domain.usecase.GenerateBookshelfAutoGroupPlanUseCase
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AiAutoGroupViewModelTest {

    @Test
    fun `loads batched preflight into ui state`() {
        val source = source(31)
        val viewModel = viewModel(source, mutableListOf())

        viewModel.onIntent(AiAutoGroupIntent.StartSession(1L))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(AiAutoGroupPhase.Preflight, viewModel.uiState.value.phase)
        assertEquals(31, viewModel.uiState.value.bookCount)
        assertEquals(2, viewModel.uiState.value.estimatedRequestCount)
    }

    @Test
    fun `analysis options default off and survive restart within the session`() {
        val viewModel = viewModel(source(1), mutableListOf())
        viewModel.onIntent(AiAutoGroupIntent.StartSession(1L))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(viewModel.uiState.value.includeBookIntro)
        assertFalse(viewModel.uiState.value.enableDeepThinking)

        viewModel.onIntent(AiAutoGroupIntent.SetIncludeBookIntro(true))
        viewModel.onIntent(AiAutoGroupIntent.SetDeepThinkingEnabled(true))
        viewModel.onIntent(AiAutoGroupIntent.Restart)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(viewModel.uiState.value.includeBookIntro)
        assertTrue(viewModel.uiState.value.enableDeepThinking)

        viewModel.onIntent(AiAutoGroupIntent.CloseSession)
        assertFalse(viewModel.uiState.value.includeBookIntro)
        assertFalse(viewModel.uiState.value.enableDeepThinking)
    }

    @Test
    fun `revision failure keeps the reviewed plan`() {
        val source = source(1)
        val viewModel = viewModel(
            source,
            mutableListOf(
                Result.success(AiGenerateResponse("""{"groups":[{"name":"Group","books":[{"id":"b1"}]}]}""")),
                Result.failure(IllegalStateException("network failure")),
            ),
        )
        viewModel.onIntent(AiAutoGroupIntent.StartSession(1L))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        viewModel.onIntent(AiAutoGroupIntent.Analyze)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val reviewedGroups = viewModel.uiState.value.groups

        viewModel.onIntent(AiAutoGroupIntent.UpdateRevisionInstruction("Try again"))
        viewModel.onIntent(AiAutoGroupIntent.Revise)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(AiAutoGroupPhase.Reviewing, viewModel.uiState.value.phase)
        assertEquals(reviewedGroups, viewModel.uiState.value.groups)
        assertTrue(viewModel.uiState.value.groups.isNotEmpty())
    }

    private fun viewModel(
        source: BookshelfAutoGroupSource,
        responses: MutableList<Result<AiGenerateResponse>>,
    ): AiAutoGroupViewModel {
        val gateway = FakeBookshelfGateway(source)
        val generate = GenerateBookshelfAutoGroupPlanUseCase(
            gateway = gateway,
            promptGateway = object : BookshelfAutoGroupPromptGateway {
                override fun getPromptText() = promptText()
            },
            aiProfileGateway = fakeProfileGateway(preset()),
            aiTextGateway = QueueAiTextGateway(responses),
        )
        return AiAutoGroupViewModel(generate, ApplyBookshelfAutoGroupPlanUseCase(gateway))
    }

    private fun promptText() = BookshelfAutoGroupPromptText(
        defaultSystemPrompt = "Bookshelf assistant",
        mandatoryRules = "Return JSON only.",
        generateTask = "Create a grouping plan.",
        reviseTask = "Revise the grouping plan.",
        existingGroups = "Existing groups",
        noExistingGroups = "none",
        userRequirements = "User requirements",
        previouslyProposedGroups = "Previously proposed groups",
        reuseGroupNamesRule = "Reuse matching group names.",
        reasonRule = "Include one short reason.",
        currentPlan = "Current plan",
        books = "Books",
        outputSchemaLabel = "Return this JSON shape",
        outputSchema = "{\"groups\":[],\"ignoredBooks\":[]}",
    )

    private fun source(count: Int) = BookshelfAutoGroupSource(
        books = (1..count).map { index ->
            BookshelfAutoGroupBook(
                bookUrl = "url-$index",
                name = "Book $index",
                author = "Author",
                intro = "Intro",
                kind = "Kind",
                currentGroupNames = emptyList(),
            )
        },
        existingGroupNames = emptyList(),
    )

    private fun preset(): AiTaskPresetConfig {
        val model = AiModelConfig(
            id = "model",
            provider = AiProviderConfig(
                id = "provider",
                name = "Provider",
                protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
                baseUrl = "https://example.test",
                apiKey = "key",
            ),
            displayName = "Model",
            modelId = "model-id",
            maxOutputTokens = 4_096,
        )
        return AiTaskPresetConfig(
            id = "preset",
            taskType = AiTaskType.BOOKSHELF_AUTO_GROUP,
            name = "Preset",
            model = model,
            promptTemplate = "",
            params = AiGenerationParams(maxOutputTokens = 4_096),
            runtimeOptions = AiTaskRuntimeOptions(maxInputChars = 100_000),
        )
    }

    private fun fakeProfileGateway(preset: AiTaskPresetConfig): AiProfileGateway {
        return Proxy.newProxyInstance(
            AiProfileGateway::class.java.classLoader,
            arrayOf(AiProfileGateway::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getTaskPreset" -> preset
                "observeProviders", "observeModels", "observePresets" -> emptyFlow<Any>()
                else -> null
            }
        } as AiProfileGateway
    }

    private class FakeBookshelfGateway(
        private val source: BookshelfAutoGroupSource,
    ) : BookshelfAutoGroupGateway {
        override suspend fun loadSource() = source
        override suspend fun applyPlan(plan: BookshelfAutoGroupPlan) =
            BookshelfAutoGroupApplyResult(0, 0, 0, 0)
    }

    private class QueueAiTextGateway(
        private val responses: MutableList<Result<AiGenerateResponse>>,
    ) : AiTextGateway {
        override suspend fun generate(request: AiGenerateRequest) = responses.removeAt(0)
        override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun fetchModels(provider: AiProviderConfig) =
            Result.success(emptyList<AiAvailableModel>())
    }
}
