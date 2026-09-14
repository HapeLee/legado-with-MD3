plugins {
    id("legado.kmp.compose")
}

// D5 PoC：验证 **Compose UI 测试** 能在 `jvm("desktop")` target 上跑起来。
//
// 这是 M1-4「最小 Desktop host 主路径」的**前置可行性探针**：
// 在投入建 desktop host（Koin graph + Room + 真实 Screen 渲染 + 数据路径断言）之前，
// 必须先证明「commonMain 里的一份 CMP 代码，能在 desktop 上被真实渲染并断言」这条工具链通。
// 此前本仓所有 desktop 证据都止于**编译**——编译通过证明不了能渲染。
//
// 三个待验证的点，缺一不可：
//   1. `compose.uiTestJUnit4` 有 desktop 变体，能在 KMP 的 jvm target 上解析；
//   2. `compose.desktop.currentOs`（Skiko 渲染运行时）能在这个非 application 模块里用；
//   3. `runComposeUiTest` 在本机（Windows）能不开窗口地跑完 headless 渲染。
//
// 探针结论与后续决策记在 `docs/dev/feature-slicing-audit-tagrules.md` §30。
// 本模块只验证工具链，不承载业务代码。

kotlin {
    sourceSets {
        commonMain.dependencies {
            // runtime / foundation 由 `legado.kmp.compose` convention 注入，这里只补实际用到的。
            // ⚠️ 不要写 `compose.runtime` / `compose.foundation` / `compose.material3`：
            // CMP 1.12.0 的 Gradle 插件已把这些 accessor 标记 deprecated，并要求「直接写坐标」。
            implementation(libs.compose.multiplatform.material3)
        }
        desktopTest.dependencies {
            implementation(kotlin("test"))
            // Skiko 渲染运行时：UI 测试要靠它真正把帧画出来。这个 accessor 未被废弃。
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.multiplatform.ui.test.junit4)
        }
    }
}
