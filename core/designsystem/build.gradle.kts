plugins {
    id("legado.kmp.compose")
}

// `:core:designsystem` 是**真 CMP 模块**：Compose 依赖在 `commonMain`，一份 UI 代码跨
// android / desktop（将来加 iOS 只加 target，不加源集）。CMP 装回的时机由「有没有真实
// 消费方」决定——见下方"切片规则"。
//
// 历史：这里曾挂过 android/desktop 共享的 `composeMain` 中间源集放设计 token，但那套
// token 一直没有真实消费方（AGENTS.md 禁止无调用方抽象），已随 token 一并删除，CMP 也
// 一并撤掉。`composeMain` 是「commonMain 必须零 Compose」时代的过渡形态；M0-1 之后
// `checkSharedPurity` 按模块类型分策，本模块登记为 **cmp**，Compose 直接进 commonMain。
// 版本配方（已验证）：Kotlin 2.4.10 ↔ CMP `org.jetbrains.compose` **1.12.0**
// （要求 AGP 9.1.1+ / compileSdk 37）。
//
// 切片规则（`M1-2` 起）：**只承载有真实消费方的东西**。
//   1. 迁进来的一定是当前已被某个 Feature（首个是 `:feature:tagrules`）消费的
//      theme / 组件 / 资源；拒绝预建空 token 和"将来可能用到"的组件。
//   2. 包名沿用 `io.legado.app.ui.*`（与 `:core:ui` 同命名空间），因此把组件从
//      `:core:ui` 搬进来时**调用方 import 零改动**，只需消费方已依赖本模块。
//   3. 平台 SDK（`android.*`）一律不进 commonMain——需要它的留在 `:core:ui`，
//      或抽窄契约后进共享层。
//
// 另注：本仓的间距/形态是**引擎条件式**的（Miuix 12dp / Material3 16dp，见 `AdaptivePadding.kt`
// 里的 `AdaptiveSpacing`），不是一套常量刻度。所以共享层先落"当前引擎"这个语义
// （`ComposeEngine`），而不是先把数字抽象成 token——否则就是把真实语义抹平的假抽象。

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // 三件都必须是**有 desktop/jvm 变体的 KMP 制品**（commonMain 一份代码跨目标）：
                //   - material3 用 CMP 坐标：它本身就是 KMP 模块，android 变体内部再委托给
                //     `androidx.compose.material3:material3`，所以「android 上最终就是 AndroidX
                //     Material3」是 CMP 自动完成的，不需要按平台手写分支。版本见
                //     libs.versions.toml 的注（≠ 插件版本号，由 convention 断言）；
                //   - 图标用 androidx 1.7.8：Google 当年确实发布了它的 jvm 变体（只发到 1.7.8 这一档，
                //     1.9+ 只有 android）；CMP 自己的 icons 坐标冻在 1.7.3 且已 deprecate。
                //     两条路都不再更新——真要用新图标得迁 Material Symbols（见 cmp-module-convention.md）；
                //   - Miuix 取**不带 `-android` 后缀**的 KMP 模块（`basic.Switch` 在 miuix-ui 里，
                //     不在 miuix-core——踩过一次）。
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.compose.materialIcons)
                implementation(libs.miuix.ui)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
