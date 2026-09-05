package io.legado.app.ui.book.read.sheet

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.toBitmap
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.error
import coil3.size.Size
import io.legado.app.R
import io.legado.app.help.book.BookHelp
import io.legado.app.help.coil.CoverExtras
import io.legado.app.model.BookCover
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.ImageSaveUtils.saveImageToGallery
import io.legado.app.utils.toastOnUi
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream

@Composable
fun PhotoSheet(
    show: Boolean,
    src: String,
    sourceOrigin: String? = null,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val imageLoader: ImageLoader = koinInject()
    val saveSuccessMessage = stringResource(R.string.save_success)
    val saveFailedMessage = stringResource(R.string.save_failed)
    val providedBitmap = ImageProvider.get(src)
    val localImage = ReadBook.book
        ?.let { book -> BookHelp.getImage(book, src) }
        ?.takeIf { it.exists() }
    val data = providedBitmap ?: localImage ?: src
    var loadedBitmap by remember(src, sourceOrigin) { mutableStateOf(providedBitmap) }
    val request = remember(data, src, sourceOrigin) {
        ImageRequest.Builder(context)
            .data(data)
            .size(Size.ORIGINAL)
            .allowHardware(false)
            .listener(
                onSuccess = { _, result ->
                    loadedBitmap = result.image.toBitmap()
                },
            )
            .apply {
                if (data != src) diskCachePolicy(CachePolicy.DISABLED)
                extras[CoverExtras.SourceOrigin] = sourceOrigin
            }
            .apply {
                if (data == src) error(BookCover.defaultDrawable)
                else error(R.drawable.image_loading_error)
            }
            .build()
    }
    val zoomableImageState = rememberZoomableImageState(rememberZoomableState())

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.photo),
    ) {
        ZoomableAsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            state = zoomableImageState,
            gestures = EnabledZoomGestures.ZoomAndPan,
            contentScale = ContentScale.Fit,
            onLongClick = {
                loadedBitmap?.let { bitmap ->
                    val byteArray = ByteArrayOutputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                        stream.toByteArray()
                    }
                    val success = saveImageToGallery(context, byteArray)
                    context.toastOnUi(
                        if (success) saveSuccessMessage else saveFailedMessage,
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
        )
    }
}
