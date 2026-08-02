package com.github.aviantdev.composeextract.intentions

import com.github.aviantdev.composeextract.core.psi.createComposableFunction
import com.github.aviantdev.composeextract.core.psi.isComposable
import com.github.aviantdev.composeextract.core.psi.normalizeComposableAnnotation
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ExtractComposableIntention : PsiElementBaseIntentionAction() {

  override fun getText(): String = "Extract Composable function"

  override fun getFamilyName(): String = "ComposeExtract"

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    val targetFunction = element.getParentOfType<KtFunction>(strict = false) ?: return false
    return targetFunction.isComposable()
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val psiFile = element.containingFile as? KtFile ?: return
    val currentFunction = element.getParentOfType<KtFunction>(strict = false) ?: return
    val psiFactory = KtPsiFactory(project)

    val newFunction = createComposableFunction(
      psiFactory = psiFactory,
      targetFile = psiFile,
      functionName = "ExtractedWidget",
      bodyText = "// Generated code"
    )

    currentFunction.normalizeComposableAnnotation(psiFactory)
    currentFunction.parent.addAfter(newFunction, currentFunction)
  }

  override fun startInWriteAction(): Boolean = true
}