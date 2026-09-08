plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// `:core:ui` 是 **Android 专用** 的 Compose UI 模块，承载 `io.legado.app.ui.theme`
// （主题引擎、配色方案、自适应间距/密度）。
//
// 为什么不放 `:core:designsystem`：那是一个 KMP 模块，commonMain 受 `checkSharedPurity`
// 约束（零 Compose、零 `android.*`）。而这套主题大量使用 `Context` / `Bitmap` / `Uri` /
// `LruCache` 以及 Material 3 + Miuix 双引擎，只能落在 Android 侧。
//
// 存在的意义：此前 `ui/theme` 与 `ui/widget/components` 都留在 `:app`，任何 Feature 提升为
// Gradle 模块都会形成 `:app → :feature:x → :app` 的循环依赖。本模块是拆开这个环的第一步——
// 先把主题面下沉，组件面随后跟进。
//
// 依赖只列 theme 实际用到的；新增文件需同步补依赖，不要顺手塞通用 UI 库。

android {
    compileSdk = 37
    namespace = "io.legado.app.core.ui"

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkDependencies = true
        targetSdk = 37
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)

    implementation(libs.core.ktx)
    implementation(libs.material.kolor)
    implementation(libs.haze.core)
    implementation(libs.haze.materials)
    implementation(libs.miuix.core)
    implementation(libs.miuix.ui.android)
    implementation(libs.backdrop)
    implementation(libs.coil.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.compose)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
