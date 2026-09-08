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

    suspend fun deleteGroup(groupId: Long)

    /**
     * 用当前库中的全部标签分组规则重算**所有书籍**的分组位（在单个事务内完成）。
     *
     * 对应 app 侧原 `io.legado.app.help.book.applyTagGroupRules(books, rules)`：
     * 后者由调用方先取 `books`/`rules` 再在事务里应用，语义等价但多一次读；
     * 本方法在事务内直接读全量，行为以 `TagGroupRuleApplier` 为准
     * （只添加匹配分组、保留手工分组、非法正则跳过）。
     */
    suspend fun applyTagGroupRulesToAllBooks()
}
