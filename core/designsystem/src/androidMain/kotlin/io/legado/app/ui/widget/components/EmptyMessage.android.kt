package io.legado.app.ui.widget.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * [EmptyMessage] 的 **Android 专用 `@StringRes` 重载**（M5-1c-pre 从 commonMain 分出来）。
 *
 * 为什么它在 `androidMain`：重载体里的 `stringResource(id: Int)` 是
 * `androidx.compose.ui.res.stringResource`，Android-only。CMP 侧的替代
 * （`org.jetbrains.compose.resources.stringResource`）接的是 `StringResource` 而不是 `Int`，
 * 所以这条「传资源 id」的便利写法无法跨平台——`AGENTS.md` 因此要求按源集分家，而不是
 * 在 commonMain 里放一个假实现。
 *
 * 参数面**刻意只保留现存调用方用到的两个**（`:app` 侧 5 处调用全部只传
 * `modifier` + `messageResId`）。其余可选参数（`isLoading` / `buttonText` / `faces` …）
 * 共享层的 `String` 重载已经提供，重复一套默认值只会让两处定义漂移。
 */
@Composable
fun EmptyMessage(
    @StringRes messageResId: Int,
    modifier: Modifier = Modifier,
) {
    EmptyMessage(
        message = stringResource(id = messageResId),
        modifier = modifier,
    )
}
