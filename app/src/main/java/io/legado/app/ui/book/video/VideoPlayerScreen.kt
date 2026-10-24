package io.legado.app.ui.book.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun VideoPlayerScreen(
    bookUrl: String,
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(bookUrl) {
        viewModel.init(bookUrl)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is VideoPlayerEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = true
                        player = viewModel.exoPlayer
                    }
                },
                onRelease = {
                    it.player = null
                },
            )
            if (state.chapterTitle.isNotBlank()) {
                Text(
                    text = "${state.bookName} · ${state.chapterTitle}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            if (state.error != null) {
                Text(
                    text = state.error ?: "",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
