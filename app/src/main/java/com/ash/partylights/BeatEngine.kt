package com.ash.partylights

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Produces beats from one of three sources: an internal metronome, taps, or the microphone.
 * All times are System.nanoTime().
 */
class BeatEngine(private val context: Context) {
    @Volatile var lastBeatNanos = 0L
        private set
    private val beatCounter = AtomicInteger(0)
    val beats: Int get() = beatCounter.get()

    /** Music loudness 0..1 while the mic is on. */
    @Volatile var level = 0f
        private set

    /** Minimum gap between mic beats; raised in safe mode. */
    @Volatile var minGapNanos = 280_000_000L

    private var nextBeat = 0L
    private val taps = ArrayDeque<Long>()
    @Volatile var tapIntervalNanos = 500_000_000L
        private set

    @Volatile private var micRunning = false
    private var micThread: Thread? = null

    val micActive: Boolean get() = micRunning

    fun pulse(now: Long): Float {
        if (lastBeatNanos == 0L) return 0f
        val dt = (now - lastBeatNanos) / 1e9f
        return exp(-dt * 6f).coerceIn(0f, 1f)
    }

    fun beat(now: Long) {
        lastBeatNanos = now
        beatCounter.incrementAndGet()
    }

    /** Called every frame for the metronome-style sources. */
    fun tick(now: Long, source: BeatSource, autoBpm: Float) {
        val interval = when (source) {
            BeatSource.AUTO -> (60e9 / autoBpm).toLong()
            BeatSource.TAP -> tapIntervalNanos
            BeatSource.MIC -> return
        }
        if (nextBeat == 0L || now - nextBeat > interval * 2) nextBeat = now
        if (now >= nextBeat) {
            beat(now)
            nextBeat += interval
        }
    }

    val tapBpm: Int get() = (60e9 / tapIntervalNanos).toInt()

    fun tap(now: Long) {
        if (taps.isNotEmpty() && now - taps.last() > 2_000_000_000L) taps.clear()
        taps.addLast(now)
        while (taps.size > 6) taps.removeFirst()
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()) / (taps.size - 1)
            tapIntervalNanos = avg.coerceIn(240_000_000L, 2_000_000_000L)
        }
        beat(now)
        nextBeat = now + tapIntervalNanos
    }

    fun hasMicPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startMic(): Boolean {
        if (micRunning) return true
        if (!hasMicPermission()) return false
        val rate = 44100
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 4096))
        } catch (_: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); return false
        }
        micRunning = true
        micThread = Thread({ micLoop(rec) }, "beat-mic").apply { start() }
        return true
    }

    fun stopMic() {
        micRunning = false
        micThread?.join(500)
        micThread = null
        level = 0f
    }

    private fun micLoop(rec: AudioRecord) {
        val buf = ShortArray(1024)
        val history = FloatArray(43)
        var hIdx = 0
        var filled = 0
        var lp = 0f
        try {
            rec.startRecording()
            while (micRunning) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                var sumAll = 0.0
                for (i in 0 until n) {
                    val s = buf[i].toFloat()
                    lp += 0.05f * (s - lp) // low-pass ~350 Hz to follow the kick drum
                    sum += lp * lp
                    sumAll += s * s
                }
                val energy = sqrt(sum / n).toFloat()
                level = (sqrt(sumAll / n).toFloat() / 6000f).coerceIn(0f, 1f)
                var avg = 0f
                val count = max(filled, 1)
                for (i in 0 until filled) avg += history[i]
                avg /= count
                val now = System.nanoTime()
                if (filled > 10 && energy > avg * 1.35f && energy > 250f && now - lastBeatNanos > minGapNanos) {
                    beat(now)
                }
                history[hIdx] = energy
                hIdx = (hIdx + 1) % history.size
                if (filled < history.size) filled++
            }
        } catch (_: Exception) {
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
            micRunning = false
        }
    }
}
