package io.legado.app.ui.book.read

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION") // StateRestorationTester 的 JUnit4 适配仍接收旧 ComposeContentTestRule。
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadStyleFilePickersTest {
    @get:Rule val compose = createComposeRule()
    private val intents = mutableListOf<ReadBookIntent>()
    private lateinit var launch: (ReadBookEffect) -> Unit
    private val registry = object : ActivityResultRegistry() {
        var requestCode = 0
        override fun <I, O> onLaunch(
            requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?,
        ) {
            this.requestCode = requestCode
        }
    }
    private val uri = Uri.parse("content://test/theme.zip")

    @Test
    fun newImportRemainsNewAfterStateRestoration() {
        val restoration = showPickers()
        compose.runOnIdle { launch(ReadBookEffect.OpenReadStyleImportAsNew) }
        restoration.emulateSavedInstanceStateRestore()
        complete()
        compose.runOnIdle {
            assertEquals(listOf(ReadBookIntent.ReadStyleConfigImportAsNewSelected(uri)), intents)
        }
    }

    @Test
    fun selectedExportRestoresTheOriginalSelection() {
        val restoration = showPickers()
        compose.runOnIdle { launch(ReadBookEffect.OpenReadStyleConfigsExport("themes.zip", listOf(5, 7))) }
        restoration.emulateSavedInstanceStateRestore()
        complete()
        compose.runOnIdle {
            assertEquals(listOf(ReadBookIntent.ReadStyleConfigsExportSelected(uri, listOf(5, 7))), intents)
        }
    }

    @Test
    fun cancellingNewImportDoesNotChangeTheNextReplaceImport() {
        showPickers()
        compose.runOnIdle { launch(ReadBookEffect.OpenReadStyleImportAsNew) }
        complete(cancelled = true)
        compose.runOnIdle { launch(ReadBookEffect.OpenReadStyleImport) }
        complete()
        compose.runOnIdle {
            assertEquals(listOf(ReadBookIntent.ReadStyleConfigImportSelected(uri)), intents)
        }
    }

    @Test
    fun cancellingSelectedExportDoesNotChangeTheNextCurrentExport() {
        showPickers()
        compose.runOnIdle { launch(ReadBookEffect.OpenReadStyleConfigsExport("themes.zip", listOf(5, 7))) }
        complete(cancelled = true)
        compose.runOnIdle { launch(ReadBookEffect.OpenReadStyleExport("current.zip")) }
        complete()
        compose.runOnIdle {
            assertEquals(listOf(ReadBookIntent.ReadStyleConfigExportSelected(uri)), intents)
        }
    }

    private fun showPickers(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                launch = rememberReadStyleFilePickers(intents::add)
            }
        }
        return restoration
    }

    private fun complete(cancelled: Boolean = false) {
        compose.runOnIdle {
            registry.dispatchResult(
                registry.requestCode,
                if (cancelled) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                if (cancelled) null else Intent().setData(uri),
            )
        }
    }
}
