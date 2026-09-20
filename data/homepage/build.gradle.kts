plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:homepage"))
            // 过渡期依赖：Room 的 DAO 与实体仍归 `:core:data`（M3 的 `data:database`
            // 「Room 唯一 owner」尚未拆出）。等它落地后本行改成 `:data:database`。
            implementation(project(":core:data"))
            // `ModuleItem` / `CustomSetItem`（见 `:domain:homepage` 的说明）：`:core:data`
            // 对 `:core:model` 是 `implementation`（不传递）⇒ 本模块必须显式声明。
            implementation(project(":core:model"))
            // `systemTimeMillis()`：`createCustomSet` 用当前时间拼 id（迁移前是
            // `System.currentTimeMillis()`），commonMain 拿不到 `java.lang.System`。
            // ⚠️ 这里是**文件本身要迁进 commonMain**，才换；不是「顺手统一」（M4-2 的判据）。
            implementation(project(":core:platform"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
