/*
 * Copyright (c) 2026 Auxio Project
 * KeyboardMode.kt is part of Auxio.
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

import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import org.oxycblt.auxio.R

/**
 * The keyboard phone mode, for phones with a hardware keyboard like the Clicks Communicator: no
 * search row (typing opens the search), and keys for playback and the tabs.
 */
object KeyboardMode {
    /**
     * Whether the mode is on for the saved [setting] (one of the `keyboard_mode_*` integers) and
     * the keyboard described by [config]. Auto follows whether a hardware keyboard is there and not
     * folded away.
     */
    fun isActive(setting: Int, config: Configuration, autoValue: Int, onValue: Int): Boolean =
        when (setting) {
            onValue -> true
            autoValue ->
                config.keyboard == Configuration.KEYBOARD_QWERTY &&
                    config.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
            else -> false
        }

    /** Whether the mode is on now, read from the settings. */
    fun isActive(context: Context): Boolean {
        val auto = context.resources.getInteger(R.integer.keyboard_mode_auto)
        val setting =
            PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(context.getString(R.string.set_key_keyboard_mode), auto)
        return isActive(
            setting,
            context.resources.configuration,
            auto,
            context.resources.getInteger(R.integer.keyboard_mode_on),
        )
    }

    /**
     * What a key press does outside of a text field.
     *
     * @param keyCode The [KeyEvent] key code.
     * @param alt Whether Alt is held.
     * @param ctrl Whether Ctrl (or Meta) is held.
     * @param char The character the key types without modifiers, 0 if none.
     * @return What to do, or null to let the key do what it does anyway.
     */
    fun actionFor(keyCode: Int, alt: Boolean, ctrl: Boolean, char: Int): KeyAction? {
        if (ctrl) return null
        if (alt) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> KeyAction.Previous
                KeyEvent.KEYCODE_DPAD_RIGHT -> KeyAction.Next
                KeyEvent.KEYCODE_DPAD_UP -> KeyAction.OpenPlayer
                KeyEvent.KEYCODE_DPAD_DOWN -> KeyAction.ClosePlayer
                in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 ->
                    KeyAction.Tab(keyCode - KeyEvent.KEYCODE_1)
                else -> null
            }
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE) return KeyAction.PlayPause
        return if (char != 0 && Character.isLetterOrDigit(char)) {
            KeyAction.Type(String(Character.toChars(char)))
        } else {
            null
        }
    }
}

/** Something a key does in the keyboard phone mode, see [KeyboardMode.actionFor]. */
sealed interface KeyAction {
    data object PlayPause : KeyAction

    data object Previous : KeyAction

    data object Next : KeyAction

    data object OpenPlayer : KeyAction

    data object ClosePlayer : KeyAction

    /** Switch to the tab at [index], 0 for the first. */
    data class Tab(val index: Int) : KeyAction

    /** Start searching or filtering with [text]. */
    data class Type(val text: String) : KeyAction
}
