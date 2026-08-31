package com.nova.galaxysandbox.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Small deterministic xorshift RNG. Used everywhere so a galaxy/world can be
 * regenerated exactly from a single seed.
 */
class Rng(seed: Long) {
    private var s: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

    fun nextLong(): Long {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        s = x
        return x
    }

    fun nextInt(bound: Int): Int {
        if (bound <= 0) return 0
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun range(from: Int, until: Int): Int = if (until <= from) from else from + nextInt(until - from)

    fun nextFloat(): Float = ((nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat()

    fun range(from: Float, to: Float): Float = from + nextFloat() * (to - from)

    fun chance(p: Float): Boolean = nextFloat() < p

    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]

    fun gaussian(): Float {
        // Irwin-Hall approximation, cheap and good enough for visuals.
        var sum = 0f
        repeat(4) { sum += nextFloat() }
        return (sum - 2f) * 0.866f
    }
}

object MathX {
    const val TAU = (Math.PI * 2.0).toFloat()

    fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))
    fun clamp(v: Int, lo: Int, hi: Int): Int = max(lo, min(hi, v))
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    fun invLerp(a: Float, b: Float, v: Float): Float =
        if (abs(b - a) < 1e-6f) 0f else clamp((v - a) / (b - a), 0f, 1f)

    fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    fun dist2(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return dx * dx + dy * dy
    }

    fun approach(current: Float, target: Float, rate: Float): Float {
        return if (current < target) min(target, current + rate) else max(target, current - rate)
    }
}

/** Classic value noise with fbm on top — used for planet terrain and nebulae. */
class ValueNoise(seed: Long) {
    private val perm = IntArray(512)

    init {
        val rng = Rng(seed)
        val p = IntArray(256) { it }
        for (i in 255 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = p[i]; p[i] = p[j]; p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    private fun hash(x: Int, y: Int): Float {
        val h = perm[(perm[x and 255] + (y and 255)) and 255]
        return h / 255f
    }

    fun noise(x: Float, y: Float): Float {
        val xi = floor(x.toDouble()).toInt()
        val yi = floor(y.toDouble()).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = MathX.smoothstep(xf)
        val v = MathX.smoothstep(yf)
        val a = hash(xi, yi)
        val b = hash(xi + 1, yi)
        val c = hash(xi, yi + 1)
        val d = hash(xi + 1, yi + 1)
        return MathX.lerp(MathX.lerp(a, b, u), MathX.lerp(c, d, u), v)
    }

    /** Fractal brownian motion, result in 0..1. */
    fun fbm(x: Float, y: Float, octaves: Int = 5, lacunarity: Float = 2f, gain: Float = 0.5f): Float {
        var amp = 1f
        var freq = 1f
        var sum = 0f
        var norm = 0f
        repeat(octaves) {
            sum += amp * noise(x * freq, y * freq)
            norm += amp
            amp *= gain
            freq *= lacunarity
        }
        return if (norm == 0f) 0f else sum / norm
    }

    /** Ridged noise, good for mountain chains. */
    fun ridge(x: Float, y: Float, octaves: Int = 4): Float {
        var amp = 1f
        var freq = 1f
        var sum = 0f
        var norm = 0f
        repeat(octaves) {
            val n = 1f - abs(noise(x * freq, y * freq) * 2f - 1f)
            sum += amp * n * n
            norm += amp
            amp *= 0.5f
            freq *= 2f
        }
        return if (norm == 0f) 0f else sum / norm
    }
}

/** Procedural name generation for stars, planets, factions and kingdoms. */
object Names {
    private val starPrefix = listOf(
        "Vega", "Altair", "Rigel", "Zeta", "Kepler", "Draco", "Lyra", "Orion", "Cygnus", "Tau",
        "Sirius", "Antares", "Pollux", "Mira", "Nyx", "Helios", "Cassio", "Perseus", "Vela", "Auriga",
        "Corvus", "Hydra", "Phoenix", "Aquila", "Bootes", "Cetus", "Dorado", "Eridani", "Fornax", "Grus"
    )
    private val starSuffix = listOf(
        "Prime", "Major", "Minor", "IX", "VII", "XII", "Alpha", "Beta", "Gamma", "Delta",
        "Nova", "Core", "Reach", "Gate", "Expanse", "Verge", "Drift", "Halo", "Rift", "Crown"
    )
    private val planetRoots = listOf(
        "Tarn", "Kael", "Vor", "Zhen", "Aeth", "Bor", "Cryo", "Dun", "Ely", "Fyr",
        "Gol", "Hax", "Ion", "Jor", "Kri", "Lum", "Mek", "Nul", "Oss", "Pyr",
        "Quor", "Rha", "Sol", "Thal", "Umb", "Vex", "Wren", "Xan", "Yrd", "Zar"
    )
    private val planetTails = listOf(
        "us", "ia", "on", "ar", "eth", "ax", "or", "is", "um", "ex", "ara", "ion", "yss", "ora", "een"
    )
    private val factionA = listOf(
        "Solar", "Void", "Iron", "Crimson", "Azure", "Silent", "Eternal", "Nova", "Obsidian", "Radiant",
        "Hollow", "Verdant", "Frozen", "Gilded", "Shattered", "Ashen"
    )
    private val factionB = listOf(
        "Dominion", "Covenant", "Concord", "Hegemony", "Collective", "Ascendancy", "Syndicate",
        "Imperium", "Republic", "Swarm", "Order", "Directorate", "Compact", "Union"
    )
    private val kingdomA = listOf(
        "Aeld", "Bran", "Corv", "Dorn", "Eryn", "Fen", "Gath", "Hal", "Ith", "Jarn",
        "Kor", "Lorn", "Mor", "Nar", "Orn", "Pell", "Rok", "Sil", "Thar", "Uld"
    )
    private val kingdomB = listOf(
        "mark", "gard", "hold", "reach", "fell", "moor", "wick", "dale", "spire", "watch",
        "crest", "haven", "forge", "vale", "burg", "helm"
    )

    fun star(rng: Rng): String = "${rng.pick(starPrefix)} ${rng.pick(starSuffix)}"

    fun planet(rng: Rng): String {
        val base = rng.pick(planetRoots) + rng.pick(planetTails)
        return if (rng.chance(0.25f)) "$base-${rng.range(1, 99)}" else base
    }

    fun faction(rng: Rng): String = "${rng.pick(factionA)} ${rng.pick(factionB)}"

    fun kingdom(rng: Rng): String = rng.pick(kingdomA) + rng.pick(kingdomB)
}
