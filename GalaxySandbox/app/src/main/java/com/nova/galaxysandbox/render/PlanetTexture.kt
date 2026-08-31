package com.nova.galaxysandbox.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.core.Rng
import com.nova.galaxysandbox.core.ValueNoise
import com.nova.galaxysandbox.galaxy.Planet
import com.nova.galaxysandbox.galaxy.PlanetType
import com.nova.galaxysandbox.world.Biome
import com.nova.galaxysandbox.world.PlanetWorld
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds and caches a shaded sphere bitmap for every planet. Textures are
 * regenerated when a planet's condition changes (burning, cracked, destroyed)
 * or when its surface has been edited by the player.
 */
class PlanetTextureCache(private val texSize: Int = 128) {

    private class Entry(val bitmap: Bitmap) {
        var signature: Int = 0
    }

    private val cache = HashMap<Int, Entry>()
    private val pixels = IntArray(texSize * texSize)

    fun get(planet: Planet, world: PlanetWorld?): Bitmap {
        val sig = signature(planet, world)
        val existing = cache[planet.id]
        if (existing != null && existing.signature == sig) return existing.bitmap
        val bmp = existing?.bitmap ?: Bitmap.createBitmap(texSize, texSize, Bitmap.Config.ARGB_8888)
        render(planet, world, bmp)
        val e = Entry(bmp)
        e.signature = sig
        cache[planet.id] = e
        return bmp
    }

    fun invalidate(planetId: Int) { cache.remove(planetId) }

    private fun signature(planet: Planet, world: PlanetWorld?): Int {
        var s = planet.state.ordinal * 31
        s = s * 31 + (planet.integrity * 12).toInt()
        s = s * 31 + (planet.burning * 8).toInt()
        s = s * 31 + (planet.radiation * 6).toInt()
        s = s * 31 + (planet.temperature / 25f).toInt()
        s = s * 31 + (if (planet.population > 1) 1 else 0)
        s = s * 31 + (world?.tick?.div(120)?.toInt() ?: 0)
        return s
    }

    private fun render(planet: Planet, world: PlanetWorld?, bmp: Bitmap) {
        val n = ValueNoise(planet.seed)
        val clouds = ValueNoise(planet.seed * 3 + 11)
        val r = texSize / 2f
        val lightX = -0.45f
        val lightY = -0.55f
        val lightZ = 0.70f
        val hot = planet.burning
        val destroyed = planet.destroyed

        for (py in 0 until texSize) {
            for (px in 0 until texSize) {
                val dx = (px + 0.5f - r) / r
                val dy = (py + 0.5f - r) / r
                val d2 = dx * dx + dy * dy
                val i = py * texSize + px
                if (d2 > 1f) { pixels[i] = 0; continue }
                val dz = sqrt(max(0f, 1f - d2))

                // Spherical mapping so the texture wraps believably.
                val u = (kotlin.math.atan2(dx, dz) / MathX.TAU + 0.5f)
                val v = (kotlin.math.asin(MathX.clamp(dy, -1f, 1f)) / Math.PI.toFloat() + 0.5f)

                var color = surfaceColor(planet, world, n, u, v)

                // Clouds / storm bands.
                if (planet.atmosphere > 0.25f && !destroyed) {
                    val cl = clouds.fbm(u * 8f, v * 5f, 4)
                    val threshold = if (planet.type.gas) 0.42f else 0.62f
                    if (cl > threshold) {
                        val a = MathX.clamp((cl - threshold) * 3.2f, 0f, 0.8f) * min(1f, planet.atmosphere)
                        color = blend(color, 0xFFFFFFFF.toInt(), a)
                    }
                }

                // Molten cracks after heavy damage.
                if (hot > 0.02f) {
                    val crack = n.ridge(u * 9f + 4f, v * 9f, 3)
                    if (crack > 1f - hot * 0.75f) {
                        color = blend(color, 0xFFFF6A1E.toInt(), MathX.clamp(hot * 1.4f, 0f, 1f))
                    }
                }

                // Night side + city lights.
                val lambert = MathX.clamp(dx * lightX + dy * lightY + dz * lightZ, -1f, 1f)
                var shade = MathX.clamp(0.14f + lambert * 1.05f, 0.05f, 1.25f)
                if (lambert < 0.02f && planet.population > 2.0 && !destroyed) {
                    val lights = n.fbm(u * 26f, v * 26f, 3)
                    val density = MathX.clamp((planet.population / 3000.0).toFloat(), 0f, 1f)
                    if (lights > 0.72f - density * 0.18f) {
                        color = blend(color, 0xFFFFD59A.toInt(), 0.55f)
                        shade = max(shade, 0.5f)
                    }
                }
                color = shadeColor(color, shade)

                // Rim light for a bit of atmosphere scattering.
                val rim = MathX.clamp((sqrt(d2) - 0.82f) / 0.18f, 0f, 1f)
                if (rim > 0f && planet.atmosphere > 0.2f) {
                    val atmoColor = if (planet.type.gas) 0xFFFFD9A0.toInt() else 0xFF7FC4FF.toInt()
                    color = blend(color, atmoColor, rim * 0.55f * min(1f, planet.atmosphere))
                }
                pixels[i] = color
            }
        }

        // Shatter the sphere visually when the planet is broken apart.
        if (planet.state.ordinal >= 3 && !destroyed) {
            val rng = Rng(planet.seed + 5)
            repeat(3) {
                val ang = rng.range(0f, MathX.TAU)
                carveCrack(pixels, texSize, ang, rng)
            }
        }
        bmp.setPixels(pixels, 0, texSize, 0, 0, texSize, texSize)
    }

