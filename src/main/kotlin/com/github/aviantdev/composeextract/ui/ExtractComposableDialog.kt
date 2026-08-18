package com.github.aviantdev.composeextract.ui

import com.github.aviantdev.composeextract.core.analysis.TargetScopeResolver
import com.github.aviantdev.composeextract.core.model.ExtractComposableConfig
import com.github.aviantdev.composeextract.core.model.TargetDestination
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.Alarm
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
  project: Project,
  defaultComposableName: String = DEFAULT_COMPOSABLE_NAME,
  private val validator: ExtractComposableConfigValidator = ExtractComposableConfigValidator(),
  private val targetScopeResolver: TargetScopeResolver = TargetScopeResolver()
) : DialogWrapper(project) {

  private val validationAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable as Disposable)
  private var composableName: String = defaultComposableName
  private var targetDestination: TargetDestination = TargetDestination.SAME_FILE
  private var includeModifier: Boolean = true

  private lateinit var destinationComboBox: JComboBox<TargetDestination>
  private lateinit var visibilityValueLabel: JLabel
  private lateinit var composableNameField: JTextField
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
      }
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
    })
    scheduleValidationUpdate(immediate = true)
  }

  val config: ExtractComposableConfig?
    get() = validator.buildConfig(composableName, targetDestination, includeModifier)

  override fun createCenterPanel(): JComponent = dialogPanel

  override fun doValidate(): ValidationInfo? {
    dialogPanel.apply()
    targetDestination = destinationComboBox.selectedItem as? TargetDestination ?: TargetDestination.SAME_FILE
    val nameError = validator.validateComposableName(composableName)
    return if (nameError != null) ValidationInfo(nameError, composableNameField) else null
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

  companion object {
    const val DEFAULT_COMPOSABLE_NAME = "ExtractedWidget"
    private const val VALIDATION_DEBOUNCE_MS = 250
  }
}
