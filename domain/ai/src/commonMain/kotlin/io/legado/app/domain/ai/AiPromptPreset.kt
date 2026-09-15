package io.legado.app.domain.ai

import io.legado.app.core.platform.systemTimeMillis

/**
 * AI 提示词预设的领域模型（M4-1）。
 *
 * 字段集合、字段顺序、可变性、默认值与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.AiPromptPreset` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **判等是「全字段」**：实体是全仓少数几个**没有**重写 `equals` / `hashCode` 的 data class
 * 之一（主键是 `id: String`，但没做 id-only 判等）。本类照抄该语义，**不要向 M3-6 的
 * [io.legado.app.domain.rules.TagGroupRule] 看齐给它补一个 id-only 的 `equals`**——那会让领域
 * 模型与实体的判等分叉。连带后果有两个（写在 `AiPromptPresetMapperTest` 里当护栏）：
 * ① 映射用例**可以**用整对象 `assertEquals(实体, 领域.toEntity())`（M3-6 那种只按主键判等的
 * 域明令禁止这么写，因为漏映射照样通过）；② 需要一条**反向用例**钉住「仅 `name` 不同的两条
 * 预设不相等」，同时钉住实体侧也是全字段判等。
 *
 * ⚠️ **字段一律 `val`**（与 `TagGroupRule` 的全 `var` 相反，与 [io.legado.app.domain.rules.RuleSub]
 * 同类）。判断依据是「本模型有没有反序列化路径」：本域**不进备份/恢复**（`Restore.kt` /
 * `Backup.kt` 里没有 `ai_prompt_presets`），也没有「粘贴/导入进领域模型」的路径，因此不会经
 * `:core:platform` 的 `JsonCodec`(Gson) 反射直写字段 ⇒ `val` 不会踩到 M3-4 那个「`final` 字段
 * 在 JVM 反射下与 ART 行为不一致」的坑。照抄实体即可，不要为了「对齐上一片」改成 `var`。
 *
 * ⚠️ [createdAt] / [updatedAt] 的默认值取 `systemTimeMillis()`，与实体一致（新建预设时
 * `ReadAiDelegate` 会显式传 `now`，但默认值仍属契约：任何不传的构造点都依赖它）。
 * `domain/ai` 从建模块起就 `implementation(":core:platform")`，引用它不越界——G2 的 pure 判据
 * 只看 import 前缀（`android.*` / `java.io.File` / `kotlin.jvm.*` / `androidx.*` / 三方实现库），
 * `io.legado.app.core.platform` 不在禁列。
 *
 * ⚠️ 字段名（`taskType` / `sortNumber` / `builtIn` …）与**声明顺序**都与实体一致：它们是 Room
 * 的列名（`Index(value = ["taskType", "enabled", "sortNumber"])` 也按名字引用），改名会同时打断
 * 查询与潜在的备份键名，属持久化契约，**不得重命名、不得重排**。
 */
data class AiPromptPreset(
    val id: String,
    val taskType: String,
    val name: String,
    val instruction: String,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val sortNumber: Int = 0,
    val createdAt: Long = systemTimeMillis(),
    val updatedAt: Long = systemTimeMillis(),
)
