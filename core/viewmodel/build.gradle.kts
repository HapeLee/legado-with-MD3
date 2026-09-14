plugins {
    id("legado.kmp.library")
}

// `:core:viewmodel` 承载 **ViewModel 层的共享基类、异步辅助与规则导入/导出协议**：
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
// M1-3u：模块从 Android library 转成 **KMP**（`legado.kmp.library`，android + desktop 两目标）。
// 拆分的判据是「哪些文件要 Android SDK」，不是「哪些文件看起来像 UI」：
//   - `commonMain`：`io.legado.app.core.rules.*`（4 个文件）。它们只依赖 kotlinx.coroutines、
//     `:core:data` 的 `UploadRepository` 接口与 `:core:designsystem` 的纯状态类型
//     （`BaseImportUiState` / `ImportItemWrapper` / `ImportStatus`），零 platform/JVM-only import
//     ⇒ 现在能被非 Android 目标编译验证（`compileKotlinDesktop`）。这是把
//     `:feature:tagrules` 的 VM 侧依赖搬进 CMP 的最后一块前置——该 Feature 的两个 VM 都是普通
//     `ViewModel` 且经构造函数拿 `UploadRepository` / `RuleTransferPlatform`。
//   - `androidMain`：`base/`（要用 `android.app.Application` / `android.net.Uri` /
//     `androidx.lifecycle.viewModelScope`）与 `help/coroutine/` + `DebugFlags`
//     （留平台源集不是「不干净」，而是当前没有非 Android 消费方——搬上去只是让多一个目标编译它们）。
//
// 边界纪律：
// - **不依赖 `:app`**；只依赖 `:core:data`（`UploadRepository` 接口）与 `:core:designsystem`
//   （`ListUiState` / `BaseImportUiState` 等纯契约）；
// - `commonMain` 不得出现 `import android.*` / `androidx.*`——G2 把本模块登记为 **pure**，
//   比「放行 androidx 依赖 / 先编译再说」严一格；违反会在 `checkSharedPurity` 拦下；
// - 包名全部保持原样（`io.legado.app.base` / `io.legado.app.help.coroutine` /
//   `io.legado.app.core.rules` / `io.legado.app.core.viewmodel`），`:app` 与各 Feature 侧
//   **零 import 改动**，依赖边也一字不变。
//
// 与 app 的两处解耦（行为等价，见各文件注释）：
// - `BaseViewModel.context` 原为 `getApplication<App>()`（`App` 在 `:app`），改为
//   `getApplication<Application>()`，对外声明类型仍是 `Context`；
// - `Coroutine` 原直接调 `Throwable.printOnDebug()`（依赖 `io.legado.app.BuildConfig`），
//   改为读本模块的 [io.legado.app.core.viewmodel.DebugFlags]，由 `:app` 在 `App.onCreate`
//   首行按 `BuildConfig.DEBUG` 注册。
//
// 测试：两个既有用例（JUnit4 + Robolectric，`BaseRuleViewModelTransferTest` /
// `RuleTransferUseCaseTest`）随 Android 主机单元测走 **`androidHostTest`** 源集。注意转 KMP
// 后跑它们的任务从 `:core:viewmodel:testDebugUnitTest` 变成了
// **`:core:viewmodel:testAndroidHostTest`**——KMP android 目标不像老 Android library 那样按
// `debug`/`release` 生成变体单位测任务，`testDebugUnitTest` 已不存在。
// 它们测的对象离不开 Android 框架抽象（构造 `Application`、验证 `Uri` 导出路径），
// 强行搬到 `commonTest` 等于改它们测的对象。

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`：本模块的 public API（`RuleTransferUseCase` 的入参、返回值类型）里有这俩模块的
            // 类型，消费方编译时要看得见；同时也让 `androidMain` 不必重复声明。
            api(project(":core:data"))
            api(project(":core:designsystem"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.android)
        }
        androidHostTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
