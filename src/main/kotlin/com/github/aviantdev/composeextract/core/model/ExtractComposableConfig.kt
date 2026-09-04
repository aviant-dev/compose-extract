package com.github.aviantdev.composeextract.core.model

data class ExtractComposableConfig(
  val composableName: String,
  val destination: TargetDestination,
  val visibility: VisibilityModifier,
  val includeModifier: Boolean
) {
  var targetModuleName: String? = null
    private set
  var targetPackageName: String? = null
    private set
  var targetDirectoryPath: String? = null
    private set

  /** Keeps the original four-argument constructor stable for existing callers and tests. */
  constructor(
    composableName: String,
    destination: TargetDestination,
    visibility: VisibilityModifier,
    includeModifier: Boolean,
    targetModuleName: String?,
    targetPackageName: String?
  ) : this(composableName, destination, visibility, includeModifier) {
    this.targetModuleName = targetModuleName
    this.targetPackageName = targetPackageName
  }

  constructor(
    composableName: String,
    destination: TargetDestination,
    visibility: VisibilityModifier,
    includeModifier: Boolean,
    targetModuleName: String?,
    targetPackageName: String?,
    targetDirectoryPath: String?
  ) : this(composableName, destination, visibility, includeModifier, targetModuleName, targetPackageName) {
    this.targetDirectoryPath = targetDirectoryPath
  }
}
