package io.legado.app.data.ai

import io.legado.app.data.dao.AiChatDao
import io.legado.app.data.entities.AiChatConversation as AiChatConversationEntity
import io.legado.app.data.entities.AiChatMessage as AiChatMessageEntity
import io.legado.app.domain.ai.AiChatConversation
import io.legado.app.domain.ai.AiChatGateway
import io.legado.app.domain.ai.AiChatMessage
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessagePartJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/**
 * [AiChatGateway] 的实现（M4-4）。
 *
 * **本类是 `:core:data` 的 `data/repository/AiChatRepository.kt` 的整份搬迁**：方法体与 DAO
 * 调用形态逐条保留，唯一的差异是公开签名从 Room 实体换成领域模型（[AiChatConversation] /
 * [AiChatMessage]），于是在 DAO 边界上多了映射（见 `AiChatConversationMapper.kt` /
 * `AiChatMessageMapper.kt`），类名加了 `Impl` 后缀。
 *
 * ⚠️ **本实现保留迁移前的 `withContext(Dispatchers.IO)`**（除两个 `Flow` 方法外，每个方法都包）。
 * 与 M4-3 相反（那边迁前就是裸调 DAO，所以实现侧不包），与 M4-1 / M4-2 同侧。
 * **本片是本域最需要照抄调度行为的一片**，因为下面几条分支逻辑里 DAO 调用是**成串**的
 * （读兄弟 → 逐条改写 → 写新消息 → 推会话时间戳），中间任何一次挂起都不该跑在调用者的调度器上。
 *
 * ⚠️ **本域与前三片最大的不同：实现里有真实的分支逻辑**，所以按 M4-2 立的判据，
 * mapper 测试**不够**，必须配 `AiChatRepositoryImplTest`：
 * - [saveRegeneratedMessage]：先 `countBranches(parentMessageId)` 定 `branchIndex`，再把现有兄弟
 *   逐个 `copy(isSelected = false)` 落库（**旧分支不删**，界面上的「1/3」计数靠这些行），
 *   最后写新消息并 `touchConversation`；
 * - [selectBranch]：两处提前返回（消息不存在 / 消息没有 `parentMessageId`）——
 *   根消息没有兄弟可切，直接不动；
 * - [saveMessage] / [createConversation]：先生成 id 与时间戳，再落库，最后推会话 `updatedAt`；
 * - [getBranchCounts]：DAO 返回 `List<BranchCount>`，这里**聚合成 `Map`**；
 * - [deleteConversation]：**两条** DAO 调用，先删消息再删会话。
 * 这些都不是「单条 `dao.xxx()` 委派」，逻辑删掉后 mapper 用例全绿也发现不了。
 *
 * ⚠️ **本域两个实体的集合映射分别住各自的 Mapper 文件**（不是像 [AiArtifactRepositoryImpl] 那样
 * 住本文件）：两条 `List<*>.toDomainList()` 放进同一个文件会撞 JVM facade 签名。
 *
 * ⚠️ `newId` 用 `kotlin.uuid.Uuid`（与迁移前一致），前缀是 `chat` / `message`。**不要**为了
 * 「跨平台纯度」换成别的实现：id 的形态是本域已有数据的一部分，改了会与库里既有的 id 不一致。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换 DAO 的
 * 来源，构造签名与调用方都不动。
 */
class AiChatRepositoryImpl(
    private val aiChatDao: AiChatDao,
) : AiChatGateway {

    override fun observeConversations(): Flow<List<AiChatConversation>> =
        aiChatDao.observeConversations().map { entities -> entities.toDomainList() }

    override fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessage>> =
        aiChatDao.observeSelectedMessages(conversationId).map { entities -> entities.toDomainList() }

    override suspend fun getConversation(id: String): AiChatConversation? = withContext(Dispatchers.IO) {
        aiChatDao.getConversation(id)?.toDomain()
    }

    override suspend fun createConversation(title: String): AiChatConversation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        AiChatConversationEntity(
            id = newId("chat"),
            title = title,
            createdAt = now,
            updatedAt = now,
        ).also { aiChatDao.insertConversation(it) }.toDomain()
    }

    override suspend fun saveMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String?,
        thinkingDuration: Int,
    ): AiChatMessage = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        AiChatMessageEntity(
            id = newId("message"),
            conversationId = conversationId,
            role = role,
            partsJson = AiMessagePartJson.encode(parts),
            createdAt = now,
            branchIndex = 0,
            isSelected = true,
            parentMessageId = parentMessageId,
            thinkingDuration = thinkingDuration,
        ).also {
            aiChatDao.insertMessage(it)
            aiChatDao.touchConversation(conversationId, now)
        }.toDomain()
    }

    override suspend fun saveRegeneratedMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String,
        thinkingDuration: Int,
    ): AiChatMessage = withContext(Dispatchers.IO) {
        val branchCount = aiChatDao.countBranches(parentMessageId)
        val now = System.currentTimeMillis()
        // Deselect existing branches for this parent
        val siblings = aiChatDao.getBranches(parentMessageId)
        siblings.forEach { sibling ->
            aiChatDao.insertMessage(sibling.copy(isSelected = false))
        }
        AiChatMessageEntity(
            id = newId("message"),
            conversationId = conversationId,
            role = role,
            partsJson = AiMessagePartJson.encode(parts),
            createdAt = now,
            branchIndex = branchCount,
            isSelected = true,
            parentMessageId = parentMessageId,
            thinkingDuration = thinkingDuration,
        ).also {
            aiChatDao.insertMessage(it)
            aiChatDao.touchConversation(conversationId, now)
        }.toDomain()
    }

    override suspend fun selectBranch(messageId: String) = withContext(Dispatchers.IO) {
        val message = aiChatDao.getMessage(messageId) ?: return@withContext
        val parentId = message.parentMessageId ?: return@withContext
        // Deselect siblings
        val siblings = aiChatDao.getBranches(parentId)
        siblings.forEach { sibling ->
            aiChatDao.insertMessage(sibling.copy(isSelected = false))
        }
        // Select this branch
        aiChatDao.selectBranch(messageId)
    }

    override suspend fun getBranchCounts(conversationId: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            aiChatDao.getBranchCounts(conversationId).associate { it.parentMessageId to it.cnt }
        }

    override suspend fun updateConversationTitle(conversationId: String, title: String) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationTitle(conversationId, title, System.currentTimeMillis())
        }

    override suspend fun updateReasoningLevel(conversationId: String, reasoningLevel: String) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationReasoningLevel(
                conversationId = conversationId,
                reasoningLevel = reasoningLevel,
                updatedAt = System.currentTimeMillis(),
            )
        }

    override suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        aiChatDao.deleteMessagesByConversation(conversationId)
        aiChatDao.deleteConversation(conversationId)
    }

    private fun newId(prefix: String): String = "${prefix}_${Uuid.random().toString().replace("-", "")}"
}
