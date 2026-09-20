plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:contentprocess"))
            // 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（M3 的 `data:database`
            // 「Room 唯一 owner」尚未拆出）。等它落地后本行改成 `:data:database`。
            implementation(project(":core:data"))
            // `withContext(Dispatchers.IO)`：迁移前的实现每个方法都自己包 IO（方法体内是
            // 裸 DAO 调用或 `maxOrder() + 1`），实现侧照抄。
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
