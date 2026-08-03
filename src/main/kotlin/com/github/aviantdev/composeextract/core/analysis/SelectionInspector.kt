package com.github.aviantdev.composeextract.core.analysis

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * Parses active editor selection or caret position into target PSI elements.
 */
class SelectionInspector {

  // Inspects the editor to collect target UI elements
  fun inspect(file: KtFile, editor: Editor): List<PsiElement> {
    val selectionModel = editor.selectionModel

    return if (selectionModel.hasSelection()) {
      val range = TextRange(selectionModel.selectionStart, selectionModel.selectionEnd)
      getElementsByRange(file, range)
    } else {
      val caretOffset = editor.caretModel.offset
      getImplicitElementAtCaret(file, caretOffset)
    }
  }

  // Extracts top-level PSI elements fully contained within the highlighted TextRange.
  private fun getElementsByRange(file: KtFile, range: TextRange): List<PsiElement> {
    val startElement = file.findElementAt(range.startOffset) ?: return emptyList()
    val endElement = file.findElementAt((range.endOffset - 1).coerceAtLeast(range.startOffset)) ?: startElement

    val commonParent = PsiTreeUtil.findCommonParent(startElement, endElement) ?: return emptyList()

    // Apply validation on early-return path when commonParent is fully contained
    if (range.contains(commonParent.textRange)) {
      return if (isValidTargetElement(commonParent)) {
        listOf(commonParent)
      } else {
        emptyList()
      }
    }

    // Filter children to include only valid KtExpression nodes
    val result = mutableListOf<PsiElement>()
    var child = commonParent.firstChild
    while (child != null) {
      if (range.contains(child.textRange) && isValidTargetElement(child)) {
        result.add(child)
      }
      child = child.nextSibling
    }

    return result
  }

  // Resolves the innermost enclosing KtCallExpression (e.g., Column, Text, Row) at the caret position
  private fun getImplicitElementAtCaret(file: KtFile, offset: Int): List<PsiElement> {
    val elementAtCaret = file.findElementAt(offset) ?: return emptyList()
    val enclosingCall = PsiTreeUtil.getParentOfType(elementAtCaret, KtCallExpression::class.java, false)

    return if (enclosingCall != null) {
      listOf(enclosingCall)
    } else {
      emptyList()
    }
  }

  private fun isValidTargetElement(element: PsiElement): Boolean =
    element is KtExpression && element !is PsiWhiteSpace && element !is PsiComment
}