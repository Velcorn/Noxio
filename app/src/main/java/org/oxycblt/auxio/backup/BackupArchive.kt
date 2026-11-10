/*
 * Copyright (c) 2026 Auxio Project
 * BackupArchive.kt is part of Auxio.
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
 
package org.oxycblt.auxio.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One song of a backed up playlist, with what is needed to find it again in the library. */
data class BackupSong(val path: String, val title: String, val artist: String, val album: String)

/** A playlist of a backup. */
data class BackupPlaylist(val name: String, val songs: List<BackupSong>)

/**
 * What a backup says about itself.
 *
 * @param format The version of the file layout, see [BackupArchive.FORMAT].
 * @param appVersion The version of the app that made the backup.
 * @param createdAt When the backup was made, in milliseconds since the unix epoch.
 * @param plays How many plays of the listening history it holds.
 * @param playlists How many playlists it holds.
 * @param settings How many settings it holds.
 */
data class BackupInfo(
    val format: Int,
    val appVersion: String,
    val createdAt: Long,
    val plays: Int,
    val playlists: Int,
    val settings: Int,
)

/**
 * The content of a backup.
 *
 * @param settings The settings by key. A value is a Boolean, Int, Long, Float, String or a set of
 *   strings.
 * @param history The listening history as CSV, see [org.oxycblt.auxio.stats.StatsCsv].
 */
data class BackupData(
    val info: BackupInfo,
    val settings: Map<String, Any>,
    val history: ByteArray,
    val playlists: List<BackupPlaylist>,
)

/**
 * The file of a full backup: a zip with `info.json`, `settings.json`, `history.csv` and
 * `playlists.json`. Everything in it is plain text, so a backup can be opened and read by hand.
 */
object BackupArchive {
    /** The layout of the files that this version writes and reads. */
    const val FORMAT = 1

    private const val INFO = "info.json"
    private const val SETTINGS = "settings.json"
    private const val HISTORY = "history.csv"
    private const val PLAYLISTS = "playlists.json"

    fun write(data: BackupData, out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        entry(
            INFO,
            JSONObject()
                .put("format", data.info.format)
                .put("appVersion", data.info.appVersion)
                .put("createdAt", data.info.createdAt)
                .put("plays", data.info.plays)
                .put("playlists", data.info.playlists)
                .put("settings", data.info.settings)
                .toString(2)
                .toByteArray(),
        )
        entry(SETTINGS, settingsToJson(data.settings).toString(2).toByteArray())
        entry(HISTORY, data.history)
        entry(PLAYLISTS, playlistsToJson(data.playlists).toString(2).toByteArray())
        zip.finish()
        zip.flush()
    }

    /** @return What the backup says about itself, or null if the stream is not a backup. */
    fun readInfo(stream: InputStream): BackupInfo? = read(stream, onlyInfo = true)?.info

    /** @return The whole backup, or null if the stream is not a readable backup. */
    fun read(stream: InputStream): BackupData? = read(stream, onlyInfo = false)

    private fun read(stream: InputStream, onlyInfo: Boolean): BackupData? {
        val entries = mutableMapOf<String, ByteArray>()
        try {
            val zip = ZipInputStream(stream)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name in WANTED && (!onlyInfo || entry.name == INFO)) {
                    val bytes = ByteArrayOutputStream().also { zip.copyTo(it) }.toByteArray()
                    entries[entry.name] = bytes
                }
                if (onlyInfo && INFO in entries) break
            }
            val info = infoOf(entries[INFO] ?: return null)
            if (onlyInfo) return BackupData(info, emptyMap(), ByteArray(0), emptyList())
            return BackupData(
                info,
                settingsOf(entries[SETTINGS] ?: return null),
                entries[HISTORY] ?: return null,
                playlistsOf(entries[PLAYLISTS] ?: return null),
            )
        } catch (e: java.io.IOException) {
            return null
        } catch (e: JSONException) {
            return null
        }
    }

    private val WANTED = setOf(INFO, SETTINGS, HISTORY, PLAYLISTS)

    private fun infoOf(bytes: ByteArray): BackupInfo {
        val json = JSONObject(String(bytes))
        return BackupInfo(
            format = json.getInt("format"),
            appVersion = json.optString("appVersion"),
            createdAt = json.optLong("createdAt"),
            plays = json.optInt("plays"),
            playlists = json.optInt("playlists"),
            settings = json.optInt("settings"),
        )
    }

    private fun settingsToJson(settings: Map<String, Any>): JSONObject {
        val json = JSONObject()
        for ((key, value) in settings.toSortedMap()) {
            val typed = JSONObject()
            when (value) {
                is Boolean -> typed.put("type", "boolean").put("value", value)
                is Int -> typed.put("type", "int").put("value", value)
                is Long -> typed.put("type", "long").put("value", value)
                is Float -> typed.put("type", "float").put("value", value.toDouble())
                is String -> typed.put("type", "string").put("value", value)
                is Set<*> ->
                    typed
                        .put("type", "strings")
                        .put("value", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            json.put(key, typed)
        }
        return json
    }

    private fun settingsOf(bytes: ByteArray): Map<String, Any> {
        val json = JSONObject(String(bytes))
        val settings = mutableMapOf<String, Any>()
        for (key in json.keys()) {
            val typed = json.getJSONObject(key)
            val value: Any =
                when (typed.getString("type")) {
                    "boolean" -> typed.getBoolean("value")
                    "int" -> typed.getInt("value")
                    "long" -> typed.getLong("value")
                    "float" -> typed.getDouble("value").toFloat()
                    "string" -> typed.getString("value")
                    "strings" ->
                        typed.getJSONArray("value").let { array ->
                            (0 until array.length()).map { array.getString(it) }.toSet()
                        }
                    else -> continue
                }
            settings[key] = value
        }
        return settings
    }

    private fun playlistsToJson(playlists: List<BackupPlaylist>) =
        JSONArray(
            playlists.map { playlist ->
                JSONObject()
                    .put("name", playlist.name)
                    .put(
                        "songs",
                        JSONArray(
                            playlist.songs.map {
                                JSONObject()
                                    .put("path", it.path)
                                    .put("title", it.title)
                                    .put("artist", it.artist)
                                    .put("album", it.album)
                            }
                        ),
                    )
            }
        )

    private fun playlistsOf(bytes: ByteArray): List<BackupPlaylist> {
        val array = JSONArray(String(bytes))
        return (0 until array.length()).map { index ->
            val playlist = array.getJSONObject(index)
            val songs = playlist.getJSONArray("songs")
            BackupPlaylist(
                playlist.getString("name"),
                (0 until songs.length()).map {
                    val song = songs.getJSONObject(it)
                    BackupSong(
                        song.optString("path"),
                        song.optString("title"),
                        song.optString("artist"),
                        song.optString("album"),
                    )
                },
            )
        }
    }
}
