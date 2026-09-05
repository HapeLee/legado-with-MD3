plugins {
    `kotlin-dsl`
}

repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.2.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
}

gradlePlugin {
    plugins {
        register("legadoKmpLibrary") {
            id = "legado.kmp.library"
            implementationClass = "io.legado.buildlogic.LegadoKmpLibraryConventionPlugin"
        }
    }
}
