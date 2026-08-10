package com.github.aviantdev.composeextract.core.generator

import com.github.aviantdev.composeextract.core.analysis.SelectionInspector
import com.github.aviantdev.composeextract.core.analysis.VariableUsageVisitor
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class CallSiteReplacerTest : LightJavaCodeInsightFixtureTestCase() {

  private val inspector = SelectionInspector()
  private val replacer = CallSiteReplacer()

  fun testReplaceCallSiteSingleElementSingleArgument() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample(message: String) {
                <selection>Text(message)</selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    var replacedCall: KtCallExpression? = null

    WriteCommandAction.runWriteCommandAction(project) {
      replacedCall = replacer.replaceCallSite(
        psiFactory = psiFactory,
        functionName = "CustomText",
        parameters = externalVars,
        selectedElements = selectedElements
      )
    }

    assertNotNull(replacedCall)
    assertEquals("CustomText", replacedCall?.calleeExpression?.text)
    assertEquals("CustomText(message = message)", replacedCall?.text)

    val fileText = file.text.replace("\r\n", "\n")
    assertTrue(fileText.contains("CustomText(message = message)"))
  }

  fun testReplaceCallSiteMultipleElementsDeletesTailAndFormatsMultiLine() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample(title: String, count: Int, onClick: () -> Unit) {
                Column {
                    <selection>
                    Text(title)
                    Text("Count: ${'$'}count")
                    Button(onClick = onClick)
                    </selection>
                }
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    var replacedCall: KtCallExpression? = null

    WriteCommandAction.runWriteCommandAction(project) {
      replacedCall = replacer.replaceCallSite(
        psiFactory = psiFactory,
        functionName = "ExtractedWidget",
        parameters = externalVars,
        selectedElements = selectedElements
      )
    }

    assertNotNull(replacedCall)
    assertEquals("ExtractedWidget", replacedCall?.calleeExpression?.text)

    val valueArgs = replacedCall?.valueArguments?.map { it.text }
    assertEquals(listOf("title = title", "count = count", "onClick = onClick"), valueArgs)

    val fileText = file.text.replace("\r\n", "\n")
    assertTrue(fileText.contains("ExtractedWidget("))
    assertTrue(fileText.contains("title = title"))
    assertTrue(fileText.contains("count = count"))
    assertTrue(fileText.contains("onClick = onClick"))
    assertFalse(fileText.contains("Text(title)"))
  }

  fun testReplaceCallSiteDetectsOuterModifierInsideComposeLambda() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text
            import androidx.compose.ui.Modifier

            @Composable
            fun Sample(title: String, modifier: Modifier = Modifier) {
                Column {
                    <selection>
                    Text(title, modifier = modifier)
                    </selection>
                }
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    var replacedCall: KtCallExpression? = null

    WriteCommandAction.runWriteCommandAction(project) {
      replacedCall = replacer.replaceCallSite(
        psiFactory = psiFactory,
        functionName = "ExtractedWidget",
        parameters = externalVars,
        selectedElements = selectedElements
      )
    }

    assertNotNull(replacedCall)
    val valueArgs = replacedCall?.valueArguments?.map { it.text }
    // Verify 'modifier = modifier' is preserved even inside Column { ... } lambda
    assertEquals(listOf("title = title", "modifier = modifier"), valueArgs)
  }

  fun testReplaceCallSiteRespectsTwoSpaceIndentOption() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample(title: String, count: Int) {
              Column {
                <selection>
                Text(title)
                Text("Count: ${'$'}count")
                </selection>
              }
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    CodeStyle.getIndentOptions(file).INDENT_SIZE = 2

    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    var replacedCall: KtCallExpression? = null

    WriteCommandAction.runWriteCommandAction(project) {
      replacedCall = replacer.replaceCallSite(
        psiFactory = psiFactory,
        functionName = "WidgetTwoSpaces",
        parameters = externalVars,
        selectedElements = selectedElements
      )
    }

    assertNotNull(replacedCall)
    assertEquals("WidgetTwoSpaces", replacedCall?.calleeExpression?.text)

    val fileText = file.text.replace("\r\n", "\n")
    assertTrue(fileText.contains("WidgetTwoSpaces("))
    assertTrue(fileText.contains("title = title"))
    assertTrue(fileText.contains("count = count"))
  }

  fun testReplaceCallSiteDetectsOuterModifierFromEnclosingScopeThroughNestedFunction() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text
            import androidx.compose.ui.Modifier

            @Composable
            fun OuterSample(modifier: Modifier = Modifier) {
                fun InnerFunction() {
                    <selection>
                    Text("Hello", modifier = modifier)
                    </selection>
                }
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    var replacedCall: KtCallExpression? = null

    WriteCommandAction.runWriteCommandAction(project) {
      replacedCall = replacer.replaceCallSite(
        psiFactory = psiFactory,
        functionName = "ExtractedWidget",
        parameters = externalVars,
        selectedElements = selectedElements
      )
    }

    assertNotNull(replacedCall)
    val valueArgs = replacedCall?.valueArguments?.map { it.text }

    assertEquals(listOf("modifier = modifier"), valueArgs)
  }

  fun testReplaceCallSiteDetectsLocalModifierPropertyInScope() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text
            import androidx.compose.ui.Modifier

            @Composable
            fun Sample() {
                val modifier = Modifier.fillMaxSize()
                <selection>
                Text("Hello", modifier = modifier)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    var replacedCall: KtCallExpression? = null

    WriteCommandAction.runWriteCommandAction(project) {
      replacedCall = replacer.replaceCallSite(
        psiFactory = psiFactory,
        functionName = "ExtractedWidget",
        parameters = externalVars,
        selectedElements = selectedElements
      )
    }

    assertNotNull(replacedCall)
    val valueArgs = replacedCall?.valueArguments?.map { it.text }

    assertEquals(listOf("modifier = modifier"), valueArgs)
  }
}