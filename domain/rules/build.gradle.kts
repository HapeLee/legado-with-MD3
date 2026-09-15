plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `systemTimeMillis`：领域模型的 `id` 默认值必须与迁移前的 Room 实体逐字一致
            // （原 `ReplaceRule.id = systemTimeMillis()`），否则「新建规则」的 id 生成规则
            // 会静默改变。它是 `:core:platform` 的 `expect fun`，各 target 都有 actual，
            // 不是平台实现库 —— pure 模块引用它不越界（G2 只拦 android./java.io./jvm/三方实现库）。
            implementation(project(":core:platform"))
            implementation(libs.kotlinx.coroutines.core)
        }
        // 无 commonTest 块：`legado.kmp.library` 已在约定的 sourceSets 里给 commonTest
        // 加了 `kotlin("test")`（见 build-logic 的 LegadoKmpLibraryConventionPlugin），
        // 本模块目前也没有自己的用例（映射器用例住 `:data:rules`——它才认识实体）。
    }
}
