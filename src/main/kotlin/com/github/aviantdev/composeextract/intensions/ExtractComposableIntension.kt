package com.github.aviantdev.composeextract.intensions

import com.github.aviantdev.composeextract.core.psi.createComposableFunction
import com.github.aviantdev.composeextract.core.psi.isComposable
import com.github.aviantdev.composeextract.core.psi.normalizeComposableAnnotation
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ExtractComposableIntension : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    // get context
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

    // get context from the position of the cursor
    val offset = editor.caretModel.offset
    val element = psiFile.findElementAt(offset)
    val currentFunction = element?.getParentOfType<KtFunction>(strict = false)

    // check and create a new function
    if (currentFunction != null && currentFunction.isComposable()) {
      val psiFactory = KtPsiFactory(project)

      WriteCommandAction.runWriteCommandAction(project, "Extract Composable", null, Runnable {
        val newFunction = createComposableFunction(
          psiFactory = psiFactory,
          targetFile = psiFile as KtFile,
          functionName = "ExtractedWidget",
          bodyText = "// Generated code"
        )

        currentFunction.normalizeComposableAnnotation(psiFactory)

        currentFunction.parent.addAfter(newFunction, currentFunction)
      })

    }
  }
}