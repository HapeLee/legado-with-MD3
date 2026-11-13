package io.legado.app.forks

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI fork 固定来源契约。
 *
 * `scripts/prepare-ui-forks.py` 在构建前校验 coui / mochi / miuix 的 gitlink、
 * 版本串与工作树。前两者从子模块源码发布到 `build/ui-forks/maven`；Miuix 走
 * `settings.gradle` 的 `includeBuild`，**只校验不发布**。Miuix 曾完全不受校验：
 * 开发机改了 vendored 源码，CI 会用干净子模块构建且不报错，线上与本地就此分叉。
 *
 * 静态契约只锁结构；真实校验以 `python scripts/prepare-ui-forks.py --check` 为准。
 * 注意不要放在名为 `build/` 的包下，仓库 .gitignore 会把它整目录忽略。
 */
class UiForkPinContractTest {

    @Test
    fun allThreeForksAreValidatedBeforeBuild() {
        val script = repoFileOf(SCRIPT)
        assertTrue(
            "COUI/Mochi 发布分支未走统一校验",
            script.contains("validate_fork(name, versions, strict_suffix=True)"),
        )
        assertTrue(
            "Miuix 未被纳入只校验分支",
            script.contains("""CHECK_ONLY_FORKS = ("miuix",)"""),
        )
        assertTrue(
            "Miuix 只校验分支未走统一校验",
            script.contains("validate_fork(name, versions, strict_suffix=False)"),
        )
        assertTrue(
            "脚本未用 12 位 commit 校验版本串",
            script.contains("commit[:12]"),
        )
        assertTrue(
            "工作树检查未覆盖未跟踪文件（composite build 会编译子模块源码目录）",
            script.contains("""["git", "-C", str(directory), "status", "--porcelain"]"""),
        )
    }

    @Test
    fun miuixVersionCarriesTwelveCharacterCommit() {
        val versions = repoFileOf(VERSIONS)
        val line = versions.lines().first { it.startsWith("miuix = ") }
        assertTrue(
            "miuix 版本必须带 12 位 commit 前缀（与 coui/mochi 同口径）",
            Regex("""^miuix = ".*-[0-9a-f]{12}(?:-SNAPSHOT)?"$""").matches(line),
        )
    }

    private fun repoFileOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val SCRIPT = "scripts/prepare-ui-forks.py"
        const val VERSIONS = "gradle/libs.versions.toml"
    }
}
