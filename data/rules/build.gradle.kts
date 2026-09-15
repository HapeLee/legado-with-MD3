plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:rules"))
            // 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（M3 的 `data:database`
            // 「Room 唯一 owner」尚未拆出）。等它落地后本行改成 `:data:database`。
            implementation(project(":core:data"))
            // `String.splitNotBlank`（`io.legado.app.utils`，分组重排要用）。`:core:data`
            // 是 `implementation(project(":core:model"))`，**不传递** ⇒ 这里必须自己声明。
            implementation(project(":core:model"))
            implementation(libs.kotlinx.coroutines.core)
        }
        // 无 commonTest 块：`kotlin("test")` 由 `legado.kmp.library` 约定统一加
        // （见 build-logic 的 LegadoKmpLibraryConventionPlugin）。
    }
}
