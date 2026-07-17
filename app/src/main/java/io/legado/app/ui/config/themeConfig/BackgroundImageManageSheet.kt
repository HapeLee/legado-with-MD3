package io.legado.app.ui.config.themeConfig

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundImageManageSheet(
    isDarkTheme: Boolean?,
    onDismissRequest: () -> Unit,
    viewModel: ThemeConfigViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()

    val selectImage =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch {
                    viewModel.setBackgroundFromUri(
                        uri = it,
                        isDarkTheme = isDarkTheme == true
                    )
                }
            }
        }

    AppModalBottomSheet(
        data = isDarkTheme,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.background_image),
    ) { isDark ->
        val currentPath = if (isDark) {
            ThemeConfig.bgImageDark
        } else {
            ThemeConfig.bgImageLight
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {

            if (currentPath.isNullOrBlank()) {
                NormalCard(
                    onClick = { selectImage.launch("image/*") },
                    cornerRadius = 12.dp,
                    containerColor = LegadoTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add),
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
                    NormalCard(
                        cornerRadius = 12.dp,
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
                    SmallTonalButton(
                        onClick = { viewModel.removeBackground(isDark) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp),
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
        }
    }
}
