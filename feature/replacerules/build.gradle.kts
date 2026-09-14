plugins {
    id("legado.kmp.compose")
}

// `:feature:replacerules` —— 第二个由 `:app` 提升出来的真实 Feature 模块（Stage A 首例：
// Contract/Screen/ViewModel 全量一次迁入）。M1-3x 起从 Android library 转成 **KMP/CMP 模块**：
// Contract / ViewModel / Screen 全部进 `commonMain`，一份 UI 代码跨 android / desktop。
//
// 承载「替换规则管理」两个屏幕（列表 ReplaceRuleScreen + 编辑 ReplaceEditScreen）：
//   - 数据/仓储来自 `:core:data`（ReplaceRule/BookContentProcess 实体、DAO、仓储）；
//   - 导入/导出/上传编排来自 `:core:viewmodel` 的 `RuleTransferUseCase`（M1-3b 从
//     `BaseRuleViewModel` 抽出的无 UI 编排）；
//   - UI 组件与主题来自 `:core:designsystem`；
//   - 剪贴板/文件选择走 `:core:platform` 的窄契约。
//
// 包名保持 `io.legado.app.feature.replacerules.*`，`:app` 侧 import 零改动。
//
// 与 Stage A 时期的三点差异：
//   1. **两个 VM 都不再继承 `BaseRuleViewModel`**（那是个吃 `android.app.Application` 的
//      `AndroidViewModel`）。列表/搜索/选择留在本模块，导入导出下沉 `RuleTransferUseCase`。
//      `ReplaceRuleViewModel` 的旧实现里还有两处 Android 直连，本次一并去掉：
//        - `android.net.Uri`（`exportToUri`）→ 契约本来就是 `String`（`RuleTransferPlatform.writeExport`）；
//        - `GSON` 门面（`core:data/androidMain`）→ `:core:platform` 的 `JsonCodec`（字节级等价；
//          `ReplaceRule` 在 app 侧 `GSON` 里**没有**注册自定义 deserializer，故解析行为不变）。
//   2. **旧格式导入文本的兼容解析留成平台契约**（`ReplaceRuleImportCompat`）：`ReplaceAnalyzer`
//      的旧分支依赖 `com.jayway.jsonpath`（JVM 三方库，见 `core/data/build.gradle.kts` 注释），
//      只能在平台侧提供。共享层先按标准格式用 `JsonCodec` 解；解不出（或数组里有条目
//      `pattern` 为空）才回落到该契约——Android 实现直接委托 `ReplaceAnalyzer`，**行为逐字不变**。
//   3. **Route 留在 `androidMain`**：两个 Route 的依赖都是 Android-only 的
//      —— `org.koin.androidx.compose.koinViewModel` 与 `io.legado.app.ui.platform.rememberDocumentPicker`
//      （ActivityResult launcher 必须在 Composition 里注册）。Route/Screen 分离是既有设计：
//      过去 Route 与 Screen 同居一个文件，现在按源集分开。
//
// 边界纪律：
// - **不依赖 `:core:ui` 的 commonMain**（那还是 Android-only 模块）：`commonMain` 只用
//   `:core:designsystem`；`:core:ui` 只在 `androidMain` 出现。
// - `commonMain` 不得出现 `import android.*`；G2 把本模块登记为 **cmp**。

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`：public 签名里出现这两个模块的类型——`ReplaceRuleItemUi(…)`/`ReplaceRuleIntent(…)`
            // 带 `ReplaceRule`（`:core:data`），`ReplaceRuleScreen(importState: BaseImportUiState<…>)`
            // 与 `ReplaceRuleUiState` 里的 `ContentProcessConfigUiState`/`ListUiState`
            // （`:core:designsystem`）；消费方编译时要看得见。
            api(project(":core:data"))
            api(project(":core:designsystem"))
            // `isJsonArray()` / `isJsonObject()`（`io.legado.app.utils`，纯 KMP 扩展）与 `AppPattern`。
            implementation(project(":core:model"))
            // `Clipboard` / `JsonCodec` / `DocumentPicker` 窄契约（实现由 `:app` 在 Koin 里注入）。
            implementation(project(":core:platform"))
            // `RuleTransferUseCase` / `RuleEntitySpec` / `RuleTransferEvent` / `RuleTransferPlatform`。
            implementation(project(":core:viewmodel"))

            // —— Compose 及 KMP 制品（都用 CMP 坐标或已确认有 desktop 变体的坐标）
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.materialIcons)
            // CMP 多平台资源：`Res.string.*` 替代 android-only 的 `R.string.*`。
            implementation(libs.compose.multiplatform.resources)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
            // `rememberReorderableLazyListState`（拖拽排序），实测有 `reorderable-jvm` 变体。
            implementation(libs.reorderable)
            // `GlassTopAppBarDefaults.defaultScrollBehavior()`（`ReplaceEditScreen` 的折叠顶栏）
            // 的 public 返回类型带 `dev.chrisbanes.haze.HazeState`，本模块要自行声明才能解析该类型
            // （`:core:designsystem` 内 haze 是 `implementation`，不透出）。`haze-core` 实测有
            // `haze-jvm` 变体 ⇒ CMP commonMain 可用。
            implementation(libs.haze.core)
            // VM 基类与 `collectAsStateWithLifecycle`（Route 侧）。两条都取
            // `androidx.lifecycle` 的 **KMP 坐标**（`-desktop` 变体实测有产物，
            // 与 Android 侧同版本 2.11.0）；细节见 libs.versions.toml 的注释。
            implementation(libs.androidx.lifecycle.viewmodel.kmp)
            implementation(libs.androidx.lifecycle.runtime.compose.kmp)
        }

        androidMain.dependencies {
            // 仅两个 Route 需要：
            //   - `rememberDocumentPicker()`（`:core:ui`，SAF 的 ActivityResult launcher）；
            //   - `koinViewModel()`（`org.koin.androidx.compose`）。
            implementation(project(":core:ui"))
            // `libs.koin.compose`（`io.insert-koin:koin-androidx-compose`）在版本目录里
            // **无版本号**——版本一直由 Koin BOM 提供（app 侧 `platform(libs.koin.bom)`）。
            // 这里同样要引 BOM，否则在 KMP 的 androidMain 配置下会解析成
            // 「no version specified」。
            // ⚠️ 只能写 `project.dependencies.platform(...)`：KMP 的
            // `sourceSets.*.dependencies {}` 由 `KotlinDependencyHandler` 提供，它**不**继承
            // Gradle 的 `DependencyHandler`，因此那块作用域里没有 `platform` 扩展
            // （实测报 `Unresolved reference 'platform'`）。
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.compose)
            // **版本对齐**（不是直接使用）：`lifecycle-runtime-compose-android` 会传递
            // `androidx.navigationevent:navigationevent-compose`，其默认版本在本机缓存里没有
            // （解析到 1.0.2 ⇒ 离线直接失败）。app 侧本来就为「预测式返回崩溃」手动抬到
            // 1.2.0-alpha04（见 libs.versions.toml 的 `navigationevent` 注），这里声明同一条
            // 让它取同一个版本。放在 `androidMain` 是因为只有 android 变体引它
            // （desktop 侧是 `lifecycle-runtime-compose-desktop`，不依赖 navigationevent）。
            implementation(libs.androidx.navigationevent.compose)
        }

        // 测试：`ReplaceRuleStateTest` 原先住 `:app` 的 `src/test`，M1-3x 随模块转 KMP 一起
        // 搬进 `androidHostTest`（与 tagrules 同法）——Feature 的测试跟着 Feature 走。
        // 它只断言 `ReplaceRuleItemUi` / `ReplaceEditRoute` 两个纯 data class 的等值语义，
        // 不碰 Android 框架，故只需 JUnit（Robolectric / Room 都不需要）。
        // ⚠️ 转 KMP 后跑它的任务名变成 **`:feature:replacerules:testAndroidHostTest`**。
        androidHostTest.dependencies {
            implementation(libs.junit)
        }
    }
}
