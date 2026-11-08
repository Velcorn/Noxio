/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimerDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.timer

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSleepTimerBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * A bottom sheet for starting, checking and stopping the [SleepTimer]. Opened from the button above
 * the seek bar of the playback panel.
 *
 * The Custom tile doubles as the input: tap it to type a number of minutes, after which it shows "N
 * min". Tapping it again while selected edits the value again.
 */
@AndroidEntryPoint
class SleepTimerDialog : ViewBindingBottomSheetDialogFragment<DialogSleepTimerBinding>() {
    @Inject lateinit var sleepTimer: SleepTimer

    private enum class Choice {
        SHORT,
        CUSTOM,
        LONG,
    }

    private var choice = Choice.SHORT
    private var customMinutes: Int? = null
    private var editing = false

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogSleepTimerBinding.inflate(inflater)

    override fun onBindingCreated(binding: DialogSleepTimerBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.root.setOnApplyWindowInsetsListener { v, insets ->
            v.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }

        binding.sleep30.text = getString(R.string.fmt_sleep_minutes, SHORT_MINUTES)
        binding.sleep60.text = getString(R.string.fmt_sleep_minutes, LONG_MINUTES)

        customMinutes = sleepTimer.lastCustomMinutes
        if (savedInstanceState == null) {
            // Start from whatever was used last time.
            val last = sleepTimer.lastMode
            choice =
                when (last.minutes) {
                    SHORT_MINUTES -> Choice.SHORT
                    LONG_MINUTES -> Choice.LONG
                    else -> {
                        customMinutes = last.minutes
                        Choice.CUSTOM
                    }
                }
        } else {
            choice = Choice.valueOf(savedInstanceState.getString(KEY_CHOICE, choice.name))
        }
        binding.sleepFinishSong.isChecked = sleepTimer.finishSong
        binding.sleepFade.isChecked = sleepTimer.fade

        binding.sleep30.setOnClickListener { select(Choice.SHORT) }
        binding.sleep60.setOnClickListener { select(Choice.LONG) }
        binding.sleepCustom.setOnClickListener {
            if (editing) {
                return@setOnClickListener
            }
            // The first tap on a saved value selects it, a tap on the selected tile edits it.
            if (customMinutes != null && choice != Choice.CUSTOM) {
                select(Choice.CUSTOM)
            } else {
                startEditing()
            }
        }
        binding.sleepCustomInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishEditing()
                true
            } else {
                false
            }
        }
        binding.sleepCustomInput.setOnFocusChangeListener { _, focused ->
            if (!focused) {
                finishEditing()
            }
        }
        binding.sleepFinishSong.setOnCheckedChangeListener { _, checked ->
            sleepTimer.finishSong = checked
        }
        binding.sleepFade.setOnCheckedChangeListener { _, checked -> sleepTimer.fade = checked }
        binding.sleepStart.setOnClickListener { start() }
        binding.sleepRestart.setOnClickListener { sleepTimer.reset() }
        binding.sleepStop.setOnClickListener {
            sleepTimer.cancel()
            dismiss()
        }

        select(choice)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sleepTimer.state.collectLatest(::showState)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CHOICE, choice.name)
    }

    private fun select(newChoice: Choice) {
        finishEditing()
        choice = newChoice
        updateTiles()
    }

    private fun updateTiles() {
        val binding = requireBinding()
        binding.sleep30.isChecked = choice == Choice.SHORT
        binding.sleepCustom.isChecked = choice == Choice.CUSTOM
        binding.sleep60.isChecked = choice == Choice.LONG
        val custom = customMinutes
        binding.sleepCustom.text =
            when {
                editing -> ""
                custom != null -> getString(R.string.fmt_sleep_minutes, custom)
                else -> getString(R.string.lbl_sleep_custom)
            }
        binding.sleepCustomEditRow.visibility = if (editing) View.VISIBLE else View.GONE
        binding.sleepStart.isEnabled = choice != Choice.CUSTOM || (custom != null && !editing)
    }

    private fun startEditing() {
        val binding = requireBinding()
        choice = Choice.CUSTOM
        editing = true
        updateTiles()
        binding.sleepCustomInput.apply {
            setText(
                String.format(
                    Locale.US,
                    "%d",
                    customMinutes ?: SleepTimer.DEFAULT_CUSTOM_MINUTES,
                )
            )
            requestFocus()
            selectAll()
            post {
                context
                    .getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun finishEditing() {
        if (!editing) {
            return
        }
        // Clear this first, since hiding the keyboard and clearing focus re-enter here.
        editing = false
        val binding = requireBinding()
        val minutes = binding.sleepCustomInput.text?.toString()?.toIntOrNull()
        if (minutes != null && minutes in SleepTimer.MIN_MINUTES..SleepTimer.MAX_MINUTES) {
            customMinutes = minutes
            sleepTimer.lastCustomMinutes = minutes
        }
        binding.sleepCustomInput.clearFocus()
        context
            ?.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.sleepCustomInput.windowToken, 0)
        updateTiles()
    }

    private fun start() {
        finishEditing()
        val mode =
            when (choice) {
                Choice.SHORT -> SleepTimerMode.Minutes(SHORT_MINUTES)
                Choice.LONG -> SleepTimerMode.Minutes(LONG_MINUTES)
                Choice.CUSTOM -> SleepTimerMode.Minutes(customMinutes ?: return)
            }
        sleepTimer.finishSong = requireBinding().sleepFinishSong.isChecked
        sleepTimer.fade = requireBinding().sleepFade.isChecked
        sleepTimer.start(mode)
        dismiss()
    }

    private suspend fun showState(state: SleepTimerState) {
        val binding = requireBinding()
        val active = state as? SleepTimerState.Active
        binding.sleepSetup.visibility = if (active == null) View.VISIBLE else View.GONE
        binding.sleepRunning.visibility = if (active != null) View.VISIBLE else View.GONE
        if (active == null) {
            return
        }
        when (active.mode) {
            SleepTimerMode.EndOfTrack -> {
                binding.sleepRemaining.setText(R.string.lbl_sleep_end_of_track)
                binding.sleepRemainingNote.setText(R.string.lbl_sleep_stops_after_track)
                binding.sleepRestart.visibility = View.GONE
            }
            is SleepTimerMode.Minutes -> {
                binding.sleepRemainingNote.setText(
                    if (active.finishSong) R.string.lbl_sleep_stops_after_song_over
                    else R.string.lbl_sleep_stops_when_over
                )
                binding.sleepRestart.visibility = View.VISIBLE
                // collectLatest cancels this suspend function as soon as the state changes.
                while (true) {
                    val remaining = active.endsAt - SystemClock.elapsedRealtime()
                    binding.sleepRemaining.text = formatRemaining(remaining)
                    delay(TICK_MS)
                }
            }
        }
    }

    private fun formatRemaining(remainingMs: Long): String {
        val totalSeconds = ((remainingMs + 999) / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    companion object {
        const val TAG = "SleepTimerDialog"
        private const val KEY_CHOICE = "noxio_sleep_choice"
        private const val SHORT_MINUTES = 30
        private const val LONG_MINUTES = 60
        private const val TICK_MS = 250L
    }
}
