plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `systemTimeMillis`：领域模型的 `createdAt` / `updatedAt` 默认值必须与迁移前的
            // Room 实体逐字一致（原 `AiPromptPreset.createdAt = systemTimeMillis()`），否则
            // 「新建预设」的时间戳生成规则会静默改变。它是 `:core:platform` 的 `expect fun`，
            // 各 target 都有 actual，不是平台实现库 —— pure 模块引用它不越界（G2 只拦
            // `android.*` / `java.io.File` / `kotlin.jvm.*` / `androidx.*` / 三方实现库）。
            implementation(project(":core:platform"))
            // 本域的 port（[AiPromptPresetGateway]）只声明 `suspend` 方法、不用 `Flow`，
            // 因此不需要 `kotlinx-coroutines-core`（`suspend` 由语言/标准库提供）。
        }
        // 无 commonTest 块：`legado.kmp.library` 已在约定的 sourceSets 里给 commonTest
        // 加了 `kotlin("test")`（见 build-logic 的 LegadoKmpLibraryConventionPlugin），
        // 本模块目前也没有自己的用例（映射器用例住 `:data:ai`——它才认识实体）。
    }
}
