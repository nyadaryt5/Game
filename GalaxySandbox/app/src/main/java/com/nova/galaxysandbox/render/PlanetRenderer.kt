package com.nova.galaxysandbox.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.nova.galaxysandbox.action.Tool
import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.engine.Camera
import com.nova.galaxysandbox.engine.GameEngine
import com.nova.galaxysandbox.fx.ParticleKind
import com.nova.galaxysandbox.world.ActiveDisaster
import com.nova.galaxysandbox.world.Biome
import com.nova.galaxysandbox.world.PlanetWorld
import com.nova.galaxysandbox.world.STRUCT_CITY
import com.nova.galaxysandbox.world.STRUCT_FARM
import com.nova.galaxysandbox.world.STRUCT_HOUSE
import com.nova.galaxysandbox.world.STRUCT_HUT
import com.nova.galaxysandbox.world.STRUCT_NONE
import com.nova.galaxysandbox.world.STRUCT_RUIN
import com.nova.galaxysandbox.world.STRUCT_TOWER
import com.nova.galaxysandbox.world.Species
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a planet surface: terrain bitmap, kingdom borders, structures, creatures,
 * disasters, weather and the brush cursor.
 */
class PlanetRenderer(private val engine: GameEngine) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val dst = RectF()
    private val path = Path()

    private var tileBitmap: Bitmap? = null
    private var tilePixels: IntArray = IntArray(0)
    private var boundWorld: PlanetWorld? = null
    private var rebuildTimer = 0f
    var time = 0f

    var brushScreenX = -1f
    var brushScreenY = -1f

    fun draw(canvas: Canvas, dt: Float) {
        val world = engine.world ?: return
        time += dt
        val cam = engine.planetCam
        if (boundWorld !== world) bind(world)

        rebuildTimer -= dt
        if (world.dirty && rebuildTimer <= 0f) {
            rebuildTiles(world)
            world.dirty = false
            rebuildTimer = 0.08f
        }

        drawSpaceBorder(canvas, cam, world)
        drawTerrain(canvas, cam, world)
        drawStructures(canvas, cam, world)
        drawCreatures(canvas, cam, world)
        drawDisasters(canvas, cam, world)
        drawParticles(canvas, cam)
        drawBrush(canvas, cam)
        drawEdgeShadow(canvas, cam)
    }

    private fun bind(world: PlanetWorld) {
        boundWorld = world
        tilePixels = IntArray(world.size)
        tileBitmap = Bitmap.createBitmap(world.width, world.height, Bitmap.Config.ARGB_8888)
        rebuildTiles(world)
    }

    // --------------------------------------------------------------- terrain

    private fun rebuildTiles(world: PlanetWorld) {
        val bmp = tileBitmap ?: return
        val px = tilePixels
        val w = world.width
        val h = world.height
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val b = Biome.of(world.biome[i].toInt())
                var c = b.color

                // Height shading: fake sun from the north-west.
                val hh = world.heightMap[i]
                val hl = if (x > 0 && y > 0) world.heightMap[i - w - 1] else hh
                val slope = MathX.clamp((hh - hl) * 6f, -0.35f, 0.35f)
                c = PlanetTextureCache.shadeColor(c, 1f + slope)

                if (b.liquid && b != Biome.LAVA) {
                    // Depth tint for water.
                    val depth = MathX.clamp((world.seaLevel - hh) * 3.2f, 0f, 0.8f)
                    c = PlanetTextureCache.blend(c, 0xFF04162E.toInt(), depth)
                }

                val fire = world.fire[i]
                if (fire > 0.02f) {
                    c = PlanetTextureCache.blend(c, 0xFFFF7A22.toInt(), MathX.clamp(fire, 0f, 0.85f))
                }
                val rad = world.radiation[i]
                if (rad > 0.02f) {
                    c = PlanetTextureCache.blend(c, 0xFF9CCC65.toInt(), MathX.clamp(rad * 0.55f, 0f, 0.6f))
                }

                val claimId = world.claim[i].toInt()
                if (claimId > 0 && engine.showBorders) {
                    val k = world.kingdoms.getOrNull(claimId - 1)
                    if (k != null && k.alive) {
                        val border = isBorder(world, x, y, claimId)
                        c = PlanetTextureCache.blend(c, k.color, if (border) 0.72f else 0.20f)
                    }
                }

                val st = world.structure[i].toInt()
                if (st != STRUCT_NONE) {
                    val sc = when (st) {
                        STRUCT_CITY -> 0xFFF2E2C0.toInt()
                        STRUCT_HOUSE -> 0xFFD9C39A.toInt()
                        STRUCT_TOWER -> 0xFFB8B2A8.toInt()
                        STRUCT_FARM -> 0xFFC8D46A.toInt()
                        STRUCT_RUIN -> 0xFF54504A.toInt()
                        else -> 0xFFBFA57A.toInt()
                    }
                    c = PlanetTextureCache.blend(c, sc, 0.75f)
                }
                px[i] = c
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun isBorder(world: PlanetWorld, x: Int, y: Int, claimId: Int): Boolean {
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (!world.inBounds(nx, ny)) return true
            if (world.claim[world.idx(nx, ny)].toInt() != claimId) return true
        }
        return false
    }

    private fun drawSpaceBorder(canvas: Canvas, cam: Camera, world: PlanetWorld) {
        canvas.drawColor(0xFF060812.toInt())
        val x0 = cam.worldToScreenX(0f)
        val y0 = cam.worldToScreenY(0f)
        val x1 = cam.worldToScreenX(world.width.toFloat())
        val y1 = cam.worldToScreenY(world.height.toFloat())
        glowPaint.shader = RadialGradient(
            (x0 + x1) / 2f, (y0 + y1) / 2f, max(x1 - x0, y1 - y0) * 0.75f,
            intArrayOf(0x3348A0FF, 0x1122507F, 0x00000000),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, cam.viewW, cam.viewH, glowPaint)
        glowPaint.shader = null
    }

    private fun drawTerrain(canvas: Canvas, cam: Camera, world: PlanetWorld) {
        val bmp = tileBitmap ?: return
        val x0 = cam.worldToScreenX(0f)
        val y0 = cam.worldToScreenY(0f)
        val x1 = cam.worldToScreenX(world.width.toFloat())
        val y1 = cam.worldToScreenY(world.height.toFloat())
        dst.set(x0, y0, x1, y1)
        // Smooth at low zoom, crisp pixels when zoomed right in.
        tilePaint.isFilterBitmap = cam.zoom < 6f
        canvas.drawBitmap(bmp, null, dst, tilePaint)

        // Animated water shimmer overlay.
        val shimmer = 0.5f + 0.5f * sin(time * 1.4f)
        paint.color = PlanetTextureCache.withAlpha(0xFF7FD8FF.toInt(), 0.04f + shimmer * 0.03f)
        canvas.drawRect(x0, y0, x1, y1, paint)
    }

    // ------------------------------------------------------------ structures

    private fun drawStructures(canvas: Canvas, cam: Camera, world: PlanetWorld) {
        val z = cam.zoom
        if (z < 7f) return   // below this the bitmap already shows them as pixels
        val left = max(0, cam.visibleLeft().toInt() - 1)
        val right = min(world.width - 1, cam.visibleRight().toInt() + 1)
        val top = max(0, cam.visibleTop().toInt() - 1)
        val bottom = min(world.height - 1, cam.visibleBottom().toInt() + 1)
        for (ty in top..bottom) {
            for (tx in left..right) {
                val i = world.idx(tx, ty)
                val st = world.structure[i].toInt()
                if (st == STRUCT_NONE) continue
                val x = cam.worldToScreenX(tx + 0.5f)
                val y = cam.worldToScreenY(ty + 0.5f)
                val s = z * 0.5f
                val k = world.kingdoms.getOrNull(world.claim[i].toInt() - 1)
                val accent = k?.color ?: 0xFF9E9E9E.toInt()
                when (st) {
                    STRUCT_HUT -> {
                        paint.color = 0xFF9A7B4F.toInt()
                        canvas.drawRect(x - s * 0.5f, y - s * 0.4f, x + s * 0.5f, y + s * 0.6f, paint)
                        paint.color = 0xFF6E4A2A.toInt()
                        path.reset()
                        path.moveTo(x - s * 0.65f, y - s * 0.35f)
                        path.lineTo(x, y - s * 0.95f)
                        path.lineTo(x + s * 0.65f, y - s * 0.35f)
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                    STRUCT_HOUSE -> {
                        paint.color = 0xFFE8D8B8.toInt()
                        canvas.drawRect(x - s * 0.55f, y - s * 0.5f, x + s * 0.55f, y + s * 0.6f, paint)
                        paint.color = accent
                        canvas.drawRect(x - s * 0.6f, y - s * 0.75f, x + s * 0.6f, y - s * 0.45f, paint)
                    }
                    STRUCT_CITY -> {
                        paint.color = 0xFFEFE6D2.toInt()
                        canvas.drawRect(x - s * 0.7f, y - s * 1.1f, x - s * 0.1f, y + s * 0.6f, paint)
                        canvas.drawRect(x + s * 0.05f, y - s * 0.7f, x + s * 0.7f, y + s * 0.6f, paint)
                        paint.color = accent
                        canvas.drawRect(x - s * 0.7f, y - s * 1.25f, x - s * 0.1f, y - s * 1.05f, paint)
                        paint.color = 0xFFFFE082.toInt()
                        canvas.drawCircle(x - s * 0.4f, y - s * 0.55f, s * 0.09f, paint)
                        canvas.drawCircle(x + s * 0.35f, y - s * 0.3f, s * 0.09f, paint)
                    }
                    STRUCT_TOWER -> {
                        paint.color = 0xFFB8B2A8.toInt()
                        canvas.drawRect(x - s * 0.32f, y - s * 1.3f, x + s * 0.32f, y + s * 0.6f, paint)
                        paint.color = accent
                        path.reset()
                        path.moveTo(x + s * 0.32f, y - s * 1.3f)
                        path.lineTo(x + s * 1.0f, y - s * 1.1f)
                        path.lineTo(x + s * 0.32f, y - s * 0.9f)
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                    STRUCT_FARM -> {
                        paint.color = 0xFFC8D46A.toInt()
                        canvas.drawRect(x - s * 0.7f, y - s * 0.5f, x + s * 0.7f, y + s * 0.6f, paint)
                        paint.color = 0xFF8FA43F.toInt()
                        var gx = x - s * 0.6f
                        while (gx < x + s * 0.7f) {
                            canvas.drawRect(gx, y - s * 0.5f, gx + s * 0.1f, y + s * 0.6f, paint)
                            gx += s * 0.28f
                        }
                    }
                    STRUCT_RUIN -> {
                        paint.color = 0xFF5A544C.toInt()
                        canvas.drawRect(x - s * 0.5f, y - s * 0.1f, x - s * 0.1f, y + s * 0.5f, paint)
                        canvas.drawRect(x + s * 0.15f, y - s * 0.3f, x + s * 0.45f, y + s * 0.5f, paint)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------- creatures

    private fun drawCreatures(canvas: Canvas, cam: Camera, world: PlanetWorld) {
        val z = cam.zoom
        val r = max(1.2f, z * 0.26f)
        for (u in world.units) {
            if (!u.alive) continue
            val x = cam.worldToScreenX(u.x)
            val y = cam.worldToScreenY(u.y)
            if (x < -20 || y < -20 || x > cam.viewW + 20 || y > cam.viewH + 20) continue
            val bob = sin(u.bob) * r * 0.22f

            // Shadow
            paint.color = 0x40000000
            canvas.drawOval(x - r * 0.8f, y + r * 0.6f, x + r * 0.8f, y + r * 1.05f, paint)

            val body = if (u.flash > 0f)
                PlanetTextureCache.blend(u.species.color, 0xFFFF5252.toInt(), u.flash)
            else u.species.color

            if (z > 12f) {
                // Detailed little body when zoomed in.
                paint.color = u.species.accent
                canvas.drawRect(x - r * 0.5f, y - r * 0.1f + bob, x + r * 0.5f, y + r * 0.75f + bob, paint)
                paint.color = body
                canvas.drawCircle(x, y - r * 0.45f + bob, r * 0.52f, paint)
                if (u.species == Species.DRAGON) {
                    paint.color = PlanetTextureCache.withAlpha(u.species.accent, 0.85f)
                    path.reset()
                    val flap = sin(u.bob * 2f) * r * 0.5f
                    path.moveTo(x, y + bob)
                    path.lineTo(x - r * 2.2f, y - r * 0.8f + flap + bob)
                    path.lineTo(x - r * 0.4f, y + r * 0.4f + bob)
                    path.close()
                    canvas.drawPath(path, paint)
                    path.reset()
                    path.moveTo(x, y + bob)
                    path.lineTo(x + r * 2.2f, y - r * 0.8f + flap + bob)
                    path.lineTo(x + r * 0.4f, y + r * 0.4f + bob)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                // Health bar for wounded creatures.
                if (u.hp < u.maxHp * 0.95f) {
                    val bw = r * 1.6f
                    paint.color = 0xAA000000.toInt()
                    canvas.drawRect(x - bw / 2f, y - r * 1.6f, x + bw / 2f, y - r * 1.35f, paint)
                    paint.color = 0xFF66DD77.toInt()
                    canvas.drawRect(
                        x - bw / 2f, y - r * 1.6f,
                        x - bw / 2f + bw * MathX.clamp(u.hp / u.maxHp, 0f, 1f), y - r * 1.35f, paint
                    )
                }
            } else {
                paint.color = body
                canvas.drawCircle(x, y + bob, r * 0.75f, paint)
            }
        }
    }

    // -------------------------------------------------------------- disasters

    private fun drawDisasters(canvas: Canvas, cam: Camera, world: PlanetWorld) {
        for (d in world.disasters) {
            val x = cam.worldToScreenX(d.x)
            val y = cam.worldToScreenY(d.y)
            val r = d.radius * cam.zoom
            when (d.kind) {
                ActiveDisaster.Kind.TORNADO -> {
                    paint.color = 0x66CFD8DC
                    for (i in 0 until 10) {
                        val t = i / 9f
                        val rr = r * (0.25f + t * 1.1f)
                        val wob = sin(time * 6f + i * 0.7f) * r * 0.25f
                        canvas.drawOval(
                            x - rr + wob, y - r * 2.4f + t * r * 2.6f - rr * 0.16f,
                            x + rr + wob, y - r * 2.4f + t * r * 2.6f + rr * 0.16f, paint
                        )
                    }
                    paint.color = 0x99B0BEC5.toInt()
                    canvas.drawCircle(x, y, r * 0.3f, paint)
                }
                ActiveDisaster.Kind.VOLCANO -> {
                    glowPaint.shader = RadialGradient(
                        x, y, r * 2.4f,
                        intArrayOf(0xCCFF7A22.toInt(), 0x44FF3B00, 0x00000000),
                        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(x, y, r * 2.4f, glowPaint)
                    glowPaint.shader = null
                }
                ActiveDisaster.Kind.TSUNAMI -> {
                    paint.color = 0xAA2E9BD8.toInt()
                    canvas.drawRect(
                        x - cam.zoom * 1.5f, cam.worldToScreenY(d.y - d.radius),
                        x + cam.zoom * 1.5f, cam.worldToScreenY(d.y + d.radius), paint
                    )
                    paint.color = 0xCCFFFFFF.toInt()
                    canvas.drawRect(
                        x - cam.zoom * 0.4f, cam.worldToScreenY(d.y - d.radius),
                        x + cam.zoom * 0.4f, cam.worldToScreenY(d.y + d.radius), paint
                    )
                }
                ActiveDisaster.Kind.PLAGUE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = 0x668ED14A
                    canvas.drawCircle(x, y, r, paint)
                    paint.style = Paint.Style.FILL
                }
                ActiveDisaster.Kind.ICE_AGE -> {
                    paint.color = PlanetTextureCache.withAlpha(0xFFAFE8FF.toInt(), 0.12f)
                    canvas.drawCircle(x, y, r, paint)
                }
                ActiveDisaster.Kind.ACID_RAIN -> {
                    paint.color = PlanetTextureCache.withAlpha(0xFF9CCC65.toInt(), 0.10f)
                    canvas.drawCircle(x, y, r, paint)
                }
                else -> Unit
            }
        }
    }

    private fun drawParticles(canvas: Canvas, cam: Camera) {
        val fx = engine.worldFx
        for (i in 0 until fx.size()) {
            val p = fx.at(i)
            if (!p.active) continue
            val x = cam.worldToScreenX(p.x)
            val y = cam.worldToScreenY(p.y)
            if (x < -40 || y < -40 || x > cam.viewW + 40 || y > cam.viewH + 40) continue
            val t = MathX.clamp(p.life / p.maxLife, 0f, 1f)
            val s = max(0.8f, p.size * cam.zoom * 0.5f)
            when (p.kind) {
                ParticleKind.SMOKE -> {
                    paint.color = PlanetTextureCache.withAlpha(p.color, t * 0.4f)
                    canvas.drawCircle(x, y, s, paint)
                }
                ParticleKind.SHOCKWAVE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = PlanetTextureCache.withAlpha(p.color, t * 0.5f)
                    canvas.drawCircle(x, y, s, paint)
                    paint.style = Paint.Style.FILL
                }
                ParticleKind.RAIN -> {
                    paint.color = PlanetTextureCache.withAlpha(p.color, t * 0.7f)
                    canvas.drawRect(x, y, x + 1.5f, y + s * 3f, paint)
                }
                else -> {
                    paint.color = PlanetTextureCache.withAlpha(p.color, t)
                    canvas.drawCircle(x, y, s, paint)
                }
            }
        }
    }

    private fun drawBrush(canvas: Canvas, cam: Camera) {
        if (brushScreenX < 0f) return
        if (engine.selectedTool == Tool.INSPECT) return
        val r = engine.selectedTool.defaultRadius * engine.brushScale * cam.zoom
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = PlanetTextureCache.withAlpha(engine.selectedTool.tint, 0.85f)
        canvas.drawCircle(brushScreenX, brushScreenY, r, paint)
        paint.strokeWidth = 1f
        paint.color = PlanetTextureCache.withAlpha(engine.selectedTool.tint, 0.30f)
        canvas.drawCircle(brushScreenX, brushScreenY, r * (1.1f + 0.06f * sin(time * 5f)), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawEdgeShadow(canvas: Canvas, cam: Camera) {
        glowPaint.shader = RadialGradient(
            cam.viewW / 2f, cam.viewH / 2f, max(cam.viewW, cam.viewH) * 0.8f,
            intArrayOf(0x00000000, 0x00000000, 0x77000000),
            floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, cam.viewW, cam.viewH, glowPaint)
        glowPaint.shader = null
    }
}
