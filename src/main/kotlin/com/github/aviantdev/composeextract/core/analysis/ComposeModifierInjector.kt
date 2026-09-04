package com.github.aviantdev.composeextract.core.analysis

import com.github.aviantdev.composeextract.core.psi.addImportIfMissing
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Manages `modifier: Modifier = Modifier` parameter injection
 * and binds `modifier = modifier` to the root layout element.
 */
class ComposeModifierInjector {

  companion object {
    const val MODIFIER_PARAM_NAME = "modifier"
    const val MODIFIER_PARAM_SIGNATURE = "modifier: Modifier = Modifier"
    const val MODIFIER_PACKAGE = "androidx.compose.ui.Modifier"
  }

  // Auto-imports `androidx.compose.ui.Modifier` if it is not already imported in the target file.
  fun ensureModifierImport(psiFactory: KtPsiFactory, targetFile: KtFile) {
    targetFile.addImportIfMissing(MODIFIER_PACKAGE, psiFactory)
  }

  // Injects `modifier = modifier` into the root layout [KtCallExpression]
  fun injectModifierToRootCall(psiFactory: KtPsiFactory, rootCall: KtCallExpression) {
    val existingModifierArg = rootCall.valueArguments.firstOrNull { arg ->
      isModifierArgument(arg)
    }

    // If existing modifier arg is found, chain it
    if (existingModifierArg != null) {
      val existingExpr = existingModifierArg.getArgumentExpression()?.text ?: return

      // Avoid wrapping if the expression is already 'modifier'
      if (existingExpr == MODIFIER_PARAM_NAME) {
        return
      }

      val chainedExpr = psiFactory.createExpression("$MODIFIER_PARAM_NAME.then($existingExpr)")
      existingModifierArg.getArgumentExpression()?.replace(chainedExpr)
    } else {
      val argList = rootCall.valueArgumentList
      val newArg = psiFactory.createArgument(
        psiFactory.createExpression(MODIFIER_PARAM_NAME),
        Name.identifier(MODIFIER_PARAM_NAME)
      )

      if (argList != null) {
        argList.addArgument(newArg)
      } else {
        val callee = rootCall.calleeExpression
        if (callee != null) {
          val newArgList = psiFactory.createCallArguments("($MODIFIER_PARAM_NAME = $MODIFIER_PARAM_NAME)")
          rootCall.addAfter(newArgList, callee)
        }
      }
    }
  }

  /** Replaces an existing root modifier expression with the generated modifier parameter. */
  fun replaceRootModifierWithParameter(psiFactory: KtPsiFactory, rootCall: KtCallExpression): Boolean {
    val modifierArgument = rootCall.valueArguments.firstOrNull { isModifierArgument(it) } ?: return false
    val modifierParameter = psiFactory.createExpression(MODIFIER_PARAM_NAME)
    modifierArgument.getArgumentExpression()?.replace(modifierParameter)
    return true
  }

  // Checks if a value argument is a modifier (either named or positional)
  private fun isModifierArgument(arg: KtValueArgument): Boolean {
    if (arg.isNamed()) {
      return arg.getArgumentName()?.asName?.asString() == MODIFIER_PARAM_NAME
    }

    val expr = arg.getArgumentExpression() ?: return false
    val text = expr.text.trim()
    return text.startsWith("Modifier") || text.contains("Modifier.") || text == MODIFIER_PARAM_NAME
  }
}
