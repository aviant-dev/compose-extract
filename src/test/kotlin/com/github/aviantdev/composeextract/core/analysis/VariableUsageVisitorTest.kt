package com.github.aviantdev.composeextract.core.analysis

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile

class VariableUsageVisitorTest : LightJavaCodeInsightFixtureTestCase() {

  private val inspector = SelectionInspector()

  fun testDetectsOuterParametersAndLocalVariables() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample(title: String, onClick: () -> Unit) {
                val count = 10
                <selection>
                Text(title)
                Text("Count: ${'$'}count")
                onClick()
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)

    val visitor = VariableUsageVisitor(selectedElements)
    val externalVars = visitor.analyze()

    val names = externalVars.map { it.name }
    assertEquals(3, names.size)
    assertTrue(names.containsAll(listOf("title", "count", "onClick")))
  }

  fun testIgnoresVariablesDeclaredInsideSelection() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample(outerValue: String) {
                <selection>
                val internalVar = "Inside"
                Text(internalVar)
                Text(outerValue)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)

    val visitor = VariableUsageVisitor(selectedElements)
    val externalVars = visitor.analyze()

    val names = externalVars.map { it.name }
    assertEquals(1, names.size)
    assertEquals("outerValue", names.first())
    assertFalse(names.contains("internalVar"))
  }

  fun testIgnoresTopLevelAndMemberProperties() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            val TOP_LEVEL_CONST = "Constant"

            @Composable
            fun Sample() {
                <selection>
                Text(TOP_LEVEL_CONST)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)

    val visitor = VariableUsageVisitor(selectedElements)
    val externalVars = visitor.analyze()

    assertTrue(externalVars.isEmpty())
  }

  fun testIgnoresNamedArgumentLabels() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable

            @Composable
            fun Button(onClick: () -> Unit) {}

            @Composable
            fun Sample(onClick: () -> Unit) {
                <selection>
                Button(onClick = onClick)
                </selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val selectedElements = inspector.inspect(file, myFixture.editor)

    val visitor = VariableUsageVisitor(selectedElements)
    val externalVars = visitor.analyze()

    val names = externalVars.map { it.name }

    assertEquals(1, names.size)
    assertEquals("onClick", names.first())
  }
}