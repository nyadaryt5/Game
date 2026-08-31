package com.nova.galaxysandbox.fx

import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.core.Rng
import kotlin.math.cos
import kotlin.math.sin

enum class ParticleKind { SPARK, SMOKE, EMBER, DEBRIS, SHOCKWAVE, GLOW, FLASH, PLASMA, SNOW, RAIN, DUST }

class Particle {
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var life = 0f; var maxLife = 1f
    var size = 1f
    var growth = 0f
    var color = 0xFFFFFFFF.toInt()
    var color2 = 0xFFFFFFFF.toInt()
    var kind = ParticleKind.SPARK
    var rot = 0f
    var spin = 0f
    var drag = 0.99f
    var gravity = 0f
    var active = false
}

/**
 * Fixed-capacity particle pool: no allocations per frame, safe to run thousands
 * of particles at 60fps on a mid-range phone.
 */
class ParticleSystem(private val capacity: Int = 3000) {
    private val pool = Array(capacity) { Particle() }
    private var cursor = 0
    val rng = Rng(0xC0FFEE)
    var count = 0
        private set

    fun clear() {
        for (p in pool) p.active = false
        count = 0
    }

    fun obtain(): Particle {
        var tries = 0
        while (tries < capacity) {
            val p = pool[cursor]
            cursor = (cursor + 1) % capacity
            tries++
            if (!p.active) {
                p.active = true
                count++
                return p
            }
        }
        // Everything busy: recycle the oldest slot.
        val p = pool[cursor]
        cursor = (cursor + 1) % capacity
        return p
    }

    inline fun forEachActive(action: (Particle) -> Unit) {
        for (i in 0 until size()) {
            val p = at(i)
            if (p.active) action(p)
        }
    }

    fun size(): Int = capacity
    fun at(i: Int): Particle = pool[i]

    fun update(dt: Float) {
        var alive = 0
        for (p in pool) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) { p.active = false; continue }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += p.gravity * dt
            p.vx *= p.drag
            p.vy *= p.drag
            p.rot += p.spin * dt
            p.size += p.growth * dt
            if (p.size < 0f) { p.active = false; continue }
            alive++
        }
        count = alive
    }

    // --------------------------------------------------------------- emitters

    fun spark(x: Float, y: Float, count: Int, speed: Float, color: Int, life: Float = 0.7f, size: Float = 2.4f) {
        repeat(count) {
            val a = rng.range(0f, MathX.TAU)
            val s = rng.range(speed * 0.25f, speed)
            val p = obtain()
            p.kind = ParticleKind.SPARK
            p.x = x; p.y = y
            p.vx = cos(a) * s; p.vy = sin(a) * s
            p.life = life * rng.range(0.5f, 1.3f); p.maxLife = p.life
            p.size = size * rng.range(0.6f, 1.5f)
            p.growth = -p.size * 0.4f
            p.color = color
            p.color2 = color
            p.drag = 0.985f
            p.gravity = 0f
        }
    }

    fun explosion(x: Float, y: Float, radius: Float, colorHot: Int, colorCool: Int, amount: Int = 60) {
        // Core flash
        val flash = obtain()
        flash.kind = ParticleKind.FLASH
        flash.x = x; flash.y = y
        flash.life = 0.35f; flash.maxLife = flash.life
        flash.size = radius * 1.4f
        flash.growth = radius * 1.6f
        flash.color = 0xFFFFFFFF.toInt()

        val wave = obtain()
        wave.kind = ParticleKind.SHOCKWAVE
        wave.x = x; wave.y = y
        wave.life = 0.9f; wave.maxLife = wave.life
        wave.size = radius * 0.3f
        wave.growth = radius * 4.5f
        wave.color = colorHot

        repeat(amount) {
            val a = rng.range(0f, MathX.TAU)
            val s = rng.range(radius * 0.6f, radius * 3.4f)
            val p = obtain()
            p.kind = if (rng.chance(0.65f)) ParticleKind.EMBER else ParticleKind.SMOKE
            p.x = x + cos(a) * radius * 0.2f
            p.y = y + sin(a) * radius * 0.2f
            p.vx = cos(a) * s; p.vy = sin(a) * s
            p.life = rng.range(0.5f, 1.9f); p.maxLife = p.life
            p.size = radius * rng.range(0.08f, 0.32f)
            p.growth = if (p.kind == ParticleKind.SMOKE) p.size * 0.9f else -p.size * 0.3f
            p.color = if (p.kind == ParticleKind.SMOKE) 0xFF3A3A44.toInt() else colorHot
            p.color2 = colorCool
            p.drag = 0.965f
        }
    }

    fun beamImpact(x: Float, y: Float, color: Int) {
        spark(x, y, 6, 90f, color, 0.35f, 2f)
        val g = obtain()
        g.kind = ParticleKind.GLOW
        g.x = x; g.y = y
        g.life = 0.3f; g.maxLife = g.life
        g.size = 14f
        g.growth = 34f
        g.color = color
    }

    fun debris(x: Float, y: Float, amount: Int, spread: Float, color: Int) {
        repeat(amount) {
            val a = rng.range(0f, MathX.TAU)
            val s = rng.range(spread * 0.2f, spread)
            val p = obtain()
            p.kind = ParticleKind.DEBRIS
            p.x = x; p.y = y
            p.vx = cos(a) * s; p.vy = sin(a) * s
            p.life = rng.range(1.2f, 3.6f); p.maxLife = p.life
            p.size = rng.range(1.4f, 5f)
            p.growth = 0f
            p.rot = rng.range(0f, MathX.TAU)
            p.spin = rng.range(-4f, 4f)
            p.color = color
            p.drag = 0.995f
        }
    }

    fun smokePlume(x: Float, y: Float, amount: Int, rise: Float, scale: Float) {
        repeat(amount) {
            val p = obtain()
            p.kind = ParticleKind.SMOKE
            p.x = x + rng.range(-scale, scale)
            p.y = y + rng.range(-scale, scale)
            p.vx = rng.range(-rise * 0.2f, rise * 0.2f)
            p.vy = -rise * rng.range(0.5f, 1.2f)
            p.life = rng.range(1.4f, 3.2f); p.maxLife = p.life
            p.size = scale * rng.range(0.6f, 1.6f)
            p.growth = scale * 1.1f
            p.color = 0xFF4A4A55.toInt()
            p.drag = 0.98f
        }
    }

    fun plasma(x: Float, y: Float, vx: Float, vy: Float, color: Int, size: Float, life: Float) {
        val p = obtain()
        p.kind = ParticleKind.PLASMA
        p.x = x; p.y = y
        p.vx = vx; p.vy = vy
        p.life = life; p.maxLife = life
        p.size = size
        p.growth = -size * 0.25f
        p.color = color
        p.drag = 0.995f
    }

    fun weather(x: Float, y: Float, kind: ParticleKind, vx: Float, vy: Float, size: Float, life: Float, color: Int) {
        val p = obtain()
        p.kind = kind
        p.x = x; p.y = y
        p.vx = vx; p.vy = vy
        p.life = life; p.maxLife = life
        p.size = size
        p.growth = 0f
        p.color = color
        p.drag = 1f
    }
}
