package com.github.aviantdev.composeextract.intentions

import com.github.aviantdev.composeextract.actions.ExtractComposableActionHandler
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile

class ExtractComposableIntention : PsiElementBaseIntentionAction() {

  override fun getText(): String = "Extract Composable function"

  override fun getFamilyName(): String = "ComposeExtract"

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    val psiFile = element.containingFile as? KtFile ?: return false
    return editor != null && ExtractComposableActionHandler.isAvailable(psiFile, editor)
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val psiFile = element.containingFile as? KtFile ?: return
    if (editor == null) return
    ExtractComposableActionHandler.execute(project, psiFile, editor)
  }

  override fun startInWriteAction(): Boolean = false
}
