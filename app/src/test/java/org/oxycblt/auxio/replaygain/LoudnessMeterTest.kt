/*
 * Copyright (c) 2026 Auxio Project
 * LoudnessMeterTest.kt is part of Auxio.
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
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessMeterTest {
    /** A stereo sine of [seconds], at [dbfs] decibels under full scale, with [lead] of silence. */
    private fun measure(
        sampleRate: Int = 48_000,
        dbfs: Double = -23.0,
        seconds: Int = 20,
        silence: Int = 0,
        channels: Int = 2,
    ): Loudness? {
        val meter = LoudnessMeter(sampleRate, channels)
        val amplitude = 10.0.pow(dbfs / 20)
        val frames = sampleRate * seconds
        val samples = FloatArray(frames * channels)
        for (i in 0 until frames) {
            val v = (amplitude * sin(2 * PI * 1000.0 * i / sampleRate)).toFloat()
            for (c in 0 until channels) samples[i * channels + c] = v
        }
        meter.add(samples)
        meter.add(FloatArray(sampleRate * silence * channels))
        return meter.result()
    }

    @Test
    fun `a stereo sine at -23 dBFS is -23 LUFS`() {
        // EBU Tech 3341 test signal.
        assertEquals(-23.0, measure()!!.lufs, 0.1)
    }

    @Test
    fun `the sample rate does not change the result`() {
        assertEquals(-23.0, measure(sampleRate = 44_100)!!.lufs, 0.1)
        assertEquals(-23.0, measure(sampleRate = 96_000)!!.lufs, 0.1)
    }

    @Test
    fun `twice the level is six decibels louder`() {
        assertEquals(measure(dbfs = -23.0)!!.lufs + 6.02, measure(dbfs = -17.0)!!.lufs, 0.05)
    }

    @Test
    fun `silence is gated out`() {
        assertEquals(measure()!!.lufs, measure(silence = 20)!!.lufs, 0.1)
    }

    @Test
    fun `silence has no loudness`() {
        assertNull(measure(dbfs = -120.0))
    }

    @Test
    fun `too short audio has no loudness`() {
        assertNull(measure(seconds = 0))
    }

    @Test
    fun `mono and many channels are measured`() {
        assertNotNull(measure(channels = 1))
        assertTrue(measure(channels = 6)!!.lufs > measure(channels = 2)!!.lufs)
    }

    @Test
    fun `the peak is the loudest sample`() {
        assertEquals(10.0.pow(-23.0 / 20), measure()!!.peak.toDouble(), 0.001)
    }

    @Test
    fun `gain brings a song to -18 LUFS`() {
        // -23 LUFS needs +5 dB, and the peak of -23 dBFS leaves room for it.
        assertEquals(5f, measure()!!.replayGainDb(), 0.1f)
        // A loud song is turned down.
        assertEquals(-6f, measure(dbfs = -12.0)!!.replayGainDb(), 0.1f)
    }

    @Test
    fun `a boost never pushes the peak over full scale`() {
        assertEquals(5f, Loudness(-23.0, 0.5f).replayGainDb(), 0.001f)
        assertEquals(1.94f, Loudness(-23.0, 0.8f).replayGainDb(), 0.01f)
    }
}
