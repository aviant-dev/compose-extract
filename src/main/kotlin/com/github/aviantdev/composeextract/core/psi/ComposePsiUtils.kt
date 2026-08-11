package com.github.aviantdev.composeextract.core.psi

import com.github.aviantdev.composeextract.core.generator.ImportResolver.Companion.COMPOSABLE_PACKAGE
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Checks if the function is annotated with @Composable
 */
fun KtFunction.isComposable(): Boolean {
  val annotations = this.annotationEntries
  if (annotations.isEmpty()) return false

  return annotations.any { annotation ->
    val shortName = annotation.shortName?.asString()
    shortName == "Composable" || annotation.text.contains(COMPOSABLE_PACKAGE)
  }
}

/**
 * Checks if the KtFile already imports a given package/class
 */
fun KtFile.hasImport(fqName: String): Boolean =
  importDirectives.any { directive ->
    directive.importedFqName?.asString() == fqName
  }

/**
 * Automatically inserts an import directive into the KtFile if missing
 */
fun KtFile.addImportIfMissing(fqName: String, psiFactory: KtPsiFactory) {
  if (hasImport(fqName)) return

  val dummyFile = psiFactory.createFile("import $fqName")
  val importDirective = dummyFile.importDirectives.firstOrNull() ?: return
  val targetImportList = this.importList

  if (targetImportList != null) {
    targetImportList.add(psiFactory.createNewLine())
    targetImportList.add(importDirective)
  } else {
    val packageDirective = this.packageDirective
    if (packageDirective != null && packageDirective.text.isNotEmpty()) {
      val addedDirective = addAfter(importDirective, packageDirective)
      addBefore(psiFactory.createNewLine(), addedDirective)
      addAfter(psiFactory.createNewLine(), addedDirective)
    } else {
      val firstChild = this.firstChild
      if (firstChild != null) {
        addBefore(importDirective, firstChild)
        addBefore(psiFactory.createNewLine(), firstChild)
      } else {
        add(importDirective)
      }
    }
  }
}
