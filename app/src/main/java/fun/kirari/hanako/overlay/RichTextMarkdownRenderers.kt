package `fun`.kirari.hanako.overlay

import android.content.Intent
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

@Composable
fun MarkdownLatexText(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current
) {
    var parsed by remember(content) {
        mutableStateOf(parseMarkdown(content))
    }
    val currentContent by rememberUpdatedState(content)

    LaunchedEffect(content) {
        parsed = withContext(Dispatchers.Default) {
            parseMarkdown(currentContent)
        }
    }

    ProvideTextStyle(style) {
        Column(modifier = modifier) {
            parsed.astTree.children.fastForEach { child ->
                MarkdownNode(
                    node = child,
                    content = parsed.preprocessed
                )
            }
        }
    }
}

@Composable
private fun MarkdownNode(
    node: ASTNode,
    content: String
) {
    when (node.type) {
        MarkdownElementTypes.MARKDOWN_FILE -> node.children.fastForEach { child -> MarkdownNode(child, content) }
        MarkdownElementTypes.PARAGRAPH -> Paragraph(node = node, content = content)
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6 -> Heading(node = node, content = content)
        MarkdownElementTypes.UNORDERED_LIST -> ListNode(node = node, content = content, ordered = false)
        MarkdownElementTypes.ORDERED_LIST -> ListNode(node = node, content = content, ordered = true)
        MarkdownElementTypes.BLOCK_QUOTE -> QuoteBlock(node = node, content = content)
        MarkdownTokenTypes.HORIZONTAL_RULE -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            thickness = 1.dp
        )
        MarkdownElementTypes.INLINE_LINK -> LinkNode(node = node, content = content)
        GFMElementTypes.INLINE_MATH -> LatexInline(latex = node.getText(content), modifier = Modifier.padding(horizontal = 1.dp))
        GFMElementTypes.BLOCK_MATH -> LatexBlock(latex = node.getText(content), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        MarkdownElementTypes.CODE_SPAN -> CodeSpan(node = node, content = content)
        MarkdownElementTypes.CODE_FENCE -> CodeFence(node = node, content = content)
        MarkdownElementTypes.IMAGE -> ImageNode(node = node, content = content)
        MarkdownElementTypes.HTML_BLOCK -> HtmlBlock(html = node.getText(content), modifier = Modifier.fillMaxWidth())
        GFMTokenTypes.CHECK_BOX -> CheckBox(node = node, content = content)
        GFMElementTypes.TABLE -> MarkdownTable(node = node, content = content)
        MarkdownTokenTypes.TEXT -> Text(text = node.getText(content))
        else -> node.children.fastForEach { child -> MarkdownNode(child, content) }
    }
}

@Composable
private fun Heading(node: ASTNode, content: String) {
    val style = when (node.type) {
        MarkdownElementTypes.ATX_1 -> MaterialTheme.typography.headlineLarge
        MarkdownElementTypes.ATX_2 -> MaterialTheme.typography.headlineMedium
        MarkdownElementTypes.ATX_3 -> MaterialTheme.typography.headlineSmall
        MarkdownElementTypes.ATX_4 -> MaterialTheme.typography.titleLarge
        MarkdownElementTypes.ATX_5 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    val padding = when (node.type) {
        MarkdownElementTypes.ATX_1 -> 16.dp
        MarkdownElementTypes.ATX_2 -> 14.dp
        MarkdownElementTypes.ATX_3 -> 12.dp
        MarkdownElementTypes.ATX_4 -> 10.dp
        MarkdownElementTypes.ATX_5 -> 8.dp
        else -> 6.dp
    }
    ProvideTextStyle(style) {
        node.children.fastForEach { child ->
            if (child.type == MarkdownTokenTypes.ATX_CONTENT) {
                Paragraph(node = child, content = content, trim = true, extraPadding = padding)
            }
        }
    }
}

@Composable
private fun QuoteBlock(node: ASTNode, content: String) {
    Column(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(start = 10.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
    ) {
        node.children.fastForEach { child -> MarkdownNode(node = child, content = content) }
    }
}

@Composable
private fun LinkNode(node: ASTNode, content: String) {
    val linkText = node.findChildRecursive(MarkdownElementTypes.LINK_TEXT)
        ?.findChildRecursive(GFMTokenTypes.GFM_AUTOLINK, MarkdownTokenTypes.TEXT)
        ?.getText(content)
        .orEmpty()
    val linkDest = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getText(content).orEmpty()
    val context = LocalContext.current
    Text(
        text = linkText.ifBlank { linkDest },
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, linkDest.toUri()))
            }
        }
    )
}

