/*
 * Copyright (c) 2026 Auxio Project
 * OtherAudio.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.service

/**
 * Noxio: pausing for sounds of other apps that do not ask Android for audio focus, like the voice
 * messages of some messengers. Android only tells which players exist, not whose they are or
 * whether they play, so this reacts to changes: a player that appears while Noxio plays pauses it,
 * and once the players are back to what there was before, Noxio plays on. A player that was there
 * all along, perhaps stopped, never pauses Noxio.
 */
class OtherAudio {
    enum class Action {
        NONE,
        PAUSE,
        RESUME,
    }

    /** How many players of other apps there were when Noxio last started playing. */
    private var baseline = 0

    /** Whether Noxio is paused because of another player, so it may play on by itself. */
    var pausedForOther = false
        private set

    /** Noxio starts or stops playing for any other reason, with [others] players of other apps. */
    fun onOwnPlaying(playing: Boolean, others: Int) {
        if (playing) {
            baseline = others
        }
        pausedForOther = false
    }

    /** The players changed to [others] players of other apps while Noxio [playing] or not. */
    fun onPlayersChanged(others: Int, playing: Boolean): Action =
        when {
            playing && others > baseline -> {
                pausedForOther = true
                Action.PAUSE
            }
            pausedForOther && others <= baseline -> {
                pausedForOther = false
                Action.RESUME
            }
            else -> Action.NONE
        }

    companion object {
        /**
         * How many of the [players] (each its usage and content type) are not Noxio's own one.
         * Noxio plays as media with music, so one such player is taken for its own. Short sounds
         * (notifications, clicks, alarms, navigation) are left out: they ask for focus anyway.
         */
        fun othersAmong(players: List<Pair<Int, Int>>, ownUsage: Int, ownContent: Int): Int {
            val relevant = players.filter { (usage, _) -> usage in RELEVANT_USAGES }
            val own = if (relevant.any { it == ownUsage to ownContent }) 1 else 0
            return relevant.size - own
        }

        /**
         * Whether a player plays, from its [description] ("state:started"). A description without a
         * state, which some Android versions may give, counts as playing.
         */
        fun isStarted(description: String): Boolean {
            val state = Regex("state:(\\w+)").find(description)?.groupValues?.get(1) ?: return true
            return state == "started"
        }

        // android.media.AudioAttributes: USAGE_UNKNOWN, USAGE_MEDIA, USAGE_VOICE_COMMUNICATION.
        private val RELEVANT_USAGES = setOf(0, 1, 2)
    }
}
