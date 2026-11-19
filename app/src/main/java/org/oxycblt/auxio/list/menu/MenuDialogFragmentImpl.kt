/*
 * Copyright (c) 2023 Auxio Project
 * MenuDialogFragmentImpl.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.menu

import android.view.MenuItem
import androidx.core.content.edit
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogMenuBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.image.ArtistPictures
import org.oxycblt.auxio.list.ListViewModel
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.PlaylistSongs
import org.oxycblt.auxio.music.PrimaryAlbumArtists
import org.oxycblt.auxio.music.displayArtists
import org.oxycblt.auxio.music.primaryAlbumArtistsOnly
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.playback.PlaySong
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.playback.formatDurationMs
import org.oxycblt.auxio.playback.queue.QueueViewModel
import org.oxycblt.auxio.util.getPlural
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song

/**
 * [MenuDialogFragment] implementation for a [Song].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class SongMenuDialogFragment : MenuDialogFragment<Menu.ForSong>() {
    override val menuModel: MenuViewModel by activityViewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val queueModel: QueueViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: SongMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    // Nothing to disable in song menus.
    override fun getDisabledItemIds(menu: Menu.ForSong) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForSong) {
        val context = requireContext()
        binding.menuCover.bind(menu.song)
        binding.menuType.text = getString(R.string.lbl_song)
        binding.menuName.text = menu.song.name.resolve(context)
        binding.menuInfo.text =
            menu.song.displayArtists(context.primaryAlbumArtistsOnly()).resolveNames(context)
    }

    override fun onClick(item: MenuItem, menu: Menu.ForSong) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.playExplicit(menu.song, menu.playWith)
            R.id.action_shuffle -> playbackModel.shuffleExplicit(menu.song, menu.playWith)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.song)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.song)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.song)
            R.id.action_playlist_remove -> {
                // Only in the menu of a song on a playlist page.
                val playlist = (menu.playWith as? PlaySong.FromPlaylist)?.which
                if (playlist != null) musicModel.removeFromPlaylist(menu.song, playlist)
            }
            // Only in the menu of a song in the queue.
            R.id.action_queue_remove -> queueModel.removeMenuSong(menu.song)
            R.id.action_artist_details -> detailModel.showArtist(menu.song)
            R.id.action_album_details -> detailModel.showAlbum(menu.song.album)
            R.id.action_detail -> detailModel.showSong(menu.song)
            else -> error("Unexpected menu item selected $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [AlbumMenuDialogFragment].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class AlbumMenuDialogFragment : MenuDialogFragment<Menu.ForAlbum>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: AlbumMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    // Nothing to disable in album menus.
    override fun getDisabledItemIds(menu: Menu.ForAlbum) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForAlbum) {
        val context = requireContext()
        binding.menuCover.bind(menu.album)
        binding.menuType.text = menu.album.releaseType.resolve(context)
        binding.menuName.text = menu.album.name.resolve(context)
        binding.menuInfo.text =
            menu.album.displayArtists(context.primaryAlbumArtistsOnly()).resolveNames(context)
    }

    override fun onClick(item: MenuItem, menu: Menu.ForAlbum) {
        when (item.itemId) {
            R.id.action_sort -> findNavController().navigate(R.id.album_song_sort_dialog)
            R.id.action_play -> playbackModel.play(menu.album)
            R.id.action_shuffle -> playbackModel.shuffle(menu.album)
            R.id.action_detail -> detailModel.showAlbum(menu.album)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.album)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.album)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_artist_details -> detailModel.showArtist(menu.album)
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.album)
            else -> error("Unexpected menu item selected $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Artist].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class ArtistMenuDialogFragment : MenuDialogFragment<Menu.ForArtist>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: ArtistMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForArtist) =
        if (menu.artist.songs.isEmpty()) {
            // Disable any operations that require some kind of songs to work with, as there won't
            // be any in an empty artist.
            setOf(
                R.id.action_play,
                R.id.action_shuffle,
                R.id.action_play_next,
                R.id.action_queue_add,
                R.id.action_playlist_add,
            )
        } else {
            setOf()
        }

    override fun getHiddenItemIds(menu: Menu.ForArtist) = buildSet {
        if (detailModel.artistTab.value == org.oxycblt.auxio.detail.ArtistTab.SONGS)
            add(R.id.action_sort)
    }

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForArtist) {
        val context = requireContext()
        val primaryOnly = context.primaryAlbumArtistsOnly()
        val albums =
            if (primaryOnly) PrimaryAlbumArtists.albums(menu.artist, menu.artist.explicitAlbums)
            else menu.artist.explicitAlbums
        val songs =
            if (primaryOnly) PrimaryAlbumArtists.songs(menu.artist, menu.artist.explicitAlbums)
            else menu.artist.songs
        binding.menuCover.bind(menu.artist)
        binding.menuType.text = getString(R.string.lbl_artist)
        binding.menuName.text = menu.artist.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                if (albums.isNotEmpty()) {
                    context.getPlural(R.plurals.fmt_album_count, albums.size)
                } else {
                    context.getString(R.string.def_album_count)
                },
                if (songs.isNotEmpty()) {
                    context.getPlural(R.plurals.fmt_song_count, songs.size)
                } else {
                    getString(R.string.def_song_count)
                },
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForArtist) {
        val primarySongs =
            if (requireContext().primaryAlbumArtistsOnly())
                PrimaryAlbumArtists.songs(menu.artist, menu.artist.explicitAlbums)
            else null
        when (item.itemId) {
            R.id.action_sort -> findNavController().navigate(R.id.artist_album_sort_dialog)
            R.id.action_choose_artist_picture -> chooseArtistPicture(menu.artist)
            R.id.action_play ->
                if (primarySongs != null) playbackModel.play(primarySongs)
                else playbackModel.play(menu.artist)
            R.id.action_shuffle ->
                if (primarySongs != null) playbackModel.shuffle(primarySongs)
                else playbackModel.shuffle(menu.artist)
            R.id.action_detail -> detailModel.showArtist(menu.artist)
            R.id.action_play_next -> {
                if (primarySongs != null) playbackModel.playNext(primarySongs)
                else playbackModel.playNext(menu.artist)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                if (primarySongs != null) playbackModel.addToQueue(primarySongs)
                else playbackModel.addToQueue(menu.artist)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add ->
                if (primarySongs != null) musicModel.addToPlaylist(primarySongs)
                else musicModel.addToPlaylist(menu.artist)
            else -> error("Unexpected menu item $item")
        }
    }

    private fun chooseArtistPicture(artist: Artist) {
        val context = requireContext()
        // Only albums with a cover can stand for the artist.
        val albums =
            (if (context.primaryAlbumArtistsOnly())
                    PrimaryAlbumArtists.albums(artist, artist.explicitAlbums)
                else artist.explicitAlbums)
                .filter { it.covers.covers.isNotEmpty() }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.set_key_artist_pictures)
        val choices = ArtistPictures.decode(prefs.getString(key, null))
        val selected =
            ArtistPictures.resolve(choices, artist.uid.toString(), albums.map { it.uid.toString() })
        val options =
            listOf(getString(R.string.lbl_collage)) + albums.map { it.name.resolve(context) }
        val selectedIndex =
            if (selected == null) 0 else albums.indexOfFirst { it.uid.toString() == selected } + 1
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_choose_artist_picture)
            .setSingleChoiceItems(options.toTypedArray(), selectedIndex) { dialog, which ->
                val albumUid = albums.getOrNull(which - 1)?.uid?.toString()
                prefs.edit {
                    putString(
                        key,
                        ArtistPictures.encode(
                            ArtistPictures.choose(choices, artist.uid.toString(), albumUid),
                        ),
                    )
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.lbl_cancel, null)
            .show()
    }
}

/**
 * [MenuDialogFragment] implementation for a [Genre].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class GenreMenuDialogFragment : MenuDialogFragment<Menu.ForGenre>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: GenreMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForGenre) = setOf<Int>()

    override fun getHiddenItemIds(menu: Menu.ForGenre) =
        setOf(R.id.action_sort, R.id.action_choose_artist_picture)

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForGenre) {
        val context = requireContext()
        binding.menuCover.bind(menu.genre)
        binding.menuType.text = getString(R.string.lbl_genre)
        binding.menuName.text = menu.genre.name.resolve(context)
        binding.menuInfo.text =
            getString(
                R.string.fmt_two,
                context.getPlural(R.plurals.fmt_artist_count, menu.genre.artists.size),
                context.getPlural(R.plurals.fmt_song_count, menu.genre.songs.size),
            )
    }

    override fun onClick(item: MenuItem, menu: Menu.ForGenre) {
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.genre)
            R.id.action_shuffle -> playbackModel.shuffle(menu.genre)
            R.id.action_detail -> detailModel.showGenre(menu.genre)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.genre)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.genre)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.genre)
            else -> error("Unexpected menu item $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Playlist].
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class PlaylistMenuDialogFragment : MenuDialogFragment<Menu.ForPlaylist>() {
    override val menuModel: MenuViewModel by viewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: PlaylistMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    override fun getDisabledItemIds(menu: Menu.ForPlaylist) =
        if (menu.playlist.songs.isEmpty()) {
            // Disable any operations that require some kind of songs to work with, as there won't
            // be any in an empty playlist.
            setOf(
                R.id.action_play,
                R.id.action_shuffle,
                R.id.action_play_next,
                R.id.action_queue_add,
                R.id.action_playlist_add,
                R.id.action_export,
                R.id.action_remove_duplicates,
            )
        } else if (!PlaylistSongs.hasDuplicates(menu.playlist.songs)) {
            setOf(R.id.action_remove_duplicates)
        } else {
            setOf()
        }

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForPlaylist) {
        val context = requireContext()
        binding.menuCover.bind(menu.playlist)
        binding.menuType.text = getString(R.string.lbl_playlist)
        binding.menuName.text = menu.playlist.name.resolve(context)
        binding.menuInfo.text =
            if (menu.playlist.songs.isNotEmpty()) {
                context.getPlural(R.plurals.fmt_song_count, menu.playlist.songs.size)
            } else {
                getString(R.string.def_song_count)
            }
    }

    override fun onClick(item: MenuItem, menu: Menu.ForPlaylist) {
        when (item.itemId) {
            R.id.action_sort -> findNavController().navigate(R.id.playlist_song_sort_dialog)
            R.id.action_play -> playbackModel.play(menu.playlist)
            R.id.action_shuffle -> playbackModel.shuffle(menu.playlist)
            R.id.action_detail -> detailModel.showPlaylist(menu.playlist)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.playlist)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.playlist)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_rename -> musicModel.renamePlaylist(menu.playlist)
            R.id.action_import -> musicModel.importPlaylist(target = menu.playlist)
            R.id.action_export -> musicModel.exportPlaylist(menu.playlist)
            R.id.action_remove_duplicates -> musicModel.removeDuplicates(menu.playlist)
            R.id.action_delete -> musicModel.deletePlaylist(menu.playlist)
            else -> error("Unexpected menu item $item")
        }
    }
}

/**
 * [MenuDialogFragment] implementation for a [Song] selection.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class SelectionMenuDialogFragment : MenuDialogFragment<Menu.ForSelection>() {
    override val menuModel: MenuViewModel by activityViewModels()
    override val listModel: ListViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val args: SelectionMenuDialogFragmentArgs by navArgs()

    override val parcel
        get() = args.parcel

    // Nothing to disable in song menus.
    override fun getDisabledItemIds(menu: Menu.ForSelection) = setOf<Int>()

    override fun updateMenu(binding: DialogMenuBinding, menu: Menu.ForSelection) {
        binding.menuCover.bind(
            menu.songs,
            getString(R.string.desc_selection_image),
            R.drawable.ic_song_24,
        )
        binding.menuType.text = getString(R.string.lbl_selection)
        binding.menuName.text =
            requireContext().getPlural(R.plurals.fmt_song_count, menu.songs.size)
        binding.menuInfo.text = menu.songs.sumOf { it.durationMs }.formatDurationMs(true)
    }

    override fun onClick(item: MenuItem, menu: Menu.ForSelection) {
        listModel.dropSelection()
        when (item.itemId) {
            R.id.action_play -> playbackModel.play(menu.songs)
            R.id.action_shuffle -> playbackModel.shuffle(menu.songs)
            R.id.action_play_next -> {
                playbackModel.playNext(menu.songs)
                requireContext().showToast(R.string.lng_play_next)
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(menu.songs)
                requireContext().showToast(R.string.lng_queue_added)
            }
            R.id.action_playlist_add -> musicModel.addToPlaylist(menu.songs)
            else -> error("Unexpected menu item selected $item")
        }
    }
}
