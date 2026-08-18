package com.github.aviantdev.composeextract.core.model

data class ExtractComposableConfig(
  val composableName: String,
  val destination: TargetDestination,
  val visibility: VisibilityModifier,
  val includeModifier: Boolean
)
