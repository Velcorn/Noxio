/*
 * Copyright (c) 2026 Auxio Project
 * KeyboardShortcutsDialog.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.ui

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.databinding.DialogKeyboardShortcutsBinding
import org.oxycblt.auxio.databinding.ItemKeyboardShortcutBinding
import org.oxycblt.auxio.util.systemBarInsetsCompat

/** The overview of the keys of the keyboard phone mode, from [KeyboardMode.shortcuts]. */
@AndroidEntryPoint
class KeyboardShortcutsDialog :
    ViewBindingBottomSheetDialogFragment<DialogKeyboardShortcutsBinding>() {
    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogKeyboardShortcutsBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: DialogKeyboardShortcutsBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }
        val inflater = LayoutInflater.from(requireContext())
        for (shortcut in KeyboardMode.shortcuts) {
            val row = ItemKeyboardShortcutBinding.inflate(inflater, binding.keysList, true)
            row.shortcutKeys.text = shortcut.keys
            row.shortcutDescription.setText(shortcut.description)
        }
    }

    companion object {
        const val TAG = "KeyboardShortcutsDialog"
    }
}
