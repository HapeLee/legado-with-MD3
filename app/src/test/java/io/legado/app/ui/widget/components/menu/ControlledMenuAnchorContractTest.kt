package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * 受控弹层模式的定位前提：`Popup` 没有触点锚点时，`anchorBounds` 是**父布局矩形**。
 *
 * 两个统一入口的受控分支因此不向 COUI 传 `anchor`：COUI 的触点锚点只在 `open()` 记下触点后
 * 才产生坐标，受控模式没有触点，传进去只会得到 null，反而要靠 `Popup` 的父容器矩形定位；
 * 而挂 `couiMenuAnchor` 会给覆盖层加上 `pointerInput`，挡住调用方触发按钮的点击
 * （见 [ActionMenuControlledContractTest.controlledEntriesNeverPutTouchAnchorOnCallerContainer]）。
 *
 * 这条契约依赖 Compose 的 `Popup` 语义而非本仓代码：升级 Compose 后若 `anchorBounds`
 * 变成 0 矩形，受控菜单会整体跑到窗口左上角，说明这里需要重新评估锚点方案。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ControlledMenuAnchorContractTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun popupWithoutTouchAnchorAnchorsToParentContainerRect() {
        var recorded: IntRect? = null
        var density = 0f
        compose.setContent {
            density = LocalDensity.current.density
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .offset(x = ANCHOR_LEFT_DP.dp, y = ANCHOR_TOP_DP.dp)
                        .size(ANCHOR_SIZE_DP.dp),
                ) {
                    Popup(
                        popupPositionProvider = object : PopupPositionProvider {
                            override fun calculatePosition(
                                anchorBounds: IntRect,
                                windowSize: IntSize,
                                layoutDirection: LayoutDirection,
                                popupContentSize: IntSize,
                            ): IntOffset {
                                if (recorded == null) recorded = anchorBounds
                                return IntOffset.Zero
                            }
                        },
                    ) { Text("anchor-probe") }
                }
            }
        }
        compose.waitForIdle()

        val bounds = recorded
        assertNotNull("Popup 未创建，定位器没有被调用，受控菜单不会出现", bounds)
        val expected = IntRect(
            left = (ANCHOR_LEFT_DP * density).toInt(),
            top = (ANCHOR_TOP_DP * density).toInt(),
            right = ((ANCHOR_LEFT_DP + ANCHOR_SIZE_DP) * density).toInt(),
            bottom = ((ANCHOR_TOP_DP + ANCHOR_SIZE_DP) * density).toInt(),
        )
        assertEquals("无触点时 Popup 应锚定父容器矩形，否则受控菜单会跑到窗口左上角", expected, bounds)
    }

    private companion object {
        const val ANCHOR_LEFT_DP = 100
        const val ANCHOR_TOP_DP = 200
        const val ANCHOR_SIZE_DP = 48
    }
}

private fun IntRect.roundToIntRectNoop(): IntRect = this
