package io.legado.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.ComposeBuildConfig
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
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
 * ### 版本来源：runtime/foundation 取插件常量，material3 取显式登记的 pin
 *
 * runtime / foundation 的版本一律取 [ComposeBuildConfig]（CMP 插件随包发布的常量），不手写。
 *
 * material3 是**唯一例外**：插件常量 `composeMaterial3Version` 语义是「material3 的最新
 * stable」，目前停在 1.9.0；而 1.9.0 把 expressive 系列标成了 `internal`（跨模块不可用）。
 * 所以这里取 [MATERIAL3_PIN]——一个**显式登记、附理由、且被断言**的版本，而不是「凭插件是
 * 1.12.0 就随手挑 alpha」。区别不在数字，在**有没有登记与断言**：
 *
 * - 只改版本目录里的 `composeMultiplatformMaterial3` 而不同步改 [MATERIAL3_PIN] → 断言失败；
 * - 把 [MATERIAL3_PIN] 降到插件常量以下 → 断言失败（pin 只允许向上覆盖）。
 *
 * `composeMultiplatform` 仍与插件常量严格相等，由 [assertComposeVersionsInSync] 在配置期
 * 断言——漂了就立刻失败，而不是等到某次运行时行为异常。
 */
class LegadoKmpComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("legado.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        assertComposeVersionsInSync()

        // CMP 多平台资源（`src/commonMain/composeResources/`）生成的 `Res` 访问器包名，
        // 与 Android namespace 同源推导：`:core:designsystem` → `io.legado.app.core.designsystem`
        // ⇒ 加 `.res` 后缀。理由：
        //   - convention 没给项目设 `group`，而 CMP 的默认包名是
        //     `{group}.{module}.generated.resources`，在这里会退化成不可读的名字，必须显式指定；
        //   - 与 namespace 同源 ⇒ 每个 CMP 模块的重资源包名唯一且可预测，模块侧不用各写一遍。
        // 只设包名，不动 `generateResClass`（默认 `auto`：没有 composeResources 目录、
        // 也没依赖 resources 库的模块不会生成 `Res`，所以对现有 CMP 模块零影响）。
        //
        // ⚠️ `resources` 不是 [ComposeExtension] 的属性，而是它作为 `ExtensionAware`
        // 注册的**子扩展**（类型 [ResourcesExtension]）——所以只能
        // `extensions.configure<ResourcesExtension>`，不能写 `compose.resources { }`
        // 里的属性访问（那样编译期就 Unresolved reference）。
        val resourcesPackage = "io.legado.app" + path
            .replace(':', '.')
            .replace("-", "") + ".res"
        extensions.configure<ComposeExtension> {
            extensions.configure<ResourcesExtension> {
                packageOfResClass = resourcesPackage
            }
        }

        extensions.configure<KotlinMultiplatformExtension> {
            // CMP 多平台资源在 AGP 的 `androidLibrary` target 上默认**不启用**资源处理，
            // 而 `composeResources/` 要经 assets 打进 Android 产物（见插件里的
            // `CopyResourcesToAndroidAssetsTask`）⇒ 必须显式打开，否则 Android 侧运行期
            // 读不到资源（`Res.string.x` 会抛资源缺失）。放在本 convention 而不是
            // `legado.kmp.library`：只有 CMP 模块会产出/消费 composeResources，
            // pure 模块不该被带上资源处理。
            targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                androidResources.enable = true
            }

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

    companion object {
        /**
         * material3 的显式 pin，相对 [ComposeBuildConfig.composeMaterial3Version]（1.9.0）
         * **向上覆盖**。理由：插件常量所对的 1.9.0 把 expressive 系列标为 `internal`，跨模块
         * 无法使用（`Cannot access '...': it is internal in file`）。
         *
         * 上游源码实测的放开点：
         * - `MaterialExpressiveTheme` / `MotionScheme` / `@ExperimentalMaterial3ExpressiveApi`
         *   自 **1.10.0-alpha05** 起 public；
         * - `modalWindowInsets` 自 **1.11.0-alpha07** 起存在；
         * - `rememberBottomSheetState` 自 **1.12.0-alpha03** 起存在。
         *
         * 故取「满足全部 expressive 需求的最低版本」，并已实测 desktop 与 Android 双目标编译通过
         * （Android 侧 `androidx.compose.material3` 仍解析到项目锁定的 1.5.0-alpha23，未降级——
         * `material3-android` 只委托到 1.5.0-alpha22）。
         *
         * 升级或回退时**必须同时**改这里的值与 `gradle/libs.versions.toml`。
         */
        private const val MATERIAL3_PIN = "1.12.0-alpha03"
    }

    /**
     * 版本目录里的 CMP 版本必须与 build-logic 声明一致。
     *
     * 不放在「记得同步改两处」的注释里——本仓的教训是注释拦不住漂移（lint 基线漂过一轮），
     * 断言才能。断言失败信息直接给出改哪里。
     */
    private fun Project.assertComposeVersionsInSync() {
        val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
        fun declared(alias: String) = catalog.findVersion(alias).get().requiredVersion

        val pluginConstant = ComposeBuildConfig.composeMaterial3Version

        // material3 只允许「向上覆盖」：pin 低于插件常量就会退回 internal 的 1.9.0。
        check(compareMajorMinor(MATERIAL3_PIN, pluginConstant) >= 0) {
            "material3 pin($MATERIAL3_PIN) 低于 CMP 插件常量($pluginConstant)。" +
                "pin 的用途是「为拿到 expressive 向上覆盖」，不是降级；要降级请改插件版本本身。"
        }

        val drift = mapOf(
            "composeMultiplatform" to ComposeBuildConfig.composeVersion,
            // material3 的期望值来自显式 pin，不是插件常量——见类注释「版本来源」。
            "composeMultiplatformMaterial3" to MATERIAL3_PIN,
        ).filter { (alias, expected) -> declared(alias) != expected }

        check(drift.isEmpty()) {
            drift.entries.joinToString(
                prefix = "CMP 版本漂移（gradle/libs.versions.toml 与 build-logic 的声明不一致）:\n",
                separator = "\n",
                postfix = "\n改 gradle/libs.versions.toml 里的版本，或改 build-logic 里的 " +
                    "MATERIAL3_PIN / compose-gradle-plugin 版本——两者必须是同一份配对的声明。",
            ) { (alias, expected) ->
                "  $alias = ${declared(alias)}，期望 $expected"
            }
        }
    }

    /** 只比较 major.minor：这里的语义是「不低于插件常量」，用不到预发布段。 */
    private fun compareMajorMinor(left: String, right: String): Int {
        fun parts(value: String) =
            value.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val l = parts(left)
        val r = parts(right)
        for (index in 0 until maxOf(l.size, r.size)) {
            val diff = l.getOrElse(index) { 0 }.compareTo(r.getOrElse(index) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}
