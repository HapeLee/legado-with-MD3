package io.legado.app.ui.book.read

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.repository.AppShellSettingsRepository
import io.legado.app.data.repository.ThemeSettingsRepository
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ReadStyleItem
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.book.read.sheet.ThemeListContent
import io.legado.app.ui.theme.AppTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeListSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        AppConfigStore.init(context)
        stopKoin()
        startKoin {
            modules(module {
                single<AppShellSettingsGateway> { AppShellSettingsRepository() }
                single<ThemeSettingsGateway> { ThemeSettingsRepository() }
            })
        }
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun sharingAfterReorderStillTargetsTheOriginallyCheckedTheme() {
        val context = RuntimeEnvironment.getApplication()
        val items = ((0..4).map { theme("preset-$it") } + listOf(theme("A"), theme("B")))
        val config = mutableStateOf(ReadBookStyleConfig(styleItems = items.toImmutableList()))
        val intents = mutableListOf<ReadBookIntent>()
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(AppUiConfiguration(), applyBackground = false) {
                    ThemeListContent(
                        onIntent = { intents.add(it) },
                        onToggleDayNight = {}, onOpenBgTextConfig = {},
                        gridMaxHeight = 400.dp, styleConfig = config.value,
                    )
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
        compose.onNodeWithText(context.getString(R.string.theme_list_manage)).performClick()
        compose.onNodeWithText("A").performClick()
        // 模拟拖动回调发布的新顺序；勾选与操作分发都走真实 ThemeListContent。
        compose.runOnIdle {
            config.value = config.value.copy(
                styleItems = (items.take(5) + items[6] + items[5]).toImmutableList(),
            )
        }
        compose.onNodeWithContentDescription(context.getString(R.string.share)).performClick()
        compose.runOnIdle {
            assertEquals(ReadBookIntent.ShareReadStyleConfigs(listOf(6)), intents.last())
        }
    }

    private fun theme(name: String) = ReadStyleItem(
        id = "identity-$name", name = name,
        bgType = 0, bgValue = "#FFFFFF", bgTypeNight = 0, bgValueNight = "#000000",
        bgTypeEInk = 0, bgValueEInk = "#FFFFFF",
        textColor = 0xFF000000.toInt(), textColorNight = 0xFFFFFFFF.toInt(),
        textColorEInk = 0xFF000000.toInt(),
    )
}
