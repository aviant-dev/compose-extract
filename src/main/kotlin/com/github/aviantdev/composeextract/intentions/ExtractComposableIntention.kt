package com.github.aviantdev.composeextract.intentions

import com.github.aviantdev.composeextract.core.analysis.SelectionInspector
import com.github.aviantdev.composeextract.core.analysis.VariableUsageVisitor
import com.github.aviantdev.composeextract.core.generator.CallSiteReplacer
import com.github.aviantdev.composeextract.core.generator.ComposableGenerator
import com.github.aviantdev.composeextract.core.generator.ImportResolver
import com.github.aviantdev.composeextract.core.psi.isComposable
import com.github.aviantdev.composeextract.ui.ExtractComposableDialog
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ExtractComposableIntention : PsiElementBaseIntentionAction() {

  private val inspector = SelectionInspector()
  private val generator = ComposableGenerator()
  private val importResolver = ImportResolver()
  private val callSiteReplacer = CallSiteReplacer()

  override fun getText(): String = "Extract Composable function"

  override fun getFamilyName(): String = "ComposeExtract"

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    if (editor == null) return false
    val targetFunction = element.getParentOfType<KtFunction>(strict = false) ?: return false
    if (!targetFunction.isComposable()) return false

    val selectedElements = inspector.inspect(element.containingFile as? KtFile ?: return false, editor)
    return selectedElements.isNotEmpty()
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    if (editor == null) return
    val dialog = ExtractComposableDialog(project)
    if (!dialog.showAndGet()) return

    val config = dialog.config ?: return

    val psiFile = element.containingFile as? KtFile ?: return
    val currentFunction = element.getParentOfType<KtFunction>(strict = false) ?: return
    if (!currentFunction.isComposable()) return

    val selectedElements = inspector.inspect(psiFile, editor)
    if (selectedElements.isEmpty()) return

    val externalVars = VariableUsageVisitor(selectedElements).analyze()
    val psiFactory = KtPsiFactory(project)

    WriteCommandAction.runWriteCommandAction(project) {
      if (!psiFile.isValid || !currentFunction.isValid) return@runWriteCommandAction

      // Resolve & inject missing imports
      importResolver.resolveAndInjectImports(
        psiFactory = psiFactory,
        targetFile = psiFile,
        parameters = externalVars,
        includeModifier = config.includeModifier
      )

      // Generate new @Composable KtFunction
      val newFunction = generator.generateComposable(
        psiFactory = psiFactory,
        functionName = config.composableName,
        visibility = config.visibility,
        parameters = externalVars,
        bodyElements = selectedElements,
        includeModifier = config.includeModifier
      )

      // Add generated function after current Composable
      currentFunction.parent.addAfter(newFunction, currentFunction)

      // Replace call-site selection with new function invocation
      callSiteReplacer.replaceCallSite(
        psiFactory = psiFactory,
        functionName = config.composableName,
        parameters = externalVars,
        selectedElements = selectedElements
      )
    }
  }

  override fun startInWriteAction(): Boolean = false
}
