package io.legado.app.ui.main.ai.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.widget.compose.releaseComposeImage
import androidx.compose.ui.window.Dialog
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.SearchBookOpenHelper
import io.legado.app.ui.main.ai.AiImagePreviewDialog
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi

@Composable
fun AiToolPreviewDialog(
    payload: AiToolDisplayPayload,
    style: AiComposeStyle,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(style.metrics.cardRadius),
            color = style.colors.cardSurface,
            border = androidx.compose.foundation.BorderStroke(style.metrics.strokeWidth, style.colors.stroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = payload.title,
                            color = style.colors.primaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (payload.summary.isNotBlank()) {
                            Text(
                                text = payload.summary,
                                color = style.colors.secondaryText,
                                fontSize = 12.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = "关闭",
                        color = style.colors.accent,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                when (payload.type) {
                    AiToolPreviewType.BookResults -> BookResultPreview(payload.books, style, onDismiss)
                    AiToolPreviewType.WebResults -> WebResultPreview(payload.webResults, style)
                    AiToolPreviewType.ImageResult -> ImageResultPreview(payload.images, style, onDismiss)
                    AiToolPreviewType.Generic -> GenericToolPreview(payload.raw, style)
                }
                Text(
                    text = "复制原始数据",
                    color = style.colors.accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 14.dp)
                        .clickable {
                            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            manager.setPrimaryClip(ClipData.newPlainText(payload.title, payload.raw))
                            context.toastOnUi("已复制")
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun BookResultPreview(
    books: List<AiSearchBookUi>,
    style: AiComposeStyle,
    onDismiss: () -> Unit
) {
    if (books.isEmpty()) {
        EmptyPreviewText("暂无书籍结果", style)
        return
    }
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(books, key = { it.bookUrl }) { book ->
            Surface(
                shape = RoundedCornerShape(style.metrics.chipRadius),
                color = style.colors.background.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(style.metrics.strokeWidth, style.colors.stroke),
                modifier = Modifier
                    .width(138.dp)
                    .clickable {
                        SearchBookOpenHelper.open(
                            context,
                            SearchBook(
                                name = book.name,
                                author = book.author,
                                bookUrl = book.bookUrl,
                                origin = book.origin,
                                originName = book.originName
                            ),
                            book.target == "video"
                        )
                        onDismiss()
                    }
            ) {
                Column(modifier = Modifier.padding(9.dp)) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        factory = {
                            CoverImageView(it).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }
                        },
                        update = {
                            it.load(
                                path = book.coverUrl,
                                name = book.name,
                                author = book.author,
                                loadOnlyWifi = false,
                                sourceOrigin = book.origin,
                                preferThumb = true
                            )
                        },
                        onRelease = { it.releaseComposeImage() }
                    )
                    Text(
                        text = book.name,
                        color = style.colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = listOf(book.author, book.originName).filter { it.isNotBlank() }.joinToString(" · "),
                        color = style.colors.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WebResultPreview(results: List<AiWebResultUi>, style: AiComposeStyle) {
    if (results.isEmpty()) {
        EmptyPreviewText("暂无搜索结果", style)
        return
    }
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.heightIn(max = 380.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results, key = { it.url.ifBlank { it.title } }) { item ->
            Surface(
                shape = RoundedCornerShape(style.metrics.chipRadius),
                color = style.colors.background.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(style.metrics.strokeWidth, style.colors.stroke),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                        }.onFailure {
                            context.toastOnUi(it.localizedMessage ?: it.javaClass.simpleName)
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = item.title.ifBlank { item.url },
                        color = style.colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.url.isNotBlank()) {
                        Text(
                            text = item.url,
                            color = style.colors.accent,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    if (item.content.isNotBlank()) {
                        Text(
                            text = item.content,
                            color = style.colors.secondaryText,
                            fontSize = 12.5.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageResultPreview(
    images: List<AiImageResultUi>,
    style: AiComposeStyle,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) {
        EmptyPreviewText("暂无图片结果", style)
        return
    }
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(images, key = { it.imageId.ifBlank { it.image } }) { image ->
            Surface(
                shape = RoundedCornerShape(style.metrics.chipRadius),
                color = style.colors.background.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(style.metrics.strokeWidth, style.colors.stroke),
                modifier = Modifier
                    .width(170.dp)
                    .clickable {
                        if (image.imageId.isNotBlank()) {
                            (context as? androidx.appcompat.app.AppCompatActivity)
                                ?.showDialogFragment(AiImagePreviewDialog(image.imageId))
                        }
                        onDismiss()
                    }
            ) {
                Column(modifier = Modifier.padding(9.dp)) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),
                        factory = {
                            ImageView(it).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }
                        },
                        update = {
                            io.legado.app.help.glide.ImageLoader.load(context, image.image)
                                .error(io.legado.app.R.drawable.image_loading_error)
                                .into(it)
                        },
                        onRelease = { it.releaseComposeImage() }
                    )
                    Text(
                        text = image.prompt.ifBlank { "图片已生成" },
                        color = style.colors.secondaryText,
                        fontSize = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GenericToolPreview(raw: String, style: AiComposeStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = raw.ifBlank { "无结果" },
            color = style.colors.secondaryText,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun EmptyPreviewText(text: String, style: AiComposeStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = style.colors.secondaryText,
            fontSize = 13.sp
        )
    }
}
