package io.legado.app.data.repository

import android.content.Context
import io.legado.app.R
import io.legado.app.domain.gateway.BookshelfAutoGroupPromptGateway
import io.legado.app.domain.model.BookshelfAutoGroupPromptText

class BookshelfAutoGroupPromptRepository(
    private val context: Context,
) : BookshelfAutoGroupPromptGateway {

    override fun getPromptText() = BookshelfAutoGroupPromptText(
        defaultSystemPrompt = context.getString(R.string.ai_prompt_default_bookshelf_auto_group),
        mandatoryRules = context.getString(R.string.ai_auto_group_prompt_mandatory_rules),
        generateTask = context.getString(R.string.ai_auto_group_prompt_generate_task),
        reviseTask = context.getString(R.string.ai_auto_group_prompt_revise_task),
        existingGroups = context.getString(R.string.ai_auto_group_prompt_existing_groups),
        noExistingGroups = context.getString(R.string.ai_auto_group_prompt_no_existing_groups),
        userRequirements = context.getString(R.string.ai_auto_group_prompt_user_requirements),
        previouslyProposedGroups = context.getString(
            R.string.ai_auto_group_prompt_previously_proposed_groups
        ),
        reuseGroupNamesRule = context.getString(R.string.ai_auto_group_prompt_reuse_group_names_rule),
        reasonRule = context.getString(R.string.ai_auto_group_prompt_reason_rule),
        currentPlan = context.getString(R.string.ai_auto_group_prompt_current_plan),
        books = context.getString(R.string.ai_auto_group_prompt_books),
        outputSchemaLabel = context.getString(R.string.ai_auto_group_prompt_output_schema_label),
        outputSchema = context.getString(R.string.ai_auto_group_prompt_output_schema),
    )
}
