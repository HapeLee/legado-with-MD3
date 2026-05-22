package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compress
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.GSON

@Composable
fun JsonRawEditor(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = label,
                style = LegadoTheme.typography.labelMediumEmphasized
            )
            Row {
                SmallIconButton(
                    onClick = {
                        runCatching {
                            val jsonElement = JsonParser.parseString(value)
                            onValueChange(GSON.toJson(jsonElement))
                        }
                    },
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "格式化"
                )
                SmallIconButton(
                    onClick = {
                        runCatching {
                            val jsonElement = JsonParser.parseString(value)
                            val compactGson = GsonBuilder().create()
                            onValueChange(compactGson.toJson(jsonElement))
                        }
                    },
                    imageVector = Icons.Default.Compress,
                    contentDescription = "压缩"
                )
            }
        }

        AppTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp, max = 400.dp),
            backgroundColor = LegadoTheme.colorScheme.onSheetContent,
            maxLines = 1000
        )
    }
}
