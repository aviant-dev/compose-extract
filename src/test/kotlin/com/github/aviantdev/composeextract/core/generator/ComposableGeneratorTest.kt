package com.github.aviantdev.composeextract.core.generator

import com.github.aviantdev.composeextract.core.analysis.SelectionInspector
import com.github.aviantdev.composeextract.core.analysis.VariableUsageVisitor
import com.github.aviantdev.composeextract.core.model.VisibilityModifier
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

class ComposableGeneratorTest : LightJavaCodeInsightFixtureTestCase() {

  private val inspector = SelectionInspector()
  private val generator = ComposableGenerator()

  fun testGenerateComposableWithMultipleParametersFormattedOnSeparatedLines() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample(title: String, onClick: () -> Unit) {
                <selection>
                Column {
                    Text(title)
                    Button(onClick = onClick)
                }
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    val generatedFunction = generator.generateComposable(
      psiFactory = psiFactory,
      functionName = "ExtractedWidget",
      visibility = VisibilityModifier.PRIVATE,
      parameters = externalVars,
      bodyElements = selectedElements,
      includeModifier = true
    )

    // Verify AST structure and parameter signatures
    assertEquals("ExtractedWidget", generatedFunction.name)
    assertTrue(generatedFunction.isPrivate())
    assertTrue(generatedFunction.annotationEntries.any { it.shortName?.asString() == "Composable" })

    val paramTexts = generatedFunction.valueParameters.map { it.text }
    assertEquals(3, paramTexts.size)
    assertEquals("title: String", paramTexts[0])
    assertEquals("modifier: Modifier = Modifier", paramTexts[1])
    assertEquals("onClick: () -> Unit", paramTexts[2])

    // Verify root layout modifier injection
    val generatedText = generatedFunction.text.replace("\r\n", "\n")
    assertTrue(generatedText.contains("Column(modifier = modifier)"))
  }

  fun testGenerateComposableWithSingleParameterOnSingleLine() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample(message: String) {
                <selection>
                Text(message)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    val generatedFunction = generator.generateComposable(
      psiFactory = psiFactory,
      functionName = "CustomText",
      visibility = VisibilityModifier.PUBLIC,
      parameters = externalVars,
      bodyElements = selectedElements,
      includeModifier = false
    )

    assertEquals("CustomText", generatedFunction.name)
    assertFalse(generatedFunction.isPrivate())

    val paramTexts = generatedFunction.valueParameters.map { it.text }
    assertEquals(1, paramTexts.size)
    assertEquals("message: String", paramTexts[0])
  }

  fun testHappyCaseMultipleParametersFormattedOnSeparatedLines() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample(title: String, count: Int, onClick: () -> Unit) {
                <selection>
                Column {
                    Text(title)
                    Text("Count: ${'$'}count")
                    Button(onClick = onClick)
                }
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    val generatedFunction = generator.generateComposable(
      psiFactory = psiFactory,
      functionName = "ExtractedWidget",
      visibility = VisibilityModifier.PRIVATE,
      parameters = externalVars,
      bodyElements = selectedElements,
      includeModifier = true
    )

    // Verify AST structure
    assertEquals("ExtractedWidget", generatedFunction.name)
    assertTrue(generatedFunction.isPrivate())
    assertTrue(generatedFunction.annotationEntries.any { it.shortName?.asString() == "Composable" })

    // Verify parameter order: required -> modifier -> lambda
    val paramTexts = generatedFunction.valueParameters.map { it.text }
    assertEquals(4, paramTexts.size)
    assertEquals("title: String", paramTexts[0])
    assertEquals("count: Int", paramTexts[1])
    assertEquals("modifier: Modifier = Modifier", paramTexts[2])
    assertEquals("onClick: () -> Unit", paramTexts[3])

    // Verify multi-line formatting (>= 2 params) & root layout injection
    val generatedText = generatedFunction.text.replace("\r\n", "\n")
    assertTrue(generatedText.contains("fun ExtractedWidget(\n    title: String,"))
    assertTrue(generatedText.contains("Column(modifier = modifier)"))
  }

