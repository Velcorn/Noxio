/*
 * Copyright (c) 2026 Noxio Project
 * SleepTimerDialog.kt is part of Noxio.
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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSleepTimerBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * A bottom sheet for starting, checking and stopping the [SleepTimer]. Opened from the toolbar of
 * the playback panel.
 */
@AndroidEntryPoint
class SleepTimerDialog : ViewBindingBottomSheetDialogFragment<DialogSleepTimerBinding>() {
    @Inject lateinit var sleepTimer: SleepTimer

    private enum class Choice {
        END_OF_TRACK,
        SHORT,
        CUSTOM,
        LONG,
    }

    private var choice = Choice.END_OF_TRACK

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

        if (savedInstanceState == null) {
            // Start from whatever was used last time. Only the first-ever run falls back to
            // the end of the track.
            binding.sleepCustomInput.setText(sleepTimer.lastCustomMinutes.toString())
            choice =
                when (val last = sleepTimer.lastMode) {
                    SleepTimerMode.EndOfTrack -> Choice.END_OF_TRACK
                    is SleepTimerMode.Minutes ->
                        when (last.minutes) {
                            SHORT_MINUTES -> Choice.SHORT
                            LONG_MINUTES -> Choice.LONG
                            else -> {
                                binding.sleepCustomInput.setText(last.minutes.toString())
                                Choice.CUSTOM
                            }
                        }
                }
        } else {
            choice = Choice.valueOf(savedInstanceState.getString(KEY_CHOICE, choice.name))
        }
        binding.sleepFade.isChecked = sleepTimer.fade

        binding.sleepEndOfTrack.setOnClickListener { select(Choice.END_OF_TRACK) }
        binding.sleep30.setOnClickListener { select(Choice.SHORT) }
        binding.sleepCustom.setOnClickListener { select(Choice.CUSTOM) }
        binding.sleep60.setOnClickListener { select(Choice.LONG) }
        binding.sleepCustomInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}

                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}

                override fun afterTextChanged(s: Editable?) = updateStartEnabled()
            }
        )
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
        choice = newChoice
        val binding = requireBinding()
        binding.sleepEndOfTrack.isChecked = choice == Choice.END_OF_TRACK
        binding.sleep30.isChecked = choice == Choice.SHORT
        binding.sleepCustom.isChecked = choice == Choice.CUSTOM
        binding.sleep60.isChecked = choice == Choice.LONG
        binding.sleepCustomContainer.visibility =
            if (choice == Choice.CUSTOM) View.VISIBLE else View.GONE
        updateStartEnabled()
    }

    private fun customMinutes(): Int? =
        requireBinding()
            .sleepCustomInput
            .text
            ?.toString()
            ?.toIntOrNull()
            ?.takeIf { it in SleepTimer.MIN_MINUTES..SleepTimer.MAX_MINUTES }

    private fun updateStartEnabled() {
        val binding = requireBinding()
        val valid = choice != Choice.CUSTOM || customMinutes() != null
        binding.sleepStart.isEnabled = valid
        binding.sleepCustomContainer.error =
            if (!valid && !binding.sleepCustomInput.text.isNullOrEmpty()) {
                getString(R.string.err_sleep_minutes)
            } else {
                null
            }
    }

    private fun start() {
        val mode =
            when (choice) {
                Choice.END_OF_TRACK -> SleepTimerMode.EndOfTrack
                Choice.SHORT -> SleepTimerMode.Minutes(SHORT_MINUTES)
                Choice.LONG -> SleepTimerMode.Minutes(LONG_MINUTES)
                Choice.CUSTOM -> {
                    val minutes = customMinutes() ?: return
                    sleepTimer.lastCustomMinutes = minutes
                    SleepTimerMode.Minutes(minutes)
                }
            }
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
                binding.sleepRemainingNote.setText(R.string.lbl_sleep_stops_when_over)
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
