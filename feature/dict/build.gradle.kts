plugins {
    id("legado.kmp.compose")
}

// `:feature:dict` —— 第五个由 `:app` 提升出来的真实 Feature 模块。
//
// M1-3z 起从 Android library 转成 **KMP/CMP 模块**：Contract / ViewModel / Screen 全部进
// `commonMain`，一份 UI 代码跨 android / desktop。
//
// 它承载「词典规则管理」一个屏幕（`rule` 子域：DictRuleScreen / DictRuleContract /
// DictRuleViewModel）：
//   - 数据/仓储来自 `:core:data`（DictRule 实体、DAO、DictRuleRepository 均已在 commonMain）；
//   - 导入/导出/上传编排来自 `:core:viewmodel` 的 `RuleTransferUseCase`（`io.legado.app.core.rules`）；
//   - UI 组件与主题来自 `:core:designsystem`，剪贴板走 `:core:platform` 的契约。
//
// **不在本模块**：`dict` 查询弹窗面板（DictActivity / DictSheet / DictViewModel）——
// 其 `DictRule.search` 依赖 app 独有 `DictRuleAndroid.kt`（`model.analyzeRule.AnalyzeRule` /
// `AnalyzeUrl` 引擎），属 platform island，留 `:app`。`DictRuleActivity` 是薄宿主
// （依赖 `:app` 的 `BaseComposeActivity`），同样留 `:app`，只 import 本模块的
// `DictRuleRouteScreen`。
//
// 与 Stage B 时期的四点差异：
//   1. **资源不再是 Android res**：`src/main/res/values*/strings.xml` 搬成
//      `src/commonMain/composeResources/values*/strings.xml`，`R.string.x` 改由 CMP 的
//      `Res.string.x` 提供（包名 `io.legado.app.feature.dict.res`，由 convention 按
//      namespace 推导）。搬前逐条核对过：19 条文案与 `:app` 侧同名资源**逐字一致**，
//      四语言齐全（HK/TW 无缺口）。
//      ⚠️ composeResources **不参与 Android 资源合并**：它打进 assets 由 `Res` 读取，
//      所以共享层必须自带语言目录，不能再依赖 `:app` 覆盖。
//   2. **`DictRuleRouteScreen` 留在 `androidMain`**：两个依赖是 Android-only 的——
//      `org.koin.androidx.compose.koinViewModel` 与
//      `io.legado.app.ui.platform.rememberDocumentPicker`（`androidx.activity` 的
//      ActivityResult launcher 必须在 Composition 里注册）。Screen 本体只收回调、
//      含零 Android API，因此拆成两个文件。Route/Screen 分离本来就是既有结构，
//      只是过去同居一个文件。
//   3. **VM 不再继承 `BaseRuleViewModel`**（那是个吃 `android.app.Application` 的
//      `AndroidViewModel`）：导入/导出/上传下沉 `RuleTransferUseCase`，列表/搜索/选择/排序
//      留在 `DictRuleViewModel`，后者只剩 `androidx.lifecycle.ViewModel` 一个 Android 依赖。
//   4. **不需要平台契约**（与 `txttocrules` 的关键差异）：`DictRule` 在 app 侧 `GSON` 门面上
//      **没有**注册自定义 `JsonDeserializer`，实体也没有 `@SerializedName(alternate = …)` 的
//      旧键名兼容 ⇒ `JsonCodec`（配置等于 `INITIAL_GSON`）与 `GSON` 对本类型逐字等价，
//      导入解析直接在共享层用 `JsonCodec` 即可。
//
// 边界纪律：
// - `commonMain` 不得出现 `import android.*`；也不再用 `:core:ui`（Android-only，只在 `androidMain`
//   出现）——这是本模块能真转 CMP 的分水岭：所有跨平台组件都已在 `:core:designsystem` 收口。
// - G2 把本模块登记为 **cmp**（放行 `androidx.compose.*` / `androidx.lifecycle.*` /
//   `androidx.navigation3.*`）；必须在根 `build.gradle.kts` 的 `kmpModuleTypes` 同步登记，
//   否则 G2 拦。
//
// 测试：本模块**没有**模块级测试（跑 `:app:testAppDebugUnitTest` 时不覆盖本模块的任何断言）。
//
// 包名 `io.legado.app.feature.dict.rule`，`:app` 侧 import 零改动。

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`：public 签名里出现这两个模块的类型——`DictRuleScreen` / `DictRuleContract`
            // 的实体参数来自 `:core:data`，`BaseImportUiState<DictRule>` 来自 `:core:designsystem`；
            // 消费方编译时要看得见。
            api(project(":core:data"))
            api(project(":core:designsystem"))
            // `isJsonArray()` / `isJsonObject()`（`io.legado.app.utils`，纯 KMP 扩展）。
            implementation(project(":core:model"))
            // `Clipboard` / `JsonCodec` 窄契约（实现由 `:app` 在 Koin 里注入）。
            implementation(project(":core:platform"))
            // `RuleTransferUseCase` / `RuleEntitySpec` / `RuleTransferPlatform`
            // ——M1-3u 起在 `:core:viewmodel` 的 commonMain。
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
            // 仅 `DictRuleRouteScreen` 需要：
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
