package io.legado.app.data.repository

import android.app.Application
import io.legado.app.data.entities.HighlightRule
import io.legado.app.support.InMemoryAppDatabaseFixture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * sanitizeRule 是保存链路的必经一步，清理参数时必须保留编辑器支持的字段与范围。
 *
 * 曾经漏抄过命中字距、命中行行距、九宫格长度偏移：滑杆调完点保存，值直接被清零，
 * 正文当然看不出任何变化。这条测试把「每个字段都必须原样活下来」钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HighlightRuleSanitizeTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun saveAndReloadKeepFullRangeNineSliceFractionsAfterReplacingTheImage() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        InMemoryAppDatabaseFixture(context).use { fixture ->
            val repository = HighlightRuleRepository(fixture.database.highlightRuleDao, context)
            val rule = HighlightRule(
                id = "off-center-bubble",
                pattern = "dialog",
                bgImage = temporaryFolder.newFile("first.png").absolutePath,
                bgImageFit = 3,
                npLeft = 0.8f,
                npRight = 0.65f,
                npTop = 0.9f,
                npBottom = 1f,
            )

            repository.save("", listOf(rule))
            val reloaded = repository.load("").single()
            assertEquals(rule, reloaded)

            val replaced = reloaded.copy(bgImage = temporaryFolder.newFile("second.png").absolutePath)
            repository.save("", listOf(replaced))
            assertEquals(replaced, repository.load("").single())
        }
    }

    @Test
    fun sanitizeClampsNineSliceFractionsToTheEditorsZeroToOneRange() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        InMemoryAppDatabaseFixture(context).use { fixture ->
            val repository = HighlightRuleRepository(fixture.database.highlightRuleDao, context)
            val rule = HighlightRule(npLeft = -0.2f, npRight = 1.2f, npTop = -1f, npBottom = 2f)

            assertEquals(
                rule.copy(npLeft = 0f, npRight = 1f, npTop = 0f, npBottom = 1f),
                repository.sanitizeRule(rule),
            )
        }
    }

    @Test
    fun sanitizeKeepsEveryFieldOfAFullyConfiguredRule() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        InMemoryAppDatabaseFixture(RuntimeEnvironment.getApplication()).use { fixture ->
            val repository = HighlightRuleRepository(
                dao = fixture.database.highlightRuleDao,
                context = RuntimeEnvironment.getApplication(),
            )
            val rule = HighlightRule(
                id = "rule-1",
                name = "对话",
                pattern = "“[^”]*”",
                sampleText = HighlightRule.DEFAULT_SAMPLE_TEXT,
                targetScope = HighlightRule.TARGET_BODY,
                enabled = false,
                position = 3,
                textColor = 0xFF2495FF.toInt(),
                bgColor = 0xFF00FF00.toInt(),
                underlineMode = 5,
                underlineColor = 0xFFFF0000.toInt(),
                underlineWidth = 1.5f,
                underlineOffset = 3f,
                underlineSvgPath = "M0,50 L100,50",
                bgImage = "/data/user/0/io.legato.kazusa.mod/files/bg_images/x.png",
                bgImageFit = 3,
                bgImageScale = 1.5f,
                configName = "[\"默认\"]",
                fontPath = "/data/user/0/io.legato.kazusa.mod/files/fonts/kaiti.ttf",
                fontWeight = 500,
                isItalic = true,
                fontSizeOffset = 2,
                npLeft = 0.2f,
                npRight = 0.25f,
                npTop = 0.3f,
                npBottom = 0.35f,
                manualNineSlice = false,
                letterSpacingBefore = 20.5f,
                letterSpacingAfter = 22.5f,
                lineSpacingTop = 21.5f,
                lineSpacingBottom = 14f,
                bgLengthOffsetLeft = -12.5f,
                bgLengthOffsetRight = 7.5f,
            )
            assertEquals(rule, repository.sanitizeRule(rule))
        }
    }
}
