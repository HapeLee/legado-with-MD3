package io.legado.app.ui.config.themeConfig

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.SmallTonalIconButton
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundImageManageSheet(
    show: Boolean,
    isDarkTheme: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: ThemeConfigViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    var showFilePicker by remember { mutableStateOf(false) }

    val selectImage =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch {
                    viewModel.setBackgroundFromUri(
                        uri = it,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }

    val currentPath = if (isDarkTheme) {
        ThemeConfig.bgImageDark
    } else {
        ThemeConfig.bgImageLight
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.background_image),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {

            if (currentPath.isNullOrBlank()) {
                Surface(
                    onClick = { showFilePicker = true },
                    shape = MaterialTheme.shapes.medium,
                    color = LegadoTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(48.dp),
                            tint = LegadoTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 2.dp
                    ) {
                        AsyncImage(
                            model = currentPath,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f),
                            contentScale = ContentScale.Crop
                        )
                    }
                    SmallTonalIconButton(
                        onClick = { viewModel.removeBackground(isDarkTheme) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp),
                        imageVector = Icons.Default.Close
                    )
                }
            }
        }
    }

    FilePickerSheet(
        show = showFilePicker,
        onDismissRequest = { showFilePicker = false },
        onSelectSysFile = {
            selectImage.launch("image/*")
            showFilePicker = false
        },
        allowExtensions = arrayOf("jpg", "jpeg", "png", "webp")
    )
}
