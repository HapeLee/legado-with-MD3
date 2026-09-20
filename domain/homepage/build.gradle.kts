plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `ModuleItem` / `CustomSetItem`：本域的**领域模型**早在下沉前就住在 `:core:model`
            // 的 `io.legado.app.domain.model`（与 `domain/ai` 引 `AiMessagePart` 同形）。
            // 本片**不搬它们** —— `:core:model` 是共享层，模型放在那儿是对的，搬走只会让
            // 消费方多改两行 import 而没有任何边界收益。
            implementation(project(":core:model"))
            // `Flow`：本域端口有多个流方法（`flowEnabled` / `flowAll` / `flowBySource` /
            // `flowCustomSets`），`Flow` 的类型在 `kotlinx-coroutines-core`。
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
