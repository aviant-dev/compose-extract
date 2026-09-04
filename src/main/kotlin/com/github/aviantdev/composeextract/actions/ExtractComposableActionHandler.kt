package com.github.aviantdev.composeextract.actions

import com.github.aviantdev.composeextract.core.analysis.SelectionInspector
import com.github.aviantdev.composeextract.core.psi.isComposable
import com.github.aviantdev.composeextract.core.service.ComposeExtractorService
import com.github.aviantdev.composeextract.ui.ExtractComposableDialog
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ExtractComposableActionHandler {

  private val inspector = SelectionInspector()
  private val extractorService = ComposeExtractorService()
  private val lazyScopeReceiverNames = setOf("LazyListScope", "LazyGridScope")
  private val lazyContentCallNames = setOf("item", "items", "itemsIndexed", "stickyHeader")

  fun isAvailable(file: KtFile, editor: Editor): Boolean {
    val targetFunction = findTargetFunction(file, editor) ?: return false
    if (!isInComposableContext(file, editor, targetFunction)) return false

    val selectedElements = inspector.inspect(file, editor)
    return selectedElements.isNotEmpty()
  }

  fun execute(project: Project, file: KtFile, editor: Editor) {
    val currentFunction = findTargetFunction(file, editor) ?: return
    if (!isInComposableContext(file, editor, currentFunction)) return

    val selectedElements = inspector.inspect(file, editor)
    if (selectedElements.isEmpty()) return

    val dialog = ExtractComposableDialog(project)
    if (!dialog.showAndGet()) return

    val config = dialog.config ?: return
    val result = extractorService.extract(project, file, currentFunction, selectedElements, config)
    if (result is ComposeExtractorService.ExtractionResult.Failure) {
      Messages.showErrorDialog(project, result.message, "Extract Composable")
    }
  }

  private fun findTargetFunction(file: KtFile, editor: Editor): KtNamedFunction? {
    val offset = if (editor.selectionModel.hasSelection()) {
      editor.selectionModel.selectionStart
    } else {
      editor.caretModel.offset
    }

    val element = file.findElementAt(offset) ?: return null
    // KtFunction also represents function literals, such as the lambda passed to Column.
    // Extraction must target the enclosing named composable declaration instead.
    return element.getParentOfType<KtNamedFunction>(strict = false)
  }

  private fun isInComposableContext(
    file: KtFile,
    editor: Editor,
    enclosingFunction: KtNamedFunction
  ): Boolean {
    if (enclosingFunction.isComposable()) return true

    val offset = if (editor.selectionModel.hasSelection()) {
      editor.selectionModel.selectionStart
    } else {
      editor.caretModel.offset
    }
    val element = file.findElementAt(offset) ?: return false
    return isInsideLazyScopeContentLambda(element, enclosingFunction)
  }

  private fun isInsideLazyScopeContentLambda(
    element: PsiElement,
    enclosingFunction: KtNamedFunction
  ): Boolean {
    val receiverName = enclosingFunction.receiverTypeReference?.text
      ?.substringAfterLast('.')
      ?.substringBefore('<')
      ?.removeSuffix("?")
    if (receiverName !in lazyScopeReceiverNames) return false

    var current = element
    while (current != enclosingFunction) {
      if (current is KtLambdaExpression) {
        val call = current.getParentOfType<KtCallExpression>(strict = true)
        if (call?.calleeExpression?.text in lazyContentCallNames) return true
      }
      current = current.parent ?: return false
    }
    return false
  }

}
