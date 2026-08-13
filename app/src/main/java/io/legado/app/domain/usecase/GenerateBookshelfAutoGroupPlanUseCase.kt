package io.legado.app.domain.usecase

import com.google.gson.Gson
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.BookshelfAutoGroupGateway
import io.legado.app.domain.gateway.BookshelfAutoGroupPromptGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.BookshelfAutoGroupBook
import io.legado.app.domain.model.BookshelfAutoGroupErrorReason
import io.legado.app.domain.model.BookshelfAutoGroupException
import io.legado.app.domain.model.BookshelfAutoGroupIgnoredBook
import io.legado.app.domain.model.BookshelfAutoGroupOptions
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupPlanGroup
import io.legado.app.domain.model.BookshelfAutoGroupPreflight
import io.legado.app.domain.model.BookshelfAutoGroupProgress
import io.legado.app.domain.model.BookshelfAutoGroupPromptText
import io.legado.app.domain.model.BookshelfAutoGroupSource
import kotlin.text.Charsets.UTF_8

class GenerateBookshelfAutoGroupPlanUseCase(
    private val gateway: BookshelfAutoGroupGateway,
    private val promptGateway: BookshelfAutoGroupPromptGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
) {

    private val gson = Gson()
    private val parser = BookshelfAutoGroupPlanParser()

    suspend fun loadSource(): BookshelfAutoGroupSource = gateway.loadSource()

    suspend fun preflight(
        source: BookshelfAutoGroupSource,
        groupingInstruction: String = "",
        options: BookshelfAutoGroupOptions = BookshelfAutoGroupOptions(),
    ): BookshelfAutoGroupPreflight {
        validateSource(source)
        val analysisSource = source.forAnalysis(options)
        if (analysisSource.books.isEmpty()) {
            return BookshelfAutoGroupPreflight(
                analyzedBookCount = 0,
                effectiveInputCharLimit = 0,
                estimatedRequestCount = 0,
            )
        }
        val promptText = promptGateway.getPromptText()
        val preset = resolvePreset()
        val systemPrompt = resolveSystemPrompt(preset, promptText)
        val inputBudget = effectiveInputBudget(preset)
        val batches = createBatches(analysisSource, batchInputBudget(inputBudget), systemPrompt) { books ->
            buildGeneratePrompt(analysisSource, books, groupingInstruction, emptyList(), options, promptText)
        }
        return BookshelfAutoGroupPreflight(
            analyzedBookCount = analysisSource.bookCount,
            effectiveInputCharLimit = inputBudget.effectiveCharLimit,
            estimatedRequestCount = batches.size,
        )
    }

    suspend fun generate(
        source: BookshelfAutoGroupSource,
        groupingInstruction: String,
        options: BookshelfAutoGroupOptions = BookshelfAutoGroupOptions(),
        onProgress: (BookshelfAutoGroupProgress) -> Unit = {},
    ): BookshelfAutoGroupPlan {
        validateSource(source)
        val analysisSource = source.forAnalysis(options)
        if (analysisSource.books.isEmpty()) return BookshelfAutoGroupPlan(emptyList())
        val promptText = promptGateway.getPromptText()
        val preset = resolvePreset()
        val systemPrompt = resolveSystemPrompt(preset, promptText)
        val inputBudget = effectiveInputBudget(preset)
        val batches = createBatches(analysisSource, batchInputBudget(inputBudget), systemPrompt) { books ->
            buildGeneratePrompt(analysisSource, books, groupingInstruction, emptyList(), options, promptText)
        }
        val plans = mutableListOf<BookshelfAutoGroupPlan>()
        val proposedGroupNames = linkedSetOf<String>()
        batches.forEachIndexed { index, batch ->
            onProgress(BookshelfAutoGroupProgress(index + 1, batches.size))
            val sharedGroupNames = fitSharedGroupNames(
                candidates = proposedGroupNames,
                inputBudget = inputBudget,
                systemPrompt = systemPrompt,
            ) { names ->
                buildGeneratePrompt(analysisSource, batch, groupingInstruction, names, options, promptText)
            }
            val plan = generateBatch(
                preset = preset,
                systemPrompt = systemPrompt,
                prompt = buildGeneratePrompt(
                    analysisSource,
                    batch,
                    groupingInstruction,
                    sharedGroupNames,
                    options,
                    promptText,
                ),
                books = batch,
                existingGroupNames = analysisSource.existingGroupNames.toSet(),
                options = options,
            )
            plans += plan
            plan.groups.mapTo(proposedGroupNames, BookshelfAutoGroupPlanGroup::name)
        }
        return mergePlans(plans, analysisSource)
    }

    suspend fun revise(
        source: BookshelfAutoGroupSource,
        currentPlan: BookshelfAutoGroupPlan,
        instruction: String,
        options: BookshelfAutoGroupOptions = BookshelfAutoGroupOptions(),
        onProgress: (BookshelfAutoGroupProgress) -> Unit = {},
    ): BookshelfAutoGroupPlan {
        require(instruction.isNotBlank()) { "Revision instruction is required" }
        validateSource(source)
        val analysisSource = source.forAnalysis(options)
        if (analysisSource.books.isEmpty()) return BookshelfAutoGroupPlan(emptyList())
        val promptText = promptGateway.getPromptText()
        val preset = resolvePreset()
        val systemPrompt = resolveSystemPrompt(preset, promptText)
        val inputBudget = effectiveInputBudget(preset)
        val batches = createBatches(analysisSource, batchInputBudget(inputBudget), systemPrompt) { books ->
            buildRevisePrompt(analysisSource, currentPlan, books, instruction, emptyList(), options, promptText)
        }
        val plans = mutableListOf<BookshelfAutoGroupPlan>()
        val proposedGroupNames = currentPlan.groups
            .mapTo(linkedSetOf(), BookshelfAutoGroupPlanGroup::name)
        batches.forEachIndexed { index, batch ->
            onProgress(BookshelfAutoGroupProgress(index + 1, batches.size))
            val sharedGroupNames = fitSharedGroupNames(
                candidates = proposedGroupNames,
                inputBudget = inputBudget,
                systemPrompt = systemPrompt,
            ) { names ->
                buildRevisePrompt(analysisSource, currentPlan, batch, instruction, names, options, promptText)
            }
            val plan = generateBatch(
                preset = preset,
                systemPrompt = systemPrompt,
                prompt = buildRevisePrompt(
                    analysisSource,
                    currentPlan,
                    batch,
                    instruction,
                    sharedGroupNames,
                    options,
                    promptText,
                ),
                books = batch,
                existingGroupNames = analysisSource.existingGroupNames.toSet(),
                options = options,
            )
            plans += plan
            plan.groups.mapTo(proposedGroupNames, BookshelfAutoGroupPlanGroup::name)
        }
        return mergePlans(plans, analysisSource)
    }

    private suspend fun generateBatch(
        preset: AiTaskPresetConfig,
        systemPrompt: String,
        prompt: String,
        books: List<PromptBook>,
        existingGroupNames: Set<String>,
        options: BookshelfAutoGroupOptions,
    ): BookshelfAutoGroupPlan {
        val response = aiTextGateway.generate(
            AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                    AiMessage(AiMessageRole.USER, prompt),
                ),
                params = autoGroupParams(preset, options),
            )
        ).getOrThrow().text
        return parser.parse(
            response = response,
            booksByPromptId = books.associate { it.id to it.book },
            existingGroupNames = existingGroupNames,
        )
    }

    private suspend fun resolvePreset(): AiTaskPresetConfig {
        return aiProfileGateway.getTaskPreset(AiTaskType.BOOKSHELF_AUTO_GROUP)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.TEXT_FACTORY)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_BOOK)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
            ?: throw BookshelfAutoGroupException(BookshelfAutoGroupErrorReason.MissingModel)
    }

    private fun resolveSystemPrompt(
        preset: AiTaskPresetConfig,
        promptText: BookshelfAutoGroupPromptText,
    ): String {
        val taskPrompt = preset.promptTemplate.takeIf {
            preset.taskType == AiTaskType.BOOKSHELF_AUTO_GROUP && it.isNotBlank()
        }
        return buildString {
            append(taskPrompt ?: promptText.defaultSystemPrompt)
            append("\n\n")
            append(promptText.mandatoryRules)
        }
    }

    private fun validateSource(source: BookshelfAutoGroupSource) {
        if (source.books.isEmpty()) {
            throw BookshelfAutoGroupException(BookshelfAutoGroupErrorReason.EmptyBookshelf)
        }
    }

    private fun BookshelfAutoGroupSource.forAnalysis(
        options: BookshelfAutoGroupOptions,
    ): BookshelfAutoGroupSource {
        if (!options.incrementalOnly) return this
        // Keep existing group names for reuse while removing grouped book metadata from AI input.
        return copy(books = books.filter { it.currentGroupNames.isEmpty() })
    }

    private fun effectiveInputBudget(preset: AiTaskPresetConfig): InputBudget {
        val configuredLimit = preset.runtimeOptions.maxInputChars
            .takeIf { it > 0 }
            ?: DEFAULT_MAX_INPUT_CHARS
        val outputReserve = preset.params.maxOutputTokens
            ?.takeIf { it > 0 }
            ?: preset.model.maxOutputTokens.takeIf { it > 0 }?.coerceAtMost(DEFAULT_OUTPUT_RESERVE)
            ?: DEFAULT_OUTPUT_RESERVE
        val contextTokenLimit = preset.model.contextWindow
            .takeIf { it > 0 }
            ?.minus(outputReserve)
            ?: Int.MAX_VALUE
        if (contextTokenLimit < MIN_INPUT_CHARS) {
            throw BookshelfAutoGroupException(BookshelfAutoGroupErrorReason.CapacityTooSmall)
        }
        return InputBudget(
            maxChars = configuredLimit,
            // UTF-8 bytes are a conservative upper bound for byte-level tokenizer input tokens.
            maxUtf8Bytes = contextTokenLimit,
        )
    }

    private fun autoGroupParams(
        preset: AiTaskPresetConfig,
        options: BookshelfAutoGroupOptions,
    ) = preset.params.copy(
        temperature = AUTO_GROUP_TEMPERATURE.takeUnless { options.enableDeepThinking },
        reasoningLevel = if (options.enableDeepThinking) {
            AiReasoningLevel.HIGH
        } else {
            AiReasoningLevel.OFF
        },
    )

    private fun createBatches(
        source: BookshelfAutoGroupSource,
        inputBudget: InputBudget,
        systemPrompt: String,
        promptBuilder: (List<PromptBook>) -> String,
    ): List<List<PromptBook>> {
        val promptBooks = source.books.mapIndexed { index, book -> PromptBook("b${index + 1}", book) }
        val batches = mutableListOf<List<PromptBook>>()
        var current = mutableListOf<PromptBook>()
        promptBooks.forEach { book ->
            val candidate = current + book
            val fits = candidate.size <= MAX_BOOKS_PER_BATCH &&
                inputBudget.fits(systemPrompt, promptBuilder(candidate))
            if (fits) {
                current += book
            } else {
                if (current.isNotEmpty()) batches += current.toList()
                if (!inputBudget.fits(systemPrompt, promptBuilder(listOf(book)))) {
                    throw BookshelfAutoGroupException(BookshelfAutoGroupErrorReason.CapacityTooSmall)
                }
                current = mutableListOf(book)
            }
        }
        if (current.isNotEmpty()) batches += current.toList()
        return batches
    }

    private fun batchInputBudget(inputBudget: InputBudget): InputBudget {
        return InputBudget(
            maxChars = (inputBudget.maxChars - SHARED_GROUP_NAMES_RESERVE_CHARS)
                .coerceAtLeast(minOf(MIN_INPUT_CHARS, inputBudget.maxChars)),
            maxUtf8Bytes = (inputBudget.maxUtf8Bytes - SHARED_GROUP_NAMES_RESERVE_UTF8_BYTES)
                .coerceAtLeast(minOf(MIN_INPUT_CHARS, inputBudget.maxUtf8Bytes)),
        )
    }

    private fun fitSharedGroupNames(
        candidates: Collection<String>,
        inputBudget: InputBudget,
        systemPrompt: String,
        promptBuilder: (List<String>) -> String,
    ): List<String> {
        val included = mutableListOf<String>()
        candidates.forEach { name ->
            val candidate = included + name
            if (inputBudget.fits(systemPrompt, promptBuilder(candidate))) {
                included += name
            }
        }
        return included
    }

    private fun buildGeneratePrompt(
        source: BookshelfAutoGroupSource,
        books: List<PromptBook>,
        groupingInstruction: String,
        proposedGroupNames: List<String>,
        options: BookshelfAutoGroupOptions,
        promptText: BookshelfAutoGroupPromptText,
    ): String = buildString {
        append(promptText.generateTask)
        append("\n")
        append(promptText.existingGroups)
        append(": ")
        append(source.existingGroupNames.joinToString(", ").ifBlank { promptText.noExistingGroups })
        groupingInstruction.trim().takeIf(String::isNotBlank)?.let { instruction ->
            append("\n")
            append(promptText.userRequirements)
            append(":\n")
            append(instruction)
        }
        appendSharedGroupNames(proposedGroupNames, promptText)
        appendReasonRequirement(promptText)
        append("\n")
        append(promptText.outputSchemaLabel)
        append(":\n")
        append(promptText.outputSchema)
        append("\n")
        append(promptText.books)
        append(":\n")
        append(gson.toJson(books.map { it.toPromptMap(options.includeBookIntro) }))
    }

    private fun buildRevisePrompt(
        source: BookshelfAutoGroupSource,
        currentPlan: BookshelfAutoGroupPlan,
        books: List<PromptBook>,
        instruction: String,
        proposedGroupNames: List<String>,
        options: BookshelfAutoGroupOptions,
        promptText: BookshelfAutoGroupPromptText,
    ): String = buildString {
        append(promptText.reviseTask)
        append("\n")
        append(promptText.userRequirements)
        append(": ")
        append(instruction.trim())
        append("\n")
        append(promptText.existingGroups)
        append(": ")
        append(source.existingGroupNames.joinToString(", ").ifBlank { promptText.noExistingGroups })
        appendSharedGroupNames(proposedGroupNames, promptText)
        appendReasonRequirement(promptText)
        append("\n")
        append(promptText.currentPlan)
        append(":\n")
        append(currentPlan.toPromptJson(books))
        append("\n")
        append(promptText.books)
        append(":\n")
        append(gson.toJson(books.map { it.toPromptMap(options.includeBookIntro) }))
        append("\n")
        append(promptText.outputSchemaLabel)
        append(":\n")
        append(promptText.outputSchema)
    }

    private fun StringBuilder.appendSharedGroupNames(
        names: List<String>,
        promptText: BookshelfAutoGroupPromptText,
    ) {
        if (names.isEmpty()) return
        append("\n")
        append(promptText.previouslyProposedGroups)
        append(": ")
        append(gson.toJson(names))
        append("\n")
        append(promptText.reuseGroupNamesRule)
    }

    private fun StringBuilder.appendReasonRequirement(promptText: BookshelfAutoGroupPromptText) {
        append("\n")
        append(promptText.reasonRule)
    }

    private fun BookshelfAutoGroupPlan.toPromptJson(books: List<PromptBook>): String {
        val idsByUrl = books.associate { it.book.bookUrl to it.id }
        return gson.toJson(
            mapOf(
                "groups" to groups.mapNotNull { group ->
                    val promptBooks = group.books.mapNotNull { book ->
                        idsByUrl[book.bookUrl]?.let { id -> mapOf("id" to id, "reason" to book.reason) }
                    }
                    group.takeIf { promptBooks.isNotEmpty() }?.let {
                        mapOf("name" to group.name, "description" to group.description, "books" to promptBooks)
                    }
                },
                "ignoredBooks" to ignoredBooks.mapNotNull { book ->
                    idsByUrl[book.bookUrl]?.let { id -> mapOf("id" to id, "reason" to book.reason) }
                },
            )
        )
    }

    private fun PromptBook.toPromptMap(includeBookIntro: Boolean): Map<String, Any> = buildMap {
        put("id", id)
        put("name", book.name)
        book.author.takeIf(String::isNotBlank)?.let { put("author", it) }
        book.kind.takeIf(String::isNotBlank)?.let { put("kind", it) }
        if (includeBookIntro) {
            book.intro.takeIf(String::isNotBlank)?.let { put("intro", it) }
        }
        book.currentGroupNames.takeIf { it.isNotEmpty() }?.let { put("currentGroups", it) }
    }

    private fun mergePlans(
        plans: List<BookshelfAutoGroupPlan>,
        source: BookshelfAutoGroupSource,
    ): BookshelfAutoGroupPlan {
        val existingNames = source.existingGroupNames.toSet()
        val assignedUrls = linkedSetOf<String>()
        val groupsByName = linkedMapOf<String, BookshelfAutoGroupPlanGroup>()
        plans.flatMap(BookshelfAutoGroupPlan::groups).forEach { group ->
            val books = group.books.filter { assignedUrls.add(it.bookUrl) }
            if (books.isEmpty()) return@forEach
            val existing = groupsByName[group.name]
            groupsByName[group.name] = if (existing == null) {
                group.copy(reuseExisting = group.name in existingNames, books = books)
            } else {
                existing.copy(books = existing.books + books)
            }
        }
        val ignoredUrls = linkedSetOf<String>()
        val ignored = plans.flatMap(BookshelfAutoGroupPlan::ignoredBooks)
            .filter { it.bookUrl !in assignedUrls && ignoredUrls.add(it.bookUrl) }
            .toMutableList()
        source.books.forEach { book ->
            if (book.bookUrl !in assignedUrls && ignoredUrls.add(book.bookUrl)) {
                ignored += BookshelfAutoGroupIgnoredBook(book.bookUrl, book.name, book.author, "")
            }
        }
        return BookshelfAutoGroupPlan(groupsByName.values.toList(), ignored)
    }

    private data class PromptBook(
        val id: String,
        val book: BookshelfAutoGroupBook,
    )

    private data class InputBudget(
        val maxChars: Int,
        val maxUtf8Bytes: Int,
    ) {
        val effectiveCharLimit: Int get() = minOf(maxChars, maxUtf8Bytes)

        fun fits(systemPrompt: String, userPrompt: String): Boolean {
            val charCount = systemPrompt.length + userPrompt.length
            if (charCount > maxChars) return false
            val utf8Bytes = systemPrompt.toByteArray(UTF_8).size +
                userPrompt.toByteArray(UTF_8).size
            return utf8Bytes <= maxUtf8Bytes
        }
    }

    private companion object {
        const val DEFAULT_MAX_INPUT_CHARS = 10_000
        const val DEFAULT_OUTPUT_RESERVE = 4_096
        const val MIN_INPUT_CHARS = 512
        const val MAX_BOOKS_PER_BATCH = 30
        const val SHARED_GROUP_NAMES_RESERVE_CHARS = 640
        const val SHARED_GROUP_NAMES_RESERVE_UTF8_BYTES = 640
        const val AUTO_GROUP_TEMPERATURE = 0.3f

    }
}
