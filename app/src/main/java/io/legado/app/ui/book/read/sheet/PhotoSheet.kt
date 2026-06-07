package io.legado.app.ui.book.read.sheet

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.help.book.BookHelp
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.model.BookCover
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.image.PhotoView
import io.legado.app.utils.ImageSaveUtils.saveImageToGallery
import io.legado.app.utils.toastOnUi
import java.io.ByteArrayOutputStream

@Composable
fun PhotoSheet(
    show: Boolean,
    src: String,
    sourceOrigin: String? = null,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.photo),
    ) {
        AndroidView(
            factory = { ctx ->
                PhotoView(ctx).apply {
                    // Try to load from ImageProvider first
                    val bitmap = ImageProvider.get(src)
                    if (bitmap != null) {
                        setImageBitmap(bitmap)
                    } else {
                        // Try to load from local file
                        val file = ReadBook.book?.let { book ->
                            BookHelp.getImage(book, src)
                        }
                        if (file?.exists() == true) {
                            ImageLoader.load(ctx, file)
                                .error(R.drawable.image_loading_error)
                                .dontTransform()
                                .downsample(DownsampleStrategy.NONE)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .into(this)
                        } else {
                            // Load from URL
                            val request = ImageLoader.load(ctx, src).apply {
                                sourceOrigin?.let { origin ->
                                    apply(
                                        RequestOptions().set(
                                            OkHttpModelLoader.sourceOriginOption,
                                            origin
                                        )
                                    )
                                }
                            }
                            request.error(BookCover.defaultDrawable)
                                .dontTransform()
                                .downsample(DownsampleStrategy.NONE)
                                .into(this)
                        }
                    }

                    // Long press to save
                    setOnLongClickListener {
                        val drawable = drawable
                        val bmp = (drawable as? BitmapDrawable)?.bitmap
                        if (bmp != null) {
                            val byteArray = ByteArrayOutputStream().use { stream ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                                stream.toByteArray()
                            }
                            val success = saveImageToGallery(context, byteArray)
                            context.toastOnUi(
                                if (success) context.getString(R.string.save_success)
                                else "保存失败"
                            )
                        }
                        true
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
        )
    }
}
