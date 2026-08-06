package io.legado.app.domain.model

/**
 * Localized text used to assemble bookshelf auto-group prompts.
 * JSON field names remain protocol constants and are therefore not localized.
 */
data class BookshelfAutoGroupPromptText(
    val defaultSystemPrompt: String,
    val mandatoryRules: String,
    val generateTask: String,
    val reviseTask: String,
    val existingGroups: String,
    val noExistingGroups: String,
    val userRequirements: String,
    val previouslyProposedGroups: String,
    val reuseGroupNamesRule: String,
    val reasonRule: String,
    val currentPlan: String,
    val books: String,
    val outputSchemaLabel: String,
    val outputSchema: String,
)
