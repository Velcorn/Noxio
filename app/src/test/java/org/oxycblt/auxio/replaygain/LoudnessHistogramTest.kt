/*
 * Copyright (c) 2026 Auxio Project
 * LoudnessHistogramTest.kt is part of Auxio.
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

class LoudnessHistogramTest {
    /** A stereo 1 kHz sine of [seconds] at [dbfs], measured. */
    private fun sine(dbfs: Double, seconds: Int = 20): Loudness {
        val rate = 48_000
        val meter = LoudnessMeter(rate, 2)
        val amplitude = 10.0.pow(dbfs / 20)
        val samples = FloatArray(rate * seconds * 2)
        for (i in 0 until rate * seconds) {
            val v = (amplitude * sin(2 * PI * 1000.0 * i / rate)).toFloat()
            samples[i * 2] = v
            samples[i * 2 + 1] = v
        }
        meter.add(samples)
        return meter.result()!!
    }

    private fun Loudness.song() = SongLoudness(histogram!!, peak)

    @Test
    fun `one song alone is as loud as the meter says`() {
        val song = sine(-23.0)
        assertEquals(song.lufs, LoudnessHistogram.integrated(listOf(song.histogram!!))!!, 0.1)
    }

    @Test
    fun `an album of one song has the song's gain`() {
        val song = sine(-23.0)
        assertEquals(song.replayGainDb(), AlbumGain.of(listOf(song.song()))!!, 0.1f)
    }

    @Test
    fun `two equally long songs average their energy`() {
        // -20 and -26 LUFS: the mean energy is (1 + 0.25) / 2 of -20, about -21.97 LUFS.
        val loud = sine(-20.0)
        val quiet = sine(-26.0)
        val lufs = LoudnessHistogram.integrated(listOf(loud.histogram!!, quiet.histogram!!))!!
        assertEquals(-21.97, lufs, 0.15)
        assertEquals(
            (-18.0 - lufs).toFloat(),
            AlbumGain.of(listOf(loud.song(), quiet.song()))!!,
            0.01f,
        )
    }

    @Test
    fun `a much quieter song falls under the relative gate of the album`() {
        // At -40 it is more than 10 LU under the album, so it does not pull the album down.
        val loud = sine(-20.0)
        val quiet = sine(-40.0)
        val lufs = LoudnessHistogram.integrated(listOf(loud.histogram!!, quiet.histogram!!))!!
        assertEquals(loud.lufs, lufs, 0.1)
    }

    @Test
    fun `the album boost is limited by its loudest sample`() {
        // Quiet songs would get a big boost, the peak of -6 dBFS allows only 6 dB.
        val peaky = Loudness(-40.0, 0.5f, sine(-40.0).histogram)
        assertEquals(6.02f, AlbumGain.of(listOf(peaky.song()))!!, 0.01f)
    }

    @Test
    fun `an album with a song that is not measured has no gain`() {
        assertNull(AlbumGain.of(listOf(sine(-20.0).song(), null)))
        assertNull(AlbumGain.of(emptyList()))
    }

    @Test
    fun `encoding and decoding gives the same result`() {
        val song = sine(-23.0)
        val bytes = song.histogram!!.encode()
        // A steady sine fills a few bins, so it is tiny.
        assertTrue(bytes.size < 32)
        val decoded = LoudnessHistogram.decode(bytes)
        assertNotNull(decoded)
        assertEquals(
            LoudnessHistogram.integrated(listOf(song.histogram!!))!!,
            LoudnessHistogram.integrated(listOf(decoded!!))!!,
            0.0,
        )
    }

    @Test
    fun `big counts and far bins survive the encoding`() {
        val histogram = LoudnessHistogram()
        repeat(100_000) { histogram.add(-69.95) }
        histogram.add(4.99)
        histogram.add(30.0)
        val decoded = LoudnessHistogram.decode(histogram.encode())!!
        assertEquals(
            LoudnessHistogram.integrated(listOf(histogram))!!,
            LoudnessHistogram.integrated(listOf(decoded))!!,
            0.0,
        )
    }

    @Test
    fun `silence and broken bytes give nothing`() {
        val empty = LoudnessHistogram()
        empty.add(-80.0)
        assertTrue(empty.isEmpty)
        assertNull(LoudnessHistogram.integrated(listOf(empty)))
        assertNull(LoudnessHistogram.decode(byteArrayOf(0x80.toByte())))
        assertNull(LoudnessHistogram.decode(byteArrayOf(0, 1)))
        // A bin past the end.
        assertNull(LoudnessHistogram.decode(byteArrayOf(0xE8.toByte(), 0x07, 1)))
    }
}
