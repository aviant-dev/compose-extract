package com.github.aviantdev.composeextract.core.analysis

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class ComposeModifierInjectorTest : LightJavaCodeInsightFixtureTestCase() {

  private val injector = ComposeModifierInjector()

  fun testEnsureModifierImportAddsImportWhenMissing() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable

            @Composable
            fun Sample() {}
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val psiFactory = KtPsiFactory(project)

    // Wrap PSI modification inside WriteCommandAction for testing
    WriteCommandAction.runWriteCommandAction(project) {
      injector.ensureModifierImport(psiFactory, file)
    }

    val imports = file.importDirectives.mapNotNull { it.importedFqName?.asString() }
    assertTrue(imports.contains("androidx.compose.ui.Modifier"))
  }

  fun testInjectModifierToRootCallWithoutArgumentList() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.foundation.layout.Column

            @Composable
            fun Sample() {
                Column { }
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val psiFactory = KtPsiFactory(project)
    val rootCall = file.findDescendantOfType<KtCallExpression>()!!

    WriteCommandAction.runWriteCommandAction(project) {
      injector.injectModifierToRootCall(psiFactory, rootCall)
    }

    assertEquals("Column(modifier = modifier) { }", rootCall.text)
  }

  fun testInjectModifierToRootCallWithExistingModifierChainsExpression() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.foundation.layout.Column
            import androidx.compose.ui.Modifier

            @Composable
            fun Sample() {
                Column(modifier = Modifier.fillMaxSize()) { }
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val psiFactory = KtPsiFactory(project)
    val rootCall = file.findDescendantOfType<KtCallExpression>()!!

    WriteCommandAction.runWriteCommandAction(project) {
      injector.injectModifierToRootCall(psiFactory, rootCall)
    }

    assertEquals("Column(modifier = modifier.then(Modifier.fillMaxSize())) { }", rootCall.text)
  }

  fun testInjectModifierToRootCallWithPositionalModifierChainsExpression() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.foundation.layout.Column
            import androidx.compose.ui.Modifier

            @Composable
            fun Sample() {
                Column(Modifier.fillMaxSize()) { }
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val psiFactory = KtPsiFactory(project)
    val rootCall = file.findDescendantOfType<KtCallExpression>()!!

    WriteCommandAction.runWriteCommandAction(project) {
      injector.injectModifierToRootCall(psiFactory, rootCall)
    }

    assertEquals("Column(modifier.then(Modifier.fillMaxSize())) { }", rootCall.text)
  }
}