    private fun carveCrack(px: IntArray, size: Int, angle: Float, rng: Rng) {
        var x = size / 2f
        var y = size / 2f
        var a = angle
        var steps = 0
        while (steps < size) {
            steps++
            a += rng.range(-0.25f, 0.25f)
            x += cos(a) * 1.4f
            y += sin(a) * 1.4f
            val w = rng.range(1f, 2.6f)
            for (oy in -w.toInt()..w.toInt()) for (ox in -w.toInt()..w.toInt()) {
                val ix = (x + ox).toInt()
                val iy = (y + oy).toInt()
                if (ix < 0 || iy < 0 || ix >= size || iy >= size) continue
                val i = iy * size + ix
                if (px[i] ushr 24 == 0) continue
                px[i] = blend(px[i], 0xFF1A0A06.toInt(), 0.85f)
            }
        }
    }

    private fun surfaceColor(
        planet: Planet,
        world: PlanetWorld?,
        n: ValueNoise,
        u: Float,
        v: Float
    ): Int {
        if (world != null) {
            // Planet has a live surface: sample the actual tile map.
            val tx = MathX.clamp((u * world.width).toInt(), 0, world.width - 1)
            val ty = MathX.clamp((v * world.height).toInt(), 0, world.height - 1)
            val i = world.idx(tx, ty)
            var c = Biome.of(world.biome[i].toInt()).color
            if (world.fire[i] > 0.2f) c = blend(c, 0xFFFF6A1E.toInt(), 0.7f)
            if (world.radiation[i] > 0.2f) c = blend(c, 0xFF9CCC65.toInt(), 0.35f)
            if (world.structure[i].toInt() != 0) c = blend(c, 0xFFEFE3C8.toInt(), 0.45f)
            return c
        }
        val t = planet.type
        val h = n.fbm(u * 6f, v * 6f, 5)
        val detail = n.fbm(u * 18f + 3f, v * 18f, 3)
        val polar = MathX.clamp((abs(v - 0.5f) - 0.32f) / 0.18f, 0f, 1f)

        var c = when (t) {
            PlanetType.GAS_GIANT, PlanetType.ICE_GIANT -> {
                val band = (sin(v * 26f + n.fbm(u * 3f, v * 3f, 3) * 4f) * 0.5f + 0.5f)
                blend(t.baseColor, t.altColor, band)
            }
            else -> {
                val sea = when (t) {
                    PlanetType.OCEAN -> 0.60f
                    PlanetType.TERRAN -> 0.50f
                    PlanetType.JUNGLE -> 0.46f
                    PlanetType.SAVANNA -> 0.42f
                    PlanetType.DESERT -> 0.33f
                    PlanetType.ICE, PlanetType.TUNDRA -> 0.46f
                    else -> 0.30f
                }
                if (h < sea && t.habitability > 0.2f) {
                    blend(0xFF10386E.toInt(), t.baseColor, MathX.clamp(h / sea, 0f, 1f))
                } else {
                    blend(t.baseColor, t.altColor, MathX.clamp((h - sea) * 2.2f + detail * 0.3f, 0f, 1f))
                }
            }
        }
        if (t == PlanetType.LAVA) {
            val glow = n.ridge(u * 7f, v * 7f, 3)
            if (glow > 0.55f) c = blend(c, 0xFFFF7A22.toInt(), (glow - 0.55f) * 2.2f)
        }
        if (polar > 0f && planet.temperature < 40f && !t.gas) {
            c = blend(c, 0xFFEFF7FF.toInt(), polar * MathX.clamp(1f - planet.temperature / 40f, 0f, 1f))
        }
        if (planet.radiation > 0.1f) c = blend(c, 0xFF9CCC65.toInt(), planet.radiation * 0.3f)
        return c
    }

