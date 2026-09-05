package io.legado.app.ui.association

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import io.legado.app.R
import io.legado.app.help.coil.CoverExtras
import io.legado.app.ui.book.read.sheet.PhotoSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog

/** Compose replacement for the former Fragment verification-code dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationCodeDialog(
    imageUrl: String,
    sourceOrigin: String,
    sourceName: String,
    onSubmit: (String) -> Unit,
    onDisableSource: () -> Unit,
    onDeleteSource: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showPhoto by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val request = remember(imageUrl, sourceOrigin) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .apply { extras[CoverExtras.SourceOrigin] = sourceOrigin }
            .build()
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
            color = LegadoTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.input_verification_code))
                            sourceName.takeIf { it.isNotBlank() }?.let { Text(it) }
                        }
                    },
                    actions = {
                        TextButton(onClick = { onSubmit(code) }) {
                            Text(stringResource(R.string.ok))
                        }
                        TextButton(onClick = { menuExpanded = true }) { Text("⋮") }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.disable_source)) },
                                onClick = {
                                    menuExpanded = false
                                    onDisableSource()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.draw)) },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteConfirmation = true
                                },
                            )
                        }
                    },
                )
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    error = painterResource(R.drawable.image_loading_error),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(horizontal = 16.dp)
                        .clickable { showPhoto = true },
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.verification_code)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }

    AppAlertDialog(
        show = showDeleteConfirmation,
        onDismissRequest = { showDeleteConfirmation = false },
        text = stringResource(R.string.sure_del) + "\n" + sourceName,
        onConfirm = onDeleteSource,
        onDismiss = { showDeleteConfirmation = false },
    )
    PhotoSheet(
        show = showPhoto,
        src = imageUrl,
        sourceOrigin = sourceOrigin,
        onDismissRequest = { showPhoto = false },
    )
}
