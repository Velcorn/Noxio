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

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BackportBottomSheetBehavior
import com.google.android.material.bottomsheet.BackportBottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSleepTimerBinding
import org.oxycblt.auxio.databinding.ViewSleepTimerDurationBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.systemBarInsetsCompat

/**
 * A bottom sheet for starting, checking and stopping the [SleepTimer]. Opened by holding the
 * play/pause button of the playback panel.
 *
 * All three durations can be edited on tap. Confirmed edits are saved and sorted left to right.
 */
@AndroidEntryPoint
class SleepTimerDialog : ViewBindingBottomSheetDialogFragment<DialogSleepTimerBinding>() {
    @Inject lateinit var sleepTimer: SleepTimer
    @Inject lateinit var presets: SleepTimerPresets

    private var minutes = listOf(30, 45, 60)
    private var selectedIndex = 0
    private var editingIndex: Int? = null

    private fun tiles(): List<ViewSleepTimerDurationBinding> =
        requireBinding().let {
            listOf(it.sleepDurationFirst, it.sleepDurationSecond, it.sleepDurationThird)
        }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogSleepTimerBinding.inflate(inflater)

    override fun onBindingCreated(binding: DialogSleepTimerBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.root.setOnApplyWindowInsetsListener { v, insets ->
            val ime =
                WindowInsetsCompat.toWindowInsetsCompat(insets, v)
                    .getInsets(WindowInsetsCompat.Type.ime())
                    .bottom
            v.updatePadding(bottom = maxOf(insets.systemBarInsetsCompat.bottom, ime))
            if (ime > 0) v.post { showEditorAboveKeyboard() }
            insets
        }

        val lastMinutes = sleepTimer.lastMode.minutes
        val legacyCustom =
            if (lastMinutes != 30 && lastMinutes != 60) lastMinutes
            else sleepTimer.lastCustomMinutes
        minutes =
            savedInstanceState?.getIntArray(KEY_MINUTES)?.toList() ?: presets.load(legacyCustom)
        selectedIndex =
            savedInstanceState?.getInt(KEY_SELECTED)
                ?: minutes.indexOf(lastMinutes).coerceAtLeast(0)
        binding.sleepFinishSong.isChecked = sleepTimer.finishSong
        binding.sleepFade.isChecked = sleepTimer.fade
        binding.sleepShake.isChecked = sleepTimer.shakeToReset

        tiles().forEachIndexed { index, tile ->
            tile.durationButton.setOnClickListener { startEditing(index) }
            tile.durationInput.hint = getString(R.string.fmt_sleep_range, SleepTimer.MAX_MINUTES)
            tile.durationInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    finishEditing()
                    true
                } else false
            }
            tile.durationInput.setOnFocusChangeListener { _, focused ->
                if (!focused && editingIndex == index) finishEditing()
            }
        }
        binding.sleepFinishSong.setOnCheckedChangeListener { _, checked ->
            sleepTimer.finishSong = checked
        }
        binding.sleepFade.setOnCheckedChangeListener { _, checked -> sleepTimer.fade = checked }
        binding.sleepShake.setOnCheckedChangeListener { _, checked ->
            sleepTimer.shakeToReset = checked
        }
        binding.sleepStart.setOnClickListener { start() }
        binding.sleepRestart.setOnClickListener { sleepTimer.reset() }
        binding.sleepStop.setOnClickListener {
            sleepTimer.cancel()
            dismiss()
        }

        updateTiles()
        val restoredEditing = savedInstanceState?.getInt(KEY_EDITING, -1) ?: -1
        if (restoredEditing in minutes.indices)
            binding.root.post {
                startEditing(restoredEditing, savedInstanceState?.getString(KEY_DRAFT))
            }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sleepTimer.state.collectLatest(::showState)
            }
        }
    }

    override fun onDestroyBinding(binding: DialogSleepTimerBinding) {
        editingIndex = null
        tiles().forEach { it.durationInput.onFocusChangeListener = null }
        binding.root.setOnApplyWindowInsetsListener(null)
        super.onDestroyBinding(binding)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(KEY_MINUTES, minutes.toIntArray())
        outState.putInt(KEY_SELECTED, selectedIndex)
        outState.putInt(KEY_EDITING, editingIndex ?: -1)
        editingIndex?.let {
            outState.putString(KEY_DRAFT, tiles()[it].durationInput.text?.toString())
        }
    }

    private fun updateTiles() {
        tiles().forEachIndexed { index, tile ->
            tile.durationButton.isChecked = selectedIndex == index
            tile.durationButton.text =
                if (editingIndex == index) ""
                else getString(R.string.fmt_sleep_minutes, minutes[index])
            tile.durationEditRow.visibility = if (editingIndex == index) View.VISIBLE else View.GONE
        }
        requireBinding().sleepStart.isEnabled = editingIndex == null
    }

    private fun startEditing(index: Int, draft: String? = null) {
        if (editingIndex == index) return
        selectedIndex =
            if (editingIndex != null) {
                val target = minutes[index]
                finishEditing()
                minutes.indexOf(target)
            } else index
        editingIndex = selectedIndex
        updateTiles()
        (dialog as? BackportBottomSheetDialog)?.behavior?.state =
            BackportBottomSheetBehavior.STATE_EXPANDED
        tiles()[selectedIndex].durationInput.apply {
            setText(draft ?: String.format(Locale.US, "%d", minutes[selectedIndex]))
            requestFocus()
            selectAll()
            post {
                context
                    .getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                showEditorAboveKeyboard()
            }
        }
    }

    private fun showEditorAboveKeyboard() {
        val index = editingIndex ?: return
        val input = tiles()[index].durationInput
        input.requestRectangleOnScreen(Rect(0, 0, input.width, input.height), true)
    }

    private fun finishEditing() {
        val index = editingIndex ?: return
        editingIndex = null
        val input = tiles()[index].durationInput
        SleepTimerPresets.edit(minutes, index, input.text?.toString().orEmpty())?.let { edited ->
            minutes = edited.minutes
            selectedIndex = edited.selectedIndex
            presets.save(minutes)
            sleepTimer.lastCustomMinutes = minutes[selectedIndex]
        }
        input.clearFocus()
        context
            ?.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(input.windowToken, 0)
        updateTiles()
    }

    private fun start() {
        finishEditing()
        val mode = SleepTimerMode.Minutes(minutes[selectedIndex])
        sleepTimer.finishSong = requireBinding().sleepFinishSong.isChecked
        sleepTimer.fade = requireBinding().sleepFade.isChecked
        sleepTimer.shakeToReset = requireBinding().sleepShake.isChecked
        pendingMode = mode
        // The countdown also shows in a notification of its own, which needs the permission to
        // post notifications. The timer starts either way.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPending()
        }
    }

    private var pendingMode: SleepTimerMode? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startPending() }

    private fun startPending() {
        val mode = pendingMode ?: return
        pendingMode = null
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
                    if (active.pausedRemainingMs != null) R.string.lbl_sleep_paused
                    else if (active.finishSong) R.string.lbl_sleep_stops_after_song_over
                    else R.string.lbl_sleep_stops_when_over
                )
                binding.sleepRestart.visibility = View.VISIBLE
                // collectLatest cancels this suspend function as soon as the state changes.
                while (true) {
                    val remaining = active.remainingMs()
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
        private const val KEY_MINUTES = "noxio_sleep_durations"
        private const val KEY_SELECTED = "noxio_sleep_selected"
        private const val KEY_EDITING = "noxio_sleep_editing"
        private const val KEY_DRAFT = "noxio_sleep_draft"
        private const val TICK_MS = 250L
    }
}
