package com.github.aviantdev.composeextract.core.service

import com.github.aviantdev.composeextract.core.analysis.SelectionInspector
import com.github.aviantdev.composeextract.core.model.ExtractComposableConfig
import com.github.aviantdev.composeextract.core.model.TargetDestination
import com.github.aviantdev.composeextract.core.model.VisibilityModifier
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class ComposeExtractorServiceTest : LightJavaCodeInsightFixtureTestCase() {

  private val inspector = SelectionInspector()
  private val service = ComposeExtractorService()

  fun testCreatesNewFileWithPackageImportsAndConfiguredOptions() {
    val source = myFixture.configureByText(
      "Screen.kt",
      """
        package com.example.feature

        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable

        @Composable
        fun Screen() {
            <selection>Text(text = "Hello")</selection>
        }
      """.trimIndent()
    ) as KtFile
    val selectedElements = inspector.inspect(source, myFixture.editor)
    val sourceFunction = source.declarations.filterIsInstance<KtNamedFunction>().single()
    val config = ExtractComposableConfig(
      composableName = "Greeting",
      destination = TargetDestination.NEW_FILE_SAME_MODULE,
      visibility = VisibilityModifier.INTERNAL,
      includeModifier = false
    )

    val result = service.extract(project, source, sourceFunction, selectedElements, config)
    assertTrue(result is ComposeExtractorService.ExtractionResult.Success)
    val target = (result as ComposeExtractorService.ExtractionResult.Success).targetFile

    assertEquals("Greeting.kt", target.name)
    assertTrue(target.virtualFile.exists())
    assertTrue(target.text.contains("package com.example.feature"))
    assertTrue(target.text.contains("import androidx.compose.material3.Text"))
    assertTrue(target.text.contains("import androidx.compose.runtime.Composable"))
    assertTrue(target.text.contains("internal fun Greeting()"))
    assertFalse(target.text.contains("modifier: Modifier"))
    assertTrue(source.text.contains("Greeting()"))
    assertFalse(source.text.contains("Text(text = \"Hello\")"))
  }

  fun testReportsExistingNewFile() {
    val source = myFixture.configureByText(
      "Screen.kt",
      """
        import androidx.compose.runtime.Composable

        @Composable
        fun Screen() {
            <selection>Text(text = "Hello")</selection>
        }
      """.trimIndent()
    ) as KtFile
    myFixture.addFileToProject("Greeting.kt", "package com.example")
    val selectedElements = inspector.inspect(source, myFixture.editor)
    val sourceFunction = source.declarations.filterIsInstance<KtNamedFunction>().single()
    val config = ExtractComposableConfig(
      composableName = "Greeting",
      destination = TargetDestination.NEW_FILE_SAME_MODULE,
      visibility = VisibilityModifier.INTERNAL,
      includeModifier = false
    )

    val result = service.extract(project, source, sourceFunction, selectedElements, config)

    assertEquals(
      ComposeExtractorService.ExtractionResult.Failure(
        "Cannot create 'Greeting.kt' because a file with that name already exists."
      ),
      result
    )
    assertTrue(source.text.contains("Text(text = \"Hello\")"))
  }

  fun testHoistsScopedRootModifierToCallSite() {
    val source = myFixture.configureByText(
      "Screen.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun Screen(imageUrl: String) {
            Column {
                <selection>DynamicAsyncImage(
                    imageUrl = imageUrl,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )</selection>
            }
        }
      """.trimIndent()
    ) as KtFile
    val selectedElements = inspector.inspect(source, myFixture.editor)
    val sourceFunction = source.declarations.filterIsInstance<KtNamedFunction>().single()
    val config = ExtractComposableConfig(
      composableName = "TopicImage",
      destination = TargetDestination.SAME_FILE,
      visibility = VisibilityModifier.PRIVATE,
      includeModifier = true
    )

    val result = service.extract(project, source, sourceFunction, selectedElements, config)

    assertTrue(result is ComposeExtractorService.ExtractionResult.Success)
    assertTrue(source.text, source.text.contains("TopicImage("))
    assertTrue(source.text, source.text.contains("imageUrl = imageUrl"))
    assertTrue(source.text, source.text.contains("modifier = Modifier.align(Alignment.CenterHorizontally)"))
    assertTrue(source.text.contains("modifier = modifier"))
    assertFalse(source.text.contains("modifier.then(Modifier.align"))
  }
}
