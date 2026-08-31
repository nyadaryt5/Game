package com.nova.galaxysandbox.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import com.nova.galaxysandbox.action.Weapon
import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.engine.Camera
import com.nova.galaxysandbox.engine.GameEngine
import com.nova.galaxysandbox.fx.ParticleKind
import com.nova.galaxysandbox.fx.ParticleSystem
import com.nova.galaxysandbox.galaxy.Planet
import com.nova.galaxysandbox.galaxy.StarSystem
import com.nova.galaxysandbox.galaxy.StarType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class GalaxyRenderer(private val engine: GameEngine) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    private val path = Path()
    private val matrix = Matrix()

    val textures = PlanetTextureCache(128)
    private var backdrop = SpaceBackdrop(1234)
    private var backdropSeed = 0L
    var time = 0f

    fun draw(canvas: Canvas, dt: Float) {
        time += dt
        val cam = engine.galaxyCam
        val galaxy = engine.galaxy
        if (backdropSeed != galaxy.seed) {
            backdrop = SpaceBackdrop(galaxy.seed)
            backdropSeed = galaxy.seed
        }

        drawBackground(canvas, cam)
        drawGalacticHaze(canvas, cam)

        // Systems: skip anything far outside the viewport.
        for (system in galaxy.systems) {
            val sx = cam.worldToScreenX(system.x)
            val sy = cam.worldToScreenY(system.y)
            val margin = 2600f * cam.zoom
            if (sx < -margin || sy < -margin || sx > cam.viewW + margin || sy > cam.viewH + margin) continue
            drawSystem(canvas, cam, system, sx, sy)
        }

        drawDebris(canvas, cam)
        drawFleets(canvas, cam)
        drawWeaponEffects(canvas, cam)
        drawParticles(canvas, cam, engine.fx)
        drawSelection(canvas, cam)
        drawOverlays(canvas, cam)
    }

    // ------------------------------------------------------------- background

    private fun drawBackground(canvas: Canvas, cam: Camera) {
        canvas.drawColor(0xFF04030B.toInt())
        val neb = backdrop.nebula
        if (neb != null) {
            bmpPaint.alpha = 190
            val tiles = 3
            val span = 4200f * cam.zoom
            val ox = -cam.x * cam.zoom * 0.35f
            val oy = -cam.y * cam.zoom * 0.35f
            for (ty in -1..tiles) for (tx in -1..tiles) {
                val left = ox + tx * span + cam.viewW / 2f - span
                val top = oy + ty * span + cam.viewH / 2f - span
                if (left > cam.viewW || top > cam.viewH || left + span < 0 || top + span < 0) continue
                dst.set(left, top, left + span, top + span)
                canvas.drawBitmap(neb, null, dst, bmpPaint)
            }
            bmpPaint.alpha = 255
        }

        for (layer in backdrop.layers) {
            for (i in 0 until layer.count) {
                val px = ((layer.xs[i] * cam.viewW - cam.x * cam.zoom * layer.parallax) % cam.viewW + cam.viewW) % cam.viewW
                val py = ((layer.ys[i] * cam.viewH - cam.y * cam.zoom * layer.parallax) % cam.viewH + cam.viewH) % cam.viewH
                val tw = 0.7f + 0.3f * sin(time * 2f + layer.twinkle[i])
                paint.color = PlanetTextureCache.withAlpha(layer.tint[i], layer.alpha[i] * tw)
                canvas.drawCircle(px, py, layer.size * (0.8f + tw * 0.4f), paint)
            }
        }
    }

    /** Soft glow along the galactic disc so the spiral reads at low zoom. */
    private fun drawGalacticHaze(canvas: Canvas, cam: Camera) {
        val cx = cam.worldToScreenX(0f)
        val cy = cam.worldToScreenY(0f)
        val r = 6400f * cam.zoom
        if (r < 12f) return
        glowPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(0x66443C8A, 0x33241B52, 0x00000000),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, glowPaint)
        glowPaint.shader = null
    }

    // ----------------------------------------------------------------- system

    private fun drawSystem(canvas: Canvas, cam: Camera, system: StarSystem, sx: Float, sy: Float) {
        val z = cam.zoom

        // Orbit rings
        if (engine.showOrbits && z > 0.09f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(0.6f, 1f * z)
            paint.color = 0x2A9AB6FF
            for (p in system.planets) {
                if (p.destroyed) continue
                canvas.drawOval(
                    sx - p.orbitRadius * z, sy - p.orbitRadius * 0.9f * z,
                    sx + p.orbitRadius * z, sy + p.orbitRadius * 0.9f * z, paint
                )
            }
            paint.style = Paint.Style.FILL
        }

        drawStar(canvas, system, sx, sy, z)

        for (p in system.planets) {
            if (p.destroyed) continue
            val px = cam.worldToScreenX(p.x)
            val py = cam.worldToScreenY(p.y)
            drawPlanet(canvas, cam, p, px, py, p.radius * z)
        }

        if (engine.showNames && z > 0.16f) {
            textPaint.color = 0xB0CFE4FF.toInt()
            textPaint.textSize = MathX.clamp(15f * z, 11f, 30f)
            canvas.drawText(system.name, sx, sy - system.starRadius * z - 14f, textPaint)
        }
    }

    private fun drawStar(canvas: Canvas, system: StarSystem, sx: Float, sy: Float, z: Float) {
        val r = system.starRadius * z
        val pulse = 1f + 0.045f * sin(system.starPulse * 1.6f + system.id)
        if (!system.starAlive) {
            // Collapsed remnant: dark disc with a faint accretion ring.
            paint.style = Paint.Style.FILL
            paint.color = 0xFF07060D.toInt()
            canvas.drawCircle(sx, sy, r * 0.5f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, r * 0.08f)
            paint.color = 0x66FF9A3C
            canvas.drawCircle(sx, sy, r * 0.78f, paint)
            paint.style = Paint.Style.FILL
            return
        }
        if (system.novaTimer > 0f) {
            val t = 1f - system.novaTimer / 2.2f
            val blast = r * (1f + t * 14f)
            glowPaint.shader = RadialGradient(
                sx, sy, blast,
                intArrayOf(0xFFFFFFFF.toInt(), 0xCCFFD27A.toInt(), 0x00FF6A1E),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(sx, sy, blast, glowPaint)
            glowPaint.shader = null
            return
        }

        if (system.starType == StarType.BLACK_HOLE) {
            drawBlackHole(canvas, sx, sy, r, system.starPulse)
            return
        }

        val glowR = r * 4.2f * pulse
        glowPaint.shader = RadialGradient(
            sx, sy, glowR,
            intArrayOf(
                PlanetTextureCache.withAlpha(system.starType.core, 0.95f),
                PlanetTextureCache.withAlpha(system.starType.glow, 0.42f),
                PlanetTextureCache.withAlpha(system.starType.glow, 0f)
            ),
            floatArrayOf(0.06f, 0.28f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sx, sy, glowR, glowPaint)
        glowPaint.shader = null

        paint.color = system.starType.core
        canvas.drawCircle(sx, sy, r * pulse, paint)

        // Corona spikes
        if (z > 0.2f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, r * 0.06f)
            paint.color = PlanetTextureCache.withAlpha(system.starType.glow, 0.5f)
            for (i in 0 until 8) {
                val a = i * (MathX.TAU / 8) + system.starPulse * 0.25f
                val len = r * (1.4f + 0.5f * sin(system.starPulse * 3f + i))
                canvas.drawLine(
                    sx + cos(a) * r * 1.05f, sy + sin(a) * r * 1.05f,
                    sx + cos(a) * len, sy + sin(a) * len, paint
                )
            }
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawBlackHole(canvas: Canvas, sx: Float, sy: Float, r: Float, phase: Float) {
        matrix.reset()
        matrix.postRotate(phase * 30f, sx, sy)
        val sweep = SweepGradient(
            sx, sy,
            intArrayOf(0x00000000, 0xCCFFAA44.toInt(), 0x66FF6622, 0xFFFFD9A0.toInt(), 0x00000000),
            floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)
        )
        sweep.setLocalMatrix(matrix)
        glowPaint.shader = sweep
        canvas.drawCircle(sx, sy, r * 2.6f, glowPaint)
        glowPaint.shader = null
        paint.color = 0xFF000000.toInt()
        canvas.drawCircle(sx, sy, r * 1.05f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.2f, r * 0.1f)
        paint.color = 0xAAFFD9A0.toInt()
        canvas.drawCircle(sx, sy, r * 1.25f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPlanet(canvas: Canvas, cam: Camera, p: Planet, px: Float, py: Float, r: Float) {
        if (r < 0.7f) {
            paint.color = PlanetTextureCache.withAlpha(p.type.baseColor, 0.85f)
            canvas.drawCircle(px, py, max(0.8f, r), paint)
            return
        }
        // Atmospheric halo
        if (p.atmosphere > 0.15f && r > 3f) {
            glowPaint.shader = RadialGradient(
                px, py, r * 1.7f,
                intArrayOf(0x00000000, PlanetTextureCache.withAlpha(0xFF7FC4FF.toInt(), 0.22f * min(1f, p.atmosphere)), 0x00000000),
                floatArrayOf(0.55f, 0.78f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(px, py, r * 1.7f, glowPaint)
            glowPaint.shader = null
        }

        // Rings behind the planet
        if (p.hasRings && r > 4f) drawRings(canvas, p, px, py, r, back = true)

        val world = if (engine.world?.planet === p) engine.world else null
        val tex = textures.get(p, world)
        val jitter = if (p.quakeTimer > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * r * 0.12f else 0f
        dst.set(px - r + jitter, py - r, px + r + jitter, py + r)
        canvas.drawBitmap(tex, null, dst, bmpPaint)

        if (p.hasRings && r > 4f) drawRings(canvas, p, px, py, r, back = false)

        // Burning glow
        if (p.burning > 0.03f && r > 2f) {
            glowPaint.shader = RadialGradient(
                px, py, r * 2.2f,
                intArrayOf(PlanetTextureCache.withAlpha(0xFFFF6A1E.toInt(), 0.5f * p.burning), 0x00000000),
                floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(px, py, r * 2.2f, glowPaint)
            glowPaint.shader = null
        }

        // Shield bubble
        if (p.shield > 0.03f && r > 3f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, r * 0.07f)
            val flicker = 0.6f + 0.4f * sin(time * 5f + p.id)
            paint.color = PlanetTextureCache.withAlpha(0xFF7FD0FF.toInt(), p.shield * 0.75f * flicker)
            canvas.drawCircle(px, py, r * 1.22f, paint)
            paint.style = Paint.Style.FILL
        }

        // Faction ownership marker
        if (p.factionId >= 0 && engine.showBorders && r > 2.5f) {
            val f = engine.galaxy.factions.getOrNull(p.factionId)
            if (f != null) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, r * 0.09f)
                paint.color = PlanetTextureCache.withAlpha(f.color, 0.75f)
                canvas.drawCircle(px, py, r * 1.35f, paint)
                paint.style = Paint.Style.FILL
            }
        }

        // Moons
        for (m in p.moons) {
            if (!m.alive) continue
            val mx = cam.worldToScreenX(m.x)
            val my = cam.worldToScreenY(m.y)
            val mr = m.radius * cam.zoom
            if (mr < 0.5f) continue
            paint.color = m.color
            canvas.drawCircle(mx, my, mr, paint)
            paint.color = 0x44000000
            canvas.drawCircle(mx + mr * 0.3f, my + mr * 0.2f, mr * 0.8f, paint)
        }

        if (engine.showNames && r > 10f) {
            textPaint.textSize = MathX.clamp(r * 0.45f, 12f, 34f)
            textPaint.color = 0xCCE8F4FF.toInt()
            canvas.drawText(p.name, px, py + r + textPaint.textSize + 4f, textPaint)
            if (p.population > 1) {
                textPaint.textSize *= 0.8f
                textPaint.color = 0x99A8F0C0.toInt()
                canvas.drawText(
                    com.nova.galaxysandbox.galaxy.GalaxySim.formatPop(p.population),
                    px, py + r + textPaint.textSize * 2.4f, textPaint
                )
            }
        }
    }

    private fun drawRings(canvas: Canvas, p: Planet, px: Float, py: Float, r: Float, back: Boolean) {
        canvas.save()
        canvas.rotate(p.tilt * 40f, px, py)
        paint.style = Paint.Style.STROKE
        val bands = 5
        for (i in 0 until bands) {
            val rr = r * (1.5f + i * 0.16f)
            paint.strokeWidth = max(1f, r * 0.08f)
            paint.color = PlanetTextureCache.withAlpha(p.ringColor, if (i % 2 == 0) 0.5f else 0.28f)
            val rect = RectF(px - rr, py - rr * 0.28f, px + rr, py + rr * 0.28f)
            if (back) canvas.drawArc(rect, 180f, 180f, false, paint)
            else canvas.drawArc(rect, 0f, 180f, false, paint)
        }
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    // ---------------------------------------------------------------- effects

    private fun drawDebris(canvas: Canvas, cam: Camera) {
        for (field in engine.galaxy.debris) {
            for (c in field.chunks) {
                val x = cam.worldToScreenX(c[0])
                val y = cam.worldToScreenY(c[1])
                if (x < -40 || y < -40 || x > cam.viewW + 40 || y > cam.viewH + 40) continue
                val s = c[4] * cam.zoom
                if (s < 0.4f) continue
                canvas.save()
                canvas.rotate(c[5] * 57.3f, x, y)
                paint.color = PlanetTextureCache.shadeColor(field.color, 0.75f)
                canvas.drawRect(x - s, y - s * 0.7f, x + s, y + s * 0.7f, paint)
                paint.color = PlanetTextureCache.withAlpha(0xFFFFFFFF.toInt(), 0.12f)
                canvas.drawRect(x - s, y - s * 0.7f, x + s * 0.2f, y - s * 0.2f, paint)
                canvas.restore()
            }
        }
    }

    private fun drawFleets(canvas: Canvas, cam: Camera) {
        if (cam.zoom < 0.05f) return
        for (fleet in engine.galaxy.fleets) {
            val f = engine.galaxy.factions.getOrNull(fleet.factionId) ?: continue
            val x = cam.worldToScreenX(fleet.x)
            val y = cam.worldToScreenY(fleet.y)
            if (x < -50 || y < -50 || x > cam.viewW + 50 || y > cam.viewH + 50) continue
            // Trail
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, 2.4f * cam.zoom)
            paint.color = PlanetTextureCache.withAlpha(f.color, 0.35f)
            path.reset()
            var started = false
            var i = 0
            val trail = fleet.trail.toList()
            while (i + 1 < trail.size) {
                val tx = cam.worldToScreenX(trail[i])
                val ty = cam.worldToScreenY(trail[i + 1])
                if (!started) { path.moveTo(tx, ty); started = true } else path.lineTo(tx, ty)
                i += 2
            }
            if (started) canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            val s = max(2f, 5f * cam.zoom)
            paint.color = f.color
            canvas.drawCircle(x, y, s, paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, y, s * 0.4f, paint)
        }
    }

    private fun drawWeaponEffects(canvas: Canvas, cam: Camera) {
        val ws = engine.weapons

        // Beams
        for (b in ws.beams) {
            val x1 = cam.worldToScreenX(b.x1)
            val y1 = cam.worldToScreenY(b.y1)
            val x2 = cam.worldToScreenX(b.x2)
            val y2 = cam.worldToScreenY(b.y2)
            val w = max(1.5f, b.width * cam.zoom)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = PlanetTextureCache.withAlpha(b.color, 0.28f)
            paint.strokeWidth = w * 3.4f
            canvas.drawLine(x1, y1, x2, y2, paint)
            paint.color = PlanetTextureCache.withAlpha(b.color, 0.75f)
            paint.strokeWidth = w * 1.6f
            canvas.drawLine(x1, y1, x2, y2, paint)
            paint.color = 0xFFFFFFFF.toInt()
            paint.strokeWidth = max(1f, w * 0.55f)
            canvas.drawLine(x1, y1, x2, y2, paint)
            paint.style = Paint.Style.FILL
            glowPaint.shader = RadialGradient(
                x2, y2, w * 6f,
                intArrayOf(0xFFFFFFFF.toInt(), PlanetTextureCache.withAlpha(b.color, 0.5f), 0x00000000),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x2, y2, w * 6f, glowPaint)
            glowPaint.shader = null
        }

        // Projectiles
        for (p in ws.projectiles) {
            val x = cam.worldToScreenX(p.x)
            val y = cam.worldToScreenY(p.y)
            if (x < -120 || y < -120 || x > cam.viewW + 120 || y > cam.viewH + 120) continue
            val s = max(1.5f, p.size * cam.zoom)
            when (p.weapon) {
                Weapon.RAILGUN -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = s * 0.8f
                    paint.color = 0xFFCFF2FF.toInt()
                    canvas.drawLine(x - p.vx * 0.02f * cam.zoom, y - p.vy * 0.02f * cam.zoom, x, y, paint)
                    paint.style = Paint.Style.FILL
                }
                Weapon.ASTEROID, Weapon.METEOR_STORM -> {
                    glowPaint.shader = RadialGradient(
                        x, y, s * 4f,
                        intArrayOf(0xCCFFB27A.toInt(), 0x33FF6A1E, 0x00000000),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(x, y, s * 4f, glowPaint)
                    glowPaint.shader = null
                    canvas.save()
                    canvas.rotate(p.rot * 57.3f, x, y)
                    paint.color = 0xFF8A7A66.toInt()
                    canvas.drawRoundRect(x - s, y - s * 0.8f, x + s, y + s * 0.8f, s * 0.4f, s * 0.4f, paint)
                    paint.color = 0xFF5C4F42.toInt()
                    canvas.drawCircle(x + s * 0.3f, y - s * 0.2f, s * 0.28f, paint)
                    canvas.restore()
                }
                else -> {
                    paint.color = PlanetTextureCache.withAlpha(p.weapon.tint, 0.9f)
                    canvas.drawCircle(x, y, s, paint)
                    paint.color = 0xFFFFFFFF.toInt()
                    canvas.drawCircle(x, y, s * 0.45f, paint)
                }
            }
        }

        // Singularities
        for (s in ws.singularities) {
            val x = cam.worldToScreenX(s.x)
            val y = cam.worldToScreenY(s.y)
            val r = s.radius * cam.zoom
            matrix.reset()
            matrix.postRotate(s.spin * 57.3f, x, y)
            val sweep = SweepGradient(
                x, y,
                intArrayOf(0x00000000, 0xAA9C7BFF.toInt(), 0x553B1E6E, 0xDDD0B8FF.toInt(), 0x00000000),
                floatArrayOf(0f, 0.28f, 0.6f, 0.86f, 1f)
            )
            sweep.setLocalMatrix(matrix)
            glowPaint.shader = sweep
            canvas.drawCircle(x, y, r * 4.5f, glowPaint)
            glowPaint.shader = null
            paint.color = 0xFF000000.toInt()
            canvas.drawCircle(x, y, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, r * 0.14f)
            paint.color = 0xCCD0B8FF.toInt()
            canvas.drawCircle(x, y, r * 1.35f, paint)
            paint.style = Paint.Style.FILL
        }

        // Motherships
        for (m in ws.motherships) {
            val x = cam.worldToScreenX(m.x)
            val y = cam.worldToScreenY(m.y)
            val s = max(6f, 42f * cam.zoom)
            if (m.state == 1) {
                val target = engine.galaxy.planet(m.targetPlanetId)
                if (target != null) {
                    val tx = cam.worldToScreenX(target.x)
                    val ty = cam.worldToScreenY(target.y)
                    paint.style = Paint.Style.FILL
                    path.reset()
                    path.moveTo(x - s * 0.5f, y)
                    path.lineTo(x + s * 0.5f, y)
                    path.lineTo(tx + s * 1.4f, ty)
                    path.lineTo(tx - s * 1.4f, ty)
                    path.close()
                    paint.color = PlanetTextureCache.withAlpha(
                        0xFF32FF9A.toInt(), 0.22f + 0.12f * sin(m.beamPhase * 4f)
                    )
                    canvas.drawPath(path, paint)
                }
            }
            paint.color = 0xFF2A3340.toInt()
            canvas.drawOval(x - s, y - s * 0.35f, x + s, y + s * 0.35f, paint)
            paint.color = 0xFF32FF9A.toInt()
            canvas.drawOval(x - s * 0.45f, y - s * 0.5f, x + s * 0.45f, y + s * 0.1f, paint)
            paint.color = PlanetTextureCache.withAlpha(0xFF32FF9A.toInt(), 0.6f + 0.4f * sin(m.beamPhase))
            canvas.drawCircle(x, y + s * 0.28f, s * 0.14f, paint)
        }
    }

    private fun drawParticles(canvas: Canvas, cam: Camera, fx: ParticleSystem) {
        for (i in 0 until fx.size()) {
            val p = fx.at(i)
            if (!p.active) continue
            val x = cam.worldToScreenX(p.x)
            val y = cam.worldToScreenY(p.y)
            if (x < -80 || y < -80 || x > cam.viewW + 80 || y > cam.viewH + 80) continue
            val t = MathX.clamp(p.life / p.maxLife, 0f, 1f)
            val s = max(0.5f, p.size * cam.zoom)
            when (p.kind) {
                ParticleKind.FLASH -> {
                    glowPaint.shader = RadialGradient(
                        x, y, s,
                        intArrayOf(PlanetTextureCache.withAlpha(0xFFFFFFFF.toInt(), t), 0x00FFFFFF),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(x, y, s, glowPaint)
                    glowPaint.shader = null
                }
                ParticleKind.SHOCKWAVE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = max(1f, s * 0.05f)
                    paint.color = PlanetTextureCache.withAlpha(p.color, t * 0.55f)
                    canvas.drawCircle(x, y, s, paint)
                    paint.style = Paint.Style.FILL
                }
                ParticleKind.SMOKE -> {
                    paint.color = PlanetTextureCache.withAlpha(p.color, t * 0.35f)
                    canvas.drawCircle(x, y, s, paint)
                }
                ParticleKind.DEBRIS -> {
                    canvas.save()
                    canvas.rotate(p.rot * 57.3f, x, y)
                    paint.color = PlanetTextureCache.withAlpha(p.color, t)
                    canvas.drawRect(x - s, y - s * 0.6f, x + s, y + s * 0.6f, paint)
                    canvas.restore()
                }
                ParticleKind.GLOW, ParticleKind.PLASMA -> {
                    glowPaint.shader = RadialGradient(
                        x, y, max(1f, s * 2f),
                        intArrayOf(PlanetTextureCache.withAlpha(p.color, t), 0x00000000),
                        floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(x, y, max(1f, s * 2f), glowPaint)
                    glowPaint.shader = null
                }
                else -> {
                    paint.color = PlanetTextureCache.withAlpha(
                        PlanetTextureCache.blend(p.color2, p.color, t), t
                    )
                    canvas.drawCircle(x, y, s, paint)
                }
            }
        }
    }

    private fun drawSelection(canvas: Canvas, cam: Camera) {
        val p = engine.selectedPlanet ?: return
        if (p.destroyed) return
        val x = cam.worldToScreenX(p.x)
        val y = cam.worldToScreenY(p.y)
        val r = max(16f, p.radius * cam.zoom * 1.6f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f
        paint.color = 0xCC7FE8FF.toInt()
        val sweep = 40f
        for (i in 0 until 4) {
            canvas.drawArc(
                x - r, y - r, x + r, y + r,
                i * 90f + time * 26f, sweep, false, paint
            )
        }
        paint.strokeWidth = 1.2f
        paint.color = 0x557FE8FF
        canvas.drawCircle(x, y, r * 1.25f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawOverlays(canvas: Canvas, cam: Camera) {
        // Impact flash
        if (engine.weapons.flashAmount > 0.01f) {
            canvas.drawColor(
                PlanetTextureCache.withAlpha(0xFFFFFFFF.toInt(), engine.weapons.flashAmount * 0.55f)
            )
        }
        // Vignette
        glowPaint.shader = RadialGradient(
            cam.viewW / 2f, cam.viewH / 2f, max(cam.viewW, cam.viewH) * 0.75f,
            intArrayOf(0x00000000, 0x00000000, 0x88000000.toInt()),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, cam.viewW, cam.viewH, glowPaint)
        glowPaint.shader = null
    }
}
