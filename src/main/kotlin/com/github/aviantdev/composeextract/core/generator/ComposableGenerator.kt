package com.github.aviantdev.composeextract.core.generator

import com.github.aviantdev.composeextract.core.analysis.ComposeModifierInjector
import com.github.aviantdev.composeextract.core.model.VisibilityModifier
import com.intellij.application.options.CodeStyle
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

class ComposableGenerator(
  private val modifierInjector: ComposeModifierInjector = ComposeModifierInjector()
) {

  private val standardLayouts = setOf(
    "Column", "Row", "Box", "LazyColumn", "LazyRow", "LazyVerticalGrid",
    "Surface", "Scaffold", "Card", "Spacer", "Text", "Button", "Image", "Icon",
    "ConstraintLayout", "FlowRow", "FlowColumn", "Canvas"
  )

  fun generateComposable(
    psiFactory: KtPsiFactory,
    functionName: String,
    visibility: VisibilityModifier,
    parameters: Collection<KtNamedDeclaration>,
    bodyElements: List<PsiElement>,
    includeModifier: Boolean = true
  ): KtFunction {
    // Automatically resolve target file and project code style settings
    val targetFile = bodyElements.firstOrNull()?.containingFile as? KtFile
    val indentSize = targetFile?.let { CodeStyle.getIndentOptions(it).INDENT_SIZE } ?: 4
    val indent = " ".repeat(indentSize)

    val paramSpecs = buildParameterSignatures(parameters, includeModifier)
    val visibilityPrefix = if (visibility == VisibilityModifier.PUBLIC) "" else "${visibility.keyword} "

    // Format parameters dynamically according to project indent size
    val paramString = if (paramSpecs.size >= 2) {
      "\n" + paramSpecs.joinToString(",\n") { "$indent$it" } + "\n"
    } else {
      paramSpecs.joinToString(", ")
    }

    // Clean & re-indent body elements using project indent size
    val rawBody = bodyElements.joinToString("\n") { it.text }.trimIndent()
    val bodyText = rawBody.lines().joinToString("\n") { line ->
      if (line.isBlank()) "" else "$indent$line"
    }

    val functionTemplate = """
@Composable
${visibilityPrefix}fun $functionName($paramString) {
$bodyText
}
""".trimIndent()

    val generatedFunction = psiFactory.createFunction(functionTemplate)

    if (includeModifier) {
      val rootCall = findTargetRootCall(generatedFunction)
      if (rootCall != null && canAcceptModifier(rootCall)) {
        modifierInjector.injectModifierToRootCall(psiFactory, rootCall)
      }
    }

    return generatedFunction
  }

  private fun findTargetRootCall(generatedFunction: KtFunction): KtCallExpression? {
    val statements = generatedFunction.bodyBlockExpression?.statements ?: return null
    return statements.firstOrNull() as? KtCallExpression
  }

  private fun canAcceptModifier(call: KtCallExpression): Boolean {
    val calleeName = call.calleeExpression?.text ?: return false
    if (standardLayouts.contains(calleeName)) return true

    return call.valueArguments.any { arg ->
      arg.getArgumentName()?.asName?.asString() == ComposeModifierInjector.MODIFIER_PARAM_NAME ||
              arg.getArgumentExpression()?.text?.contains("Modifier") == true
    }
  }

  private fun buildParameterSignatures(
    parameters: Collection<KtNamedDeclaration>,
    includeModifier: Boolean
  ): List<String> {
    val requiredParams = mutableListOf<String>()
    val lambdaParams = mutableListOf<String>()

    for (declaration in parameters) {
      val name = declaration.name ?: continue

      if (includeModifier && name == ComposeModifierInjector.MODIFIER_PARAM_NAME) continue

      val typeText = resolveTypeText(declaration)
      val paramSpec = "$name: $typeText"

      if (typeText.contains("->")) {
        lambdaParams.add(paramSpec)
      } else {
        requiredParams.add(paramSpec)
      }
    }

    val result = mutableListOf<String>()
    result.addAll(requiredParams)

    if (includeModifier) {
      result.add(ComposeModifierInjector.MODIFIER_PARAM_SIGNATURE)
    }

    result.addAll(lambdaParams)
    return result
  }

  private fun resolveTypeText(declaration: KtNamedDeclaration): String {
    if (declaration is KtCallableDeclaration) {
      val typeRef = declaration.typeReference
      if (typeRef != null) {
        return typeRef.text
      }
    }

    val initializer = when (declaration) {
      is KtProperty -> declaration.initializer
      is KtParameter -> declaration.defaultValue
      else -> null
    }

    if (initializer != null) {
      return inferTypeFromExpression(initializer)
    }

    return "Any"
  }

  private fun inferTypeFromExpression(expression: PsiElement): String {
    val text = expression.text.trim()

    if (expression is org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS) {
      val typeRef = expression.right
      if (typeRef != null) return typeRef.text
    }

    if (text.startsWith("\"")) return "String"
    if (text == "true" || text == "false") return "Boolean"
    if (text.toIntOrNull() != null) return "Int"
    if (text.toDoubleOrNull() != null) return "Double"
    if (text.endsWith("f") || text.endsWith("F")) return "Float"

    if (text.startsWith("remember") || text.contains("state")) {
      val call = expression as? KtCallExpression
      val typeArg = call?.typeArguments?.firstOrNull()?.text
      if (typeArg != null) return typeArg
    }

    return "Any"
  }
}