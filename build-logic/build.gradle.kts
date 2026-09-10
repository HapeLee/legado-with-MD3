plugins {
    `kotlin-dsl`
}

repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.2.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    // 仅供 `LegadoKmpComposeConventionPlugin` 取 `compose.dependencies` 用。
    // 版本必须与 gradle/libs.versions.toml 的 `composeMultiplatform` 一致
    // （Kotlin 2.4.10 ↔ CMP 1.12.0 是本仓验证过的配对）。
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.12.0")
}

gradlePlugin {
    plugins {
        register("legadoKmpLibrary") {
            id = "legado.kmp.library"
            implementationClass = "io.legado.buildlogic.LegadoKmpLibraryConventionPlugin"
        }
        register("legadoKmpCompose") {
            id = "legado.kmp.compose"
            implementationClass = "io.legado.buildlogic.LegadoKmpComposeConventionPlugin"
        }
    }
}
