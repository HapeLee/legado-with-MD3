plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// `:feature:dict` —— 第三个由 `:app` 提升出来的真实 Feature 模块（Stage A 首例：
// Contract/Screen/ViewModel 全量一次迁入）。
//
// 承载「词典规则管理」一个屏幕（`rule` 子域：DictRuleScreen/DictRuleContract/
// DictRuleViewModel）：
//   - 数据/仓储来自 `:core:data`（DictRule 实体、DAO、DictRuleRepository，均已在
//     core:data commonMain）；
//   - 列表/导入/上传/排序编排来自 `:core:viewmodel` 的 `BaseRuleViewModel` 一族
//     （`exportToUri`/`RuleTransferPlatform` 在其内）；
//   - UI 组件与主题来自 `:core:ui`，纯状态契约来自 `:core:designsystem`；
//   - 剪贴板走 `:core:platform` 的 `ClipboardProvider`。
//
// **不在本模块**：`dict` 查询弹窗面板（DictActivity/DictSheet/DictViewModel）——
// 其 `DictRule.search` 依赖 app 独有 `DictRuleAndroid.kt`（`model.analyzeRule.
// AnalyzeRule`/`AnalyzeUrl` 引擎），属 platform island，留 `:app`，待 search 抽成
// 平台注入契约后再迁。
//
// `DictRuleActivity` 是薄宿主（依赖 `:app` 的 `BaseComposeActivity`），同样留 `:app`，
// 只 import 本模块的 `DictRuleRouteScreen`。
//
// 包名 `io.legado.app.feature.dict.rule`。
//
// 资源策略（沿用 tagrules/replacerules）：本模块 `res/values*/strings.xml` 只放**默认值**
//（从 app res 原样抄录），app 侧同名资源按 Android 资源合并优先级覆盖；因此 Screen 由
// `io.legado.app.R` 换成模块 R 后，用户可见文案不变。
//
// 依赖只列实际用到的；新增文件需同步补依赖。

android {
    compileSdk = 37
    namespace = "io.legado.app.feature.dict"

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
    implementation(project(":core:platform"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:viewmodel"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.materialIcons)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.compose)
    implementation(libs.reorderable)
    // Glass 顶栏组件（core:ui 内是 implementation，不透出）的 public API
    // `GlassTopAppBarDefaults.defaultScrollBehavior()` 返回类型含 dev.chrisbanes.haze.HazeState，
    // 本模块直接用它时需自行声明 haze 才能解析该类型。
    implementation(libs.haze.core)
    implementation(libs.haze.materials)
}
