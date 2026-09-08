plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// `:core:ui` 是 **Android 专用** 的 Compose UI 模块，承载：
//   1. `io.legado.app.ui.theme`——主题引擎、配色方案、自适应间距/密度；
//   2. `io.legado.app.ui.widget.components` 中 **不依赖 app 单例** 的闭包子集（通用 UI 组件：
//      Scaffold/Text/TextField/Button/Card/SettingItem/CheckBox/Divider/Swipe/Pager/TabRow/
//      ProgressIndicator/TopBar 等）；
//   3. `io.legado.app.ui.animation` 与 `io.legado.app.ui.util` 中同样无 app 单例依赖的
//      纯 Compose 交互/手势工具（`InteractiveHighlight`、`inspectDragGestures`）。
//
// 为什么不放 `:core:designsystem`：那是一个 KMP 模块，commonMain 受 `checkSharedPurity`
// 约束（零 Compose、零 `android.*`）。而这套主题与组件大量使用 `Context` / `Bitmap` / `Uri` /
// `LruCache` 以及 Material 3 + Miuix 双引擎，只能落在 Android 侧。
//
// 存在的意义：此前 `ui/theme` 与 `ui/widget/components` 都留在 `:app`，任何 Feature 提升为
// Gradle 模块都会形成 `:app → :feature:x → :app` 的循环依赖。本模块是拆开这个环的通道。
//
// 组件面的切片规则（可机械复核）：文件中不得出现 `io.legado.app` 的 app 单例、`utils.GSON`、
// `ui.main.MainDestination`；不得 import app 层包（`ui.theme` / `ui.widget.components` /
// `ui.animation` / `ui.util` / `domain.model` 除外），且其组件内依赖闭包同样满足该规则。
//
// 资源策略：库侧 `res/values*/strings.xml` 只放**默认值**，app 侧同名资源按 Android 资源
// 合并优先级覆盖；因此迁移文件只把 `io.legado.app.R` 换成 `io.legado.app.core.ui.R`，
// app 侧代码与资源均不改动。仍有 `utils.GSON` 等 app 单例依赖的组件留在 `:app`。
//
// 依赖只列实际用到的；新增文件需同步补依赖，不要顺手塞通用 UI 库。

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
    // `DynamicTopAppBar` 读取 `ui.widget.components.list.ListUiState`（纯状态契约，commonMain）。
    implementation(project(":core:designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material)
    implementation(libs.compose.materialIcons)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.reorderable)

    implementation(libs.core.ktx)
    implementation(libs.material.kolor)
    implementation(libs.haze.core)
    implementation(libs.haze.materials)
    implementation(libs.miuix.core)
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.blur.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.backdrop)
    implementation(libs.coil.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.compose)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
