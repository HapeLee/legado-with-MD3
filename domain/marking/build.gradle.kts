plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `systemTimeMillis`：`createdAt` / `updatedAt` 的默认值必须与迁移前的 Room 实体
            // 逐字一致（原 `BookMarking.createdAt = systemTimeMillis()`），否则「新建标记」
            // 的时间戳生成规则会静默改变。它是 `:core:platform` 的 `expect fun`，各 target
            // 都有 actual，不是平台实现库 —— pure 模块引用它不越界（G2 只拦
            // `android.*` / `java.io.File` / `kotlin.jvm.*` / `androidx.*` / 三方实现库）。
            implementation(project(":core:platform"))
            // `Flow`：端口的 `flowByBook` 是流方法（目录 Sheet 笔记页订阅），`Flow` 的类型
            // 在 `kotlinx-coroutines-core`（与 `domain/rules` / `domain/ai` 同因）。
            implementation(libs.kotlinx.coroutines.core)
        }
        // 无 commonTest 块：`legado.kmp.library` 已在约定的 sourceSets 里给 commonTest
        // 加了 `kotlin("test")`；本模块的映射器用例住 `:data:marking`——它才认识实体。
    }
}
