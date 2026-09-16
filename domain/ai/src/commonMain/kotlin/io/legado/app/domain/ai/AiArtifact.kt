package io.legado.app.domain.ai

import io.legado.app.core.platform.systemTimeMillis

/**
 * AI 产物（缓存的任务结果）的领域模型（M4-3）。
 *
 * 字段集合、字段顺序、可变性、默认值、常量与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.AiArtifact` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **本域是本模块里唯一带 `companion object` 常量的领域模型**，且这些常量**必须照抄**：
 * [STATUS_PENDING] / [STATUS_RUNNING] / [STATUS_SUCCESS] / [STATUS_FAILED] 被 `:app` 侧
 * **21 处**引用（4 个 UseCase 的构造点、`ReadAiDelegate` 与 `BookCharacterListViewModel` 的
 * `when (status)` 分支），本片迁移后它们改为引用本类的常量；`AiArtifactDao` 的两条 `@Query`
 * 里也以**字符串插值**引用了 `AiArtifact.STATUS_SUCCESS`（那是**实体**的常量，本片不动 DAO，
 * 所以两侧常量**此刻并存且必须取值一致**）。任何一侧改值都会让「查询成功产物」的条件与
 * UI 的分支判断错位——`AiArtifactMapperTest` 有一条用例钉住四个取值。
 *
 * ⚠️ **判等是全字段**（data class 默认，未重写 `equals` / `hashCode`），与 M4-1 `AiPromptPreset`、
 * M4-2 `AiMemory` 同侧，与 M3-6 `TagGroupRule`（只按主键判等）相反 ⇒ 映射用例可以用整对象
 * `assertEquals`，但仍需一条反向用例。
 *
 * ⚠️ **字段一律 `val`**（与 `TagGroupRule` 的全 `var` 相反）。判断依据与 M4-1 / M4-2 一致：
 * 本域**不进备份/恢复**（`Restore.kt` / `Backup.kt` 里没有 `ai_artifacts`），也没有
 * 「粘贴/导入进领域模型」的路径——`AiToolRepository` 用 GSON 序列化的只是 `Map`，不是本类
 * ⇒ 不经 Gson 反射直写字段，`val` 不踩 M3-4 那个「`final` 字段在 JVM 反射下与 ART 行为不一致」
 * 的坑。照抄实体即可。
 *
 * ⚠️ [createdAt] / [updatedAt] 的默认值取 `systemTimeMillis()`，与实体一致。注意调用方
 * （4 个 UseCase 与 `AiToolRepository`）**都显式传 `now`**（同一个 `System.currentTimeMillis()`
 * 值），默认值主要服务「不传」的构造点。
 *
 * ⚠️ 字段名与**声明顺序**都与实体一致：它们是 Room 的列名，且参与两条索引
 * （`["bookUrl", "chapterIndex", "taskType"]` 与 `["contentHash", "promptHash",
 * "modelProfileId"]`，按名字引用），改名会同时打断查询与索引，**不得重命名、不得重排**。
 */
data class AiArtifact(
    val id: String,
    val taskType: String,
    val bookUrl: String,
    val chapterIndex: Int? = null,
    val contentHash: String,
    val promptHash: String,
    val modelProfileId: String,
    val status: Int = STATUS_PENDING,
    val output: String? = null,
    val errorMessage: String? = null,
    val schemaVersion: Int = 1,
    val createdAt: Long = systemTimeMillis(),
    val updatedAt: Long = systemTimeMillis(),
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_SUCCESS = 2
        const val STATUS_FAILED = 3
    }
}
