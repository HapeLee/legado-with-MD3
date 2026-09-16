package io.legado.app.data.ai

import io.legado.app.data.entities.AiProviderProfile as AiProviderProfileEntity
import io.legado.app.domain.ai.AiProviderProfile

/**
 * Room 实体 `io.legado.app.data.entities.AiProviderProfile` ↔ 领域模型
 * `io.legado.app.domain.ai.AiProviderProfile` 的双向映射（M4-5c）。
 *
 * 位置：映射器住 `:data:ai`（M3/M4 模板：domain 只放模型/端口，实现与映射住 `data:<域>`）。
 *
 * ⚠️ **一实体一 Mapper 文件**（M4-4 立的规矩，本片有三个实体所以有三个文件）：若把两条
 * `List<*>.toDomainList()` 放进同一个文件，泛型擦除后它们会编译成同一个 JVM facade 类里的
 * 同名同形方法，构成 platform declaration clash。三个文件 = 三个 facade 类，互不冲突。
 *
 * ⚠️ **本片与 M4-1～M4-4 同侧（全字段判等）**：实体与领域模型**都没有**重写
 * `equals` / `hashCode` ⇒ 映射用例**可以**用整对象 `assertEquals`，但仍需逐字段断言
 * （失败信息更精确）与反向用例。
 *
 * ⚠️ **八个可空字段必须原样搬运 `null`**：`modelsUrl` / `secretRef` / `headersJson` /
 * `chatPath` / `responsesPath` / `messagesPath` / `modelsPath` / `customHeadersJson`。
 * 尤其 `chatPath` / `responsesPath` / `messagesPath` 的 `null` 有**回落语义**——实现侧的
 * `toConfig()` 用 `provider.chatPath ?: "/chat/completions"` 之类给出协议默认路径
 * ⇒ 归一化成空串会让请求打到空路径。`headersJson` / `customHeadersJson` 的 `null` 与
 * `"{}"` 在解析侧等价，但**映射必须恒等**（不得就地解析或规范化）。
 *
 * ⚠️ **`companion object` 的三个 `AUTH_TYPE_*` 常量不在映射范围内**（常量不是字段，不参与
 * `data class` 的 `copy` / 判等）。两侧常量此刻**并存且必须取值一致**（实现的 `saveProvider`
 * 用领域侧常量做默认值，`:app` 的 UI 仍按字面量比对）⇒ `AiProviderProfileMapperTest` 有一条
 * 用例逐值比对三个常量。
 *
 * ⚠️ 逐字段显式赋值，**不用反射/序列化**，也不做默认值回退：映射必须是恒等的。字段顺序照实体写
 * （`id` / `name` / `protocol` / `baseUrl` / `modelsUrl` / `apiKey` / `authType` / `secretRef` /
 * `headersJson` / `chatPath` / `responsesPath` / `messagesPath` / `modelsPath` /
 * `customHeadersJson` / `enabled` / `createdAt` / `updatedAt`），便于与实体声明逐行比对。
 */
fun AiProviderProfileEntity.toDomain(): AiProviderProfile = AiProviderProfile(
    id = id,
    name = name,
    protocol = protocol,
    baseUrl = baseUrl,
    modelsUrl = modelsUrl,
    apiKey = apiKey,
    authType = authType,
    secretRef = secretRef,
    headersJson = headersJson,
    chatPath = chatPath,
    responsesPath = responsesPath,
    messagesPath = messagesPath,
    modelsPath = modelsPath,
    customHeadersJson = customHeadersJson,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AiProviderProfile.toEntity(): AiProviderProfileEntity = AiProviderProfileEntity(
    id = id,
    name = name,
    protocol = protocol,
    baseUrl = baseUrl,
    modelsUrl = modelsUrl,
    apiKey = apiKey,
    authType = authType,
    secretRef = secretRef,
    headersJson = headersJson,
    chatPath = chatPath,
    responsesPath = responsesPath,
    messagesPath = messagesPath,
    modelsPath = modelsPath,
    customHeadersJson = customHeadersJson,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun List<AiProviderProfileEntity>.toDomainList(): List<AiProviderProfile> = map { it.toDomain() }
