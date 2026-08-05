package io.legado.app.ui.widget.components.pager

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

private class PagerFlingPassThroughConnection(
    private val delegate: NestedScrollConnection,
    private val orientation: Orientation,
) : NestedScrollConnection {

    private fun Velocity.reverseOnOrientation(): Velocity {
        return if (orientation == Orientation.Horizontal) {
            copy(x = -x, y = 0f)
        } else {
            copy(x = 0f, y = -y)
        }
    }

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return delegate.onPreScroll(available, source)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return delegate.onPostScroll(consumed, available, source)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return delegate.onPreFling(available)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        delegate.onPostFling(consumed, available)
        return consumed.reverseOnOrientation()
    }
}

@Composable
fun rememberPagerFlingPassThroughConnection(
    state: PagerState,
    orientation: Orientation,
): NestedScrollConnection {
    val defaultConnection = PagerDefaults.pageNestedScrollConnection(
        state = state,
        orientation = orientation,
    )
    return remember(state, orientation, defaultConnection) {
        PagerFlingPassThroughConnection(
            delegate = defaultConnection,
            orientation = orientation,
        )
    }
}
