/*
 * Copyright (c) 2026 Auxio Project
 * LoudnessHistogram.kt is part of Auxio.
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
 
package org.oxycblt.auxio.replaygain

import java.io.ByteArrayOutputStream
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * How many 400 ms blocks of a song had which loudness, in steps of 0.1 LU between -70 and +5 LUFS.
 * It is all the gating of EBU R128 needs, so the loudness of an album can be worked out from the
 * histograms of its songs without decoding them again (the same way libebur128 does it). Blocks
 * under the absolute gate of -70 LUFS are not counted.
 */
class LoudnessHistogram private constructor(private val counts: IntArray) {
    constructor() : this(IntArray(BINS))

    /** Whether no block was counted. */
    val isEmpty: Boolean
        get() = counts.all { it == 0 }

    /** Count a block with the given loudness in LUFS. Blocks at or under -70 LUFS are left out. */
    fun add(lufs: Double) {
        if (lufs <= MIN_LUFS) return
        val bin = floor((lufs - MIN_LUFS) / STEP).toInt().coerceIn(0, BINS - 1)
        counts[bin]++
    }

    /**
     * The histogram as a few bytes: for every bin that has blocks, the distance to the bin before
     * and the count, both as variable-length numbers.
     */
    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        var last = -1
        for (bin in counts.indices) {
            val count = counts[bin]
            if (count == 0) continue
            writeVarInt(out, bin - last)
            writeVarInt(out, count)
            last = bin
        }
        return out.toByteArray()
    }

    companion object {
        private const val MIN_LUFS = -70.0
        private const val MAX_LUFS = 5.0
        private const val STEP = 0.1
        private val BINS = ((MAX_LUFS - MIN_LUFS) / STEP).toInt()
        private const val RELATIVE_GATE = 10.0

        /** Read what [encode] wrote, or null if the bytes are not a histogram. */
        fun decode(bytes: ByteArray): LoudnessHistogram? {
            val counts = IntArray(BINS)
            var pos = 0
            var bin = -1
            while (pos < bytes.size) {
                val (delta, afterDelta) = readVarInt(bytes, pos) ?: return null
                val (count, afterCount) = readVarInt(bytes, afterDelta) ?: return null
                bin += delta
                if (delta <= 0 || bin >= BINS) return null
                counts[bin] = count
                pos = afterCount
            }
            return LoudnessHistogram(counts)
        }

        /**
         * The integrated loudness in LUFS of all [histograms] together, as if their songs played
         * one after the other, or null if there is nothing above the gates.
         */
        fun integrated(histograms: List<LoudnessHistogram>): Double? {
            val total = IntArray(BINS)
            for (histogram in histograms) {
                for (bin in 0 until BINS) total[bin] += histogram.counts[bin]
            }
            val all = meanEnergy(total, 0) ?: return null
            val gate = lufs(all) - RELATIVE_GATE
            // The first bin whose blocks are all above the relative gate.
            val first = (floor((gate - MIN_LUFS) / STEP).toInt() + 1).coerceIn(0, BINS)
            return meanEnergy(total, first)?.let(::lufs)
        }

        private fun meanEnergy(counts: IntArray, from: Int): Double? {
            var energy = 0.0
            var blocks = 0L
            for (bin in from until BINS) {
                val count = counts[bin]
                if (count == 0) continue
                energy += count * energy(MIN_LUFS + (bin + 0.5) * STEP)
                blocks += count
            }
            return if (blocks > 0) energy / blocks else null
        }

        private fun energy(lufs: Double) = 10.0.pow((lufs + 0.691) / 10)

        private fun lufs(energy: Double) = -0.691 + 10 * log10(energy)

        private fun writeVarInt(out: ByteArrayOutputStream, value: Int) {
            var v = value
            while (v >= 0x80) {
                out.write((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            out.write(v)
        }

        private fun readVarInt(bytes: ByteArray, start: Int): Pair<Int, Int>? {
            var value = 0
            var shift = 0
            var pos = start
            while (pos < bytes.size && shift < 32) {
                val b = bytes[pos++].toInt() and 0xFF
                value = value or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return value to pos
                shift += 7
            }
            return null
        }
    }
}

/** What an album's measurement needs of one song: its histogram and its highest sample. */
data class SongLoudness(val histogram: LoudnessHistogram, val peak: Float)

/** The ReplayGain of an album, worked out from what was measured of its songs. */
object AlbumGain {
    /**
     * The album adjustment in dB, the same for every song of the album: the loudness of all songs
     * together brought to -18 LUFS, a boost limited by the loudest sample of the album. Null if a
     * song is missing (null) or there is nothing to measure.
     */
    fun of(songs: List<SongLoudness?>): Float? {
        if (songs.isEmpty() || songs.any { it == null }) return null
        val measured = songs.filterNotNull()
        val lufs = LoudnessHistogram.integrated(measured.map { it.histogram }) ?: return null
        return Loudness(lufs, measured.maxOf { it.peak }).replayGainDb()
    }
}
