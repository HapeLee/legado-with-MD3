plugins {
    id("legado.kmp.library")
    // ⚠️ 必须在本模块显式 apply：`@Serializable` 的序列化器由**所在模块**的编译期插件生成，
    // 上游模块 apply 不会替下游的类生成（`legado.kmp.library` 也不带）。缺它时
    // `AiMessagePartJson` 的多态编解码会在运行期抛 `SerializationException`（编译期无感）。
    // `86c7428d24` 把 `AiMessageParts.kt` 从 `:app`（有插件）移进本模块时漏了这一步。
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:platform"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
