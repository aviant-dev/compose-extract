package com.github.aviantdev.composeextract.core.generator

import com.github.aviantdev.composeextract.core.analysis.ComposeModifierInjector
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Replaces extracted selection at call-site with a new function invocation.
 */
class CallSiteReplacer {

  @JvmOverloads
  fun replaceCallSite(
    psiFactory: KtPsiFactory,
    functionName: String,
    parameters: Collection<KtNamedDeclaration>,
    selectedElements: List<PsiElement>,
    callerHasModifier: Boolean = callerHasModifierInScope(selectedElements)
  ): KtCallExpression? {
    val validElements = selectedElements.filterIsInstance<KtElement>()
    if (validElements.isEmpty()) return null

    val arguments = buildCallArguments(parameters, callerHasModifier)

    val callText = if (arguments.size >= 2) {
      "$functionName(\n${arguments.joinToString(",\n")}\n)"
    } else {
      "$functionName(${arguments.joinToString(", ")})"
    }

    val newCallExpression = psiFactory.createExpression(callText) as? KtCallExpression ?: return null
    val firstElement = validElements.first()

    // Delete trailing sibling elements from last to second to preserve PSI validity
    for (i in validElements.size - 1 downTo 1) {
      val elementToDelete = validElements[i]
      if (elementToDelete.isValid && elementToDelete != firstElement) {
        elementToDelete.delete()
      }
    }

    val replacedCall = firstElement.replace(newCallExpression) as? KtCallExpression ?: return null

    // Automatically reformat replaced call according to surrounding indentation & project code style
    val project = firstElement.project
    return CodeStyleManager.getInstance(project).reformat(replacedCall) as? KtCallExpression
  }

  private fun buildCallArguments(
    parameters: Collection<KtNamedDeclaration>,
    callerHasModifier: Boolean
  ): List<String> {
    val requiredArgs = mutableListOf<String>()
    val modifierArg = mutableListOf<String>()
    val lambdaArgs = mutableListOf<String>()

    for (declaration in parameters) {
      val name = declaration.name ?: continue
      val typeText = (declaration as? KtCallableDeclaration)?.typeReference?.text ?: ""

      if (name == ComposeModifierInjector.MODIFIER_PARAM_NAME) {
        if (callerHasModifier) {
          modifierArg.add("$name = $name")
        }
      } else if (typeText.contains("->")) {
        lambdaArgs.add("$name = $name")
      } else {
        requiredArgs.add("$name = $name")
      }
    }

    val result = mutableListOf<String>()
    result.addAll(requiredArgs)
    result.addAll(modifierArg)
    result.addAll(lambdaArgs)
    return result
  }

  companion object {
    // Search for KtNamedFunction to skip anonymous lambdas like Column { ... }
    fun callerHasModifierInScope(selectedElements: List<PsiElement>): Boolean {
      val anchor = selectedElements.firstOrNull() ?: return true

      var current: PsiElement? = anchor
      while (current != null && current !is KtFile) {
        val parent = current.parent ?: break

        // 1. Check local properties/declarations prior to anchor in the same block
        if (parent is KtBlockExpression) {
          for (stmt in parent.statements) {
            if (stmt == current) break
            if (stmt is KtProperty && stmt.name == ComposeModifierInjector.MODIFIER_PARAM_NAME) {
              return true
            }
            if (stmt is KtDestructuringDeclaration) {
              if (stmt.entries.any { it.name == ComposeModifierInjector.MODIFIER_PARAM_NAME }) {
                return true
              }
            }
          }
        }

        // 2. Check function parameters across all enclosing scopes (outer functions, lambdas)
        if (parent is KtFunction) {
          if (parent.valueParameters.any { it.name == ComposeModifierInjector.MODIFIER_PARAM_NAME }) {
            return true
          }
        }

        current = parent
      }

      return false
    }
  }
}