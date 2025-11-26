package io.legado.app.ui.widget.components

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview(showBackground = true)
@Composable
fun PreviewTextCard() {
    MaterialTheme {
        TextCard(
            text = "v1.0.0"
        )
    }
}

@Composable
fun TextCard(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = colorScheme.tertiaryContainer,
    contentColor: Color = colorScheme.onTertiaryContainer,
    cornerRadius: Dp = 12.dp,
    paddingHorizontal: Dp = 8.dp,
    paddingVertical: Dp = 0.dp,
    textSize: TextUnit = 10.sp,
    bold: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(cornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = paddingHorizontal,
                vertical = paddingVertical
            ),
            fontSize = textSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
