package io.legado.app.ui.main.ai.compose

import android.net.Uri
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.widget.compose.releaseComposeImage
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.ai.AiImageGalleryManager
import io.legado.app.help.glide.ImageLoader
import io.legado.app.ui.book.SearchBookOpenHelper
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.parseToUri
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlin.math.min

@Composable
internal fun AiCopyTextButton(
    text: String,
    style: AiComposeStyle,
    modifier: Modifier = Modifier,
    label: String = "复制"
) {
    if (text.isBlank()) return
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Text(
        text = label,
        color = style.colors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(style.metrics.chipRadius))
            .background(style.colors.accent.copy(alpha = 0.09f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                clipboardManager.setText(AnnotatedString(text))
                context.toastOnUi(R.string.copy_complete)
            }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
internal fun AiComposeMarkdownText(
    content: String,
    style: AiComposeStyle,
    modifier: Modifier = Modifier,
    color: Color = style.colors.primaryText
) {
    val normalizedContent = remember(content) { normalizeAiMarkdownContent(content) }
    val blocks = remember(normalizedContent) { parseAiMarkdownBlocks(normalizedContent) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is AiSharedMarkdownBlock.Paragraph -> AiMarkdownInlineContent(
                    text = block.text,
                    style = style,
                    color = color
                )
                is AiSharedMarkdownBlock.Heading -> AiMarkdownRichText(
                    text = block.text,
                    style = style,
                    color = color,
                    fontSize = when {
                        block.level <= 2 -> 17.sp
                        block.level == 3 -> 16.sp
                        else -> 15.sp
                    },
                    lineHeight = when {
                        block.level <= 2 -> 24.sp
                        block.level == 3 -> 23.sp
                        else -> 21.sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = if (block.level <= 2) 2.dp else 0.dp)
                )
                is AiSharedMarkdownBlock.Bullet -> AiMarkdownListLine(
                    prefix = "-",
                    text = block.text,
                    style = style,
                    color = color
                )
                is AiSharedMarkdownBlock.Numbered -> AiMarkdownListLine(
                    prefix = "${block.number}.",
                    text = block.text,
                    style = style,
                    color = color
                )
                is AiSharedMarkdownBlock.Quote -> AiMarkdownQuote(
                    text = block.text,
                    style = style
                )
                is AiSharedMarkdownBlock.Table -> AiMarkdownTable(block, style)
                is AiSharedMarkdownBlock.Code -> AiMarkdownCodeBlock(
                    text = block.text,
                    style = style
                )
                AiSharedMarkdownBlock.Divider -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(style.colors.stroke)
                )
            }
        }
    }
}

@Composable
internal fun AiMarkdownRichText(
    text: String,
    style: AiComposeStyle,
    modifier: Modifier = Modifier,
    color: Color = style.colors.primaryText,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 21.sp,
    fontWeight: FontWeight? = null
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, color, style.colors.accent) {
        buildAiInlineMarkdown(text, color, style.colors.accent)
    }
    if (annotated.getStringAnnotations(AI_LINK_TAG, 0, annotated.length).isEmpty()) {
        SelectionContainer {
            Text(
                text = annotated,
                color = color,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }
    } else {
        ClickableText(
            text = annotated,
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = fontWeight
            ),
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations(AI_LINK_TAG, offset, offset)
                    .firstOrNull()
                    ?.item
                    ?.let { url ->
                        if (url.startsWith(searchBookScheme)) {
                            openSearchBookLink(context, url)
                        } else {
                            runCatching { uriHandler.openUri(url) }
                        }
                    }
            }
        )
    }
}

@Composable
private fun AiMarkdownListLine(
    prefix: String,
    text: String,
    style: AiComposeStyle,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = prefix,
            color = style.colors.secondaryText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier.width(24.dp)
        )
        AiMarkdownInlineContent(
            text = text,
            style = style,
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AiMarkdownQuote(
    text: String,
    style: AiComposeStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(style.colors.accent.copy(alpha = 0.46f))
        )
        AiMarkdownInlineContent(
            text = text,
            style = style,
            color = style.colors.secondaryText,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
    }
}