    companion object {
        fun blend(a: Int, b: Int, t: Float): Int {
            val tt = MathX.clamp(t, 0f, 1f)
            val ar = (a shr 16) and 0xFF
            val ag = (a shr 8) and 0xFF
            val ab = a and 0xFF
            val br = (b shr 16) and 0xFF
            val bg = (b shr 8) and 0xFF
            val bb = b and 0xFF
            val r = (ar + (br - ar) * tt).toInt()
            val g = (ag + (bg - ag) * tt).toInt()
            val bl = (ab + (bb - ab) * tt).toInt()
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }

        fun shadeColor(c: Int, mul: Float): Int {
            val r = MathX.clamp(((c shr 16 and 0xFF) * mul), 0f, 255f).toInt()
            val g = MathX.clamp(((c shr 8 and 0xFF) * mul), 0f, 255f).toInt()
            val b = MathX.clamp(((c and 0xFF) * mul), 0f, 255f).toInt()
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun withAlpha(c: Int, a: Float): Int {
            val alpha = MathX.clamp(a, 0f, 1f)
            return (((c ushr 24) * alpha).toInt() shl 24) or (c and 0x00FFFFFF)
        }

        fun alphaOnly(c: Int, a: Int): Int = (a shl 24) or (c and 0x00FFFFFF)
    }
}

/** Pre-rendered parallax star field + nebula clouds for the galaxy background. */
class SpaceBackdrop(seed: Long) {
    class Layer(val count: Int, val parallax: Float, val size: Float) {
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val alpha = FloatArray(count)
        val tint = IntArray(count)
        val twinkle = FloatArray(count)
    }

    val layers = ArrayList<Layer>()
    var nebula: Bitmap? = null
    private val rng = Rng(seed)

    init {
        val specs = arrayOf(
            Triple(320, 0.12f, 1.4f),
            Triple(220, 0.28f, 2.0f),
            Triple(120, 0.52f, 3.0f)
        )
        for ((count, parallax, size) in specs) {
            val layer = Layer(count, parallax, size)
            for (i in 0 until count) {
                layer.xs[i] = rng.range(0f, 1f)
                layer.ys[i] = rng.range(0f, 1f)
                layer.alpha[i] = rng.range(0.25f, 1f)
                layer.twinkle[i] = rng.range(0f, MathX.TAU)
                layer.tint[i] = when (rng.nextInt(6)) {
                    0 -> 0xFFBFD8FF.toInt()
                    1 -> 0xFFFFE2C0.toInt()
                    2 -> 0xFFD9C8FF.toInt()
                    else -> 0xFFFFFFFF.toInt()
                }
            }
            layers.add(layer)
        }
        nebula = buildNebula(seed)
    }

    private fun buildNebula(seed: Long): Bitmap {
        val s = 192
        val n1 = ValueNoise(seed + 3)
        val n2 = ValueNoise(seed + 91)
        val px = IntArray(s * s)
        for (y in 0 until s) {
            for (x in 0 until s) {
                val u = x / s.toFloat()
                val v = y / s.toFloat()
                val a = n1.fbm(u * 3.4f, v * 3.4f, 5)
                val b = n2.fbm(u * 5.1f + 4f, v * 5.1f, 4)
                val density = MathX.clamp((a * 0.7f + b * 0.5f - 0.52f) * 2.2f, 0f, 1f)
                val warm = MathX.clamp(b, 0f, 1f)
                val color = PlanetTextureCache.blend(0xFF3B1E6E.toInt(), 0xFF12496E.toInt(), warm)
                val alpha = (density * 105).toInt()
                px[y * s + x] = (alpha shl 24) or (color and 0x00FFFFFF)
            }
        }
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, s, 0, 0, s, s)
        return bmp
    }
}
