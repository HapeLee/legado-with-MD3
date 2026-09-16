package io.legado.app.data.ai

import io.legado.app.data.dao.AiArtifactDao
import io.legado.app.data.entities.AiArtifact as AiArtifactEntity
import io.legado.app.domain.ai.AiArtifact
import io.legado.app.domain.ai.AiArtifactGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [AiArtifactGateway] 的实现（M4-3）。
 *
 * **本类是 `:core:data` 的 `data/repository/AiArtifactRepository.kt` 的整份搬迁**：方法体与
 * DAO 调用形态逐条保留，唯一的差异是公开签名从 Room 实体换成领域模型 [AiArtifact]，于是在
 * DAO 边界上多了映射（[toDomain] / [toEntity]，见 `AiArtifactMapper.kt`），类名加了 `Impl`
 * 后缀。
 *
 * 与 M3/M4-1/M4-2 一致的形态：构造参数**直接收 DAO**（原实现收的就是 `AiArtifactDao`，
 * 这里原样保留；`appDatabaseModule` 本来就绑定了它）。
 *
 * ⚠️ **本实现不包 `withContext`**——迁移前的实现就是裸调 DAO（Room 的 `suspend` DAO 自带调度）。
 * 这与 M4-1 / M4-2 相反（那两片迁前每个方法都包了 `Dispatchers.IO`，实现侧照抄了）。
 * **不要为了"对齐上一片"多包一层 IO**，那会改变实际调度行为。同理，本类的构造与源码
 * **不需要 `kotlinx.coroutines.core` 之外的依赖**（连 `Dispatchers` 都不 import）。
 *
 * ⚠️ **[observeBookArtifacts] 是 `domain/ai` 下沉后唯一的 `Flow` 端口方法**（M4-2 把 `AiMemory`
 * 的两个 `Flow` 方法删了），所以这里**必须**用 `map` 在 `Flow` 上做映射，而不是把 DAO 的
 * `Flow<List<实体>>` 直接返回出去——后者会被类型系统拒绝（领域模型的泛型不匹配），但更值得
 * 注意的是**映射要发生在流内、每次发射都做一次**，不能 `first()` 了再映射。
 *
 * ⚠️ **本片没有可删的端口方法**：五个方法全有调用方（见端口 KDoc）。而 DAO 上的
 * `deleteBookArtifacts` 本来就是零调用方（本片同样不动）。**`queryArtifacts` 是本片新"扩"进
 * 端口的**（不是搬迁）：`:app` 的 `AiToolRepository` 原本直连 `aiArtifactDao.queryArtifacts`，
 * 该片把这条 DAO 直连收窄成走端口，于是 `AiToolRepository` 从「1 个 DAO + 3 个 Gateway」变成
 * 「0 个 DAO + 4 个 Gateway」。之所以不留给消费方自己 `toEntity()`，是为了不把映射细节漏到
 * `:app` 侧。
 *
 * ⚠️ 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（`data:database`「Room 唯一 owner」
 * 尚未拆出），所以本模块 `implementation(project(":core:data"))`。等它落地后，本类只换 DAO 的
 * 来源，构造签名与调用方都不动。
 */
class AiArtifactRepositoryImpl(
    private val dao: AiArtifactDao,
) : AiArtifactGateway {

    override fun observeBookArtifacts(bookUrl: String, taskType: String): Flow<List<AiArtifact>> =
        dao.observeBookArtifacts(bookUrl, taskType).map { entities -> entities.toDomainList() }

    override suspend fun getCachedArtifact(
        bookUrl: String,
        chapterIndex: Int?,
        taskType: String,
        contentHash: String,
        promptHash: String,
        modelProfileId: String,
    ): AiArtifact? = dao.getCachedArtifact(
        bookUrl = bookUrl,
        chapterIndex = chapterIndex,
        taskType = taskType,
        contentHash = contentHash,
        promptHash = promptHash,
        modelProfileId = modelProfileId,
    )?.toDomain()

    override suspend fun getArtifactsByContentHash(
        bookUrl: String,
        chapterIndex: Int,
        taskType: String,
        contentHash: String,
        limit: Int,
    ): List<AiArtifact> = dao.queryArtifactsByContentHash(
        bookUrl = bookUrl,
        chapterIndex = chapterIndex,
        taskType = taskType,
        contentHash = contentHash,
        limit = limit,
    ).toDomainList()

    override suspend fun queryArtifacts(
        bookUrl: String?,
        taskType: String?,
        chapterIndex: Int?,
        limit: Int,
    ): List<AiArtifact> = dao.queryArtifacts(
        bookUrl = bookUrl,
        taskType = taskType,
        chapterIndex = chapterIndex,
        limit = limit,
    ).toDomainList()

    override suspend fun upsertArtifact(artifact: AiArtifact) {
        dao.upsert(artifact.toEntity())
    }
}

// ---------- 边界映射的小工具 ----------
//
// 一对一的两条（`toDomain` / `toEntity`）在 `AiArtifactMapper.kt` 里，这里只放集合形态的重载。
// ⚠️ 本域端口只有**一个**集合形态的出口（`getArtifactsByContentHash` 返回 `List`、
// `observeBookArtifacts` 返回 `Flow<List>`，两者共用同一个 `toDomainList`），且 `upsertArtifact`
// 收**单条** ⇒ 与 M4-2 一样**没有** `List<领域> → 实体` 的重载，不要照抄 M4-1 的第二条。

internal fun List<AiArtifactEntity>.toDomainList(): List<AiArtifact> = map { it.toDomain() }
