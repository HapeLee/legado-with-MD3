plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:ai"))
            // 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（M3 的 `data:database`
            // 「Room 唯一 owner」尚未拆出）。等它落地后本行改成 `:data:database`。
            implementation(project(":core:data"))
            // `withContext(Dispatchers.IO)`：迁移前的实现每个方法都自己包 IO，实现侧照抄
            // （与 M3-5 的 `RuleSubRepositoryImpl` 全程不包 IO 不同，那边迁前就没包）。
            implementation(libs.kotlinx.coroutines.core)
        }
        // 无 commonTest 块：`kotlin("test")` 由 `legado.kmp.library` 约定统一加
        // （见 build-logic 的 LegadoKmpLibraryConventionPlugin）。
    }
}
