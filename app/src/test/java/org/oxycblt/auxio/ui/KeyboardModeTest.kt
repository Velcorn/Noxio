/*
 * Copyright (c) 2026 Auxio Project
 * KeyboardModeTest.kt is part of Auxio.
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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.oxycblt.auxio.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardModeTest {
    private val auto = 0
    private val on = 1
    private val off = 2

    private fun config(keyboard: Int, hidden: Int) =
        Configuration().apply {
            this.keyboard = keyboard
            hardKeyboardHidden = hidden
        }

    @Test
    fun autoFollowsAHardwareKeyboardThatIsOut() {
        val out = config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_NO)
        val folded = config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_YES)
        val none = config(Configuration.KEYBOARD_NOKEYS, Configuration.HARDKEYBOARDHIDDEN_YES)
        assertTrue(KeyboardMode.isActive(auto, out, auto, on))
        assertFalse(KeyboardMode.isActive(auto, folded, auto, on))
        assertFalse(KeyboardMode.isActive(auto, none, auto, on))
    }

    @Test
    fun onAndOffIgnoreTheKeyboard() {
        val none = config(Configuration.KEYBOARD_NOKEYS, Configuration.HARDKEYBOARDHIDDEN_YES)
        val out = config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_NO)
        assertTrue(KeyboardMode.isActive(on, none, auto, on))
        assertFalse(KeyboardMode.isActive(off, out, auto, on))
    }

    @Test
    fun keysAreOnlyThereWhenAKeyboardIsOut() {
        assertTrue(
            KeyboardMode.hasKeys(
                config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_NO)
            )
        )
        assertFalse(
            KeyboardMode.hasKeys(
                config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_YES)
            )
        )
        assertFalse(
            KeyboardMode.hasKeys(
                config(Configuration.KEYBOARD_NOKEYS, Configuration.HARDKEYBOARDHIDDEN_YES)
            )
        )
    }

    @Test
    fun switchedOnWithoutKeysTheScreenStaysUsableByTouch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.set_key_keyboard_mode)
        // Robolectric reports no hardware keyboard.
        prefs.edit { putInt(key, context.resources.getInteger(R.integer.keyboard_mode_on)) }
        assertTrue(KeyboardMode.isTouchOnly(context))
        prefs.edit { putInt(key, context.resources.getInteger(R.integer.keyboard_mode_off)) }
        assertFalse(KeyboardMode.isTouchOnly(context))
    }

    @Test
    fun theSettingIsReadFromThePreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.set_key_keyboard_mode)
        prefs.edit { putInt(key, context.resources.getInteger(R.integer.keyboard_mode_on)) }
        assertTrue(KeyboardMode.isActive(context))
        prefs.edit { putInt(key, context.resources.getInteger(R.integer.keyboard_mode_off)) }
        assertFalse(KeyboardMode.isActive(context))
    }

    private fun ctrl(code: Int) = KeyboardMode.actionFor(code, alt = false, ctrl = true, char = 0)

    @Test
    fun ctrlLettersAreNamedCommands() {
        assertEquals(KeyAction.Next, ctrl(KeyEvent.KEYCODE_N))
        assertEquals(KeyAction.Previous, ctrl(KeyEvent.KEYCODE_B))
        assertEquals(KeyAction.Player, ctrl(KeyEvent.KEYCODE_P))
        assertEquals(KeyAction.Queue, ctrl(KeyEvent.KEYCODE_Q))
        assertEquals(KeyAction.Shuffle, ctrl(KeyEvent.KEYCODE_S))
        assertEquals(KeyAction.Repeat, ctrl(KeyEvent.KEYCODE_R))
        assertEquals(KeyAction.SleepTimer, ctrl(KeyEvent.KEYCODE_T))
        assertEquals(KeyAction.Find, ctrl(KeyEvent.KEYCODE_F))
        assertEquals(KeyAction.Sort, ctrl(KeyEvent.KEYCODE_O))
        assertEquals(KeyAction.Menu, ctrl(KeyEvent.KEYCODE_M))
        assertEquals(KeyAction.SeekBack, ctrl(KeyEvent.KEYCODE_J))
        assertEquals(KeyAction.SeekForward, ctrl(KeyEvent.KEYCODE_L))
        assertEquals(KeyAction.Help, ctrl(KeyEvent.KEYCODE_H))
        // Unused letters and copy or paste keep their usual meaning.
        assertNull(ctrl(KeyEvent.KEYCODE_A))
        assertNull(ctrl(KeyEvent.KEYCODE_C))
        assertNull(ctrl(KeyEvent.KEYCODE_V))
    }

    @Test
    fun altWithAPrintedNumberTypesIt() {
        fun alt(code: Int, altChar: Int = 0) =
            KeyboardMode.actionFor(code, alt = true, ctrl = false, char = 0, altChar = altChar)
        // A BlackBerry style keyboard: Alt+W types 1, Alt+S types 4, Alt+0 is on its own key.
        assertEquals(KeyAction.Type("1"), alt(KeyEvent.KEYCODE_W, '1'.code))
        assertEquals(KeyAction.Type("4"), alt(KeyEvent.KEYCODE_S, '4'.code))
        assertEquals(KeyAction.Type("0"), alt(KeyEvent.KEYCODE_Z, '0'.code))
        // A keyboard with a number row.
        assertEquals(KeyAction.Type("1"), alt(KeyEvent.KEYCODE_1))
        assertEquals(KeyAction.Type("9"), alt(KeyEvent.KEYCODE_9))
        // Alt+B types ? there.
        assertEquals(KeyAction.Help, alt(KeyEvent.KEYCODE_B, '?'.code))
        assertNull(alt(KeyEvent.KEYCODE_T, '('.code))
        assertNull(alt(KeyEvent.KEYCODE_A))
    }

    @Test
    fun ctrlWithCommaOrPeriodSwitchesTabs() {
        assertEquals(KeyAction.PreviousTab, ctrl(KeyEvent.KEYCODE_COMMA))
        assertEquals(KeyAction.NextTab, ctrl(KeyEvent.KEYCODE_PERIOD))
        fun ctrlAlt(code: Int, altChar: Int) =
            KeyboardMode.actionFor(code, alt = true, ctrl = true, char = 0, altChar = altChar)
        // Where comma and period need Alt, as on a BlackBerry style keyboard.
        assertEquals(KeyAction.PreviousTab, ctrlAlt(KeyEvent.KEYCODE_N, ','.code))
        assertEquals(KeyAction.NextTab, ctrlAlt(KeyEvent.KEYCODE_M, '.'.code))
        // Without Alt the letter is meant, even when it has a period printed on it.
        assertEquals(
            KeyAction.Menu,
            KeyboardMode.actionFor(KeyEvent.KEYCODE_M, false, true, 0, '.'.code),
        )
        assertNull(ctrlAlt(KeyEvent.KEYCODE_P, '0'.code))
    }

    @Test
    fun spacePlaysBackspaceGoesBackAndQuestionMarkHelps() {
        assertEquals(
            KeyAction.PlayPause,
            KeyboardMode.actionFor(KeyEvent.KEYCODE_SPACE, false, false, ' '.code),
        )
        assertEquals(KeyAction.Back, KeyboardMode.actionFor(KeyEvent.KEYCODE_DEL, false, false, 0))
        assertEquals(
            KeyAction.Help,
            KeyboardMode.actionFor(KeyEvent.KEYCODE_SLASH, false, false, '?'.code),
        )
    }

    @Test
    fun lettersOrDigitsType() {
        assertEquals(
            KeyAction.Type("a"),
            KeyboardMode.actionFor(KeyEvent.KEYCODE_A, false, false, 'a'.code),
        )
        assertEquals(
            KeyAction.Type("A"),
            KeyboardMode.actionFor(KeyEvent.KEYCODE_A, false, false, 'A'.code),
        )
        assertEquals(
            KeyAction.Type("2"),
            KeyboardMode.actionFor(KeyEvent.KEYCODE_2, false, false, '2'.code),
        )
        assertEquals(
            KeyAction.Type("ü"),
            KeyboardMode.actionFor(KeyEvent.KEYCODE_U, false, false, 'ü'.code),
        )
    }

    @Test
    fun otherKeysAreLeftAlone() {
        assertNull(KeyboardMode.actionFor(KeyEvent.KEYCODE_PERIOD, false, false, '.'.code))
        assertNull(KeyboardMode.actionFor(KeyEvent.KEYCODE_DPAD_DOWN, false, false, 0))
        assertNull(KeyboardMode.actionFor(KeyEvent.KEYCODE_ENTER, false, false, 0))
    }

    @Test
    fun theOverviewListsEveryCommandOnce() {
        val shortcuts = KeyboardMode.shortcuts
        assertEquals(shortcuts.size, shortcuts.map { it.description }.toSet().size)
        assertEquals(shortcuts.size, shortcuts.map { it.keys }.toSet().size)
        // Every Ctrl letter that does something is in the overview.
        val listed = shortcuts.joinToString(" ") { it.keys }
        for (letter in "NBPQSRTFOMJLH") {
            val code = KeyEvent.keyCodeFromString("KEYCODE_$letter")
            assertTrue("Ctrl $letter", ctrl(code) != null)
            assertTrue("Ctrl $letter listed", listed.contains("Ctrl $letter"))
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        shortcuts.forEach { assertTrue(context.getString(it.description).isNotBlank()) }
    }
}
