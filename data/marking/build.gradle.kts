plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:marking"))
            // 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（M3 的 `data:database`
            // 「Room 唯一 owner」尚未拆出）。等它落地后本行改成 `:data:database`。
            implementation(project(":core:data"))
            // `withContext(Dispatchers.IO)`：迁移前的实现每个方法都自己包 IO，实现侧照抄
            // （与 M4-1 / M4-2 / M4-4 同侧）。`Dispatchers` 在这里，不是 domain 侧。
            implementation(libs.kotlinx.coroutines.core)
        }
        // 无 commonTest 块：`kotlin("test")` 由 `legado.kmp.library` 约定统一加
        // （见 build-logic 的 LegadoKmpLibraryConventionPlugin）。
    }
}
