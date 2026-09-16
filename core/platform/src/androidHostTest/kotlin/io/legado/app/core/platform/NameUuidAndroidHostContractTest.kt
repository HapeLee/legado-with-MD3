package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.UUID

class NameUuidAndroidHostContractTest : NameUuidContractTest() {

    override fun createDigest(): Digest = JcaDigest

    /**
     * 与 `java.util.UUID.nameUUIDFromBytes` 交叉确认。这是原 `:app` 那条
     * `nameUuidFromBytes 与 java UUID v3 一致` 用例的搬家版——现在它跑在**两个 target** 上，
     * 比原来只在 `:app` 跑覆盖面更宽。
     *
     * 只在 JVM target 可写（`java.util.UUID` 是 JVM API）⇒ 放在子类，不放 commonTest。
     */
    @Test
    fun `matches java UUID nameUUIDFromBytes`() {
        listOf(
            "provider123:model-abc",
            "openai:gpt-4o",
            "providerId:modelId",
        ).forEach { name ->
            val input = name.toByteArray()
            assertEquals(
                UUID.nameUUIDFromBytes(input).toString(),
                nameUuidFromBytes(input, JcaDigest).toString(),
                "与 java 实现不一致：$name",
            )
        }
    }
}
