package com.github.aviantdev.composeextract.ui

import com.github.aviantdev.composeextract.core.model.TargetDestination
import com.intellij.ui.SimpleListCellRenderer
import javax.swing.JList

class DestinationListCellRenderer : SimpleListCellRenderer<TargetDestination>() {

  override fun customize(
    list: JList<out TargetDestination>,
    value: TargetDestination?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean
  ) {
    text = when (value) {
      TargetDestination.SAME_FILE -> "Same File"
      TargetDestination.NEW_FILE_SAME_MODULE -> "New File (Same Module)"
      TargetDestination.NEW_FILE_DIFFERENT_MODULE -> "New File (Cross-Module / Design System)"
      null -> ""
    }
  }
}
