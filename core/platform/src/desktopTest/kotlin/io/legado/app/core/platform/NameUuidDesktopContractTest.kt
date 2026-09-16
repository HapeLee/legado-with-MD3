package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.UUID

class NameUuidDesktopContractTest : NameUuidContractTest() {

    override fun createDigest(): Digest = JcaDigest

    /** 见 `NameUuidAndroidHostContractTest` 的同名用例。 */
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
