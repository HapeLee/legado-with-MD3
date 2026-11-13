package io.legado.app.ui.widget.components.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * COUI 胶囊渲染器的 API 守卫契约（第五十轮）。
 *
 * `rememberCouiCapsuleRenderer()` 标了 `@RequiresApi(UPSIDE_DOWN_CAKE)`（34），minSdk 26。
 * `AppChoiceDropdown.CouiChoice` 的 Icon 分支曾把调用写在守卫之外：lint 报 `NewApi`，
 * 且低版本会无条件构造 API 34 渲染器。修法是把 `ThemeResolver.isCouiSupported()`
 * （`@ChecksSdkIntAtLeast`）放进调用所在的 if 条件，与 `TopBarButton` 同口径。
 * 注意有**两个** API 34 点：`rememberCouiCapsuleRenderer()` 与 `CouiTopAppBarButton()`；
 * lint 不把 `capsule != null` 折算成 SDK 检查，后者条件里必须再写一次守卫。
 * 不用 `@SuppressLint`、不改 `lint-baseline.xml`。
 *
 * 静态契约只锁结构；双变体 lint 与真机分档结果见 plan.md §9 第五十轮。
 */
class CouiCapsuleApiGuardContractTest {

    @Test
    fun capsuleRendererOnlyConstructedBehindSdkGuard() {
        val source = sourceOf(APP_CHOICE_DROPDOWN)
        assertFalse(
            "又回到守卫外无条件构造胶囊渲染器：低版本会走 API 34 代码，lint 报 NewApi",
            source.contains("val capsule = rememberCouiCapsuleRenderer()"),
        )
        assertTrue(
            "COUI 胶囊渲染器未放进 SDK 守卫分支",
            source.contains("val capsule = if (ThemeResolver.isCouiSupported() &&"),
        )
        val guarded = source
            .substringAfter("val capsule = if (ThemeResolver.isCouiSupported() &&")
            .substringBefore("} else {")
        assertTrue(
            "守卫分支内没有构造渲染器（守卫与调用分离了）",
            guarded.contains("rememberCouiCapsuleRenderer()"),
        )
        assertTrue(
            "CouiTopAppBarButton 的调用条件缺 SDK 守卫：lint 会把 capsule != null 当普通条件并报 NewApi",
            source.contains("if (capsule != null && ThemeResolver.isCouiSupported())"),
        )
    }

    @Test
    fun sdkGuardIsLintVisibleWithoutSuppression() {
        val resolver = sourceOf(THEME_RESOLVER)
        assertTrue(
            "isCouiSupported 缺 @ChecksSdkIntAtLeast，lint 无法把守卫折算成 SDK 34 检查",
            resolver.contains("@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)"),
        )
        assertTrue(
            "守卫不再是 SDK_INT >= 34 的语义",
            resolver.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE"),
        )
        assertFalse(
            "不要用 SuppressLint 掩盖：守卫已能被 lint 识别",
            sourceOf(APP_CHOICE_DROPDOWN).contains("""@SuppressLint("NewApi")"""),
        )
    }

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val APP_CHOICE_DROPDOWN = "io/legado/app/ui/widget/components/menu/AppChoiceDropdown.kt"
        const val THEME_RESOLVER = "io/legado/app/ui/theme/ThemeResolver.kt"
    }
}
