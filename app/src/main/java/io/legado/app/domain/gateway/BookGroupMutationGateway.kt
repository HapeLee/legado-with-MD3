package io.legado.app.domain.gateway

import io.legado.app.domain.model.BookGroupUpdate
import io.legado.app.domain.model.NewBookGroup
import io.legado.app.domain.model.TagGroupRuleUpdate

interface BookGroupMutationGateway {

    suspend fun addGroup(group: NewBookGroup)

    suspend fun saveGroup(
        bookGroup: BookGroupUpdate,
        ruleToSave: TagGroupRuleUpdate?,
        ruleIdToDelete: Long?,
    )

    suspend fun saveTagGroupRule(rule: TagGroupRuleUpdate)

    suspend fun deleteTagGroupRule(ruleId: Long)
}
