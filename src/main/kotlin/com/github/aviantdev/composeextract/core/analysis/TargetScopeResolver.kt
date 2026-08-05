package com.github.aviantdev.composeextract.core.analysis

import com.github.aviantdev.composeextract.core.model.TargetDestination
import com.github.aviantdev.composeextract.core.model.VisibilityModifier
import com.intellij.openapi.module.ModuleUtilCore
import org.jetbrains.kotlin.psi.KtFile

/**
 * Resolves the appropriate visibility modifier for the extracted Composable.
 */
class TargetScopeResolver {

  // Resolves visibility based on explicit target destination type
  fun resolveVisibility(destination: TargetDestination): VisibilityModifier =
    when (destination) {
      TargetDestination.SAME_FILE -> VisibilityModifier.PRIVATE
      TargetDestination.NEW_FILE_SAME_MODULE -> VisibilityModifier.INTERNAL
      TargetDestination.NEW_FILE_DIFFERENT_MODULE -> VisibilityModifier.PUBLIC
    }

  // Resolves visibility dynamically by comparing source and target KtFile AST nodes and Gradle modules
  fun resolveVisibility(sourceFile: KtFile, targetFile: KtFile): VisibilityModifier {
    val isSameVirtualFile = sourceFile.virtualFile != null && sourceFile.virtualFile == targetFile.virtualFile

    if (sourceFile == targetFile || isSameVirtualFile) {
      return VisibilityModifier.PRIVATE
    }

    val sourceModule = ModuleUtilCore.findModuleForPsiElement(sourceFile)
    val targetModule = ModuleUtilCore.findModuleForPsiElement(targetFile)

    return if (sourceModule != null && targetModule != null && sourceModule == targetModule) {
      VisibilityModifier.INTERNAL
    } else {
      VisibilityModifier.PUBLIC
    }
  }
}