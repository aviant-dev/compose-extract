package com.github.aviantdev.composeextract.actions

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile

class ExtractComposableActionHandlerTest : LightJavaCodeInsightFixtureTestCase() {

  fun testIsAvailableForTextCallInsideColumn() {
    val code = """
            package com.example

            import androidx.compose.foundation.layout.Column
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable

            @Composable
            private fun Test() {
                Column {
                    <selection>Text(text = "Abcd")</selection>
                }
            }
        """.trimIndent()

    val file = myFixture.configureByText("Sample.kt", code) as KtFile

    assertTrue(ExtractComposableActionHandler.isAvailable(file, myFixture.editor))
  }
}
