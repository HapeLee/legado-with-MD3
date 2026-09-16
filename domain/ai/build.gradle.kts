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
            // `AiMessagePart`（M4-4 起）：[AiChatGateway] 的 `saveMessage` /
            // `saveRegeneratedMessage` 收 `List<AiMessagePart>` —— 那是**入参**，与
            // `AiChatMessage.partsJson` 存字符串无关（消息分片的编解码是实现的职责）。
            // 该类型住 `:core:model` 且**已经共享**，本模块只是把它引进编译路径。
            implementation(project(":core:model"))
            // `kotlinx-coroutines-core`（M4-3 起）：本域的 port 到 M4-2 为止只声明 `suspend`
            // 方法（`suspend` 由语言/标准库提供，不需要这个依赖）；M4-3 的 [AiArtifactGateway]
            // 有一个 `Flow` 端口方法（`observeBookArtifacts`）⇒ 必须显式声明。
            // 注意这与「实现侧包不包 IO」无关：`domain` 只用 `Flow` 的类型，不用 `Dispatchers`。
            implementation(libs.kotlinx.coroutines.core)
        }
        // 无 commonTest 块：`legado.kmp.library` 已在约定的 sourceSets 里给 commonTest
        // 加了 `kotlin("test")`（见 build-logic 的 LegadoKmpLibraryConventionPlugin），
        // 本模块目前也没有自己的用例（映射器用例住 `:data:ai`——它才认识实体）。
    }
}
