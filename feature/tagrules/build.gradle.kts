plugins {
    id("legado.kmp.compose")
}

// `:feature:tagrules` 是仓库里**第一个**由 `:app` 提升出来的真实 Feature 模块（Stage B 首例），
// M1-3w 起从 Android library 转成 **KMP/CMP 模块**：Contract / ViewModel / Screen / EditSheet
// 全部进 `commonMain`，一份 UI 代码跨 android / desktop。
//
// 它承载「标签分组规则」与「高亮标签规则」两个屏幕：
//   - 数据与仓储来自 `:core:data`；
//   - 导入/上传编排来自 `:core:viewmodel` 的 `RuleTransferUseCase`（`io.legado.app.core.rules`）；
//   - UI 组件与主题来自 `:core:designsystem`，剪贴板/轻提示/文件选择走 `:core:platform` 的 provider。
//
// 包名保持 `io.legado.app.feature.tagrules.*`，`:app` 侧 import 零改动。
//
// 与 Stage B 时期的两点差异：
//   1. **资源不再是 Android res**：`src/main/res/values*/strings.xml` 搬成
//      `src/commonMain/composeResources/values*/strings.xml`，`R.string.x` 改由 CMP 的
//      `Res.string.x` 提供（包名 `io.legado.app.feature.tagrules.res`，由 convention 按
//      namespace 推导）。搬前逐条核对过：31 条文案 ×4 语言与 `:app` 侧同名资源**逐字一致**
//      （124/124），且 `:app` 的 4 个语言文件里都**自带这 31 条的副本** ⇒ 删掉本模块的
//      Android res 不会让任何 `io.legado.app.R.string.*` 引用解析失败，用户可见文案不变。
//      ⚠️ composeResources **不参与 Android 资源合并**：它打进 assets 由 `Res` 读取，
//      所以共享层必须自带全部 4 个语言目录，不能只放默认值。
//   2. **`HighlightTagRuleRouteScreen` 留在 `androidMain`**：它的两个依赖是 Android-only 的
//      —— `org.koin.androidx.compose.koinViewModel` 与 `io.legado.app.ui.platform.rememberDocumentPicker`
//      （`androidx.activity` 的 ActivityResult launcher 必须在 Composition 里注册，见
//      `:core:ui` 的 `AndroidDocumentPicker.kt` 注释）。Screen 本体只收回调、含零 Android API，
//      因此拆成两个文件：`commonMain/.../HighlightTagRuleScreen.kt`（纯 Screen）+
//      `androidMain/.../HighlightTagRuleRouteScreen.kt`（Route）。这与「不要为了搬而改设计」
//      一致——Route/Screen 分离本来就是既有结构，只是过去同居一个文件。
//
// 边界纪律：
// - **不依赖 `:core:ui` 的 commonMain**（那还是 Android-only 模块，`src/main`）：`commonMain`
//   只用 `:core:designsystem`；`:core:ui` 只在 `androidMain` 出现。这也是本模块能真转 CMP 的
//   分水岭——所有跨平台组件都已在 designsystem 收口。
// - `commonMain` 不得出现 `import android.*`；G2 把本模块登记为 **cmp**（放行
//   `androidx.compose.*` / `androidx.lifecycle.*` / `androidx.navigation3.*`）。
//
// 测试：`TagRulesImportExportCharacterizationTest` 走真实 GSON + 内存 Room（与 app 侧 DAO
// 测试同法），属 Android 主机测 ⇒ 随 `androidHostTest` 源集。转 KMP 后跑它的任务从
// `:feature:tagrules:testDebugUnitTest` 变成 **`:feature:tagrules:testAndroidHostTest`**。

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`：public 签名里出现这两个模块的类型——`TagGroupRuleEditSheet(rule: TagGroupRule)`
            // 来自 `:core:data`，`HighlightTagRuleScreen(importState: BaseImportUiState<…>)`
            // 来自 `:core:designsystem`；消费方编译时要看得见。
            api(project(":core:data"))
            api(project(":core:designsystem"))
            // M3-2：`HighlightTagRuleContract` / `HighlightTagRuleScreen` / `EditSheet` 的
            // public 签名里出现了 `io.legado.app.domain.rules.HighlightTagRule` 与
            // `BaseImportUiState<HighlightTagRule>`，消费方编译时要看得见 ⇒ 用 `api`。
            // 分组规则（`TagGroupRule`）本片仍走 `:core:data` 的实体，随 M3 后续片收口。
            api(project(":domain:rules"))
            // `isJsonArray()` / `isJsonObject()`（`io.legado.app.utils`，纯 KMP 扩展）。
            implementation(project(":core:model"))
            // `Clipboard` / `JsonCodec` / `Toaster` / `RuleTransferPlatform` 窄契约
            // （实现由 `:app` 在 Koin 里注入）。
            implementation(project(":core:platform"))
            // `RuleTransferUseCase` / `RuleEntitySpec` / `RuleTransferEvent`——M1-3u 起在
            // `:core:viewmodel` 的 commonMain。
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
            // VM 基类与 `collectAsStateWithLifecycle`（Route 侧）。两条都取
            // `androidx.lifecycle` 的 **KMP 坐标**（`-desktop` 变体实测有产物，
            // 与 Android 侧同版本 2.11.0）；细节与「为什么不用
            // `org.jetbrains.androidx.lifecycle`」见 libs.versions.toml 的注释。
            implementation(libs.androidx.lifecycle.viewmodel.kmp)
            implementation(libs.androidx.lifecycle.runtime.compose.kmp)
        }

        androidMain.dependencies {
            // 仅 `HighlightTagRuleRouteScreen` 需要：
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

        androidHostTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
            // 只为 `Room.inMemoryDatabaseBuilder`；`:core:data` 以 implementation 引入故不传递。
            implementation(libs.room.runtime)
            // M3-2：特征化测试要构造**真实实现**（`HighlightTagRuleRepositoryImpl`）来跑内存
            // Room 端到端，而 `:data:rules` 在 `commonMain` 是 `implementation`（端口才走 `api`）
            // ⇒ 主机测源集自己声明一次。桌面侧不需要：那边的用例只到端口。
            implementation(project(":data:rules"))
        }
    }
}
