package com.github.aviantdev.composeextract.core.generator

import com.github.aviantdev.composeextract.core.psi.addImportPathIfMissing
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Resolves and injects missing import directives for extracted parameter types,
 * generic type arguments, callbacks, and Compose core dependencies into the target file using Pure PSI.
 */
class ImportResolver {

  companion object {
    const val COMPOSABLE_PACKAGE = "androidx.compose.runtime.Composable"
    const val MODIFIER_PACKAGE = "androidx.compose.ui.Modifier"
  }

  /**
   * Resolves all required type imports for [parameters] from [sourceFile]
   * and injects missing import directives into [targetFile].
   */
  fun resolveAndInjectImports(
    psiFactory: KtPsiFactory,
    targetFile: KtFile,
    parameters: Collection<KtNamedDeclaration>,
    sourceFile: KtFile = parameters.firstOrNull()?.containingFile as? KtFile ?: targetFile,
    includeModifier: Boolean = true
  ) {
    val existingImports = getExistingImportPaths(targetFile)
    val importsToAdd = mutableSetOf<String>()

    // Compose core imports
    if (!existingImports.contains(COMPOSABLE_PACKAGE)) {
      importsToAdd.add(COMPOSABLE_PACKAGE)
    }
    if (includeModifier && !existingImports.contains(MODIFIER_PACKAGE)) {
      importsToAdd.add(MODIFIER_PACKAGE)
    }

    // Resolve imports for types referenced in parameters
    val referencedTypeShortNames = extractTypeShortNamesFromParameters(parameters)
    val sourceImportMap = buildSourceImportMap(sourceFile)

    for (shortName in referencedTypeShortNames) {
      val importPathStr = sourceImportMap[shortName]
      if (importPathStr != null && !existingImports.contains(importPathStr)) {
        importsToAdd.add(importPathStr)
      }
    }

    // Inject missing imports
    for (importPathStr in importsToAdd) {
      targetFile.addImportPathIfMissing(importPathStr, psiFactory)
    }
  }

  private fun extractTypeShortNamesFromParameters(parameters: Collection<KtNamedDeclaration>): Set<String> {
    val shortNames = mutableSetOf<String>()

    for (declaration in parameters) {
      if (declaration is KtCallableDeclaration) {
        val typeRef = declaration.typeReference ?: continue
        val userTypes = typeRef.collectDescendantsOfType<KtUserType>()

        for (userType in userTypes) {
          val name = userType.referencedName
          if (!name.isNullOrEmpty()) {
            shortNames.add(name)
          }
        }
      }
    }

    return shortNames
  }

  /**
   * Maps imported short names (or alias names) to their full import string representations
   * using Pure PSI properties, avoiding deprecated ImportPath references.
   */
  private fun buildSourceImportMap(sourceFile: KtFile): Map<String, String> {
    val map = mutableMapOf<String, String>()

    for (directive in sourceFile.importDirectives) {
      val fqName = directive.importedFqName?.asString() ?: continue
      val aliasName = directive.aliasName
      val fullPathStr = if (aliasName != null) "$fqName as $aliasName" else fqName
      val shortName = aliasName ?: directive.importedFqName?.shortName()?.asString()

      if (shortName != null) {
        map[shortName] = fullPathStr
      }
    }

    return map
  }

  private fun getExistingImportPaths(file: KtFile): Set<String> {
    return file.importDirectives.mapNotNull { directive ->
      val fqName = directive.importedFqName?.asString() ?: return@mapNotNull null
      val aliasName = directive.aliasName
      if (aliasName != null) "$fqName as $aliasName" else fqName
    }.toSet()
  }
}
