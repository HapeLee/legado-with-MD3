plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `systemTimeMillis`：`createdAt` / `updatedAt` 的默认值必须与 Room 实体逐字一致。
            implementation(project(":core:platform"))
            // `TextProcessAnchor` / `TextProcessAction`：`BookContentProcessEngine` 解析
            // `anchorJson` / `actionJson` 时用 `JsonCodec.fromJsonObject(..., KClass)`，
            // 目标类型住 `:core:model`。
            implementation(project(":core:model"))
            // `Flow`：端口历史上有一个 `flowForChapter`，本片因零调用方**删除**了它 ⇒ 本模块
            // 现在没有任何 Flow 方法，但保留该依赖是**不必要的** —— 故不声明 coroutines。
            // （仅 `suspend` 由语言提供，`domain/marking` 的说明同理。）
        }
    }
}
