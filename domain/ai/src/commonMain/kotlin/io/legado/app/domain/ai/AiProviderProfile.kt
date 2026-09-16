package io.legado.app.domain.ai

import io.legado.app.core.platform.systemTimeMillis

/**
 * AI 供应商档案的领域模型（M4-5c）。
 *
 * 字段集合、字段顺序、可变性、默认值、常量与判等语义与 `:core:data` 的 Room 实体
 * `io.legado.app.data.entities.AiProviderProfile` **逐字对齐**——本片只搬不重新设计。
 *
 * ⚠️ **`companion object` 的三个 `AUTH_TYPE_*` 常量必须照抄**：实现侧
 * （`AiProfileRepositoryImpl.saveProvider`）用 [AUTH_TYPE_BEARER] 作为「既没有既有档案、
 * 草稿又没带认证方式」时的默认值 ⇒ 改值会让新建供应商的认证方式静默变化。与 M4-3
 * `AiArtifact.STATUS_*` 一样，**实体侧的同名常量此刻并存且必须取值一致**（`:app` 里
 * `AiProviderEditViewModel` 仍按字符串字面量比对认证方式），本片不删实体常量。
 *
 * ⚠️ **判等是全字段**（data class 默认，未重写 `equals` / `hashCode`），与 M4-1～M4-4 同侧、
 * 与 M3-6 `TagGroupRule`（只按主键判等）相反 ⇒ 映射用例可以用整对象 `assertEquals`，
 * 但仍需逐字段断言与一条反向用例。
 *
 * ⚠️ **字段一律 `val`**：本域不进备份/恢复，也没有「粘贴/导入进领域模型」的路径
 * ⇒ 不经 Gson 反射直写本类字段，`val` 不踩 M3-4 那个「`final` 字段在 JVM 反射下与 ART
 * 行为不一致」的坑。照抄实体即可。
 *
 * ⚠️ **[createdAt] / [updatedAt] 的默认值取 `systemTimeMillis()`**，与实体一致（实体也是这个
 * `expect fun`）。注意实现侧**总是显式传 `now`**（同一个 `systemTimeMillis()` 值）——
 * 默认值只服务「不传」的构造点，且必须是同一个值，否则 `createdAt` / `updatedAt` 会自相矛盾。
 *
 * ⚠️ 字段名与**声明顺序**都是 Room 的列名：改名会打断建表与 `@Query`，**不得重命名、不得重排**。
 */
data class AiProviderProfile(
    val id: String,
    val name: String,
    val protocol: String,
    val baseUrl: String,
    val modelsUrl: String? = null,
    val apiKey: String = "",
    val authType: String = AUTH_TYPE_BEARER,
    val secretRef: String? = null,
    val headersJson: String? = null,
    val chatPath: String? = null,
    val responsesPath: String? = null,
    val messagesPath: String? = null,
    val modelsPath: String? = null,
    val customHeadersJson: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = systemTimeMillis(),
    val updatedAt: Long = systemTimeMillis()
) {
    companion object {
        const val AUTH_TYPE_NONE = "none"
        const val AUTH_TYPE_BEARER = "bearer"
        const val AUTH_TYPE_HEADER = "header"
    }
}
