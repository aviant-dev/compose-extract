package com.github.aviantdev.composeextract.ui

import com.github.aviantdev.composeextract.core.model.TargetDestination
import com.github.aviantdev.composeextract.core.model.VisibilityModifier
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Test

class ExtractComposableConfigValidatorTest {

  private val validator = ExtractComposableConfigValidator()

  @Test
  fun `accepts valid pascal case name`() {
    assertNull(validator.validateComposableName("ExtractedWidget"))
  }

  @Test
  fun `rejects blank name`() {
    assertEquals("Composable name is required.", validator.validateComposableName("  "))
  }

  @Test
  fun `rejects non pascal case name`() {
    assertEquals("Composable name must use PascalCase.", validator.validateComposableName("extractedWidget"))
    assertEquals("Composable name must use PascalCase.", validator.validateComposableName("Extracted_Widget"))
  }

  @Test
  fun `builds config with destination driven visibility`() {
    val sameFileConfig = validator.buildConfig("ExtractedWidget", TargetDestination.SAME_FILE, includeModifier = true)
    val sameModuleConfig = validator.buildConfig("ExtractedWidget", TargetDestination.NEW_FILE_SAME_MODULE, includeModifier = false)
    val crossModuleConfig = validator.buildConfig("ExtractedWidget", TargetDestination.NEW_FILE_DIFFERENT_MODULE, includeModifier = true)

    assertNotNull(sameFileConfig)
    assertNotNull(sameModuleConfig)
    assertNotNull(crossModuleConfig)
    assertEquals(VisibilityModifier.PRIVATE, sameFileConfig?.visibility)
    assertEquals(VisibilityModifier.INTERNAL, sameModuleConfig?.visibility)
    assertEquals(VisibilityModifier.PUBLIC, crossModuleConfig?.visibility)
    assertEquals(false, sameModuleConfig?.includeModifier)
  }
}
