/*
 * Copyright (c) 2026 Auxio Project
 * StatsShareDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.stats

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import org.oxycblt.auxio.BuildConfig
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogStatsShareBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.showToast
import org.oxycblt.auxio.util.systemBarInsetsCompat
import timber.log.Timber as L

/**
 * Previews the card of the stats screen as an image and lets the user share it or save it. The
 * numbers come from the [StatsViewModel] of the stats screen.
 */
@AndroidEntryPoint
class StatsShareDialog : ViewBindingBottomSheetDialogFragment<DialogStatsShareBinding>() {
    private val statsModel: StatsViewModel by
        viewModels(ownerProducer = { requireParentFragment() })
    private var bitmap: Bitmap? = null
    private val saveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            if (uri != null) {
                save(uri)
            }
        }

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogStatsShareBinding.inflate(inflater)

    override fun onBindingCreated(binding: DialogStatsShareBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            view.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }

        val ui = statsModel.ui.value
        if (ui == null) {
            dismiss()
            return
        }
        val image = StatsImage.render(requireContext(), content(ui))
        bitmap = image
        binding.sharePreview.setImageBitmap(image)

        binding.shareSend.setOnClickListener { share() }
        binding.shareSave.setOnClickListener {
            saveLauncher.launch("noxio-${statsModel.category.value.name.lowercase()}.png")
        }
    }

    override fun onDestroyBinding(binding: DialogStatsShareBinding) {
        binding.sharePreview.setImageDrawable(null)
        bitmap = null
    }

    private fun content(ui: StatsViewModel.Ui): StatsImageContent {
        val context = requireContext()
        val category = statsModel.category.value
        return StatsImageContent(
            category = category,
            title =
                getString(
                    when (category) {
                        StatsCategory.ARTISTS -> R.string.lbl_stats_top_artists
                        StatsCategory.ALBUMS -> R.string.lbl_stats_top_albums
                        StatsCategory.SONGS -> R.string.lbl_stats_top_songs
                    }
                ),
            rangeLabel = StatsFormat.range(context, ui.query.period, ui.range),
            rows =
                statsModel.items(ui).take(IMAGE_ROWS).map {
                    StatsImageContent.Row(
                        it.name,
                        it.subtitle,
                        StatsFormat.plays(context, it.plays),
                        StatsFormat.listened(context, it.listenedMs),
                    )
                },
            totalTime = StatsFormat.listened(context, ui.stats.listenedMs),
            totalPlays = StatsFormat.plays(context, ui.stats.plays),
        )
    }

    private fun share() {
        val context = requireContext()
        val image = bitmap ?: return
        try {
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(directory, "noxio-stats.png")
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri =
                FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    file,
                )
            ShareCompat.IntentBuilder(context).setType("image/png").addStream(uri).startChooser()
        } catch (e: Exception) {
            L.e("Failed to share stats image: $e")
            context.showToast(R.string.err_share_failed)
        }
    }

    private fun save(uri: Uri) {
        val context = requireContext()
        val image = bitmap ?: return
        try {
            context.contentResolver.openOutputStream(uri)?.use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            context.showToast(R.string.lbl_stats_image_saved)
        } catch (e: IOException) {
            L.e("Failed to save stats image: $e")
            context.showToast(R.string.err_share_failed)
        }
    }

    companion object {
        const val TAG = "StatsShareDialog"
        private const val IMAGE_ROWS = 5
    }
}
