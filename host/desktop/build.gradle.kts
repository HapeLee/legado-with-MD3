plugins {
    id("legado.kmp.compose")
}

// M1-4：最小 Desktop host。
//
// 目标（对齐 `docs/dev/kmp-cmp-migration-plan.md` §5 第 7 项）：
// 「展示同一 Feature，验证 Koin graph、ViewModel lifecycle、resources 和一条数据路径」。
//
// 这是本仓**第一次**让 desktop 证据越过「能编译」——此前四个 Feature 转 CMP 的验证都止于
// `compileKotlinDesktop`。这里在 `desktopTest` 里真的建库、起 Koin、渲染 `DictRuleScreen`、
// 断言数据从 Room 流到界面。
//
// 为什么选 `feature:dict`：四个已转 CMP 的 Feature 里它的注入面最小
// （`DictRuleRepository` + `RuleTransferPlatform` + `Clipboard` + `UploadRepository`），
// 没有 `BuiltInRulesImporter`，也没有 `TxtTocRuleImportCompat` 这类需要 JVM 侧重新实现的
// 平台契约。先打通最短的一条，再谈扩展到其他 Feature。
//
// 为什么只在 `desktopTest` 而不是 `desktopMain` 里组装：M1-4 要的是「可反复验证的证据」，
// 不是「能起窗口的演示」。headless UI 测试能进 CI 反复跑；起窗口只能手工看一次。
// 真正的应用入口（`compose.desktop { application { … } }`）留到确有演示需求时再加。

kotlin {
    sourceSets {
        commonMain.dependencies {
            // runtime / foundation 由 convention 注入。
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.resources)
            implementation(libs.kotlinx.coroutines.core)
            // `DictRuleUiState.items` 是 `ImmutableList`：测试要读它来断言「数据到了界面没」，
            // 不显式声明就报 "Cannot access class 'ImmutableList'"（KMP 不传递该依赖）。
            implementation(libs.kotlinx.collections.immutable)

            // M1-4b：Nav3 的 **runtime** 是真 KMP 制品（有 desktop 变体），navigation3-ui 不是
            // （desktop 上只有 jvmStubs，没有 NavDisplay 本体）。所以这里只声明 runtime，
            // 渲染层由 `nav/DesktopNavHost.kt` 自建。版本与 `:app` 共用同一个版本引用。
            implementation(libs.androidx.navigation3.runtime)
            // M1-4b：entry 级 `ViewModelStore`。
            // Android 侧这个能力来自 `lifecycle-viewmodel-navigation3`（**只有 -android 变体**），
            // desktop 侧由 `DesktopNavHost` 用 KMP 版 `ViewModelStore` 自己维护。
            // 必须显式声明：KMP 不传递 `koin-compose-viewmodel` 背后的 lifecycle 制品。
            implementation(libs.androidx.lifecycle.viewmodel.kmp)
        }

        desktopMain.dependencies {
            // 被展示的 Feature（commonMain 的 Contract / Screen / ViewModel 全在这里）。
            implementation(project(":feature:dict"))
            // M5-1c：`:feature:about` 的三个平台契约（desktop 侧显式不可用）。
            // 这里只用到 `commonMain` 的契约类型，`androidMain` 的 Route 与 `:app` 的实现
            // 都不参与 desktop 编译。
            implementation(project(":feature:about"))
            // 平台契约（`Clipboard` / `Toaster`）与共享能力（`JsonCodec`）。
            implementation(project(":core:platform"))
            // `RuleTransferPlatform` / `RuleTransferUseCase` 在 `:core:viewmodel` 的 commonMain。
            // ⚠️ 不能指望从 `:feature:dict` 传递过来：那边对它是 `implementation`，不传递。
            implementation(project(":core:viewmodel"))
            // 数据路径：`AppDatabase` / DAO / 实体。host 自己要建库并从聚合根取
            // `dictRuleDao` 组装绑定，不该依赖传递可见（`:feature:dict` 那边是 `api`）。
            implementation(project(":core:data"))
            // M3-3：仓储端口的**实现**（`DictRuleRepositoryImpl`）住 `:data:rules`
            // ——`:core:data` 与 `:domain:rules` 里分别只有实体/DAO 和模型/端口。
            implementation(project(":data:rules"))
            // 主题与共用组件（`RuleListScaffold` 等），渲染 Screen 时需要包一层主题。
            implementation(project(":core:designsystem"))

            // Room desktop 构造：`BundledSQLiteDriver` 属 `androidx.sqlite:sqlite-bundled`，
            // 不随 `:core:data` 传递（那边是 `implementation`）。
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            // DI：Koin core 是 KMP 制品（有 jvm 变体），compose-viewmodel 提供 `koinViewModel()`。
            // ⚠️ 两个坐标在版本目录里都**没有版本号**（版本一直由 Koin BOM 提供）；
            // KMP 源集的 `dependencies {}` 是 `KotlinDependencyHandler`，**没有 `platform()`**
            // ⇒ 必须写 `implementation(project.dependencies.platform(...))`，否则报
            // "Could not find io.insert-koin:koin-core:."（版本号为空，不是依赖不存在）。
            // ⚠️ 注意是 `implementation(平台)` 而不是裸调 `project.dependencies.platform(...)`
            // —— 后者只是创建了一个 BOM 依赖对象却没加进任何配置，不生效。
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }

        desktopTest.dependencies {
            implementation(kotlin("test"))
            // 与 `smoke/compose-desktop-probe` 同一套：v2 API + Skiko 渲染运行时。
            implementation(libs.compose.multiplatform.ui.test.junit4)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