@Composable
private fun CodeSpan(node: ASTNode, content: String) {
    Text(
        text = node.getText(content).trim('`'),
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun CodeFence(node: ASTNode, content: String) {
    val code = extractCodeFenceContent(node, content)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun ImageNode(node: ASTNode, content: String) {
    val altText = node.findChildRecursive(MarkdownElementTypes.LINK_TEXT)?.getText(content).orEmpty()
    val imageUrl = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getText(content).orEmpty()
    if (imageUrl.isNotBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = altText,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
private fun CheckBox(node: ASTNode, content: String) {
    val isChecked = node.getText(content).trim().equals("[x]", ignoreCase = true)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isChecked) "☑" else "☐",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MarkdownTable(node: ASTNode, content: String) {
    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rows = node.children.filter { it.type == GFMElementTypes.ROW }
    val columnCount = headerNode?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0
    if (columnCount == 0) return

    val headerCells = headerNode?.children.orEmpty()
        .filter { it.type == GFMTokenTypes.CELL }
        .map { it.getText(content).trim() }

    val dataRows = rows.map { row ->
        row.children.filter { it.type == GFMTokenTypes.CELL }.map { it.getText(content).trim() }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TableRow(
                cells = List(columnCount) { index -> headerCells.getOrNull(index).orEmpty() },
                header = true
            )
            dataRows.forEachIndexed { rowIndex, row ->
                if (rowIndex == 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                }
                TableRow(
                    cells = List(columnCount) { index -> row.getOrNull(index).orEmpty() },
                    header = false
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    header: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .padding(horizontal = 10.dp, vertical = if (header) 10.dp else 8.dp)
            ) {
                if (header) {
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    MarkdownLatexText(content = cell)
                }
            }
        }
    }
}

@Composable
private fun HtmlBlock(
    html: String,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(contentColor)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                textSize = 14f
            }
        },
        update = { textView ->
            textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    )
}

@Composable
private fun ListNode(
    node: ASTNode,
    content: String,
    ordered: Boolean
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        var index = 1
        node.children.fastForEach { child ->
            if (child.type != MarkdownElementTypes.LIST_ITEM) return@fastForEach
            val bullet = if (ordered) {
                child.findChildRecursive(MarkdownTokenTypes.LIST_NUMBER)?.getText(content) ?: "${index++}. "
            } else {
                "• "
            }
            ListItem(node = child, content = content, bullet = bullet)
        }
    }
}

@Composable
private fun ListItem(
    node: ASTNode,
    content: String,
    bullet: String
) {
    val (directContent, nestedLists) = splitListItem(node)
    Column {
        if (directContent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = bullet,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    directContent.fastForEach { child -> MarkdownNode(node = child, content = content) }
                }
            }
        }
        nestedLists.fastForEach { nested -> MarkdownNode(node = nested, content = content) }
    }
}

