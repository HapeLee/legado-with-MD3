plugins {
    id("legado.kmp.compose")
}

// `:feature:txttocrules` —— 第四个由 `:app` 提升出来的真实 Feature 模块。
//
// M1-3y 起从 Android library 转成 **KMP/CMP 模块**：Contract / ViewModel / Screen 全部进
// `commonMain`，一份 UI 代码跨 android / desktop。
//
// 它承载「TXT 目录规则管理」一个屏幕（TxtRuleScreen / TxtTocRuleContract / TxtTocRuleViewModel）：
//   - 领域模型与仓储端口来自 `:domain:rules`（**M3-4 起**）：`TxtTocRule` /
//     `TxtTocRuleRepository` 都不再是 `:core:data` 的实体/实现，Room 的实体与 DAO 仍归
//     `:core:data`，实现住 `:data:rules` 并由宿主在 Koin 里绑定；
//   - 导入/导出/上传编排来自 `:core:viewmodel` 的 `RuleTransferUseCase`，内置规则导入走
//     `BuiltInRulesImporter`（两者都在 `io.legado.app.core.rules` 的 commonMain）；
//   - UI 组件与主题来自 `:core:designsystem`，剪贴板/轻提示/文件选择走 `:core:platform` 的契约。
//
// 与 Stage B 时期的三点差异：
//   1. **资源不再是 Android res**：`src/main/res/values*/strings.xml` 搬成
//      `src/commonMain/composeResources/values*/strings.xml`，`R.string.x` 改由 CMP 的
//      `Res.string.x` 提供（包名 `io.legado.app.feature.txttocrules.res`，由 convention 按
//      namespace 推导）。搬前逐条核对过：26 条文案与 `:app` 侧同名资源**逐字一致**；
//      且 `zh-rHK` / `zh-rTW` 在原模块 res 与 app res 里**同样各缺 3 条**
//      （`chapter_rule` / `import_built_in_rules` / `volume_rule`），所以这里也**原样保留缺失**
//      ——CMP 会按 qualifier 回落到默认 `values`，与 Android 资源合并时的表现一致。
//      ⚠️ composeResources **不参与 Android 资源合并**：它打进 assets 由 `Res` 读取，
//      所以共享层必须自带语言目录，不能再依赖 `:app` 覆盖。
//   2. **`TxtRuleRouteScreen` 留在 `androidMain`**：它的三个依赖是 Android-only 的——
//      `org.koin.androidx.compose.koinViewModel`、`io.legado.app.ui.platform.rememberDocumentPicker`
//      （`androidx.activity` 的 ActivityResult launcher 必须在 Composition 里注册）与
//      `koinInject<Toaster>()`（轻提示实现是 Android `Toast`）。Screen 本体只收回调、含零
//      Android API，因此拆成两个文件。这与「不要为了搬而改设计」一致——Route/Screen 分离本来
//      就是既有结构，只是过去同居一个文件。
//   3. **VM 不再继承 `BaseRuleViewModel`**（那是个吃 `android.app.Application` 的
//      `AndroidViewModel`）：导入/导出/上传下沉 `RuleTransferUseCase`，列表/搜索/选择/排序留在
//      `TxtTocRuleViewModel`，后者只剩 `androidx.lifecycle.ViewModel` 一个 Android 依赖。
//
// 边界纪律：
// - `commonMain` 不得出现 `import android.*`；也不再用 `:core:ui`（Android-only，只在 `androidMain`
//   出现）——这是本模块能真转 CMP 的分水岭：所有跨平台组件都已在 `:core:designsystem` 收口。
// - G2 把本模块登记为 **cmp**（放行 `androidx.compose.*` / `androidx.lifecycle.*` /
//   `androidx.navigation3.*`）；必须在根 `build.gradle.kts` 的 `kmpModuleTypes` 同步登记，
//   否则 G2 拦。
// - **M3-4**：规则的领域模型/端口住 `:domain:rules`（`io.legado.app.domain.rules`），
//   Room 实体与 DAO 仍归 `:core:data`。本模块 `commonMain` 里因此**不再**出现
//   `io.legado.app.data.entities.TxtTocRule`；唯一还需要实体的是平台契约的 Android 实现
//   （住 `:app` 的 `AndroidTxtTocRuleImportCompat`），因为旧键名 `rule` → `chapterRule`
//   的键名提升注册在**实体类型**上，且 `Restore.kt` 按实体反序列化备份。
//
// 测试：本模块**没有**模块级测试。`TxtTocRuleDeserializerTest` 测的是 `:core:data` 的实体
// 反序列化（住 `app/src/test/.../data/entities`），不随本模块走；M3-4 起映射器的等价性基线
// 住 `:data:rules` 的 `TxtTocRuleMapperTest`（7 例，随该模块的 `desktopTest` 跑）。
//
// 包名 `io.legado.app.feature.txttocrules`，`:app` 侧 import 零改动。

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`：public 签名里出现这三个模块的类型——`UploadRepository`（
            // `TxtTocRuleViewModel` 的构造参数）来自 `:core:data`，
            // `BaseImportUiState<TxtTocRule>` 来自 `:core:designsystem`，
            // `TxtTocRule` 领域模型（`TxtTocRuleItemUi.rule`、
            // `TxtTocRuleIntent.SaveRule.rule`、`TxtTocRuleRenderState.importState` 的泛型
            // 实参、`TxtTocRuleImportCompat` 的返回类型）来自 `:domain:rules`；
            // 消费方编译时要看得见。
            api(project(":core:data"))
            api(project(":core:designsystem"))
            api(project(":domain:rules"))
            // `isJsonArray()` / `isJsonObject()`（`io.legado.app.utils`，纯 KMP 扩展）。
            implementation(project(":core:model"))
            // `Clipboard` / `JsonCodec` / `Toaster` / `DocumentPicker` 窄契约
            // （实现由 `:app` 在 Koin 里注入）。
            implementation(project(":core:platform"))
            // `RuleTransferUseCase` / `RuleEntitySpec` / `RuleTransferPlatform` /
            // `BuiltInRulesImporter`——M1-3u 起在 `:core:viewmodel` 的 commonMain。
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
            // 仅 `TxtRuleRouteScreen` 需要：
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
    }
}
