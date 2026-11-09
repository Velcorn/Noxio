/*
 * Copyright (c) 2026 Auxio Project
 * LoudnessMeter.kt is part of Auxio.
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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/** What a [LoudnessMeter] measured. */
data class Loudness(
    /** The integrated loudness in LUFS (EBU R128, ITU-R BS.1770-4). */
    val lufs: Double,
    /** The highest absolute sample value, 1.0 being full scale. */
    val peak: Float,
) {
    /**
     * The ReplayGain 2.0 adjustment in dB that brings this to the reference level of -18 LUFS. A
     * boost is limited so that the loudest sample stays under full scale.
     */
    fun replayGainDb(): Float {
        val gain = REFERENCE_LUFS - lufs
        val limit = if (peak > 0f) -20.0 * log10(peak.toDouble()) else Double.MAX_VALUE
        return minOf(gain, limit).toFloat()
    }

    companion object {
        /** The loudness ReplayGain 2.0 aims for. */
        const val REFERENCE_LUFS = -18.0
    }
}

/**
 * Measures the integrated loudness of audio fed to it, as defined by EBU R128 / ITU-R BS.1770-4:
 * K-weighting, 400 ms blocks every 100 ms, and gating at -70 LUFS and 10 LU below the rest. It only
 * keeps one number per 100 ms, so a whole song can be streamed through it.
 *
 * @param sampleRate The sample rate of the audio in Hz.
 * @param channels The number of interleaved channels. More than two are taken as the SMPTE order (L
 *   R C LFE Ls Rs), with the LFE left out and the surround channels weighed up.
 */
class LoudnessMeter(private val sampleRate: Int, private val channels: Int) {
    private class Biquad(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double,
    ) {
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private val shelf = Array(channels) { highShelf() }
    private val highPass = Array(channels) { highPass() }
    private val weights =
        DoubleArray(channels) {
            if (channels <= 2) {
                1.0
            } else {
                when (it) {
                    3 -> 0.0
                    4,
                    5 -> 1.41
                    else -> 1.0
                }
            }
        }

    private val subBlockSize = max(1, (sampleRate * 0.1).roundToInt())
    private var subBlockFill = 0
    private var subBlockEnergy = 0.0
    private val subBlocks = ArrayList<Double>()
    private var channel = 0
    private var peak = 0f

    /** Feed [count] interleaved samples (-1 to 1) of [samples]. */
    fun add(samples: FloatArray, count: Int = samples.size) {
        for (i in 0 until count) {
            val x = samples[i]
            val magnitude = abs(x)
            if (magnitude > peak) {
                peak = magnitude
            }
            val y = highPass[channel].process(shelf[channel].process(x.toDouble()))
            subBlockEnergy += weights[channel] * y * y
            if (++channel == channels) {
                channel = 0
                if (++subBlockFill == subBlockSize) {
                    subBlocks += subBlockEnergy / subBlockSize
                    subBlockFill = 0
                    subBlockEnergy = 0.0
                }
            }
        }
    }

    /** The loudness of everything fed so far, or null if it was silent or too short. */
    fun result(): Loudness? {
        // A gating block is four sub-blocks, moving ahead by one.
        val blocks = subBlocks.size - 3
        if (blocks <= 0) {
            return null
        }
        val energy = DoubleArray(blocks) { (0 until 4).sumOf { k -> subBlocks[it + k] } / 4 }

        val absolute = energy.filter { lufs(it) > ABSOLUTE_GATE }
        if (absolute.isEmpty()) {
            return null
        }
        val relativeGate = lufs(absolute.average()) - RELATIVE_GATE
        val gated = absolute.filter { lufs(it) > relativeGate }
        if (gated.isEmpty()) {
            return null
        }
        return Loudness(lufs(gated.average()), peak)
    }

    private fun lufs(meanSquare: Double) = -0.691 + 10 * log10(meanSquare)

    private fun highShelf(): Biquad {
        val f0 = 1681.974450955533
        val gain = 3.999843853973347
        val q = 0.7071752369554196
        val k = tan(PI * f0 / sampleRate)
        val vh = 10.0.pow(gain / 20)
        val vb = vh.pow(0.4996667741545416)
        val a0 = 1 + k / q + k * k
        return Biquad(
            (vh + vb * k / q + k * k) / a0,
            2 * (k * k - vh) / a0,
            (vh - vb * k / q + k * k) / a0,
            2 * (k * k - 1) / a0,
            (1 - k / q + k * k) / a0,
        )
    }

    private fun highPass(): Biquad {
        val f0 = 38.13547087602444
        val q = 0.5003270373238773
        val k = tan(PI * f0 / sampleRate)
        val a0 = 1 + k / q + k * k
        return Biquad(1.0, -2.0, 1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
    }

    private companion object {
        const val ABSOLUTE_GATE = -70.0
        const val RELATIVE_GATE = 10.0
    }
}