@Composable
private fun Paragraph(
    node: ASTNode,
    content: String,
    trim: Boolean = false,
    extraPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textStyle = androidx.compose.material3.LocalTextStyle.current
    val inlineContents = remember(node) {
        mutableMapOf<String, InlineTextContent>()
    }

    val annotated = remember(content, node, trim) {
        buildAnnotatedString {
            node.children.fastForEach { child ->
                appendMarkdownNodeContent(
                    node = child,
                    content = content,
                    trim = trim,
                    inlineContents = inlineContents,
                    colorScheme = colorScheme,
                    density = density,
                    style = textStyle
                )
            }
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContents,
        softWrap = true,
        overflow = TextOverflow.Visible,
        modifier = Modifier.padding(bottom = extraPadding)
    )
}

private fun splitListItem(node: ASTNode): Pair<List<ASTNode>, List<ASTNode>> {
    val direct = mutableListOf<ASTNode>()
    val nested = mutableListOf<ASTNode>()
    node.children.fastForEach { child ->
        when (child.type) {
            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> nested.add(child)
            else -> direct.add(child)
        }
    }
    return direct to nested
}

private fun AnnotatedString.Builder.appendMarkdownNodeContent(
    node: ASTNode,
    content: String,
    trim: Boolean,
    inlineContents: MutableMap<String, InlineTextContent>,
    colorScheme: androidx.compose.material3.ColorScheme,
    density: Density,
    style: TextStyle
) {
    when {
        node.type == GFMTokenTypes.GFM_AUTOLINK -> {
            val link = node.getText(content)
            withLink(LinkAnnotation.Url(link)) {
                withStyle(SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    append(link)
                }
            }
        }

        node is LeafASTNode -> {
            val text = node.getText(content).let { if (trim) it.trim() else it }.replace(breakLineRegex, "\n")
            if (node.type !in MarkdownInlineDelimiters) {
                appendTextWithStrongFallback(text)
            }
        }

        node.type == MarkdownElementTypes.EMPH -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.children.fastForEach { appendMarkdownNodeContent(it, content, trim, inlineContents, colorScheme, density, style) }
            }
        }

        node.type == MarkdownElementTypes.STRONG -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.fastForEach { appendMarkdownNodeContent(it, content, trim, inlineContents, colorScheme, density, style) }
            }
        }

        node.type == GFMElementTypes.STRIKETHROUGH -> {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                node.children.fastForEach { appendMarkdownNodeContent(it, content, trim, inlineContents, colorScheme, density, style) }
            }
        }

        node.type == MarkdownElementTypes.INLINE_LINK -> {
            val linkDest = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getText(content).orEmpty()
            val linkText = node.findChildRecursive(MarkdownElementTypes.LINK_TEXT)?.getText(content).orEmpty()
            withLink(LinkAnnotation.Url(linkDest)) {
                withStyle(SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    append(linkText.ifBlank { linkDest })
                }
            }
        }

        node.type == MarkdownElementTypes.AUTOLINK -> {
            val link = node.getText(content)
            withLink(LinkAnnotation.Url(link)) {
                withStyle(SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    append(link)
                }
            }
        }

        node.type == MarkdownElementTypes.CODE_SPAN -> {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colorScheme.surfaceVariant,
                    color = colorScheme.primary
                )
            ) {
                append(" ")
                append(node.getText(content).trim('`'))
                append(" ")
            }
        }

        node.type == GFMElementTypes.INLINE_MATH -> {
            val formula = node.getText(content)
            val key = formula
            val placeholder = runCatching {
                assumeLatexSize(formula, with(density) { style.fontSize.toPx() }).let { rect ->
                    Placeholder(
                        width = TextUnit(rect.width().toFloat(), TextUnitType.Sp),
                        height = TextUnit(rect.height().toFloat(), TextUnitType.Sp),
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                }
            }.getOrNull()
            if (placeholder != null) {
                inlineContents.putIfAbsent(key, InlineTextContent(placeholder = placeholder) {
                    LatexInline(latex = formula)
                })
                appendInlineContent(key, "[math]")
            } else {
                append(formula)
            }
        }

        node.type == MarkdownElementTypes.IMAGE -> {
            val altText = node.findChildRecursive(MarkdownElementTypes.LINK_TEXT)?.getText(content).orEmpty()
            val imageUrl = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getText(content).orEmpty()
            append(altText.ifBlank { imageUrl })
        }

        else -> node.children.fastForEach { appendMarkdownNodeContent(it, content, trim, inlineContents, colorScheme, density, style) }
    }
}

private val MarkdownInlineDelimiters = setOf(
    MarkdownTokenTypes.EMPH,
    MarkdownTokenTypes.CODE_FENCE_START,
    MarkdownTokenTypes.CODE_FENCE_END,
    MarkdownTokenTypes.BACKTICK,
    MarkdownTokenTypes.LBRACKET,
    MarkdownTokenTypes.RBRACKET,
    MarkdownTokenTypes.LPAREN,
    MarkdownTokenTypes.RPAREN
)

private fun AnnotatedString.Builder.appendTextWithStrongFallback(text: String) {
    var cursor = 0
    while (cursor < text.length) {
        val strongStart = text.indexOf("**", cursor)
        if (strongStart < 0) {
            append(text.substring(cursor))
            return
        }

        val strongEnd = text.indexOf("**", strongStart + 2)
        if (strongEnd < 0) {
            append(text.substring(cursor))
            return
        }

        if (strongStart > cursor) {
            append(text.substring(cursor, strongStart))
        }

        val strongContent = text.substring(strongStart + 2, strongEnd)
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(strongContent)
        }

        cursor = strongEnd + 2
    }
}
