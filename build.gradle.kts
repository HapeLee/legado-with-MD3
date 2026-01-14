buildscript {
    extra.apply {
        set("compile_sdk_version", 36)
        set("build_tool_version", "34.0.0")
    }
}

plugins {
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.download) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}