package io.legado.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeBuildConfig
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * 真 CMP 模块：[LegadoKmpLibraryConventionPlugin] 之上装 Compose Multiplatform。
 *
 * **Compose 依赖进 `commonMain` 本身**，不建中间的 `composeMain` 源集。目标架构
 * （`docs/dev/kmp-cmp-modernization.md` §3.2）里 `core:designsystem` 与 `feature:*`
 * 是 CMP 模块：UI 代码一份跨 android / desktop / iOS，将来加 native target 不需要
 * 再补一层源集。`composeMain` 是「commonMain 必须零 Compose」时代的过渡形态，
 * 那个约束现在只对 pure/data 模块成立（见 M0-1 的分策）。
 *
 * 代价：commonMain 会出现 `androidx.compose.*`。因此**使用本插件的模块必须在根
 * `build.gradle.kts` 的 `CheckSharedPurityTask.kmpModuleTypes` 里登记为 `cmp`**，
 * 否则 G2 门禁（`checkSharedPurity`）会拦。登记与插件应用必须成对出现。
 *
 * 只提供 runtime + foundation——这是「本模块能放 Compose」的最小充分集；
 * material3 / 图标 / Miuix 等由模块按实际用到的显式声明。
 *
 * ### 版本只有一个来源：CMP 插件自己
 *
 * 坐标里的版本**不手写**，一律取 [ComposeBuildConfig]（CMP 插件随包发布的常量）：
 * 手写版本会漂——本仓真实踩过：凭「插件是 1.12.0」把 material3 写成 `1.12.0-alpha03`，
 * 而插件给的其实是 `composeMaterial3Version = 1.9.0`（material3 目前只有 1.9.0 是
 * stable，1.10+ 全是 alpha）。
 *
 * 该常量与 `gradle/libs.versions.toml` 的 `composeMultiplatform` /
 * `composeMultiplatformMaterial3` 是**同一个事实的两处声明**（版本目录给模块侧用，
 * 常量给这里用）。所以 [assertComposeVersionsInSync] 在配置期断言两者相等——漂了就
 * 立刻失败，而不是等到某次运行时行为异常。
 */
class LegadoKmpComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("legado.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        assertComposeVersionsInSync()

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.named("commonMain") {
                dependencies {
                    // 直写坐标而不是 `compose.*` / `compose.dependencies.*`：见 cmp-module-convention.md
                    // §3「怎么声明 CMP 依赖」——后两者在 CMP 1.12 一个已被 deprecate、一个根本解析不出来。
                    implementation("org.jetbrains.compose.runtime:runtime:${ComposeBuildConfig.composeVersion}")
                    implementation("org.jetbrains.compose.foundation:foundation:${ComposeBuildConfig.composeVersion}")
                }
            }
        }
    }

    /**
     * 版本目录里的 CMP 版本必须与插件常量一致。
     *
     * 不放在「记得同步改两处」的注释里——本仓的教训是注释拦不住漂移（lint 基线漂过一轮），
     * 断言才能。断言失败信息直接给出改哪里。
     */
    private fun Project.assertComposeVersionsInSync() {
        val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
        fun declared(alias: String) = catalog.findVersion(alias).get().requiredVersion

        val drift = mapOf(
            "composeMultiplatform" to ComposeBuildConfig.composeVersion,
            "composeMultiplatformMaterial3" to ComposeBuildConfig.composeMaterial3Version,
        ).filter { (alias, pluginValue) -> declared(alias) != pluginValue }

        check(drift.isEmpty()) {
            drift.entries.joinToString(
                prefix = "CMP 版本漂移（gradle/libs.versions.toml 与 org.jetbrains.compose " +
                    "${ComposeBuildConfig.composeGradlePluginVersion} 插件常量不一致）:\n",
                separator = "\n",
                postfix = "\n改 gradle/libs.versions.toml 里的版本，或改 build-logic 里 " +
                    "compose-gradle-plugin 的版本——两者必须是同一份配对的 CMP。",
            ) { (alias, pluginValue) ->
                "  $alias = ${declared(alias)}，插件常量要求 $pluginValue"
            }
        }
    }
}
