package com.github.aviantdev.composeextract.ui

import com.github.aviantdev.composeextract.core.analysis.TargetScopeResolver
import com.github.aviantdev.composeextract.core.model.ExtractComposableConfig
import com.github.aviantdev.composeextract.core.model.TargetDestination
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.Alarm
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

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
      targetFolderField = TextFieldWithBrowseButton().apply {
        textField.isEditable = false
        addActionListener { chooseTargetFolder() }
      }
      cell(targetFolderField).align(AlignX.FILL)
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

  private fun chooseTargetFolder() {
    val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    descriptor.title = "Select target folder"
    descriptor.description = "Choose a folder inside the destination module's source root."
    FileChooser.chooseFile(descriptor, project, null) { directory ->
      val targetModule = ModuleUtilCore.findModuleForFile(directory, project)
      val fileIndex = com.intellij.openapi.roots.ProjectRootManager.getInstance(project).fileIndex
      val sourceRoot = fileIndex.getSourceRootForFile(directory)
      if (targetModule == null || sourceRoot == null ||
        !fileIndex.isInSourceContent(directory) || fileIndex.isInTestSourceContent(directory)
      ) {
        Messages.showErrorDialog(project, "Choose a folder inside a production source root.", "Extract Composable")
        return@chooseFile
      }
      val relativePath = VfsUtilCore.getRelativePath(directory, sourceRoot, '/') ?: return@chooseFile
      targetModuleName = targetModule.name
      targetPackageName = relativePath.replace('/', '.')
      targetDirectoryPath = directory.path
      targetFolderField.text = directory.path
      updateTargetFilePath()
    }
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