@Composable
private fun AiMarkdownCodeBlock(
    text: String,
    style: AiComposeStyle
) {
    Surface(
        shape = RoundedCornerShape(style.metrics.chipRadius),
        color = style.colors.processSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                AiCopyTextButton(
                    text = text,
                    style = style,
                    label = "复制代码"
                )
            }
            SelectionContainer {
                Text(
                    text = text,
                    color = style.colors.primaryText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun AiMarkdownInlineContent(
    text: String,
    style: AiComposeStyle,
    modifier: Modifier = Modifier,
    color: Color = style.colors.primaryText
) {
    val pieces = remember(text) { parseMarkdownInlinePieces(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        pieces.forEach { piece ->
            when (piece) {
                is AiSharedMarkdownInlinePiece.Text -> {
                    if (piece.text.isNotBlank()) {
                        AiMarkdownRichText(
                            text = piece.text.trim(),
                            style = style,
                            color = color
                        )
                    }
                }
                is AiSharedMarkdownInlinePiece.Image -> AiMarkdownImage(
                    image = piece,
                    style = style,
                    compact = false
                )
            }
        }
    }
}

@Composable
private fun AiMarkdownTable(table: AiSharedMarkdownBlock.Table, style: AiComposeStyle) {
    Surface(
        shape = RoundedCornerShape(style.metrics.chipRadius),
        color = style.colors.processSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp)
        ) {
            val columnCount = table.headers.size.coerceAtLeast(1)
            val cellWidth = when (columnCount) {
                1 -> maxWidth
                2 -> maxWidth / 2
                else -> 132.dp
            }
            val rows = table.rows.map { normalizeTableRow(it, columnCount) }
            val totalWidth = cellWidth * columnCount
            val rawHeight = 48.dp + 58.dp * rows.size
            val scale = if (totalWidth > maxWidth) {
                min(1f, maxWidth.value / totalWidth.value)
            } else {
                1f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rawHeight * scale)
            ) {
                Column(
                    modifier = Modifier
                        .width(totalWidth)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                ) {
                    AiMarkdownTableRow(
                        cells = normalizeTableRow(table.headers, columnCount),
                        style = style,
                        header = true,
                        cellWidth = cellWidth,
                        minHeight = 48.dp
                    )
                    rows.forEach { row ->
                        AiMarkdownTableRow(
                            cells = row,
                            style = style,
                            header = false,
                            cellWidth = cellWidth,
                            minHeight = 58.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMarkdownTableRow(
    cells: List<String>,
    style: AiComposeStyle,
    header: Boolean,
    cellWidth: androidx.compose.ui.unit.Dp,
    minHeight: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .width(cellWidth)
                    .fillMaxHeight()
                    .heightIn(min = minHeight)
                    .background(if (header) style.colors.accent.copy(alpha = 0.10f) else Color.Transparent)
                    .border(style.metrics.strokeWidth, style.colors.stroke)
                    .padding(horizontal = 9.dp, vertical = 8.dp)
            ) {
                val pieces = remember(cell) { parseMarkdownInlinePieces(cell) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pieces.forEach { piece ->
                        when (piece) {
                            is AiSharedMarkdownInlinePiece.Text -> {
                                if (piece.text.isNotBlank()) {
                                    AiMarkdownRichText(
                                        text = piece.text.trim(),
                                        style = style,
                                        color = if (header) style.colors.accent else style.colors.primaryText,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = if (header) FontWeight.SemiBold else null
                                    )
                                }
                            }
                            is AiSharedMarkdownInlinePiece.Image -> AiMarkdownImage(
                                image = piece,
                                style = style,
                                compact = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMarkdownImage(
    image: AiSharedMarkdownInlinePiece.Image,
    style: AiComposeStyle,
    compact: Boolean
) {
    val context = LocalContext.current
    val imageUrl = remember(image.url) { normalizeAiMarkdownImageUrl(image.url) }
    val size = if (compact) 52.dp else 180.dp
    Surface(
        modifier = Modifier
            .width(size)
            .height(size)
            .clip(RoundedCornerShape(style.metrics.chipRadius))
            .clickable {
                (context as? AppCompatActivity)
                    ?.showDialogFragment(PhotoDialog(imageUrl))
            },
        shape = RoundedCornerShape(style.metrics.chipRadius),
        color = style.colors.background.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                ImageView(it).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = image.alt
                }
            },
            update = { imageView ->
                if (imageView.tag != imageUrl) {
                    imageView.tag = imageUrl
                    ImageLoader.load(context, imageUrl)
                        .error(R.drawable.image_loading_error)
                        .into(imageView)
                }
            },
            onRelease = { it.releaseComposeImage() }
        )
    }
}

private sealed class AiSharedMarkdownBlock {
    data class Paragraph(val text: String) : AiSharedMarkdownBlock()
    data class Heading(val level: Int, val text: String) : AiSharedMarkdownBlock()
    data class Bullet(val text: String) : AiSharedMarkdownBlock()
    data class Numbered(val number: Int, val text: String) : AiSharedMarkdownBlock()
    data class Quote(val text: String) : AiSharedMarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : AiSharedMarkdownBlock()
    data class Code(val text: String) : AiSharedMarkdownBlock()
    data object Divider : AiSharedMarkdownBlock()
}

private sealed class AiSharedMarkdownInlinePiece {
    data class Text(val text: String) : AiSharedMarkdownInlinePiece()
    data class Image(val alt: String, val url: String) : AiSharedMarkdownInlinePiece()
}

private fun parseAiMarkdownBlocks(content: String): List<AiSharedMarkdownBlock> {
    val blocks = mutableListOf<AiSharedMarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val lines = content.replace("\r\n", "\n").replace('\r', '\n').lines()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += AiSharedMarkdownBlock.Paragraph(paragraph.joinToString("\n").trim())
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val raw = lines[index]
        val line = raw.trimEnd()
        val trimmed = line.trim()
        if (trimmed.isBlank()) {
            flushParagraph()
            index++
            continue
        }
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                codeLines += lines[index]
                index++
            }
            if (index < lines.size) index++
            blocks += AiSharedMarkdownBlock.Code(codeLines.joinToString("\n"))
            continue
        }
        val headingMatch = headingRegex.matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks += AiSharedMarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length.coerceIn(1, 6),
                text = headingMatch.groupValues[2].trim()
            )
            index++
            continue
        }
        if (dividerRegex.matches(trimmed)) {
            flushParagraph()
            blocks += AiSharedMarkdownBlock.Divider
            index++
            continue
        }
        val quoteMatch = quoteRegex.matchEntire(trimmed)
        if (quoteMatch != null) {
            flushParagraph()
            blocks += AiSharedMarkdownBlock.Quote(quoteMatch.groupValues[1].trim())
            index++
            continue
        }
        val bulletMatch = bulletRegex.matchEntire(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            blocks += AiSharedMarkdownBlock.Bullet(bulletMatch.groupValues[1].trim())
            index++
            continue
        }
        val numberedMatch = numberedRegex.matchEntire(trimmed)
        if (numberedMatch != null) {
            flushParagraph()
            blocks += AiSharedMarkdownBlock.Numbered(
                number = numberedMatch.groupValues[1].toIntOrNull() ?: 1,
                text = numberedMatch.groupValues[2].trim()
            )
            index++
            continue
        }
        if (looksLikeMarkdownTable(lines, index)) {
            flushParagraph()
            val tableLines = mutableListOf(line)
            index++
            while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                tableLines += lines[index].trimEnd()
                index++
            }
            parseMarkdownTable(tableLines)?.let { table ->
                blocks += table
            } ?: run {
                blocks += AiSharedMarkdownBlock.Paragraph(tableLines.joinToString("\n"))
            }
            continue
        }
        paragraph += line
        index++
    }
    flushParagraph()
    return blocks.ifEmpty { listOf(AiSharedMarkdownBlock.Paragraph(" ")) }
}

private fun parseMarkdownTable(lines: List<String>): AiSharedMarkdownBlock.Table? {
    if (lines.size < 2 || !markdownTableSeparatorRegex.matches(lines[1].trim())) return null
    val headers = parseMarkdownTableRow(lines[0])
    if (headers.isEmpty()) return null
    val rows = lines.drop(2)
        .map { parseMarkdownTableRow(it) }
        .filter { row -> row.any { it.isNotBlank() } }
    return AiSharedMarkdownBlock.Table(headers, rows)
}

private fun parseMarkdownTableRow(line: String): List<String> {
    return line.trim()
        .trim('|')
        .split('|')
        .map { it.trim() }
}

private fun normalizeTableRow(row: List<String>, size: Int): List<String> {
    return when {
        row.size == size -> row
        row.size > size -> row.take(size)
        else -> row + List(size - row.size) { "" }
    }
}

private fun parseMarkdownInlinePieces(text: String): List<AiSharedMarkdownInlinePiece> {
    val pieces = mutableListOf<AiSharedMarkdownInlinePiece>()
    var index = 0
    markdownImageRegex.findAll(text).forEach { match ->
        if (match.range.first > index) {
            pieces += AiSharedMarkdownInlinePiece.Text(text.substring(index, match.range.first))
        }
        val alt = match.groupValues[1].trim()
        val url = match.groupValues[2].trim()
        if (url.isNotBlank()) {
            pieces += AiSharedMarkdownInlinePiece.Image(alt = alt, url = url)
        }
        index = match.range.last + 1
    }
    if (index < text.length) {
        pieces += AiSharedMarkdownInlinePiece.Text(text.substring(index))
    }
    return pieces.ifEmpty { listOf(AiSharedMarkdownInlinePiece.Text(text)) }
}

private fun normalizeAiMarkdownContent(content: String): String {
    val markdownNormalized = markdownImageRegex.replace(content) { match ->
        val alt = match.groupValues[1]
        val url = normalizeAiMarkdownImageUrl(match.groupValues[2])
        "![${alt}](${url})"
    }
    return htmlImageSrcRegex.replace(markdownNormalized) { match ->
        val quote = match.groupValues[1]
        val url = normalizeAiMarkdownImageUrl(match.groupValues[2])
        """src=$quote$url$quote"""
    }
}

private fun normalizeAiMarkdownImageUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return value
    AiImageGalleryManager.resolveImageFile(value)?.let { return it.toURI().toString() }
    if (value.startsWith("file://", true)) return value
    if (value.startsWith("/", true)) return value.parseToUri().toString()
    return value
}

private fun buildAiInlineMarkdown(text: String, color: Color, accent: Color): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val match = inlineMarkdownRegex.find(text, index)
            if (match == null) {
                append(text.substring(index))
                break
            }
            if (match.range.first > index) {
                append(text.substring(index, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith("`") -> withStyle(
                    SpanStyle(
                        color = color,
                        background = accent.copy(alpha = 0.10f),
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(token.trim('`'))
                }
                token.startsWith("[") -> {
                    val label = match.groupValues[2]
                    val url = match.groupValues[3]
                    pushStringAnnotation(AI_LINK_TAG, url)
                    withStyle(
                        SpanStyle(
                            color = accent,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(label)
                    }
                    pop()
                }
                token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(token.removeSurrounding("**"))
                }
                else -> append(token)
            }
            index = match.range.last + 1
        }
    }
}

private fun openSearchBookLink(context: android.content.Context, url: String) {
    val uri = Uri.parse(url)
    val book = SearchBook(
        name = uri.getQueryParameter("name").orEmpty(),
        author = uri.getQueryParameter("author").orEmpty(),
        bookUrl = uri.getQueryParameter("bookUrl").orEmpty(),
        origin = uri.getQueryParameter("origin").orEmpty(),
        originName = uri.getQueryParameter("originName").orEmpty()
    )
    if (book.bookUrl.isBlank() || book.origin.isBlank()) return
    SearchBookOpenHelper.open(
        context,
        book,
        uri.getQueryParameter("target") == "video"
    )
}

private fun looksLikeMarkdownTable(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    return lines[index].contains('|') && markdownTableSeparatorRegex.matches(lines[index + 1].trim())
}

private const val AI_LINK_TAG = "ai_link"
private const val searchBookScheme = "legado-search-book://"
private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
private val dividerRegex = Regex("^([-*_]\\s*){3,}$")
private val quoteRegex = Regex("^>\\s?(.+)$")
private val bulletRegex = Regex("^[-*+]\\s+(.+)$")
private val numberedRegex = Regex("^(\\d+)\\.\\s+(.+)$")
private val markdownTableSeparatorRegex = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$")
private val markdownImageRegex = Regex("!\\[([^\\]]*)]\\(([^)]+)\\)")
private val htmlImageSrcRegex = Regex("""src\s*=\s*(['"])([^'"]+)\1""", RegexOption.IGNORE_CASE)
private val inlineMarkdownRegex = Regex("`([^`]+)`|\\[([^\\]]+)]\\(([^)]+)\\)|\\*\\*([^*]+)\\*\\*")
