plugins {
    id("legado.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.room.runtime)
                implementation(libs.sqlite.bundled)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

dependencies {
    "kspAndroid"(libs.room.compiler)
    "kspDesktop"(libs.room.compiler)
}

room {
    schemaDirectory(file("schemas").absolutePath)
}
