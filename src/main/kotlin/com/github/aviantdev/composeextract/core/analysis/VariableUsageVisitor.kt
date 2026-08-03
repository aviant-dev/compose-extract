package com.github.aviantdev.composeextract.core.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgumentName

/**
 * Analyzes a list of PSI elements to detect outer-scope variables
 * (parameters, local properties, callbacks) that must be hoisted as parameters.
 */
class VariableUsageVisitor(
  private val selectedElements: List<PsiElement>
) : KtTreeVisitorVoid() {

  private val externalVariables = linkedSetOf<KtNamedDeclaration>()

  // Traverses the target PSI elements and returns the set of external variable declarations
  fun analyze(): Set<KtNamedDeclaration> {
    externalVariables.clear()
    for (element in selectedElements) {
      element.accept(this)
    }
    return externalVariables
  }

  override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
    super.visitSimpleNameExpression(expression)

    if (expression.parent is KtValueArgumentName) {
      return
    }

    val resolvedTarget = expression.mainReference.resolve() as? KtNamedDeclaration ?: return

    if (isHoistableVariable(resolvedTarget) && !isDeclaredInsideSelection(resolvedTarget)) {
      externalVariables.add(resolvedTarget)
    }
  }

  // Determines whether a declaration is a candidate for parameter hoisting (lambda parameters or local variables)
  private fun isHoistableVariable(declaration: KtNamedDeclaration): Boolean =
    when (declaration) {
      is KtParameter -> true
      is KtProperty -> declaration.isLocal
      is KtDestructuringDeclarationEntry -> true
      else -> false
    }

  // Checks if the variable declaration is contained within the selection bounds
  private fun isDeclaredInsideSelection(declaration: PsiElement): Boolean =
    selectedElements.any { selected ->
      PsiTreeUtil.isAncestor(selected, declaration, false)
    }
}