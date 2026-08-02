package com.github.aviantdev.composeextract.core.psi

import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtFile

/**
 * Checks if the function is annotated with @Composable
 */
fun KtFunction.isComposable(): Boolean {
  val annotations = this.annotationEntries
  if (annotations.isEmpty()) {
    return false
  }
  return annotations.any { annotation ->
    val shortName = annotation.shortName?.asString()
    shortName == "Composable" || annotation.text.contains(COMPOSABLE_FQ_NAME)
  }
}

/**
 * Checks if the KtFile already imports a given package/class
 */
fun KtFile.hasImport(fqName: String): Boolean {
  return importDirectives.any { it.importedFqName?.asString() == fqName }
}

/**
 * Automatically inserts an import directive into the KtFile if missing
 */
fun KtFile.addImportIfMissing(fqName: String, psiFactory: KtPsiFactory) {
  if (!hasImport(fqName)) {
    // Parse a dummy file snippet to get a clean KtImportDirective node without using `ImportPath`
    val dummyFile = psiFactory.createFile("import $fqName")
    val importDirective = dummyFile.importDirectives.firstOrNull() ?: return

    val targetImportList = this.importList
    if (targetImportList != null) {
      targetImportList.add(importDirective)
    } else {
      add(importDirective)
    }
  }
}

/**
 * Normalizes `@androidx.compose.runtime.Composable` to `@Composable` on the target function.
 */
fun KtFunction.normalizeComposableAnnotation(psiFactory: KtPsiFactory) {
  val fqAnnotation = annotationEntries.firstOrNull {
    it.text.contains(COMPOSABLE_FQ_NAME)
  }
  if (fqAnnotation != null) {
    val cleanAnnotation = psiFactory.createAnnotationEntry("@Composable")
    fqAnnotation.replace(cleanAnnotation)
  }
}

/**
 * Generates a new Composable function and ensures @Composable is imported in the target file
 */
fun createComposableFunction(
  psiFactory: KtPsiFactory,
  targetFile: KtFile,
  functionName: String,
  bodyText: String = "// Generated code",
  visibility: String = "private"
): KtFunction {
  targetFile.addImportIfMissing(COMPOSABLE_FQ_NAME, psiFactory)

  val functionTemplate = """
        @Composable
        $visibility fun $functionName() {
            $bodyText
        }
    """.trimIndent()

  return psiFactory.createFunction(functionTemplate)
}

private const val COMPOSABLE_FQ_NAME = "androidx.compose.runtime.Composable"