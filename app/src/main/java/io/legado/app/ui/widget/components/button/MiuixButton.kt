package io.legado.app.ui.widget.components.button

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun MiuixIconButton(
    modifier: Modifier = Modifier,
    imageVector: ImageVector = MiuixIcons.Regular.Back,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = stringResource(id = R.string.back),
    onClick: () -> Unit,
) {
    MiuixIconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

@Composable
fun MiuixToggleButton(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconChecked: ImageVector,
    iconUnchecked: ImageVector = iconChecked,
    checkedTint: Color = MiuixTheme.colorScheme.primary,
    uncheckedTint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    MiuixIconButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (checked) iconChecked else iconUnchecked,
            contentDescription = contentDescription,
            tint = if (checked) checkedTint else uncheckedTint
        )
    }
}

@Composable
fun MiuixPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = MiuixButtonDefaults.buttonColorsPrimary()
    ) {
        MiuixText(
            text = text
        )
    }
}

@Composable
fun MiuixSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = MiuixButtonDefaults.buttonColors()
    ) {
        MiuixText(
            text = text
        )
    }
}
