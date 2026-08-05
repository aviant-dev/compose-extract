package com.github.aviantdev.composeextract.core.analysis

import com.github.aviantdev.composeextract.core.model.TargetDestination
import com.github.aviantdev.composeextract.core.model.VisibilityModifier
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile

class TargetScopeResolverTest : LightJavaCodeInsightFixtureTestCase() {

  private val resolver = TargetScopeResolver()

  fun testResolveByDestinationEnum() {
    assertEquals(VisibilityModifier.PRIVATE, resolver.resolveVisibility(TargetDestination.SAME_FILE))
    assertEquals(VisibilityModifier.INTERNAL, resolver.resolveVisibility(TargetDestination.NEW_FILE_SAME_MODULE))
    assertEquals(VisibilityModifier.PUBLIC, resolver.resolveVisibility(TargetDestination.NEW_FILE_DIFFERENT_MODULE))
  }

  fun testSameFileReturnsPrivate() {
    val sourceFile = myFixture.configureByText("Source.kt", "package com.example") as KtFile
    val visibility = resolver.resolveVisibility(sourceFile, sourceFile)

    assertEquals(VisibilityModifier.PRIVATE, visibility)
  }

  fun testNewFileInSameModuleReturnsInternal() {
    val sourceFile = myFixture.configureByText("Source.kt", "package com.example") as KtFile
    val targetFile = myFixture.addFileToProject("Target.kt", "package com.example") as KtFile

    val visibility = resolver.resolveVisibility(sourceFile, targetFile)

    assertEquals(VisibilityModifier.INTERNAL, visibility)
  }

  fun testNonPhysicalDummyFilesAreNotTreatedAsSameFile() {
    val psiFactory = org.jetbrains.kotlin.psi.KtPsiFactory(project)
    val dummySource = psiFactory.createFile("Source.kt", "package com.example")
    val dummyTarget = psiFactory.createFile("Target.kt", "package com.example")

    // Both dummy files are still got virtualFile == null
    assertNull(dummySource.virtualFile)
    assertNull(dummyTarget.virtualFile)

    val visibility = resolver.resolveVisibility(dummySource, dummyTarget)

    // Should return INTERNAL/PUBLIC, not PRIVATE
    assertFalse(visibility == VisibilityModifier.PRIVATE)
  }
}