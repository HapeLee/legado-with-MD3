plugins {
    id("legado.kmp.library")
}

// 本模块当前只承载「共享 UI 状态契约叶子」（`ui.widget.components.list` /
// `ui.widget.components.importComponents`），零依赖纯 Kotlin，android + desktop 都编译。
//
// 此前这里显式 apply 了 Compose Multiplatform（`org.jetbrains.compose` +
// `compose.compiler`）并挂了一个 android/desktop 共享的 `composeMain` 源集，用来放设计
// token 的 `Color`/`Dp` 映射。那套 token 一直没有真实消费方（AGENTS.md 禁止无调用方抽象），
// 已随 token 一并删除，CMP 也因此撤掉——等真有跨平台的共享 Compose UI 时再装回来。
//
// 恢复配方（已验证可用）：Kotlin 2.4.10 ↔ CMP 插件 `org.jetbrains.compose` **1.12.0**
// （= Compose 1.12 模块，要求 AGP 9.1.1+ / compileSdk 37）。装回后新增
// `val composeMain = create("composeMain") { dependsOn(commonMain.get()) }`，
// 再让 androidMain / desktopMain `dependsOn(composeMain)`；Compose 依赖只放该源集，
// commonMain 保持零 Compose（`checkSharedPurity` 只白名单
// `androidx.compose.runtime.Stable` / room / sqlite）。
//
// 另注：本仓库 app 侧的间距是**引擎条件式**的（Miuix 12dp / Material3 16dp，见
// `ui/theme/AdaptivePadding.kt`），不是一套常量刻度。将来若再建 token 层，必须能承载这种
// 条件语义，否则就是把真实语义抹平的假抽象。

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
