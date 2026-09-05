plugins {
    id("legado.kmp.library")
}

// D4 PoC：验证 Rhino（1.8.1，纯 JVM）能在 commonMain 契约 + android/desktop(JVM) actual 的
// capability 边界下工作，为 P1 的 RuleEngine 契约选型产出 Go/No-Go。
// Rhino 是 JVM-only，故放 androidMain/desktopMain（均 JVM），不进 commonMain。

kotlin {
    sourceSets {
        androidMain {
            dependencies {
                implementation(libs.mozilla.rhino)
            }
        }
        desktopMain {
            dependencies {
                implementation(libs.mozilla.rhino)
            }
        }
    }
}
