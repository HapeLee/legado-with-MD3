package io.legado.app.ui.widget.components.alert

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHasMiuixPopupHost
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.theme.rememberMochiOverlayBackdrop
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import io.legado.app.ui.widget.components.mochi.MochiOverlayAccessibility
import io.legado.app.ui.widget.components.mochi.mochiOverlayContentModifier
import io.legado.app.ui.widget.components.LocalAppGlassOverlay
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppAlertDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    content: (@Composable () -> Unit)? = null,
    confirmText: String = "确定", // 默认文字
    onConfirm: (() -> Unit)? = null,
    /** 确定按钮是否可点：表单类弹窗里输入还不合法时置 false，而不是点了才弹一句提示。 */
    confirmEnabled: Boolean = true,
    dismissText: String = "取消",
    onDismiss: (() -> Unit)? = null,
) {
    when (composeEngine) {
        ComposeEngine.Miuix -> {
            val themeSettings = LocalAppUiConfiguration.current.theme
            val useGlassContainer = themeSettings.useHyperOs4Components &&
                LocalHasMiuixPopupHost.current &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val backdrop = LocalMiuixBarBackdrop.current
            val glassOverlay = LocalAppGlassOverlay.current
            val dialogBody: @Composable () -> Unit = {
                // WindowDialog has no popup host; its content must not inherit the Scaffold's
                // host signal. A glass dialog shares the root host with its dropdowns.
                CompositionLocalProvider(
                    LocalHasMiuixPopupHost provides useGlassContainer,
                    LocalAppGlassOverlay provides glassOverlay.takeIf { useGlassContainer },
                    LocalMiuixBarBackdrop provides backdrop.takeIf { useGlassContainer },
                ) {
                    ProvideAppDensity {
                        if (content != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                content()
                            }
                        }

                        if (onDismiss != null || onConfirm != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onDismiss != null) {
                                    if (useGlassContainer) {
                                        GlassDialogButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                                            MiuixText(dismissText, style = MiuixTheme.textStyles.button)
                                        }
                                    } else {
                                        SecondaryButton(
                                            text = dismissText,
                                            modifier = Modifier.weight(1f),
                                            onClick = onDismiss,
                                        )
                                    }
                                }

                                if (onConfirm != null) {
                                    if (useGlassContainer) {
                                        GlassDialogButton(
                                            onClick = onConfirm,
                                            modifier = Modifier.weight(1f),
                                            enabled = confirmEnabled,
                                            role = GlassDialogButtonRole.Primary,
                                        ) {
                                            MiuixText(confirmText, style = MiuixTheme.textStyles.button)
                                        }
                                    } else {
                                        PrimaryButton(
                                            text = confirmText,
                                            modifier = Modifier.weight(1f),
                                            enabled = confirmEnabled,
                                            onClick = onConfirm,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (useGlassContainer) {
                GlassDialog(
                    onDismissRequest = onDismissRequest,
                    show = show,
                    backdrop = backdrop,
                    modifier = modifier,
                    title = title,
                    summary = text,
                    content = dialogBody,
                )
            } else {
                WindowDialog(
                    show = show,
                    modifier = modifier,
                    title = title,
                    summary = text,
                    onDismissRequest = onDismissRequest,
                    content = dialogBody,
                )
            }
        }
        ComposeEngine.Mochi -> {
            MochiOverlayAccessibility(show)
            val backdrop = rememberMochiOverlayBackdrop(show)
            CompositionLocalProvider(LocalMochiBackdrop provides backdrop) {
                MochiDialog(
                    visible = show,
                    onDismissRequest = onDismissRequest,
                    title = title.orEmpty(),
                    modifier = modifier,
                    dismissOnOutsideTap = true,
                    // Native actions auto-dismiss. Forms keep ownership of their asynchronous
                    // confirmation and validation, so render the app buttons in the content slot.
                    actions = persistentListOf(),
                ) {
                    ProvideAppDensity {
                        Column(mochiOverlayContentModifier(onDismissRequest, title).verticalScroll(rememberScrollState())) {
                            if (text != null) SelectionContainer {
                                Text(text, Modifier.padding(bottom = if (content != null) 16.dp else 0.dp))
                            }
                            content?.invoke()
                            if (onDismiss != null || onConfirm != null) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (onDismiss != null) SecondaryButton(
                                        text = dismissText, modifier = Modifier.weight(1f), onClick = onDismiss,
                                    )
                                    if (onConfirm != null) PrimaryButton(
                                        text = confirmText, modifier = Modifier.weight(1f),
                                        enabled = confirmEnabled, onClick = onConfirm,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        ComposeEngine.Material, ComposeEngine.Coui -> {
            if (show) {
                AlertDialog(
                    onDismissRequest = onDismissRequest,
                    modifier = modifier,
                    containerColor = LegadoTheme.colorScheme.surfaceContainer,
                    iconContentColor = LegadoTheme.colorScheme.primary,
                    titleContentColor = LegadoTheme.colorScheme.onSurface,
                    textContentColor = LegadoTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    title = title?.let { { ProvideAppDensity { Text(text = it) } } },
                    text = {
                        ProvideAppDensity {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                if (text != null) {
                                    SelectionContainer {
                                        Text(
                                            text = text,
                                            modifier = Modifier.padding(bottom = if (content != null) 16.dp else 0.dp)
                                        )
                                    }
                                }
                                if (content != null) {
                                    content()
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (onConfirm != null) {
                            ProvideAppDensity {
                                PrimaryButton(
                                    onClick = onConfirm,
                                    enabled = confirmEnabled,
                                    text = confirmText
                                )
                            }
                        }
                    },
                    dismissButton = {
                        if (onDismiss != null) {
                            ProvideAppDensity {
                                SecondaryButton(
                                    onClick = {
                                        onDismiss()
                                    },
                                    text = dismissText
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * 专为 nullable 数据设计的 AppAlertDialog 重载。
 * 当 [data] 不为 null 时显示弹窗；当 [data] 变为 null 时，自动缓存最后一次数据并播放退出动画。
 */
@Composable
fun <T> AppAlertDialog(
    data: T?,
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    textProvider: @Composable (T.() -> String)? = null,
    confirmText: String = "确定",
    onConfirm: ((T) -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissText: String = "取消",
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: (@Composable (T) -> Unit)? = null
) {
    var cachedData by remember { mutableStateOf(data) }

    if (data != null) {
        cachedData = data
    }

    val currentData = cachedData
    if (currentData != null) {
        val currentText = text ?: textProvider?.invoke(currentData)
        var lastValidText by remember { mutableStateOf(currentText) }
        
        if (currentText != null) {
            lastValidText = currentText
        }

        AppAlertDialog(
            show = data != null,
            onDismissRequest = onDismissRequest,
            title = title,
            text = currentText ?: lastValidText,
            modifier = modifier,
            confirmText = confirmText,
            onConfirm = onConfirm?.let { { it(currentData) } },
            confirmEnabled = confirmEnabled,
            dismissText = dismissText,
            onDismiss = onDismiss,
            content = content?.let { { it(currentData) } }
        )
    }
}
