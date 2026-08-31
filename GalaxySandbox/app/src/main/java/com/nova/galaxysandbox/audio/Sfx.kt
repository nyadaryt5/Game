package com.nova.galaxysandbox.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.nova.galaxysandbox.action.Tool
import com.nova.galaxysandbox.action.Weapon
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tiny procedural sound engine — every effect is synthesised at startup, so the
 * APK ships no audio assets and nothing has to be decoded at runtime.
 */
class Sfx {

    private val sampleRate = 22050
    private val playing = AtomicInteger(0)
    private val cache = HashMap<String, ShortArray>()
    private var lastPlay = HashMap<String, Long>()

    var enabled = true
    var volume = 0.85f

    init {
        cache["click"] = blip(880f, 0.06f, 0.25f)
        cache["laser"] = laser()
        cache["boom"] = boom(0.9f)
        cache["bigboom"] = boom(1.8f)
        cache["whoosh"] = makeWhoosh()
        cache["zap"] = zap()
        cache["thud"] = thud()
        cache["chime"] = chime()
        cache["rumble"] = rumble()
    }

    // ------------------------------------------------------------ synthesis

    private fun buffer(seconds: Float): ShortArray = ShortArray((sampleRate * seconds).toInt())

    private fun blip(freq: Float, dur: Float, amp: Float): ShortArray {
        val b = buffer(dur)
        for (i in b.indices) {
            val t = i / sampleRate.toFloat()
            val env = exp((-t / (dur * 0.35f)).toDouble()).toFloat()
            val v = sin(2.0 * PI * freq * t).toFloat() * env * amp
            b[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return b
    }

    private fun laser(): ShortArray {
        val dur = 0.28f
        val b = buffer(dur)
        for (i in b.indices) {
            val t = i / sampleRate.toFloat()
            val f = 1400f - 1000f * (t / dur)
            val env = exp((-t / 0.09f).toDouble()).toFloat()
            var v = sin(2.0 * PI * f * t).toFloat() * 0.6f
            v += sin(2.0 * PI * f * 2.02f * t).toFloat() * 0.25f
            v += (Random.nextFloat() - 0.5f) * 0.12f
            b[i] = (v * env * 0.7f * Short.MAX_VALUE).toInt().toShort()
        }
        return b
    }

    private fun boom(scale: Float): ShortArray {
        val dur = 0.85f * scale
        val b = buffer(dur)
        var lp = 0f
        for (i in b.indices) {
            val t = i / sampleRate.toFloat()
            val env = exp((-t / (0.22f * scale)).toDouble()).toFloat()
            val noise = Random.nextFloat() * 2f - 1f
            lp += (noise - lp) * 0.06f
            val sub = sin(2.0 * PI * (58f / scale) * t).toFloat()
            val v = (lp * 0.8f + sub * 0.7f) * env
            b[i] = (max(-1f, min(1f, v)) * 0.9f * Short.MAX_VALUE).toInt().toShort()
        }
        return b
    }

    private fun makeWhoosh(): ShortArray {
        val dur = 0.6f
        val b = buffer(dur)
        var lp = 0f
        for (i in b.indices) {
            val t = i / sampleRate.toFloat()
            val env = sin(PI * (t / dur)).toFloat()
            val noise = Random.nextFloat() * 2f - 1f
            val k = 0.02f + 0.25f * (t / dur)
            lp += (noise - lp) * k
            b[i] = (lp * env * 0.55f * Short.MAX_VALUE).toInt().toShort()
        }
        return b
    }

    private fun zap(): ShortArray {
        val dur = 0.35f
        val b = buffer(dur)
        for (i in b.indices) {
            val t = i / sampleRate.toFloat()
            val env = exp((-t / 0.07f).toDouble()).toFloat()
            val f = 220f + 2600f * exp((-t / 0.05f).toDouble()).toFloat()
            val v = sin(2.0 * PI * f * t).toFloat() * env
            b[i] = (v * 0.7f * Short.MAX_VALUE).toInt().toShort()
        }
        return b
    }

    private fun thud(): ShortArray {
        val dur = 0.4f
        val b = buffer(dur)
        for (i in b.indices) {
            val t = i / sampleRate.toFloat()
            val env = exp((-t / 0.1f).toDouble()).toFloat()
            val f = 120f - 60f * (t / dur)
            val v = sin(2.0 * PI * f * t).toFloat() * env
            b[i] = (v * 0.8f * Short.MAX_VALUE).toInt().toShort()
        }
        return b
    }

    private fun chime(): ShortArray {
        val dur = 0.7f
        val b = buffer(dur)
        val partials = floatArrayOf(660f, 990f, 1320f)
        for (i in b.indices) {
            val t = i / sampleRate.toFloat()
            var v = 0f
            for ((k, f) in partials.withIndex()) {
                val env = exp((-t / (0.30f - k * 0.07f)).toDouble()).toFloat()
                v += sin(2.0 * PI * f * t).toFloat() * env / (k + 1.6f)
            }
            b[i] = (v * 0.55f * Short.MAX_VALUE).toInt().toShort()
        }
        return b
    }

    private fun rumble(): ShortArray {
        val dur = 1.6f
        val b = buffer(dur)
        var lp = 0f
        for (i in b.indices) {
            val t = i / sampleRate.toFloat()
            val env = min(1f, t * 4f) * exp((-t / 0.7f).toDouble()).toFloat()
            val noise = Random.nextFloat() * 2f - 1f
            lp += (noise - lp) * 0.012f
            b[i] = (lp * env * 1.6f * Short.MAX_VALUE).toInt().toShort()
        }
        return b
    }

    // -------------------------------------------------------------- playback

    private fun play(key: String, gain: Float = 1f, minGapMs: Long = 40) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        val last = lastPlay[key] ?: 0L
        if (now - last < minGapMs) return
        lastPlay[key] = now
        if (playing.get() > 6) return
        val data = cache[key] ?: return
        try {
            val bytes = data.size * 2
            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bytes, AudioTrack.MODE_STATIC
                )
            }
            track.write(data, 0, data.size)
            val v = (volume * gain).coerceIn(0f, 1f)
            track.setVolume(v)
            playing.incrementAndGet()
            track.setNotificationMarkerPosition(data.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    try { t?.stop(); t?.release() } catch (_: Throwable) { }
                    playing.decrementAndGet()
                }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
            track.play()
        } catch (_: Throwable) {
            playing.decrementAndGet()
        }
    }

    fun click() = play("click", 0.5f, 30)
    fun whoosh() = play("whoosh", 0.9f, 120)
    fun explosion(big: Boolean = false) = play(if (big) "bigboom" else "boom", 1f, 60)
    fun rumbleShort() = play("rumble", 0.8f, 400)

    fun weapon(w: Weapon) {
        when (w) {
            Weapon.LASER -> play("laser", 0.35f, 90)
            Weapon.ICE_BEAM -> play("zap", 0.35f, 110)
            Weapon.RAILGUN -> play("zap", 0.8f, 60)
            Weapon.MISSILE -> play("whoosh", 0.8f, 60)
            Weapon.NUKE, Weapon.ASTEROID, Weapon.METEOR_STORM -> play("boom", 1f, 60)
            Weapon.ANNIHILATOR, Weapon.SUN_CRUSHER -> play("bigboom", 1f, 200)
            Weapon.BLACK_HOLE -> play("rumble", 1f, 300)
            Weapon.GRAVITY_SLAM -> play("thud", 1f, 100)
            Weapon.PLAGUE, Weapon.EMP, Weapon.UFO -> play("zap", 0.7f, 120)
            Weapon.TERRAFORM -> play("chime", 0.9f, 150)
        }
    }

    fun tool(t: Tool) {
        when (t.category) {
            Tool.Category.TERRAIN -> play("click", 0.35f, 70)
            Tool.Category.LIFE -> play("chime", 0.5f, 120)
            Tool.Category.DISASTER -> play("boom", 0.9f, 120)
            Tool.Category.DIVINE -> play("bigboom", 0.9f, 200)
            Tool.Category.SELECT -> play("click", 0.3f, 90)
        }
    }
}
