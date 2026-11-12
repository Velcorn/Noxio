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
import androidx.annotation.StringRes
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
            autoValue -> hasKeys(config)
            else -> false
        }

    /**
     * Whether [config] reports a hardware keyboard that is there and not folded away. Without one
     * the mode keeps the search row with sort and more, so the screen still works by touch.
     */
    fun hasKeys(config: Configuration): Boolean =
        config.keyboard == Configuration.KEYBOARD_QWERTY &&
            config.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES

    /** Whether the mode is on but no keys were found, see [hasKeys]. */
    fun isTouchOnly(context: Context): Boolean =
        isActive(context) && !hasKeys(context.resources.configuration)

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
     * Letters and digits type (search or filter), also a digit printed on a letter key (Alt+W is 1
     * on a BlackBerry style keyboard), Ctrl with a letter is a command named by that letter, and
     * Ctrl with comma or period goes to the previous or next tab.
     *
     * @param keyCode The [KeyEvent] key code.
     * @param alt Whether Alt is held.
     * @param ctrl Whether Ctrl (or Meta) is held.
     * @param char The character the key types as pressed (with Shift, without Alt), 0 if none.
     * @param altChar The character the key types with Alt, 0 if none.
     * @return What to do, or null to let the key do what it does anyway.
     */
    fun actionFor(
        keyCode: Int,
        alt: Boolean,
        ctrl: Boolean,
        char: Int,
        altChar: Int = 0,
    ): KeyAction? {
        // The keys that some phones have for these two.
        when (keyCode) {
            KeyEvent.KEYCODE_SEARCH -> return KeyAction.Find
            KeyEvent.KEYCODE_MENU -> return KeyAction.Menu
        }
        if (ctrl) {
            // Comma and period may need Alt, as on the Communicator; only then is the character
            // with Alt what the key means.
            val tabKey = if (alt) altChar else 0
            return when {
                keyCode == KeyEvent.KEYCODE_COMMA || tabKey == ','.code -> KeyAction.PreviousTab
                keyCode == KeyEvent.KEYCODE_PERIOD || tabKey == '.'.code -> KeyAction.NextTab
                alt -> null
                else -> CTRL[keyCode]
            }
        }
        if (alt) {
            // Digits are printed on letter keys (Alt+W types 1) and start a search like letters.
            if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                return KeyAction.Type((keyCode - KeyEvent.KEYCODE_0).toString())
            }
            if (altChar in '0'.code..'9'.code) return KeyAction.Type(altChar.toChar().toString())
            return if (altChar == '?'.code) KeyAction.Help else null
        }
        return when {
            keyCode == KeyEvent.KEYCODE_SPACE -> KeyAction.PlayPause
            keyCode == KeyEvent.KEYCODE_DEL -> KeyAction.Back
            char == '?'.code -> KeyAction.Help
            char != 0 && Character.isLetterOrDigit(char) ->
                KeyAction.Type(String(Character.toChars(char)))
            else -> null
        }
    }

    private val CTRL =
        mapOf(
            KeyEvent.KEYCODE_N to KeyAction.Next,
            KeyEvent.KEYCODE_B to KeyAction.Previous,
            KeyEvent.KEYCODE_P to KeyAction.Player,
            KeyEvent.KEYCODE_Q to KeyAction.Queue,
            KeyEvent.KEYCODE_S to KeyAction.Shuffle,
            KeyEvent.KEYCODE_R to KeyAction.Repeat,
            KeyEvent.KEYCODE_T to KeyAction.SleepTimer,
            KeyEvent.KEYCODE_F to KeyAction.Find,
            KeyEvent.KEYCODE_O to KeyAction.Sort,
            KeyEvent.KEYCODE_M to KeyAction.Menu,
            KeyEvent.KEYCODE_J to KeyAction.SeekBack,
            KeyEvent.KEYCODE_L to KeyAction.SeekForward,
            KeyEvent.KEYCODE_H to KeyAction.Help,
        )

    /** A row of the shortcut overview: the keys to press and what they do. */
    data class Shortcut(val keys: String, @StringRes val description: Int)

    /** All shortcuts, in the order the overview shows them. */
    val shortcuts =
        listOf(
            Shortcut("a–z · 0–9", R.string.lbl_keys_type),
            Shortcut("Space", R.string.lbl_keys_play_pause),
            Shortcut("Backspace", R.string.lbl_keys_back),
            Shortcut("Ctrl N · Ctrl B", R.string.lbl_keys_next_previous),
            Shortcut("Ctrl J · Ctrl L", R.string.lbl_keys_seek),
            Shortcut("Ctrl P", R.string.lbl_keys_player),
            Shortcut("Ctrl Q", R.string.lbl_keys_queue),
            Shortcut("Ctrl S", R.string.lbl_keys_shuffle),
            Shortcut("Ctrl R", R.string.lbl_keys_repeat),
            Shortcut("Ctrl T", R.string.lbl_keys_sleep_timer),
            Shortcut("Ctrl F", R.string.lbl_keys_find),
            Shortcut("Ctrl O", R.string.lbl_keys_sort),
            Shortcut("Ctrl M", R.string.lbl_keys_menu),
            Shortcut("Ctrl , · Ctrl .", R.string.lbl_keys_tabs),
            Shortcut("Ctrl H · ?", R.string.lbl_keys_help),
        )
}

/** Something a key does in the keyboard phone mode, see [KeyboardMode.actionFor]. */
sealed interface KeyAction {
    data object PlayPause : KeyAction

    data object Previous : KeyAction

    data object Next : KeyAction

    data object SeekBack : KeyAction

    data object SeekForward : KeyAction

    /** Open the player, or close it when it is open. */
    data object Player : KeyAction

    /** Open the queue, or close it when it is open. */
    data object Queue : KeyAction

    /** Shuffle the list that is shown, or turn shuffle on or off in the player. */
    data object Shuffle : KeyAction

    data object Repeat : KeyAction

    data object SleepTimer : KeyAction

    /** Search here: the library, the page that is open, or the queue. */
    data object Find : KeyAction

    data object Sort : KeyAction

    data object Menu : KeyAction

    /** What the back gesture does, except leaving the app. */
    data object Back : KeyAction

    /** The overview of the shortcuts. */
    data object Help : KeyAction

    /** The tab to the left of the shown one. */
    data object PreviousTab : KeyAction

    /** The tab to the right of the shown one. */
    data object NextTab : KeyAction

    /** Start searching or filtering with [text]. */
    data class Type(val text: String) : KeyAction
}
