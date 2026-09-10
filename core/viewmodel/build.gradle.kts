plugins {
    alias(libs.plugins.android.library)
}

// `:core:viewmodel` 承载 **Android ViewModel 层的共享基类与异步辅助**：
//   1. `io.legado.app.base.BaseViewModel`——`AndroidViewModel` + `context` + `execute/executeLazy/submit`
//      （app 侧 35 个 VM 的基类）；
//   2. `io.legado.app.help.coroutine.*`——`Coroutine` 及其组合/容器/取消异常（app 侧 46 个文件在用）；
//   3. `io.legado.app.base.BaseRuleViewModel` + `BaseRuleEvent`——其余规则列表 VM 的搜索/选择/排序/
//      导入/导出/上传编排（`tagrules` 已于 M1-3b 迁出）；
//   4. `io.legado.app.core.rules.*`——规则导入/导出的共享层：无 UI 编排 `RuleTransferUseCase`、
//      实体语义 `RuleEntitySpec`、平台契约 `RuleTransferPlatform` / `BuiltInRulesImporter`、
//      流程事件 `RuleTransferEvent`（Android 实现留在 `:app`，Koin 注入）。M1-3b 从
//      `io.legado.app.base.rules` 整体搬出：Feature 引用它会新增 `import io.legado.app.base.**`
//      的 legacy 棘轮计数，而那条基线只降不升、新区域必须为零。
//
// 为什么单独成模块：`BaseRuleViewModel` 原先继承 `:app` 的 `BaseViewModel`，后者又依赖
// `help.coroutine.Coroutine`。只要这三层还在 `:app`，`feature/tagrules`、`feature/replacerules`
// 等 Feature 就永远无法提升为独立 Gradle 模块（会形成 `:app → :feature:x → :app` 环）。
// 本模块是继 `:core:ui` 之后拆环的第二条通道：先搬「VM 基类层」，规则基类随之下沉。
//
// 边界纪律：
// - **不依赖 `:app`**；只依赖 `:core:data`（`UploadRepository` 接口）与 `:core:designsystem`
//   （`ListUiState` / `BaseImportUiState` 等纯契约）；
// - 不得 import `okhttp3.*` / `android.net.Uri` / `ContentResolver` / `io.legado.app.R`；
// - 包名全部保持原样（`io.legado.app.base[.rules]` / `io.legado.app.help.coroutine`），
//   `:app` 侧零 import 改动，只需新增一条 project 依赖。
//
// 与 app 的两处解耦（行为等价，见各文件注释）：
// - `BaseViewModel.context` 原为 `getApplication<App>()`（`App` 在 `:app`），改为
//   `getApplication<Application>()`，对外声明类型仍是 `Context`；
// - `Coroutine` 原直接调 `Throwable.printOnDebug()`（依赖 `io.legado.app.BuildConfig`），
//   改为读本模块的 [io.legado.app.core.viewmodel.DebugFlags]，由 `:app` 在 `App.onCreate`
//   首行按 `BuildConfig.DEBUG` 注册。
//
// 依赖只列实际用到的；新增文件需同步补依赖。

android {
    compileSdk = 37
    namespace = "io.legado.app.core.viewmodel"

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

    lint {
        checkDependencies = true
        targetSdk = 37
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
