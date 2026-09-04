package com.github.aviantdev.composeextract.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.kotlin.psi.KtFile

class ExtractComposableAction : AnAction("Extract Composable function") {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val project = event.project
    val editor = event.getData(CommonDataKeys.EDITOR)
    val file = event.getData(CommonDataKeys.PSI_FILE) as? KtFile

    event.presentation.isEnabled = project != null &&
      editor != null &&
      file != null &&
      ExtractComposableActionHandler.isAvailable(file, editor)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return
    val file = event.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return

    ExtractComposableActionHandler.execute(project, file, editor)
  }
}
