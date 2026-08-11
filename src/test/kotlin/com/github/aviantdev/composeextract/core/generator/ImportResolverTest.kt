package com.github.aviantdev.composeextract.core.generator

import com.github.aviantdev.composeextract.core.analysis.SelectionInspector
import com.github.aviantdev.composeextract.core.analysis.VariableUsageVisitor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class ImportResolverTest : LightJavaCodeInsightFixtureTestCase() {

  private val inspector = SelectionInspector()
  private val importResolver = ImportResolver()

  fun testResolvesParameterTypeImportFromSourceFile() {
    val code = """
            package com.example

            import com.example.model.UserProfile
            import androidx.compose.runtime.Composable

            @Composable
            fun Sample(user: UserProfile) {
                <selection>
                Text(user.name)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)

    WriteCommandAction.runWriteCommandAction(project) {
      importResolver.resolveAndInjectImports(
        psiFactory = psiFactory,
        targetFile = file,
        parameters = externalVars,
        includeModifier = true
      )
    }

    val imports = getImportPaths(file)
    assertTrue(imports.contains("com.example.model.UserProfile"))
    assertTrue(imports.contains("androidx.compose.runtime.Composable"))
    assertTrue(imports.contains("androidx.compose.ui.Modifier"))
  }

  fun testResolvesGenericAndLambdaTypeArgumentImports() {
    val code = """
            package com.example

            import com.example.model.UserProfile
            import androidx.compose.runtime.Composable

            @Composable
            fun Sample(users: List<UserProfile>, onSelect: (UserProfile) -> Unit) {
                <selection>
                users.forEach { onSelect(it) }
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)

    WriteCommandAction.runWriteCommandAction(project) {
      importResolver.resolveAndInjectImports(
        psiFactory = psiFactory,
        targetFile = file,
        parameters = externalVars,
        includeModifier = false
      )
    }

    val imports = getImportPaths(file)
    assertTrue(imports.contains("com.example.model.UserProfile"))
    assertFalse(imports.contains("androidx.compose.ui.Modifier"))
  }

  fun testDoesNotDuplicateExistingImports() {
    val code = """
            package com.example

            import com.example.model.UserProfile
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier

            @Composable
            fun Sample(user: UserProfile, modifier: Modifier) {
                <selection>
                Text(user.name, modifier = modifier)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)

    WriteCommandAction.runWriteCommandAction(project) {
      importResolver.resolveAndInjectImports(
        psiFactory = psiFactory,
        targetFile = file,
        parameters = externalVars,
        includeModifier = true
      )
    }

    val imports = getImportPaths(file)
    assertEquals(1, imports.count { it == "com.example.model.UserProfile" })
    assertEquals(1, imports.count { it == "androidx.compose.runtime.Composable" })
    assertEquals(1, imports.count { it == "androidx.compose.ui.Modifier" })
  }

  fun testSupportsImportAliases() {
    val code = """
            package com.example

            import com.example.model.UserProfile as Profile
            import androidx.compose.runtime.Composable

            @Composable
            fun Sample(profile: Profile) {
                <selection>
                Text(profile.toString())
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)

    WriteCommandAction.runWriteCommandAction(project) {
      importResolver.resolveAndInjectImports(
        psiFactory = psiFactory,
        targetFile = file,
        parameters = externalVars,
        includeModifier = false
      )
    }

    val imports = getImportPaths(file)
    assertTrue(imports.contains("com.example.model.UserProfile as Profile"))
  }

  fun testInjectsImportsAtHeaderWhenFileHasNoExistingImportList() {
    val code = """
            package com.example

            @Composable
            fun Sample() {
                <selection>
                Text("Hello")
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)

    WriteCommandAction.runWriteCommandAction(project) {
      importResolver.resolveAndInjectImports(
        psiFactory = psiFactory,
        targetFile = file,
        parameters = externalVars,
        includeModifier = true
      )
    }

    val fileText = file.text.replace("\r\n", "\n")

    // Verifies import is placed after package directive and before fun declaration
    val packageIndex = fileText.indexOf("package com.example")
    val importIndex = fileText.indexOf("import androidx.compose.runtime.Composable")
    val functionIndex = fileText.indexOf("fun Sample()")

    assertTrue(packageIndex < importIndex)
    assertTrue(importIndex < functionIndex)
  }

  /**
   * Extracts import path representations using Pure PSI, avoiding deprecated ImportPath APIs.
   */
  private fun getImportPaths(file: KtFile): List<String> {
    return file.importDirectives.mapNotNull { directive ->
      val fqName = directive.importedFqName?.asString() ?: return@mapNotNull null
      val aliasName = directive.aliasName
      if (aliasName != null) "$fqName as $aliasName" else fqName
    }
  }
}
