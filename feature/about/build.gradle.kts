plugins {
    id("legado.kmp.compose")
}

// `:feature:about` 是 M5（CMP UI 搬迁）批次 2 的第一站：把 `:app` 的 `ui/about` 整包提升成
// 真实 Feature 模块。Contract / ViewModel / Screen / Sheet 全进 `commonMain`。
//
// 与四个规则 Feature 的**根本差异**：这个页面的业务本体高度依赖 Android —— 更新检查走
// GSON + OkHttp，诊断（崩溃日志、保存日志、堆转储、logcat）走 `File` / SAF / `Runtime.exec`，
// 内置 markdown 走 `assets`。迁移前的 `AboutViewModel` 里这些是**直连**的。因此本片按
// AGENTS.md「共享领域契约不得暴露 Context/File/Uri」抽出**三个窄契约**，把平台部分整体
// 留在 `:app`：
//
//   - `AppUpdateChecker`：更新检查（Android 实现委托既有 `AppUpdate.gitHubUpdate`）；
//   - `AboutDiagnostics`：崩溃日志列举/读取/清空 + 保存日志 + 堆转储；
//   - `BundledTextReader`：读宿主内置文本（Android = `assets`）。
//
// 三者都**没有可回落的默认行为**：desktop 侧按 AGENTS.md「平台能力不可用时必须显式建模为
// capability/unsupported」抛 `UnsupportedOperationException`（先例：`:host:desktop` 的
// `DesktopImportJsonEditor` + 它的断言用例），不伪造跨平台支持。
//
// 另外三类差异也记在这里：
//   1. **`miuix-blur` 无 desktop 变体**（版本目录里只有 `miuix-blur-android`，
//      `kmp.blur.*` 在 desktop 上 Unresolved）⇒ `MiuixAboutScreen`（519 行，直接调
//      `textureBlur`）是 **platform island**，留 `:app`。两个分支复用**同一份 ViewModel 与
//      Contract**，分流点（`ThemeResolver.isMiuixEngine`）与 Effect 收集留在 `:app` 的
//      `MainNavGraph` —— **这是本模块 `androidMain` 没有代码的原因**（详见
//      `MaterialAboutScreen.kt` 的 KDoc：把分流搬进来要多抽三个平台契约）。
//   2. **版本名与 ABI 由宿主注入**：`BuildConfig.VERSION_NAME` / `Build.SUPPORTED_ABIS` 是
//      BuildConfig / `android.os.Build` 专有，共享层没有等价物（`:app` 的 `AppConst.appInfo`
//      是 Android 侧实现）。屏幕把它们当**参数**收，由宿主传。
//   3. **资源从 Android res 搬成 composeResources**（M5-1c-2）：`src/main/res/values*/strings.xml`
//      → `src/commonMain/composeResources/values*/strings.xml`，`R.string.x` 改由 CMP 的
//      `Res.string.x` 提供（包名由 convention 按 namespace 推导为
//      `io.legado.app.feature.about.res`）。搬前逐条核对过：35 条文案 ×4 语言与 `:app` 侧
//      同名资源**逐字一致**；并且额外验证了 **`.cvr` 运行期值 == aapt2 对原始 XML 的解码结果**
//      （`:app` 的 `about_description` 里写的是字面 `\u3000` 转义，必须确认 CMP 生成器同样
//      解码，否则用户会看到字面的 `\u3000`）。composeResources **不参与 Android 资源合并**，
//      所以共享层必须自带全部 4 个语言目录。`:app` 侧的同名资源**不删**：Miuix 分支、
//      `CrashReportActivity` 等仍在使用。
//
// 包名保持 `io.legado.app.feature.about.*`。
//
// 边界纪律：
// - `commonMain` 只用 `:core:designsystem`；`:core:ui`（仍是 Android-only 的 `src/main`）
//   只允许在 `androidMain` 出现——而 M5-1c-2 之后 `androidMain` 没有代码（见下方注释）。
// - `commonMain` 不得出现 `import android.*`；G2 把本模块登记为 **cmp**（放行
//   `androidx.compose.*` / `androidx.lifecycle.*` / `androidx.navigation3.*`）。
// - **本模块与四个规则 Feature 的形态差异（唯一一处）**：Route 不在 `androidMain`，而在 `:app`。
//   原因是分流点需要 `:app` 的 `MiuixAboutScreen`（platform island）与宿主动作（打开链接 /
//   Toast / 启动下载），把它们搬进来反而要新增三个平台契约。详见 `MaterialAboutScreen.kt`。

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`：`AboutViewModel` 的构造参数里出现 `OtherSettingsGateway` /
            // `BackupSettingsGateway`（`:core:data` 的公共端口），消费方编译时要看得见。
            api(project(":core:data"))
            // `api`：Screen / Sheet 的公共签名里出现 `:core:designsystem` 的组件类型
            // （`AppScaffold`、`MarkdownSheet`、`BaseImportUiState` 一族），与 tagrules 同形。
            api(project(":core:designsystem"))
            // `OtherSettings` / `BackupSettings` 的设置模型，以及 `io.legado.app.utils` 里的
            // 纯 KMP 扩展。
            implementation(project(":core:model"))
            // `AppLogStore`（保存日志/堆转储失败时记日志）与 `LogSettings`。
            implementation(project(":core:platform"))

            // —— Compose 及 KMP 制品
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.materialIcons)
            // `AppScaffold` / `GlassMediumFlexibleTopAppBar` 的 public 签名带
            // `dev.chrisbanes.haze.HazeState`，本模块要自行声明才能解析该类型
            // （`:core:designsystem` 内 haze 是 `implementation`，不透出）。`haze-core` 实测有
            // `haze-jvm` 变体 ⇒ CMP commonMain 可用。与 `:feature:replacerules` 同形。
            implementation(libs.haze.core)
            // CMP 多平台资源：`Res.string.*` 替代 android-only 的 `R.string.*`。
            implementation(libs.compose.multiplatform.resources)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
            // VM 基类与 `collectAsStateWithLifecycle`（Route 侧），取 `androidx.lifecycle`
            // 的 **KMP 坐标**（与 tagrules / replacerules / dict 同版本）。
            implementation(libs.androidx.lifecycle.viewmodel.kmp)
            implementation(libs.androidx.lifecycle.runtime.compose.kmp)
        }

        androidMain.dependencies {
            // ⚠️ **M5-1c-2 起本模块的 `androidMain` 没有代码**：Route（导航入口）留在 `:app` 的
            // `MainNavGraph`，理由是那里的职责本来就是「分流 Material/Miuix + 收集 Effect +
            // 打开链接 / Toast / 启动下载」。详见 `MaterialAboutScreen.kt` 的 KDoc。
            // 因此这里**不声明** `:core:ui` / Koin：没有任何调用方（AGENTS.md：不为架构完整
            // 留空配置）。`androidHostTest` 也不需要它们——`AboutViewModelTest` 只用 Robolectric
            // 的 Main looper 来驱动 `viewModelScope`。
            //
            // 唯一保留的一条是**版本对齐**（不是直接使用）：`lifecycle-runtime-compose-android`
            // 会传递 `androidx.navigationevent:navigationevent-compose`，其默认版本本机缓存里
            // 没有（离线直接失败）。app 侧本来就把 `navigationevent` 手动抬到 1.2.0-alpha04，
            // 这里声明同一条让它取同一个版本。
            implementation(libs.androidx.navigationevent.compose)
        }

        androidHostTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
