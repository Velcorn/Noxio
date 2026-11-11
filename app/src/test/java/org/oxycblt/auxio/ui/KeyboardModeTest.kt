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
    fun theSettingIsReadFromThePreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.set_key_keyboard_mode)
        prefs.edit { putInt(key, context.resources.getInteger(R.integer.keyboard_mode_on)) }
        assertTrue(KeyboardMode.isActive(context))
        prefs.edit { putInt(key, context.resources.getInteger(R.integer.keyboard_mode_off)) }
        assertFalse(KeyboardMode.isActive(context))
    }

    @Test
    fun altKeysControlPlaybackAndTabs() {
        fun alt(code: Int) = KeyboardMode.actionFor(code, alt = true, ctrl = false, char = 0)
        assertEquals(KeyAction.Previous, alt(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(KeyAction.Next, alt(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(KeyAction.OpenPlayer, alt(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(KeyAction.ClosePlayer, alt(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(KeyAction.Tab(0), alt(KeyEvent.KEYCODE_1))
        assertEquals(KeyAction.Tab(3), alt(KeyEvent.KEYCODE_4))
        assertNull(alt(KeyEvent.KEYCODE_A))
    }

    @Test
    fun spacePlaysAndLettersOrDigitsType() {
        assertEquals(
            KeyAction.PlayPause,
            KeyboardMode.actionFor(KeyEvent.KEYCODE_SPACE, false, false, ' '.code),
        )
        assertEquals(
            KeyAction.Type("a"),
            KeyboardMode.actionFor(KeyEvent.KEYCODE_A, false, false, 'a'.code),
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
    fun otherKeysAndCtrlAreLeftAlone() {
        assertNull(KeyboardMode.actionFor(KeyEvent.KEYCODE_PERIOD, false, false, '.'.code))
        assertNull(KeyboardMode.actionFor(KeyEvent.KEYCODE_DPAD_DOWN, false, false, 0))
        assertNull(KeyboardMode.actionFor(KeyEvent.KEYCODE_A, false, true, 'a'.code))
        assertNull(KeyboardMode.actionFor(KeyEvent.KEYCODE_ENTER, false, false, 0))
    }
}
