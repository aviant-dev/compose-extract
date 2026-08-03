package com.github.aviantdev.composeextract.core.analysis

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

class SelectionInspectorTest : LightJavaCodeInsightFixtureTestCase() {

  private val inspector = SelectionInspector()

  fun testExplicitSelectionRange() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample() {
                <selection>Text("Hello")
                Text("World")</selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val elements = inspector.inspect(file, myFixture.editor)

    assertEquals(2, elements.size)
    assertTrue(elements.all { it is KtCallExpression })
  }

  fun testImplicitCaretScopeOnComposableCall() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample() {
                Te<caret>xt("Hello")
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val elements = inspector.inspect(file, myFixture.editor)

    assertEquals(1, elements.size)
    val target = elements.first() as KtCallExpression
    assertEquals("Text(\"Hello\")", target.text)
  }

  fun testCaretInEmptySpaceReturnsEmpty() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable

            @Composable
            fun Sample() {
                <caret>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val elements = inspector.inspect(file, myFixture.editor)

    assertTrue(elements.isEmpty())
  }

  fun testExplicitSelectionOnCommentOnlyReturnsEmpty() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable

            @Composable
            fun Sample() {
                <selection>// This is a comment</selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val elements = inspector.inspect(file, myFixture.editor)

    assertTrue(elements.isEmpty())
  }

  fun testExplicitSelectionWithSemicolonsFiltersPunctuation() {
    val code = """
            package com.example

            import androidx.compose.runtime.Composable
            import androidx.compose.material3.Text

            @Composable
            fun Sample() {
                <selection>Text("A"); Text("B")</selection>
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile
    val elements = inspector.inspect(file, myFixture.editor)

    assertEquals(2, elements.size)
    assertTrue(elements.all { it is KtCallExpression })
  }
}