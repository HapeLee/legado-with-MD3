package io.legado.app.ui.widget.components.text

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import splitties.systemservices.clipboardManager
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

// ---- Markdown parser (lazy singleton) ----

private val flavour by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}

private val parser by lazy {
    MarkdownParser(flavour)
}

private data class MarkdownParseResult(
    val content: String,
    val astTree: ASTNode,
)

private fun parseMarkdown(content: String): MarkdownParseResult {
    return MarkdownParseResult(content, parser.buildMarkdownTreeFromString(content))
}

// ---- Main composable ----

/**
 * Markdown renderer using JetBrains intellij-markdown AST parser.
 * Parses on a background thread to avoid blocking the UI during streaming.
 * Uses AppText for proper theme integration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun MarkdownBlock(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LegadoTheme.typography.bodyLarge,
    onClickLink: ((String) -> Unit)? = null,
) {
    var data by remember { mutableStateOf(parseMarkdown(content)) }

    val updatedContent by rememberUpdatedState(content)
    LaunchedEffect(Unit) {
        snapshotFlow { updatedContent }
            .distinctUntilChanged()
            .mapLatest { parseMarkdown(it) }
            .catch { it.printStackTrace() }
            .flowOn(Dispatchers.Default)
            .collect { data = it }
    }

    ProvideTextStyle(style) {
        Column(modifier = modifier.padding(horizontal = 4.dp)) {
            data.astTree.children.fastForEach { child ->
                MarkdownNode(
                    node = child,
                    content = data.content,
                    onClickLink = onClickLink,
                )
            }
        }
    }
}

// ---- AST node dispatcher ----

@Composable
private fun MarkdownNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickLink: ((String) -> Unit)? = null,
    listLevel: Int = 0,
) {
    when (node.type) {
        MarkdownElementTypes.MARKDOWN_FILE -> {
            node.children.fastForEach { child ->
                MarkdownNode(node = child, content = content, modifier = modifier, onClickLink = onClickLink)
            }
        }

        MarkdownElementTypes.PARAGRAPH -> {
            MarkdownParagraph(node = node, content = content, modifier = modifier, onClickLink = onClickLink)
        }

        MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> {
            MarkdownHeading(node = node, content = content, modifier = modifier, onClickLink = onClickLink)
        }

        MarkdownElementTypes.UNORDERED_LIST -> {
            MarkdownUnorderedList(node = node, content = content, modifier = modifier, onClickLink = onClickLink, level = listLevel)
        }

        MarkdownElementTypes.ORDERED_LIST -> {
            MarkdownOrderedList(node = node, content = content, modifier = modifier, onClickLink = onClickLink, level = listLevel)
        }

        GFMTokenTypes.CHECK_BOX -> {
            val isChecked = node.getTextInNode(content).trim() == "[x]"
            AppText(
                text = if (isChecked) "☑ " else "☐ ",
                color = LegadoTheme.colorScheme.primary,
            )
        }

        MarkdownElementTypes.BLOCK_QUOTE -> {
            MarkdownBlockquote(node = node, content = content, modifier = modifier, onClickLink = onClickLink)
        }

        MarkdownElementTypes.INLINE_LINK -> {
            MarkdownInlineLink(node = node, content = content, modifier = modifier, onClickLink = onClickLink)
        }

        MarkdownElementTypes.EMPH -> {
            ProvideTextStyle(TextStyle(fontStyle = FontStyle.Italic)) {
                node.children.fastForEach { child ->
                    MarkdownNode(node = child, content = content, modifier = modifier, onClickLink = onClickLink)
                }
            }
        }

        MarkdownElementTypes.STRONG -> {
            ProvideTextStyle(TextStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.fastForEach { child ->
                    MarkdownNode(node = child, content = content, modifier = modifier, onClickLink = onClickLink)
                }
            }
        }

        GFMElementTypes.STRIKETHROUGH -> {
            AppText(
                text = node.getTextInNode(content),
                style = LocalTextStyle.current.copy(textDecoration = TextDecoration.LineThrough),
                modifier = modifier,
            )
        }

        GFMElementTypes.TABLE -> {
            MarkdownTable(node = node, content = content, modifier = modifier, onClickLink = onClickLink)
        }

        MarkdownTokenTypes.HORIZONTAL_RULE -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = LegadoTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
        }

        MarkdownElementTypes.IMAGE -> {
            val altText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content) ?: ""
            val imageUrl = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content) ?: ""
            if (imageUrl.isNotBlank()) {
                val context = LocalContext.current
                AppText(
                    text = "🖼 $altText",
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.primary,
                    modifier = modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, imageUrl.toUri()))
                    }
                )
            }
        }

        GFMElementTypes.INLINE_MATH -> {
            val formula = node.getTextInNode(content)
            AppText(
                text = formula,
                style = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 0.9.em),
                modifier = modifier.padding(horizontal = 1.dp),
            )
        }

        GFMElementTypes.BLOCK_MATH -> {
            val formula = node.getTextInNode(content)
            AppText(
                text = formula,
                style = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(8.dp),
            )
        }

        MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            AppText(
                text = code,
                style = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 0.9.em,
                ),
                color = LegadoTheme.colorScheme.primary,
            )
        }

        MarkdownElementTypes.CODE_FENCE -> {
            MarkdownCodeFence(node = node, content = content, modifier = modifier)
        }

        MarkdownElementTypes.CODE_BLOCK -> {
            val code = node.getTextInNode(content)
            MarkdownCodeBlock(code = code, language = null, modifier = modifier)
        }

        MarkdownTokenTypes.TEXT -> {
            AppText(text = node.getTextInNode(content), modifier = modifier)
        }

        else -> {
            node.children.fastForEach { child ->
                MarkdownNode(node = child, content = content, modifier = modifier, onClickLink = onClickLink)
            }
        }
    }
}

// ---- Block renderers ----

@Composable
private fun MarkdownParagraph(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickLink: ((String) -> Unit)? = null,
) {
    val colorScheme = LegadoTheme.colorScheme
    val textStyle = LocalTextStyle.current

    FlowRow(
        modifier = modifier.then(
            if (node.nextSibling() != null) Modifier.padding(bottom = with(LocalDensity.current) { LocalTextStyle.current.fontSize.toDp() })
            else Modifier
        )
    ) {
        val annotatedString = remember(content) {
            buildAnnotatedString {
                node.children.fastForEach { child ->
                    appendMarkdownInline(
                        node = child,
                        content = content,
                        colorScheme = colorScheme,
                        onClickLink = onClickLink,
                    )
                }
            }
        }
        AppText(
            text = annotatedString,
            style = textStyle,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun MarkdownHeading(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickLink: ((String) -> Unit)? = null,
) {
    val level = when (node.type) {
        MarkdownElementTypes.ATX_1 -> 1
        MarkdownElementTypes.ATX_2 -> 2
        MarkdownElementTypes.ATX_3 -> 3
        MarkdownElementTypes.ATX_4 -> 4
        MarkdownElementTypes.ATX_5 -> 5
        else -> 6
    }
    val fontSize = when (level) {
        1 -> 24.sp; 2 -> 22.sp; 3 -> 20.sp; 4 -> 18.sp; 5 -> 16.sp; else -> 14.sp
    }
    val verticalPadding = when (level) {
        1 -> 16.dp; 2 -> 14.dp; 3 -> 12.dp; 4 -> 10.dp; 5 -> 8.dp; else -> 6.dp
    }
    val headingStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = fontSize * 1.25f,
    )

    ProvideTextStyle(headingStyle) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            node.children.fastForEach { child ->
                if (child.type == MarkdownTokenTypes.ATX_CONTENT) {
                    MarkdownParagraph(
                        node = child,
                        content = content,
                        modifier = modifier.padding(vertical = verticalPadding),
                        onClickLink = onClickLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownUnorderedList(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickLink: ((String) -> Unit)? = null,
    level: Int = 0,
) {
    val bullet = when (level % 3) {
        0 -> "• "; 1 -> "◦ "; else -> "▪ "
    }
    Column(modifier = modifier.padding(start = (level * 8).dp)) {
        node.children.fastForEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                MarkdownListItem(
                    node = child,
                    content = content,
                    bulletText = bullet,
                    onClickLink = onClickLink,
                    level = level,
                )
            }
        }
    }
}

@Composable
private fun MarkdownOrderedList(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickLink: ((String) -> Unit)? = null,
    level: Int = 0,
) {
    Column(modifier = modifier.padding(start = (level * 8).dp)) {
        var index = 1
        node.children.fastForEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                val numberText = child.findChildOfTypeRecursive(MarkdownTokenTypes.LIST_NUMBER)
                    ?.getTextInNode(content) ?: "$index. "
                MarkdownListItem(
                    node = child,
                    content = content,
                    bulletText = numberText,
                    onClickLink = onClickLink,
                    level = level,
                )
                index++
            }
        }
    }
}

@Composable
private fun MarkdownListItem(
    node: ASTNode,
    content: String,
    bulletText: String,
    onClickLink: ((String) -> Unit)? = null,
    level: Int,
) {
    Column {
        val (directContent, nestedLists) = separateContentAndLists(node)
        if (directContent.isNotEmpty()) {
            Row {
                AppText(
                    text = bulletText,
                    color = LegadoTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    directContent.fastForEach { contentChild ->
                        MarkdownNode(
                            node = contentChild,
                            content = content,
                            onClickLink = onClickLink,
                            listLevel = level,
                        )
                    }
                }
            }
        }
        nestedLists.fastForEach { nestedList ->
            MarkdownNode(
                node = nestedList,
                content = content,
                onClickLink = onClickLink,
                listLevel = level + 1,
            )
        }
    }
}

private fun separateContentAndLists(listItemNode: ASTNode): Pair<List<ASTNode>, List<ASTNode>> {
    val directContent = mutableListOf<ASTNode>()
    val nestedLists = mutableListOf<ASTNode>()
    listItemNode.children.fastForEach { child ->
        when (child.type) {
            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> nestedLists.add(child)
            else -> directContent.add(child)
        }
    }
    return directContent to nestedLists
}

@Composable
private fun MarkdownBlockquote(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickLink: ((String) -> Unit)? = null,
) {
    ProvideTextStyle(LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)) {
        val borderColor = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        val bgColor = LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        Column(
            modifier = Modifier
                .drawWithContent {
                    drawRect(color = bgColor, size = size)
                    drawContent()
                    drawRect(color = borderColor, size = Size(10f, size.height))
                }
                .padding(8.dp)
        ) {
            node.children.fastForEach { child ->
                MarkdownNode(node = child, content = content, onClickLink = onClickLink)
            }
        }
    }
}

@Composable
private fun MarkdownInlineLink(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickLink: ((String) -> Unit)? = null,
) {
    val linkDest = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content) ?: ""
    val linkText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content) ?: linkDest
    val context = LocalContext.current
    AppText(
        text = linkText,
        color = LegadoTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = modifier.clickable {
            if (onClickLink != null) {
                onClickLink(linkDest)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, linkDest.toUri()))
            }
        }
    )
}

@Composable
private fun MarkdownCodeFence(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
) {
    val contentStartIndex = node.children.indexOfFirst { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    if (contentStartIndex == -1) return
    val eolElement = node.children.subList(0, contentStartIndex).findLast { it.type == MarkdownTokenTypes.EOL } ?: return
    val codeContentStartOffset = eolElement.endOffset
    val codeContentEndOffset = node.children.findLast { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.endOffset ?: return
    val code = content.substring(codeContentStartOffset, codeContentEndOffset).trimIndent()
    val language = node.findChildOfTypeRecursive(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content)

    MarkdownCodeBlock(code = code, language = language, modifier = modifier)
}

@Composable
private fun MarkdownCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val collapsedLines = 10
    val codeLines = remember(code) { code.lines() }
    var isExpanded by remember(code) { mutableStateOf(codeLines.size <= collapsedLines) }
    val canCollapse = codeLines.size > collapsedLines

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                BorderStroke(0.5.dp, LegadoTheme.colorScheme.outlineVariant),
                RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!language.isNullOrBlank()) {
                Text(
                    text = language,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = null,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        val clip = android.content.ClipData.newPlainText("code", code)
                        clipboardManager.setPrimaryClip(clip)
                    }
                    .padding(4.dp)
                    .size(16.dp),
                tint = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        Row(
            modifier = Modifier.then(
                if (true) Modifier else Modifier.horizontalScroll(scrollState)
            )
        ) {
            val displayCode = if (isExpanded) code else codeLines.take(collapsedLines).joinToString("\n")
            AppText(
                text = displayCode,
                style = LegadoTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (canCollapse) {
            Text(
                text = if (isExpanded) "▲ 收起" else "▼ 展开 (${codeLines.size} 行)",
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp)
                    .padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun MarkdownTable(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onClickLink: ((String) -> Unit)? = null,
) {
    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rowNodes = node.children.filter { it.type == GFMElementTypes.ROW }
    val columnCount = headerNode?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0
    if (columnCount == 0) return

    val headerCells = headerNode?.children
        ?.filter { it.type == GFMTokenTypes.CELL }
        ?.map { it.getTextInNode(content).trim() } ?: emptyList()

    val rows = rowNodes.map { rowNode ->
        rowNode.children.filter { it.type == GFMTokenTypes.CELL }.map { it.getTextInNode(content).trim() }
    }

    Column(
        modifier = modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(0.5.dp, LegadoTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            headerCells.forEach { cell ->
                AppText(
                    text = cell,
                    style = LegadoTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Rows
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index % 2 == 0) LegadoTheme.colorScheme.surface
                        else LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                row.forEach { cell ->
                    AppText(
                        text = cell,
                        style = LegadoTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---- Inline AnnotatedString builder ----

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendMarkdownInline(
    node: ASTNode,
    content: String,
    colorScheme: io.legado.app.ui.theme.LegadoColorScheme,
    onClickLink: ((String) -> Unit)? = null,
) {
    when {
        node is LeafASTNode -> {
            append(node.getTextInNode(content))
        }

        node.type == MarkdownElementTypes.EMPH -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.children.trimSurrounding(MarkdownTokenTypes.EMPH, 1).fastForEach {
                    appendMarkdownInline(it, content, colorScheme, onClickLink)
                }
            }
        }

        node.type == MarkdownElementTypes.STRONG -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.trimSurrounding(MarkdownTokenTypes.EMPH, 2).fastForEach {
                    appendMarkdownInline(it, content, colorScheme, onClickLink)
                }
            }
        }

        node.type == GFMElementTypes.STRIKETHROUGH -> {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                node.children.trimSurrounding(GFMTokenTypes.TILDE, 2).fastForEach {
                    appendMarkdownInline(it, content, colorScheme, onClickLink)
                }
            }
        }

        node.type == MarkdownElementTypes.INLINE_LINK -> {
            val linkDest = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content) ?: ""
            val linkText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content)
                ?.trim { it == '[' || it == ']' } ?: linkDest
            withLink(LinkAnnotation.Url(linkDest)) {
                withStyle(SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    append(linkText)
                }
            }
        }

        node.type == MarkdownElementTypes.AUTOLINK -> {
            val link = node.children.trimSurrounding(MarkdownTokenTypes.LT, 1).trimSurrounding(MarkdownTokenTypes.GT, 1)
            link.fastForEach { l ->
                withLink(LinkAnnotation.Url(l.getTextInNode(content))) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(l.getTextInNode(content))
                    }
                }
            }
        }

        node.type == MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 0.9.em,
                    color = colorScheme.primary,
                )
            ) {
                append(' ')
                append(code)
                append(' ')
            }
        }

        node.type == GFMElementTypes.INLINE_MATH -> {
            val formula = node.getTextInNode(content)
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.95.em)) {
                append(formula)
            }
        }

        node.type == GFMTokenTypes.GFM_AUTOLINK -> {
            val link = node.getTextInNode(content)
            withLink(LinkAnnotation.Url(link)) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(link)
                }
            }
        }

        else -> {
            node.children.fastForEach { child ->
                appendMarkdownInline(child, content, colorScheme, onClickLink)
            }
        }
    }
}

// ---- Utility extensions ----

private fun ASTNode.getTextInNode(text: String): String {
    return text.substring(startOffset, endOffset)
}

private fun ASTNode.nextSibling(): ASTNode? {
    val siblings = this.parent?.children ?: return null
    for (i in siblings.indices) {
        if (siblings[i] == this && i + 1 < siblings.size) {
            return siblings[i + 1]
        }
    }
    return null
}

private fun ASTNode.findChildOfTypeRecursive(vararg types: IElementType): ASTNode? {
    if (this.type in types) return this
    for (child in children) {
        val result = child.findChildOfTypeRecursive(*types)
        if (result != null) return result
    }
    return null
}

private fun List<ASTNode>.trimSurrounding(type: IElementType, size: Int): List<ASTNode> {
    if (isEmpty() || size <= 0) return this
    var start = 0
    var end = this.size
    var trimmed = 0
    while (start < end && trimmed < size && this[start].type == type) { start++; trimmed++ }
    trimmed = 0
    while (end > start && trimmed < size && this[end - 1].type == type) { end--; trimmed++ }
    return this.subList(start, end)
}

/** Fast forEach without inline overhead for List<ASTNode>. */
private inline fun <T> List<T>.fastForEach(action: (T) -> Unit) {
    for (element in this) action(element)
}