  fun testSingleParameterFormattedOnSingleLine() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample(message: String) {
                <selection>
                Text(message)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    val generatedFunction = generator.generateComposable(
      psiFactory = psiFactory,
      functionName = "CustomText",
      visibility = VisibilityModifier.PUBLIC,
      parameters = externalVars,
      bodyElements = selectedElements,
      includeModifier = false
    )

    assertEquals("CustomText", generatedFunction.name)
    assertFalse(generatedFunction.isPrivate())

    val paramTexts = generatedFunction.valueParameters.map { it.text }
    assertEquals(1, paramTexts.size)
    assertEquals("message: String", paramTexts[0])

    // Verify single-line formatting
    val generatedText = generatedFunction.text.replace("\r\n", "\n")
    assertTrue(generatedText.startsWith("@Composable\nfun CustomText(message: String)"))
  }

  fun testAvoidsInjectingModifierIntoCustomComposableWithoutModifierSupport() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable

            @Composable
            fun UserAvatar(url: String) {}

            @Composable
            fun Sample(avatarUrl: String) {
                <selection>
                UserAvatar(url = avatarUrl)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    val generatedFunction = generator.generateComposable(
      psiFactory = psiFactory,
      functionName = "AvatarSection",
      visibility = VisibilityModifier.PRIVATE,
      parameters = externalVars,
      bodyElements = selectedElements,
      includeModifier = true
    )

    // Function signature should still have modifier: Modifier = Modifier
    val paramTexts = generatedFunction.valueParameters.map { it.text }
    assertTrue(paramTexts.contains("modifier: Modifier = Modifier"))

    // Body must NOT be rewritten as UserAvatar(url = avatarUrl, modifier = modifier)
    val generatedText = generatedFunction.text.replace("\r\n", "\n")
    assertTrue(generatedText.contains("UserAvatar(url = avatarUrl)"))
    assertFalse(generatedText.contains("UserAvatar(url = avatarUrl, modifier = modifier)"))
  }

  fun testDoesNotDuplicateExistingOuterModifierArgument() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.foundation.layout.Column
            import androidx.compose.ui.Modifier

            @Composable
            fun Sample(modifier: Modifier) {
                <selection>
                Column(modifier = modifier) {
                    Text("Hello")
                }
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    val generatedFunction = generator.generateComposable(
      psiFactory = psiFactory,
      functionName = "ExtractedWidget",
      visibility = VisibilityModifier.PRIVATE,
      parameters = externalVars,
      bodyElements = selectedElements,
      includeModifier = true
    )

    val generatedText = generatedFunction.text.replace("\r\n", "\n")

    // Column(modifier = modifier) should NOT become modifier.then(modifier)
    assertTrue(generatedText.contains("Column(modifier = modifier)"))
    assertFalse(generatedText.contains("modifier.then(modifier)"))
  }

  fun testPreservesExternalModifierParameterWhenInjectionDisabled() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text
            import androidx.compose.ui.Modifier

            @Composable
            fun Sample(modifier: Modifier) {
                <selection>
                Text("Hello", modifier = modifier)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    val generatedFunction = generator.generateComposable(
      psiFactory = psiFactory,
      functionName = "CustomText",
      visibility = VisibilityModifier.PRIVATE,
      parameters = externalVars,
      bodyElements = selectedElements,
      includeModifier = false
    )

    // The outer 'modifier' variable must NOT be skipped when includeModifier = false
    val paramTexts = generatedFunction.valueParameters.map { it.text }
    assertEquals(1, paramTexts.size)
    assertEquals("modifier: Modifier", paramTexts[0])
  }

  fun testInfersPropertyTypesForImplicitDeclarations() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample() {
                val title = "Title"
                val count = 10
                val enabled = true
                <selection>
                Text(title)
                Text("Count: ${'$'}count")
                Button(enabled = enabled, onClick = {})
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)
    val externalVars = VariableUsageVisitor(selectedElements).analyze()

    val psiFactory = KtPsiFactory(project)
    val generatedFunction = generator.generateComposable(
      psiFactory = psiFactory,
      functionName = "InferredWidget",
      visibility = VisibilityModifier.PRIVATE,
      parameters = externalVars,
      bodyElements = selectedElements,
      includeModifier = false
    )

    // Verify correctly inferred property types instead of 'Any'
    val paramTexts = generatedFunction.valueParameters.map { it.text }
    assertTrue(paramTexts.contains("title: String"))
    assertTrue(paramTexts.contains("count: Int"))
    assertTrue(paramTexts.contains("enabled: Boolean"))
    assertFalse(paramTexts.any { it.contains("Any") })
  }
}