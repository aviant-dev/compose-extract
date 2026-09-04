package com.github.aviantdev.composeextract.actions

import com.github.aviantdev.composeextract.core.analysis.SelectionInspector
import com.github.aviantdev.composeextract.core.analysis.VariableUsageVisitor
import com.github.aviantdev.composeextract.core.generator.CallSiteReplacer
import com.github.aviantdev.composeextract.core.generator.ComposableGenerator
import com.github.aviantdev.composeextract.core.generator.ImportResolver
import com.github.aviantdev.composeextract.core.psi.isComposable
import com.github.aviantdev.composeextract.ui.ExtractComposableDialog
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ExtractComposableActionHandler {

  private val inspector = SelectionInspector()
  private val generator = ComposableGenerator()
  private val importResolver = ImportResolver()
  private val callSiteReplacer = CallSiteReplacer()

  fun isAvailable(file: KtFile, editor: Editor): Boolean {
    val targetFunction = findTargetFunction(file, editor) ?: return false
    if (!targetFunction.isComposable()) return false

    val selectedElements = inspector.inspect(file, editor)
    return selectedElements.isNotEmpty()
  }

  fun execute(project: Project, file: KtFile, editor: Editor) {
    val currentFunction = findTargetFunction(file, editor) ?: return
    if (!currentFunction.isComposable()) return

    val selectedElements = inspector.inspect(file, editor)
    if (selectedElements.isEmpty()) return

    val dialog = ExtractComposableDialog(project)
    if (!dialog.showAndGet()) return

    val config = dialog.config ?: return
    val externalVars = VariableUsageVisitor(selectedElements).analyze()
    val psiFactory = KtPsiFactory(project)

    WriteCommandAction.runWriteCommandAction(project) {
      if (!file.isValid || !currentFunction.isValid) return@runWriteCommandAction

      importResolver.resolveAndInjectImports(
        psiFactory = psiFactory,
        targetFile = file,
        parameters = externalVars,
        includeModifier = config.includeModifier
      )

      val newFunction = generator.generateComposable(
        psiFactory = psiFactory,
        functionName = config.composableName,
        visibility = config.visibility,
        parameters = externalVars,
        bodyElements = selectedElements,
        includeModifier = config.includeModifier
      )

      currentFunction.parent.addAfter(newFunction, currentFunction)

      callSiteReplacer.replaceCallSite(
        psiFactory = psiFactory,
        functionName = config.composableName,
        parameters = externalVars,
        selectedElements = selectedElements
      )
    }
  }

  private fun findTargetFunction(file: KtFile, editor: Editor): KtFunction? {
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
}
