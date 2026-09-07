plugins {
    id("legado.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:platform"))
            implementation(project(":core:model"))
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // 反射是不可变式守卫（如 ReadSettings 全字段落盘覆盖面）的唯一手段，而
        // kotlin-reflect 是 JVM-only：这类测试只能放在 JVM 目标下，不能进 commonTest。
        desktopTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("reflect"))
        }
    }
}

dependencies {
    "kspAndroid"(libs.room.compiler)
    "kspDesktop"(libs.room.compiler)
}

room {
    schemaDirectory(file("schemas").absolutePath)
}
