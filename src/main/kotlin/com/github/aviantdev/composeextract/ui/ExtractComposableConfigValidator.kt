package com.github.aviantdev.composeextract.ui

import com.github.aviantdev.composeextract.core.analysis.TargetScopeResolver
import com.github.aviantdev.composeextract.core.model.ExtractComposableConfig
import com.github.aviantdev.composeextract.core.model.TargetDestination
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens

class ExtractComposableConfigValidator(
  private val targetScopeResolver: TargetScopeResolver = TargetScopeResolver()
) {

  fun validateComposableName(input: String): String? {
    val name = input.trim()
    if (name.isEmpty()) return "Composable name is required."
    if (!name.first().isUpperCase()) return "Composable name must use PascalCase."
    if (!name.all { it.isLetterOrDigit() || it == '_' }) {
      return "Composable name must contain only letters, digits, or underscores."
    }
    if (name.contains('_')) return "Composable name must use PascalCase."
    if (!name.first().isLetter()) return "Composable name must start with a letter."
    return null
  }

  fun buildConfig(
    composableName: String,
    destination: TargetDestination,
    includeModifier: Boolean
  ): ExtractComposableConfig? {
    if (validateComposableName(composableName) != null) return null

    return ExtractComposableConfig(
      composableName = composableName.trim(),
      destination = destination,
      visibility = targetScopeResolver.resolveVisibility(destination),
      includeModifier = includeModifier
    )
  }
}
