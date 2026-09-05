package com.github.aviantdev.composeextract.ui

import com.github.aviantdev.composeextract.core.analysis.TargetScopeResolver
import com.github.aviantdev.composeextract.core.model.ExtractComposableConfig
import com.github.aviantdev.composeextract.core.model.TargetDestination
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.Alarm
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.FileColorManager
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import java.awt.Color
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.tree.TreePath

class ExtractComposableDialog(
  private val project: Project,
  defaultComposableName: String = DEFAULT_COMPOSABLE_NAME,
  private val validator: ExtractComposableConfigValidator = ExtractComposableConfigValidator(),
  private val targetScopeResolver: TargetScopeResolver = TargetScopeResolver()
) : DialogWrapper(project) {

  private val validationAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable as Disposable)
  private var composableName: String = defaultComposableName
  private var targetDestination: TargetDestination = TargetDestination.SAME_FILE
  private var includeModifier: Boolean = true
  private var targetModuleName: String = ""
  private var targetPackageName: String = ""
  private var targetDirectoryPath: String = ""
  private val projectDirectory = project.guessProjectDir()

  private lateinit var destinationComboBox: JComboBox<TargetDestination>
  private lateinit var visibilityValueLabel: JLabel
  private lateinit var composableNameField: JTextField
  private lateinit var targetFolderField: TextFieldWithBrowseButton
  private lateinit var targetFilePathLabel: JLabel
  private lateinit var nameValidator: ComponentValidator

  private val dialogPanel: DialogPanel = panel {
    row("Composable name:") {
      val textFieldCell = textField()
        .align(AlignX.FILL)
        .columns(30)
        .bindText(::composableName)
        .validationOnApply { validateName() }
        .focused()
      composableNameField = textFieldCell.component
    }

    row("Destination:") {
      val comboBoxCell = comboBox(TargetDestination.entries.toList())
        .align(AlignX.FILL)
      destinationComboBox = comboBoxCell.component
      destinationComboBox.renderer = DestinationListCellRenderer()
      destinationComboBox.selectedItem = targetDestination
      destinationComboBox.addActionListener {
        targetDestination = destinationComboBox.selectedItem as? TargetDestination ?: TargetDestination.SAME_FILE
        visibilityValueLabel.text = visibilityLabelText()
        updateCrossModuleControls()
      }
    }

    row("Target folder:") {
      val descriptor = object : FileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor()) {
        // Pass the project so the IDE's icon providers can recognize modules, source roots and packages.
        override fun getIcon(file: VirtualFile): Icon = IconUtil.getIcon(file, 0, project)
      }.apply {
        title = "Select target folder"
        description = "Choose a folder inside the destination module's source root."
        isForcedToUseIdeaFileChooser = true
        projectDirectory?.let { setRoots(it) }
        isShowFileSystemRoots = false
        withTreeRootVisible(true)
      }
      // Create the component without the DSL helper's default file-chooser listener.
      // The custom listener below opens the IDE directory tree directly.
      targetFolderField = cell(TextFieldWithBrowseButton())
        .align(AlignX.FILL)
        .bindText(::targetDirectoryPath)
        .component
      targetFolderField.textField.isEditable = false
      targetFolderField.addActionListener {
        val root = projectDirectory
        if (root == null) {
          Messages.showErrorDialog(project, "Cannot locate the active project directory.", "Extract Composable")
          return@addActionListener
        }
        val initialDirectory = targetDirectoryPath.takeIf { it.isNotBlank() }
          ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
          ?.takeIf { it.isValid && it.isDirectory && VfsUtilCore.isAncestor(root, it, false) }
          ?: root
        val chooser = object : FileChooserDialogImpl(descriptor, targetFolderField, project) {
          override fun createInternalTree(): Tree = object : Tree() {
            override fun isFileColorsEnabled(): Boolean {
              val colors = FileColorManager.getInstance(project)
              return colors.isEnabled && colors.isEnabledForProjectView
            }

            override fun getFileColorForPath(path: TreePath): Color? {
              if (!isFileColorsEnabled) return null
              val file = FileSystemTreeImpl.getVirtualFile(path) ?: return null
              return FileColorManager.getInstance(project).getFileColor(file)
            }
          }
        }
        val directory = chooser.choose(project, initialDirectory)
          .singleOrNull() ?: return@addActionListener
        targetFolderField.text = selectTargetFolder(directory)
      }
    }

    row("Target file:") {
      val labelCell = label("").resizableColumn()
      targetFilePathLabel = labelCell.component
    }

    row("Visibility:") {
      val labelCell = label("")
        .resizableColumn()
      visibilityValueLabel = labelCell.component
      visibilityValueLabel.text = visibilityLabelText()
    }

    row {
      checkBox("Include Modifier parameter")
        .bindSelected(::includeModifier)
    }
  }

  init {
    title = "Extract Composable"
    init()
    nameValidator = ComponentValidator(disposable)
      .withValidator { validateName() }
      .installOn(composableNameField)
    dialogPanel.registerValidators(disposable)
    initValidation()
    dialogPanel.apply()
    composableNameField.document.addDocumentListener(SimpleDocumentChangeListener {
      scheduleValidationUpdate()
      updateTargetFilePath()
    })
    scheduleValidationUpdate(immediate = true)
    updateCrossModuleControls()
  }

  val config: ExtractComposableConfig?
    get() = validator.buildConfig(
      composableName,
      targetDestination,
      includeModifier,
      targetModuleName,
      targetPackageName,
      targetDirectoryPath
    )

  override fun createCenterPanel(): JComponent = dialogPanel

  override fun doValidate(): ValidationInfo? {
    dialogPanel.apply()
    targetDestination = destinationComboBox.selectedItem as? TargetDestination ?: TargetDestination.SAME_FILE
    val nameError = validator.validateComposableName(composableName)
    if (nameError != null) return ValidationInfo(nameError, composableNameField)
    if (targetDestination == TargetDestination.NEW_FILE_DIFFERENT_MODULE && targetModuleName.isBlank()) {
      return ValidationInfo("Choose a destination folder inside a module source root.", targetFolderField)
    }
    return null
  }

  private fun validateName(): ValidationInfo? {
    val error = validator.validateComposableName(composableName)
    return if (error != null) ValidationInfo(error) else null
  }

  private fun scheduleValidationUpdate(immediate: Boolean = false) {
    validationAlarm.cancelAllRequests()
    val delay = if (immediate) 0 else VALIDATION_DEBOUNCE_MS
    validationAlarm.addRequest(
      {
        dialogPanel.apply()
        targetDestination = destinationComboBox.selectedItem as? TargetDestination ?: TargetDestination.SAME_FILE
        val validationInfo = validateName()
        nameValidator.revalidate()
        isOKActionEnabled = validationInfo == null
      },
      delay
    )
  }

  private fun visibilityLabelText(): String {
    val visibility = targetScopeResolver.resolveVisibility(targetDestination)
    return visibility.keyword.ifEmpty { "public" }
  }

  private fun updateCrossModuleControls() {
    val enabled = targetDestination == TargetDestination.NEW_FILE_DIFFERENT_MODULE
    targetFolderField.isEnabled = enabled
    targetFilePathLabel.isEnabled = enabled
    updateTargetFilePath()
  }

  private fun selectTargetFolder(directory: VirtualFile): String {
    val root = projectDirectory
    if (root == null || !VfsUtilCore.isAncestor(root, directory, false)) {
      Messages.showErrorDialog(project, "Choose a folder inside the active project.", "Extract Composable")
      return targetDirectoryPath
    }
    val targetModule = ModuleUtilCore.findModuleForFile(directory, project)
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val sourceRoot = fileIndex.getSourceRootForFile(directory)
    if (targetModule == null || sourceRoot == null ||
      !fileIndex.isInSourceContent(directory) || fileIndex.isInTestSourceContent(directory)
    ) {
      Messages.showErrorDialog(project, "Choose a folder inside a production source root.", "Extract Composable")
      return targetDirectoryPath
    }
    val relativePath = VfsUtilCore.getRelativePath(directory, sourceRoot, '/') ?: return targetDirectoryPath
    targetModuleName = targetModule.name
    targetPackageName = relativePath.replace('/', '.')
    targetDirectoryPath = directory.path
    updateTargetFilePath()
    return targetDirectoryPath
  }

  private fun updateTargetFilePath() {
    val packagePath = targetPackageName.trim().replace('.', '/').trim('/')
    val filePath = listOf(packagePath, "${composableName.trim()}.kt")
      .filter { it.isNotBlank() }
      .joinToString("/")
    targetFilePathLabel.text = filePath
  }

  companion object {
    const val DEFAULT_COMPOSABLE_NAME = "ExtractedWidget"
    private const val VALIDATION_DEBOUNCE_MS = 250
  }
}
