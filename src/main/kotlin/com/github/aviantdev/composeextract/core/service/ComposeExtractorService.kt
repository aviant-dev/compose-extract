package com.github.aviantdev.composeextract.core.service

import com.github.aviantdev.composeextract.core.analysis.VariableUsageVisitor
import com.github.aviantdev.composeextract.core.generator.CallSiteReplacer
import com.github.aviantdev.composeextract.core.generator.ComposableGenerator
import com.github.aviantdev.composeextract.core.generator.ImportResolver
import com.github.aviantdev.composeextract.core.model.ExtractComposableConfig
import com.github.aviantdev.composeextract.core.model.TargetDestination
import com.github.aviantdev.composeextract.core.psi.addImportPathIfMissing
import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens

class ComposeExtractorService(
  private val generator: ComposableGenerator = ComposableGenerator(),
  private val importResolver: ImportResolver = ImportResolver(),
  private val callSiteReplacer: CallSiteReplacer = CallSiteReplacer()
) {

  sealed interface ExtractionResult {
    data class Success(val targetFile: KtFile) : ExtractionResult
    data class Failure(val message: String) : ExtractionResult
  }

  fun extract(
    project: Project,
    sourceFile: KtFile,
    sourceFunction: KtNamedFunction,
    selectedElements: List<PsiElement>,
    config: ExtractComposableConfig
  ): ExtractionResult {
    var result: ExtractionResult = ExtractionResult.Failure("The selected code is no longer available for extraction.")

    WriteCommandAction.runWriteCommandAction(project, "Extract Composable", null, {
      if (!sourceFile.isValid || !sourceFunction.isValid || selectedElements.any { !it.isValid }) {
        return@runWriteCommandAction
      }

      val psiFactory = KtPsiFactory(project)
      val rootModifierArgument = findRootModifierArgument(selectedElements)
      if (!config.includeModifier && rootModifierArgument?.hasScopeDependentModifier() == true) {
        result = ExtractionResult.Failure(
          "This selection uses a parent layout scope modifier. Enable 'Include Modifier parameter' to extract it safely."
        )
        return@runWriteCommandAction
      }
      val hoistedModifier = if (config.includeModifier) {
        rootModifierArgument?.getArgumentExpression()?.text
      } else {
        null
      }
      val externalVariables = VariableUsageVisitor(
        selectedElements = selectedElements,
        excludedElements = rootModifierArgument?.let(::setOf).orEmpty()
      ).analyze()
      if (config.destination == TargetDestination.NEW_FILE_DIFFERENT_MODULE) {
        val targetModule = config.targetModuleName?.let { ModuleManager.getInstance(project).findModuleByName(it) }
        if (targetModule == null) {
          result = ExtractionResult.Failure("Select a target design-system module.")
          return@runWriteCommandAction
        }
        if (externalVariables.any { declaration -> !isVisibleFromTargetModule(declaration, targetModule) }) {
          result = ExtractionResult.Failure(
            "Cross-module extraction can only capture public API types visible to '${targetModule.name}'."
          )
          return@runWriteCommandAction
        }
        val invisibleReference = findInvisibleBodyReference(selectedElements, targetModule)
        if (invisibleReference != null) {
          result = ExtractionResult.Failure(
            "Cross-module extraction cannot reference '$invisibleReference' because it is not visible from '${targetModule.name}'."
          )
          return@runWriteCommandAction
        }
      }
      val targetFile = when (val targetResult = resolveTargetFile(project, psiFactory, sourceFile, config)) {
        is TargetFileResult.Success -> targetResult.file
        is TargetFileResult.Failure -> {
          result = ExtractionResult.Failure(targetResult.message)
          return@runWriteCommandAction
        }
      }
      if (targetFile != sourceFile) {
        importResolver.copyMissingImports(psiFactory, sourceFile, targetFile)
      }
      importResolver.resolveAndInjectImports(
        psiFactory = psiFactory,
        targetFile = targetFile,
        parameters = externalVariables,
        sourceFile = sourceFile,
        includeModifier = config.includeModifier
      )
      if (targetFile.packageFqName != sourceFile.packageFqName) {
        sourceFile.addImportPathIfMissing("${targetFile.packageFqName.asString()}.${config.composableName}", psiFactory)
      }

      val generatedFunction = generator.generateComposable(
        psiFactory = psiFactory,
        functionName = config.composableName,
        visibility = config.visibility,
        parameters = externalVariables,
        bodyElements = selectedElements,
        includeModifier = config.includeModifier,
        targetFile = targetFile,
        hoistRootModifier = hoistedModifier != null
      )

      val insertedFunction = if (targetFile == sourceFile) {
        sourceFunction.parent.addAfter(generatedFunction, sourceFunction)
      } else {
        targetFile.add(generatedFunction)
      }
      CodeStyleManager.getInstance(project).reformat(insertedFunction)

      callSiteReplacer.replaceCallSite(
        psiFactory = psiFactory,
        functionName = config.composableName,
        parameters = externalVariables,
        selectedElements = selectedElements,
        callerHasModifier = CallSiteReplacer.callerHasModifierInScope(selectedElements),
        hoistedModifier = hoistedModifier
      )

      result = ExtractionResult.Success(targetFile)
    })

    val extractionResult = result
    if (extractionResult is ExtractionResult.Success) {
      optimizeImports(project, sourceFile, extractionResult.targetFile)
    }
    return extractionResult
  }

  private fun resolveTargetFile(
    project: Project,
    psiFactory: KtPsiFactory,
    sourceFile: KtFile,
    config: ExtractComposableConfig
  ): TargetFileResult = when (config.destination) {
    TargetDestination.SAME_FILE -> TargetFileResult.Success(sourceFile)
    TargetDestination.NEW_FILE_SAME_MODULE -> createTargetFile(psiFactory, sourceFile.containingDirectory, sourceFile.packageFqName.asString(), config.composableName)
    TargetDestination.NEW_FILE_DIFFERENT_MODULE -> createCrossModuleFile(project, psiFactory, sourceFile, config)
  }

  private fun createCrossModuleFile(project: Project, psiFactory: KtPsiFactory, sourceFile: KtFile, config: ExtractComposableConfig): TargetFileResult {
    val sourceModule = ModuleUtilCore.findModuleForPsiElement(sourceFile)
      ?: return TargetFileResult.Failure("Cannot determine the source module.")
    val targetModule = config.targetModuleName?.let { ModuleManager.getInstance(project).findModuleByName(it) }
      ?: return TargetFileResult.Failure("Select a target design-system module.")
    if (targetModule == sourceModule) {
      return TargetFileResult.Failure("Choose a module other than '${sourceModule.name}' for cross-module extraction.")
    }
    if (targetModule !in ModuleRootManager.getInstance(sourceModule).dependencies) {
      return TargetFileResult.Failure("Module '${sourceModule.name}' must depend on '${targetModule.name}' before extraction.")
    }
    val destinationPath = config.targetDirectoryPath
      ?: return TargetFileResult.Failure("Choose a destination folder inside a production source root.")
    val destinationFile = LocalFileSystem.getInstance().findFileByPath(destinationPath)
      ?: return TargetFileResult.Failure("The selected destination folder is no longer available.")
    if (ModuleUtilCore.findModuleForFile(destinationFile, project) != targetModule) {
      return TargetFileResult.Failure("The selected destination folder no longer belongs to '${targetModule.name}'.")
    }
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val sourceRoot = fileIndex.getSourceRootForFile(destinationFile)
    if (sourceRoot == null || !fileIndex.isInSourceContent(destinationFile) || fileIndex.isInTestSourceContent(destinationFile)) {
      return TargetFileResult.Failure("The selected destination folder must be inside a production source root.")
    }
    val directory = PsiManager.getInstance(project).findDirectory(destinationFile)
      ?: return TargetFileResult.Failure("Cannot access the selected destination folder.")
    val packageName = VfsUtilCore.getRelativePath(destinationFile, sourceRoot, '/')
      ?.replace('/', '.')
      ?: return TargetFileResult.Failure("Cannot determine the package for the selected destination folder.")
    return createTargetFile(psiFactory, directory, packageName, config.composableName)
  }

  private fun createTargetFile(
    psiFactory: KtPsiFactory,
    directory: com.intellij.psi.PsiDirectory?,
    packageName: String,
    composableName: String
  ): TargetFileResult {
    val targetDirectory = directory
      ?: return TargetFileResult.Failure("Cannot create a new file because the source directory is unavailable.")
    val fileName = "$composableName.kt"
    if (targetDirectory.findFile(fileName) != null) {
      return TargetFileResult.Failure("Cannot create '$fileName' because a file with that name already exists.")
    }

    val packageText = packageName.takeIf { it.isNotBlank() }?.let { "package $it" }
    val initialText = packageText?.let { "$it\n\n" }.orEmpty()
    val targetFile = targetDirectory.add(psiFactory.createFile(fileName, initialText)) as? KtFile
      ?: return TargetFileResult.Failure("Could not create '$fileName'.")
    return TargetFileResult.Success(targetFile)
  }

  private fun isVisibleFromTargetModule(declaration: KtNamedDeclaration, targetModule: com.intellij.openapi.module.Module): Boolean {
    if (declaration is KtProperty ||
      (declaration as? KtModifierListOwner)?.hasModifier(KtTokens.PRIVATE_KEYWORD) == true ||
      (declaration as? KtModifierListOwner)?.hasModifier(KtTokens.INTERNAL_KEYWORD) == true
    ) {
      return false
    }
    val parameter = declaration as? KtParameter ?: return true
    return parameter.typeReference
      ?.collectDescendantsOfType<KtUserType>()
      ?.mapNotNull { userType -> userType.referenceExpression?.mainReference?.resolve() }
      ?.none { typeDeclaration ->
        val typeModule = ModuleUtilCore.findModuleForPsiElement(typeDeclaration)
        typeModule != null && typeModule != targetModule &&
          typeModule !in ModuleRootManager.getInstance(targetModule).dependencies ||
          (typeDeclaration as? KtModifierListOwner)?.hasModifier(KtTokens.PRIVATE_KEYWORD) == true ||
          (typeDeclaration as? KtModifierListOwner)?.hasModifier(KtTokens.INTERNAL_KEYWORD) == true
      }
      ?: true
  }

  private fun findInvisibleBodyReference(
    selectedElements: List<PsiElement>,
    targetModule: com.intellij.openapi.module.Module
  ): String? {
    val references = selectedElements.flatMap { element ->
      PsiTreeUtil.collectElementsOfType(element, KtSimpleNameExpression::class.java).asIterable()
    }
    return references.firstNotNullOfOrNull { reference ->
      if (reference.parent is KtValueArgumentName) return@firstNotNullOfOrNull null
      val declaration = reference.mainReference.resolve() as? KtNamedDeclaration
        ?: return@firstNotNullOfOrNull null
      if (declaration is KtParameter || declaration is KtProperty && declaration.isLocal ||
        selectedElements.any { selected -> PsiTreeUtil.isAncestor(selected, declaration, false) }
      ) {
        return@firstNotNullOfOrNull null
      }
      val declarationModule = ModuleUtilCore.findModuleForPsiElement(declaration)
      val isVisible = (declaration as? KtModifierListOwner)?.hasModifier(KtTokens.PRIVATE_KEYWORD) != true &&
        (declaration as? KtModifierListOwner)?.hasModifier(KtTokens.INTERNAL_KEYWORD) != true &&
        (declarationModule == null || declarationModule == targetModule ||
          declarationModule in ModuleRootManager.getInstance(targetModule).dependencies)
      if (isVisible) null else declaration.name ?: reference.text
    }
  }

  private fun findRootModifierArgument(selectedElements: List<PsiElement>): KtValueArgument? {
    val rootCall = selectedElements.singleOrNull() as? KtCallExpression ?: return null
    return rootCall.valueArguments.firstOrNull { argument ->
      argument.getArgumentName()?.asName?.asString() == "modifier"
    }
  }

  private fun KtValueArgument.hasScopeDependentModifier(): Boolean {
    val modifierText = getArgumentExpression()?.text.orEmpty()
    return scopeModifierNames.any { modifierName -> ".${modifierName}(" in modifierText }
  }

  private fun optimizeImports(project: Project, sourceFile: KtFile, targetFile: KtFile) {
    val files = linkedSetOf(sourceFile, targetFile)
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater

      for (file in files.filter(PsiElement::isValid)) {
        try {
          OptimizeImportsProcessor(project, file).run()
        } catch (exception: Exception) {
          log.warn("Could not optimize imports after extracting a composable.", exception)
        }
      }
    }
  }

  private sealed interface TargetFileResult {
    data class Success(val file: KtFile) : TargetFileResult
    data class Failure(val message: String) : TargetFileResult
  }

  private companion object {
    val log = Logger.getInstance(ComposeExtractorService::class.java)
    val scopeModifierNames = setOf(
      "align", "alignBy", "weight", "matchParentSize",
      "fillParentMaxHeight", "fillParentMaxWidth", "fillParentMaxSize"
    )
  }
}
