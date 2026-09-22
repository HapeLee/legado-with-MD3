plugins {
    id("legado.kmp.compose")
}

// `:feature:settings` 是 M5 批次 2「低风险管理页」的第二站，按 feature-catalog 的
// `ui/config/* → feature/settings` 归属建立。**它是域级模块，但按子页面分片填充**：
// M5-2a 只装实验室页（原 `:app` 的 `ui/config/labConfig`，4 文件 209 行），
// 后续子页（appearance / reading / backup / advanced / other / theme …）逐片进来。
//
// 为什么按页分片而不是整域一片：`ui/config/*` 共 78 文件，其中 `themeConfig`(11)、
// `coverConfig`(9)、`ai`(15) 等子域各有独立依赖闭包（有的要 Miuix、有的要 SAF、
// 有的直连 Repository）。整域搬迁会让「行为等价清单」长到无法逐条核对；
// 逐片迁入则每片都能独立验证、独立回滚。
//
// 本模块与 about 的形态差异（三条，都影响 build 配置）：
//   1. **零新增平台契约**。设置读写是 `:core:data` 已有的 `LabSettingsGateway`，
//      诊断计数是 `:feature:reader:core` 已有的 `LocalPageEstimateMetrics`
//      ⇒ 不需要像 about 那样绑三个 Android 实现。
//   2. **`commonMain` 零 `android.*`**，且 `androidMain` 没有代码：本页唯一离开共享层的是
//      「把诊断文本作为 `ACTION_SEND` 分享出去」，按 about 的前例留在 `:app` 的
//      `MainNavGraph` entry 里收 Effect。**不为此抽契约**——宿主收 Effect 是本仓既定形态。
//   3. **资源从 Android res 搬成 composeResources**：`R.string.*` → `Res.string.*`，
//      包名由 convention 按 namespace 推导为 `io.legado.app.feature.settings.res`。
//      ⚠️ `:app` 侧的同名资源**本片不删**：`ui/config/ConfigNavScreen.kt` 仍在用
//      `R.string.lab_setting`（设置导航列表的条目标题），只有页面内部那 11 条随页面迁走。
//      搬迁时逐条核对过 12 条文案 ×4 语言与 `:app` 侧逐字一致（含 `%1$d` 占位符）。
//
// 包名保持 `io.legado.app.feature.settings.lab`（子页名取原目录名 `labConfig` 的域内语义）。

kotlin {
    sourceSets {
        commonMain.dependencies {
            // `api`：`LabConfigViewModel` 的构造参数是 `LabSettingsGateway`
            // （`:core:data` 的公共端口），消费方（`:app` 的 entry）编译时要看得见。
            api(project(":core:data"))
            // `api`：`LabConfigScreen` 的公共签名里出现 `:core:designsystem` 的组件
            // （`AppScaffold` / `SplicedColumnGroup` / `SwitchSettingItem` …），与 about 同形。
            api(project(":core:designsystem"))
            // `LabSettings` 设置模型。
            implementation(project(":core:model"))
            // M5-4b：`AiProfileGateway`（AI 任务预设的读写端口）在 `:domain:ai`——
            // ai/* 子页的 VM 都用它，而它是 M4 域下沉时建好的共享端口，不需要新契约。
            implementation(project(":domain:ai"))
            // M5-4c：`Toaster`（M1-3a 建的轻提示共享契约）。ai/prompt 的 VM 迁移前直接
            // `appCtx.toastOnUi(...)`，且那条提示是 **Toast 而非 Snackbar**（与页面里其它
            // 提示的展示方式不同）⇒ 注入这个既有的共享契约，不新造平台能力。
            implementation(project(":core:platform"))
            // `LocalPageEstimateMetrics`（诊断计数与导出）——它已经是共享层的 object，
            // 不新增契约。这是本模块不产生 Android 实现的直接原因。
            implementation(project(":feature:reader:core"))

            // —— Compose 及 KMP 制品
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.materialIcons)
            // `AppScaffold` / `GlassMediumFlexibleTopAppBar` 的 public 签名带
            // `dev.chrisbanes.haze.HazeState`，本模块要自行声明才能解析该类型
            // （`:core:designsystem` 内 haze 是 `implementation`，不透出）。与 about 同形。
            implementation(libs.haze.core)
            // CMP 多平台资源：`Res.string.*` 替代 android-only 的 `R.string.*`。
            implementation(libs.compose.multiplatform.resources)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
            // VM 基类，取 `androidx.lifecycle` 的 **KMP 坐标**（与 about / 四个规则 Feature 同版本）。
            implementation(libs.androidx.lifecycle.viewmodel.kmp)
            implementation(libs.androidx.lifecycle.runtime.compose.kmp)
        }

        androidMain.dependencies {
            // ⚠️ 本模块的 `androidMain` 没有代码（见上方注释 2）。这里**不声明** `:core:ui` /
            // Koin——没有任何调用方（AGENTS.md：不为架构完整留空配置）。
            //
            // 唯一保留的一条是**版本对齐**（不是直接使用）：`lifecycle-runtime-compose-android`
            // 会传递 `androidx.navigationevent:navigationevent-compose`，其默认版本本机缓存里
            // 没有（离线直接失败）。app 侧本来就把 `navigationevent` 手动抬到 1.2.0-alpha04，
            // 这里声明同一条让它取同一个版本。与 about 完全一致。
            implementation(libs.androidx.navigationevent.compose)
        }

        androidHostTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
