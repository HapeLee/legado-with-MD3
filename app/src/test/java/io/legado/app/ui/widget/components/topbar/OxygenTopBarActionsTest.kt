package io.legado.app.ui.widget.components.topbar

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import java.io.File
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OxygenTopBarActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bookshelfSearchAndOverflowShareCatalogGeometryAndKeepTheirActions() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        var searches = 0
        var filters = 0
        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "oxygen"),
                    theme = ThemeSettings(useFlexibleTopAppBar = true),
                ),
                applyBackground = false,
            ) {
                GlassMediumFlexibleTopAppBar(
                    title = "Bookshelf",
                    modifier = Modifier.width(360.dp).testTag("bookshelf-actions"),
                    actions = {
                        TopBarActionButton(
                            onClick = { searches++ },
                            onLongClick = { filters++ },
                            onLongClickLabel = "Filter shelf",
                            imageVector = AppIcons.Search,
                            contentDescription = "Search",
                        )
                        AppActionMenu(
                            groups = persistentListOf(
                                AppActionGroup(id = "bookshelf_more", actions = persistentListOf(AppAction("layout", "Shelf layout")))
                            ),
                            onAction = {},
                        ) {
                            AppIcon(AppIcons.MoreVert, "More", modifier = this.modifier)
                        }
                    },
                )
            }
        }
        val search = compose.onNode(hasClickAction() and hasContentDescription("Search"))
        val more = compose.onNode(hasClickAction() and hasContentDescription("More"))
        val searchBounds = search.getUnclippedBoundsInRoot()
        val moreBounds = more.getUnclippedBoundsInRoot()
        assertEquals(48.dp, searchBounds.right - searchBounds.left)
        assertEquals(48.dp, searchBounds.bottom - searchBounds.top)
        assertEquals(48.dp, moreBounds.right - moreBounds.left)
        assertEquals(0.dp, moreBounds.left - searchBounds.right)
        val searchIcon = compose.onNode(!hasClickAction() and hasContentDescription("Search"), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val moreIcon = compose.onNode(!hasClickAction() and hasContentDescription("More"), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(24.dp, searchIcon.right - searchIcon.left)
        assertEquals(24.dp, moreIcon.right - moreIcon.left)
        assertEquals(48.dp, ((moreIcon.left + moreIcon.right) - (searchIcon.left + searchIcon.right)) / 2)
        assertEquals("Filter shelf", search.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)

        val file = File("build/outputs/oxygenx-bookshelf-actions.png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use {
            compose.onNodeWithTag("bookshelf-actions").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        search.performTouchInput { click() }
        search.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, searches)
            assertEquals(1, filters)
        }
        more.performTouchInput { click() }
        compose.onNodeWithText("Shelf layout").assertIsDisplayed()
    }
}
