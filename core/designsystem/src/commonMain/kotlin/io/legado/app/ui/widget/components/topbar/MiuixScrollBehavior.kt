package io.legado.app.ui.widget.components.topbar

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import top.yukonga.miuix.kmp.basic.ScrollBehavior


interface GlassTopAppBarScrollBehavior {
    // 滚动连接器
    val nestedScrollConnection: NestedScrollConnection

    // 折叠进度：0f 表示完全展开，1f 表示完全折叠。
    val collapsedFraction: Float
}

@OptIn(ExperimentalMaterial3Api::class)
class M3GlassScrollBehavior(
    val m3Behavior: TopAppBarScrollBehavior
) : GlassTopAppBarScrollBehavior {
    override val nestedScrollConnection: NestedScrollConnection
        get() = m3Behavior.nestedScrollConnection

    override val collapsedFraction: Float
        get() = m3Behavior.state.collapsedFraction
}

class MiuixGlassScrollBehavior(
    val miuixBehavior: ScrollBehavior
) : GlassTopAppBarScrollBehavior {
    override val nestedScrollConnection: NestedScrollConnection
        get() = miuixBehavior.nestedScrollConnection

    override val collapsedFraction: Float
        get() = miuixBehavior.state.collapsedFraction
}
