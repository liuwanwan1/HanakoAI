package `fun`.kirari.hanako.overlay

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

internal val markdownFlavor by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}

internal val markdownParser by lazy {
    MarkdownParser(markdownFlavor)
}

internal val inlineLatexRegex = Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)
internal val blockLatexRegex = Regex("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)
internal val codeBlockRegex = Regex("```[\\s\\S]*?```|`[^`\n]*`", RegexOption.DOT_MATCHES_ALL)
internal val breakLineRegex = Regex("(?i)<br\\s*/?>")

internal fun preprocessMarkdown(content: String): String {
    val codeBlocks = mutableListOf<IntRange>()
    codeBlockRegex.findAll(content).forEach { match ->
        codeBlocks.add(match.range)
    }

    fun inCodeBlock(index: Int): Boolean = codeBlocks.any { index in it }

    var result = inlineLatexRegex.replace(content) { match ->
        if (inCodeBlock(match.range.first)) match.value else "${'$'}${match.groupValues[1]}${'$'}"
    }
    result = blockLatexRegex.replace(result) { match ->
        if (inCodeBlock(match.range.first)) match.value else "${'$'}${'$'}${match.groupValues[1]}${'$'}${'$'}"
    }
    return result
}

internal data class MarkdownParseResult(
    val preprocessed: String,
    val astTree: ASTNode
)

internal fun ASTNode.getText(text: String): String = text.substring(startOffset, endOffset)

internal fun ASTNode.findChildRecursive(vararg types: IElementType): ASTNode? {
    if (type in types) return this
    for (child in children) {
        child.findChildRecursive(*types)?.let { return it }
    }
    return null
}

internal fun parseMarkdown(content: String): MarkdownParseResult {
    val preprocessed = preprocessMarkdown(content)
    return MarkdownParseResult(
        preprocessed = preprocessed,
        astTree = markdownParser.buildMarkdownTreeFromString(preprocessed)
    )
}

internal fun extractCodeFenceContent(node: ASTNode, content: String): String {
    val startIndex = node.children.indexOfFirst { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    if (startIndex == -1) return node.getText(content)
    val eolElement = node.children.subList(0, startIndex).findLast { it.type == MarkdownTokenTypes.EOL } ?: return node.getText(content)
    val startOffset = eolElement.endOffset
    val endOffset = node.children.findLast { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.endOffset ?: return node.getText(content)
    return content.substring(startOffset, endOffset).trimIndent()
}
