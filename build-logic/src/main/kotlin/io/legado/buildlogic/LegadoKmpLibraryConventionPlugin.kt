package io.legado.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The only KMP module type proven in this repository so far.
 *
 * It intentionally configures Android and JVM only. Additional targets belong in a
 * separate, evidence-backed convention once a product capability matrix requires them.
 */
class LegadoKmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                namespace = "io.legado.app" + path
                    .replace(':', '.')
                    .replace("-", "")
                compileSdk = 37
                minSdk = 26
                withHostTest {}
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
            jvm("desktop") {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
            sourceSets.named("commonTest") {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }
    }
}
