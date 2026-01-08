package io.legado.app.ui.widget.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyMessageView(
    message: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    faces: List<String> = listOf(
        "(；′⌒`)", "(つ﹏⊂)", "(•̀ᴗ•́)و", "(๑•́ ₃ •̀๑)",
        "(눈‸눈)", "(ಥ﹏ಥ)", "(｡•́︿•̀｡)"
    ),
    faceTextSize: TextUnit = 32.sp,
    onFaceClick: (() -> Unit)? = null
) {
    var currentFace by remember { mutableStateOf(faces.random()) }

    Column(
        modifier = modifier
            .wrapContentSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = isLoading,
            label = "LoadingStateAnimation"
        ) { loading ->
            if (loading) {
                ContainedLoadingIndicator()
            } else {
                AnimatedTextLine(
                    text = currentFace,
                    fontSize = faceTextSize,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable {
                            currentFace = faces.random()
                            onFaceClick?.invoke()
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedTextLine(
            text = message,
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            modifier = Modifier.widthIn(max = 240.dp)
        )
    }
}

@Composable
fun EmptyMessageView(
    @StringRes messageResId: Int,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    faces: List<String> = listOf(
        "(；′⌒`)", "(つ﹏⊂)", "(•̀ᴗ•́)و", "(๑•́ ₃ •̀๑)",
        "(눈‸눈)", "(ಥ﹏ಥ)", "(｡•́︿•̀｡)"
    ),
    faceTextSize: TextUnit = 32.sp,
    onFaceClick: (() -> Unit)? = null
) {
    val message = stringResource(id = messageResId)
    EmptyMessageView(
        message = message,
        modifier = modifier,
        isLoading = isLoading,
        faces = faces,
        faceTextSize = faceTextSize,
        onFaceClick = onFaceClick
    )
}