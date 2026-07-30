package io.legado.app.ui.book.knowledge

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CharacterAvatarCropDialog(
    sourceUri: Uri?,
    show: Boolean = sourceUri != null,
    onDismissRequest: () -> Unit,
    onConfirm: (CharacterAvatarCrop) -> Unit,
) {
    if (sourceUri == null) return

    var zoom by remember(sourceUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(sourceUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(sourceUri) { mutableFloatStateOf(0f) }
    var viewportSize by remember(sourceUri) { mutableFloatStateOf(1f) }
    var imageWidth by remember(sourceUri) { mutableFloatStateOf(1f) }
    var imageHeight by remember(sourceUri) { mutableFloatStateOf(1f) }

    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.character_avatar_crop),
        confirmText = stringResource(R.string.apply),
        onConfirm = {
            onConfirm(
                CharacterAvatarCrop(
                    zoom = zoom,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    viewportSize = viewportSize,
                )
            )
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val density = LocalDensity.current
                    val cropSize = minOf(maxWidth, 280.dp)
                    val sizePx = with(density) { cropSize.toPx() }
                    viewportSize = sizePx
                    val baseScale = maxOf(sizePx / imageWidth, sizePx / imageHeight)
                    val maxOffsetX =
                        ((imageWidth * baseScale * zoom) - sizePx).coerceAtLeast(0f) / 2f
                    val maxOffsetY =
                        ((imageHeight * baseScale * zoom) - sizePx).coerceAtLeast(0f) / 2f
                    val cropContentScale = remember(zoom) {
                        object : ContentScale {
                            override fun computeScaleFactor(
                                srcSize: Size,
                                dstSize: Size
                            ): ScaleFactor {
                                val scale = maxOf(
                                    dstSize.width / srcSize.width,
                                    dstSize.height / srcSize.height,
                                ) * zoom
                                return ScaleFactor(scale, scale)
                            }
                        }
                    }
                    val cropAlignment = remember(offsetX, offsetY) {
                        Alignment { size, space, _ ->
                            IntOffset(
                                x = ((space.width - size.width) / 2f + offsetX).roundToInt(),
                                y = ((space.height - size.height) / 2f + offsetY).roundToInt(),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(cropSize)
                            .clip(CircleShape)
                            .background(LegadoTheme.colorScheme.surfaceContainerHighest)
                            .pointerInput(sourceUri, imageWidth, imageHeight, sizePx) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    val newZoom = (zoom * gestureZoom).coerceIn(1f, 3f)
                                    val newBaseScale =
                                        maxOf(sizePx / imageWidth, sizePx / imageHeight)
                                    val newMaxOffsetX =
                                        ((imageWidth * newBaseScale * newZoom) - sizePx)
                                            .coerceAtLeast(0f) / 2f
                                    val newMaxOffsetY =
                                        ((imageHeight * newBaseScale * newZoom) - sizePx)
                                            .coerceAtLeast(0f) / 2f
                                    zoom = newZoom
                                    offsetX =
                                        (offsetX + pan.x).coerceIn(-newMaxOffsetX, newMaxOffsetX)
                                    offsetY =
                                        (offsetY + pan.y).coerceIn(-newMaxOffsetY, newMaxOffsetY)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = sourceUri,
                            contentDescription = null,
                            modifier = Modifier.size(cropSize),
                            contentScale = cropContentScale,
                            alignment = cropAlignment,
                            onSuccess = {
                                val intrinsicSize = it.painter.intrinsicSize
                                if (
                                    intrinsicSize.width.isFinite() &&
                                    intrinsicSize.height.isFinite() &&
                                    intrinsicSize.width > 0f &&
                                    intrinsicSize.height > 0f
                                ) {
                                    imageWidth = intrinsicSize.width
                                    imageHeight = intrinsicSize.height
                                }
                            },
                        )
                        Box(
                            modifier = Modifier
                                .size(cropSize)
                                .border(2.dp, LegadoTheme.colorScheme.primary, CircleShape),
                        )
                    }
                }
            }
        },
    )
}
