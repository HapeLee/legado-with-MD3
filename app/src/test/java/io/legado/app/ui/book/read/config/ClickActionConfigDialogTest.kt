package io.legado.app.ui.book.read.config

import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.base.BaseOverlayDialogFragment
import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = ClickActionConfigDialogTest.TestApplication::class
)
class ClickActionConfigDialogTest {

    @Test
    fun clickActionConfigDialog_shouldFillWindowHeight() {
        val activity = Robolectric.buildActivity(TestActivity::class.java)
            .setup()
            .get()
        val dialog = TestFullscreenDialog()

        dialog.show(activity.supportFragmentManager, "click-config")
        shadowOf(activity.mainLooper).idle()

        val windowHeight = 1920
        val windowWidth = 1080
        val decorView = dialog.requireDialog().window!!.decorView
        layoutView(decorView, windowWidth, windowHeight)

        assertEquals(
            "ClickActionConfigDialog root view should cover the full window height",
            windowHeight,
            dialog.requireView().height
        )
    }

    private fun layoutView(view: View, width: Int, height: Int) {
        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
    }

    class TestApplication : Application() {
        override fun onCreate() {
            injectAsAppCtx()
            super.onCreate()
        }
    }

    class TestFullscreenDialog : BaseOverlayDialogFragment(R.layout.dialog_click_action_config) {
        override fun onStart() {
            super.onStart()
            dialog?.window?.run {
                setBackgroundDrawableResource(R.color.transparent)
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = Unit
    }

    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_Base_WH)
            super.onCreate(savedInstanceState)
            setContentView(
                FrameLayout(this).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            )
        }
    }
}
