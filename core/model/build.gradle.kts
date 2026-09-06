plugins {
    id("legado.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:platform"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
