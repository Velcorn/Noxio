/*
 * Copyright (c) 2022 Auxio Project
 * SongDetailDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.detail

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSongDetailBinding
import org.oxycblt.auxio.detail.list.SongProperty
import org.oxycblt.auxio.detail.list.SongPropertyAdapter
import org.oxycblt.auxio.detail.list.TagEditAdapter
import org.oxycblt.auxio.list.adapter.UpdateInstructions
import org.oxycblt.auxio.music.MusicSettings
import org.oxycblt.auxio.tags.TagEdit
import org.oxycblt.auxio.tags.TagEditMigration
import org.oxycblt.auxio.tags.TagEditResult
import org.oxycblt.auxio.tags.TagEditor
import org.oxycblt.auxio.tags.TagField
import org.oxycblt.auxio.tags.TagWriteResult
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A [ViewBindingMaterialDialogFragment] that shows information about a Song.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class SongDetailDialog : ViewBindingMaterialDialogFragment<DialogSongDetailBinding>() {
    private val detailModel: DetailViewModel by activityViewModels()
    // Information about what song to display is initially within the navigation arguments
    // as a UID, as that is the only safe way to parcel an song.
    private val args: SongDetailDialogArgs by navArgs()
    private val detailAdapter = SongPropertyAdapter()

    @Inject lateinit var tagEditor: TagEditor
    @Inject lateinit var tagEditMigration: TagEditMigration
    @Inject lateinit var musicSettings: MusicSettings

    // Noxio: editing the tags. Null while the properties are only shown.
    private var editAdapter: TagEditAdapter? = null
    private var original: Map<TagField, String> = emptyMap()
    private var editSeparator = ";"
    private var pendingFields: Map<String, List<String>>? = null

    private val writeLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val fields = pendingFields
            if (it.resultCode == Activity.RESULT_OK && fields != null) save(fields, ask = false)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val fields = pendingFields
            if (granted && fields != null) save(fields, ask = false)
        }

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogSongDetailBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        super.onConfigDialog(builder)
        builder
            .setTitle(R.string.lbl_props)
            .setPositiveButton(R.string.lbl_ok, null)
            .setNeutralButton(R.string.lbl_edit_tags, null)
    }

    override fun onStart() {
        super.onStart()
        val dialog = requireDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (editAdapter == null) startEditing() else stopEditing()
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (editAdapter == null) dismiss() else checkAndSave()
        }
        updateButtons()
    }

    private fun startEditing() {
        val song = detailModel.currentSong.value ?: return
        editSeparator = musicSettings.artistSeparator
        original = TagEdit.of(song, editSeparator)
        val adapter = TagEditAdapter(original, editSeparator)
        editAdapter = adapter
        requireBinding().detailProperties.adapter = adapter
        updateButtons()
    }

    private fun stopEditing() {
        editAdapter = null
        requireBinding().detailProperties.adapter = detailAdapter
        updateButtons()
    }

    private fun updateButtons() {
        val dialog = dialog as? AlertDialog ?: return
        val editing = editAdapter != null
        dialog
            .getButton(AlertDialog.BUTTON_NEUTRAL)
            ?.setText(if (editing) R.string.lbl_cancel else R.string.lbl_edit_tags)
        dialog
            .getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setText(if (editing) R.string.lbl_save else R.string.lbl_ok)
    }

    private fun checkAndSave() {
        val adapter = editAdapter ?: return
        when (val result = TagEdit.changes(original, adapter.values, editSeparator)) {
            is TagEditResult.Invalid -> adapter.showInvalid(result.fields)
            is TagEditResult.Write ->
                if (result.fields.isEmpty()) stopEditing() else save(result.fields, ask = true)
        }
    }

    /** Write [fields] into the song's file, asking Android for access first if needed. */
    private fun save(fields: Map<String, List<String>>, ask: Boolean) {
        val song = detailModel.currentSong.value ?: return
        val context = requireContext().applicationContext
        pendingFields = fields
        lifecycleScope.launch {
            tagEditor.permission()?.let {
                if (ask) permissionLauncher.launch(it)
                return@launch
            }
            tagEditMigration.expect(song)
            when (tagEditor.write(song, fields)) {
                TagWriteResult.Saved -> {
                    pendingFields = null
                    Toast.makeText(context, R.string.lng_tags_saved, Toast.LENGTH_SHORT).show()
                    // The library reads the changed file again, which also updates this song,
                    // once Android has seen the change.
                    tagEditor.reloadAfter(listOf(song.uri))
                    dismiss()
                }
                TagWriteResult.NeedsPermission -> {
                    val request =
                        tagEditor.writeRequest(listOf(song.uri))
                            ?: tagEditor.recoverableRequest(song.uri)
                    if (ask && request != null) {
                        writeLauncher.launch(IntentSenderRequest.Builder(request).build())
                    } else {
                        Toast.makeText(context, R.string.err_tags_not_saved, Toast.LENGTH_LONG)
                            .show()
                    }
                }
                TagWriteResult.Failed ->
                    Toast.makeText(context, R.string.err_tags_not_saved, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBindingCreated(binding: DialogSongDetailBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)
        binding.detailProperties.adapter = detailAdapter
        // DetailViewModel handles most initialization from the navigation argument.
        detailModel.setSong(args.songUid)
        detailModel.toShow.consume()
        collectImmediately(detailModel.currentSong, ::updateSong)
        collectImmediately(detailModel.currentSongProperties, ::updateSongProperties)
    }

    private fun updateSong(song: Song?) {
        L.d("No song to show, navigating away")
        if (song == null) {
            findNavController().navigateUp()
            return
        }
    }

    private fun updateSongProperties(songProperties: List<SongProperty>) {
        detailAdapter.update(songProperties, UpdateInstructions.Replace(0))
    }
}
