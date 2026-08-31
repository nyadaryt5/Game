package com.nova.galaxysandbox.world

import com.nova.galaxysandbox.action.Tool
import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.core.Rng
import com.nova.galaxysandbox.fx.ParticleKind
import com.nova.galaxysandbox.fx.ParticleSystem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A disaster that keeps running for a while (tornado, volcano, wave, fallout). */
class ActiveDisaster(
    val kind: Kind,
    var x: Float,
    var y: Float,
    var timer: Float,
    var radius: Float,
    var power: Float
) {
    enum class Kind { TORNADO, VOLCANO, TSUNAMI, ACID_RAIN, ICE_AGE, PLAGUE, METEOR_FALL, QUAKE }

    var vx = 0f
    var vy = 0f
    var phase = 0f
    var alive = true
}

/**
 * Applies player tools to a planet surface and keeps long-running disasters going.
 */
object WorldTools {

    private val rng = Rng(987654321L)

    fun apply(
        world: PlanetWorld,
        tool: Tool,
        tileX: Float,
        tileY: Float,
        radiusScale: Float,
        fx: ParticleSystem?
    ) {
        val r = tool.defaultRadius * radiusScale
        when (tool) {
            Tool.INSPECT -> Unit

            Tool.RAISE -> paintHeight(world, tileX, tileY, r, +0.055f)
            Tool.LOWER -> paintHeight(world, tileX, tileY, r, -0.055f)
            Tool.FOREST -> paintBiome(world, tileX, tileY, r, Biome.FOREST, landOnly = true)
            Tool.GRASSLAND -> paintBiome(world, tileX, tileY, r, Biome.GRASS, landOnly = true)
            Tool.DESERT -> paintBiome(world, tileX, tileY, r, Biome.DESERT, landOnly = true, tempDelta = 12f)
            Tool.SNOW -> paintBiome(world, tileX, tileY, r, Biome.SNOW, landOnly = true, tempDelta = -18f)
            Tool.MOUNTAIN -> {
                paintHeight(world, tileX, tileY, r, +0.14f)
                paintBiome(world, tileX, tileY, r * 0.7f, Biome.MOUNTAIN, landOnly = true)
            }
            Tool.LAVA_BRUSH -> {
                paintBiome(world, tileX, tileY, r, Biome.LAVA, landOnly = false, tempDelta = 60f)
                fx?.spark(tileX, tileY, 10, 8f, 0xFFFF7A2A.toInt(), 0.6f, 0.4f)
            }

            Tool.HUMANS -> spawn(world, tileX, tileY, r, Species.HUMAN, 6)
            Tool.ELVES -> spawn(world, tileX, tileY, r, Species.ELF, 6)
            Tool.ORCS -> spawn(world, tileX, tileY, r, Species.ORC, 6)
            Tool.DWARVES -> spawn(world, tileX, tileY, r, Species.DWARF, 6)
            Tool.FROSTKIN -> spawn(world, tileX, tileY, r, Species.FROSTKIN, 6)
            Tool.SYNTHS -> spawn(world, tileX, tileY, r, Species.SYNTH, 5)
            Tool.WOLVES -> spawn(world, tileX, tileY, r, Species.WOLF, 4)
            Tool.BEARS -> spawn(world, tileX, tileY, r, Species.BEAR, 2)
            Tool.DRAGONS -> spawn(world, tileX, tileY, r, Species.DRAGON, 1)
            Tool.XENOS -> spawn(world, tileX, tileY, r, Species.XENO, 4)

            Tool.METEOR -> meteor(world, tileX, tileY, r, fx)
            Tool.VOLCANO -> {
                world.disasters.add(
                    ActiveDisaster(ActiveDisaster.Kind.VOLCANO, tileX, tileY, 26f, r, 1f)
                )
                crater(world, tileX, tileY, r * 0.5f, Biome.LAVA)
                world.log("A volcano tears open the ground.")
            }
            Tool.NUKE_TOOL -> nuke(world, tileX, tileY, r, fx)
            Tool.LIGHTNING -> {
                val i = clampIdx(world, tileX, tileY)
                world.fire[i] = 1f
                damageArea(world, tileX, tileY, 2f, 200f)
                fx?.spark(tileX, tileY, 24, 16f, 0xFFFFF7A0.toInt(), 0.5f, 0.5f)
                world.dirty = true
            }
            Tool.TSUNAMI -> {
                val d = ActiveDisaster(ActiveDisaster.Kind.TSUNAMI, 0f, tileY, 16f, r, 1f)
                d.vx = world.width / 14f
                world.disasters.add(d)
                world.log("A tsunami races toward the coast.")
            }
            Tool.TORNADO -> {
                val d = ActiveDisaster(ActiveDisaster.Kind.TORNADO, tileX, tileY, 40f, r, 1f)
                d.vx = rng.range(-6f, 6f)
                d.vy = rng.range(-3f, 3f)
                world.disasters.add(d)
            }
            Tool.PLAGUE_TOOL -> {
                world.disasters.add(ActiveDisaster(ActiveDisaster.Kind.PLAGUE, tileX, tileY, 22f, r, 1f))
                world.log("A plague begins to spread.")
            }
            Tool.ICE_AGE -> {
                world.disasters.add(ActiveDisaster(ActiveDisaster.Kind.ICE_AGE, tileX, tileY, 30f, r, 1f))
                world.log("The long winter comes.")
            }
            Tool.ACID_RAIN -> {
                world.disasters.add(ActiveDisaster(ActiveDisaster.Kind.ACID_RAIN, tileX, tileY, 22f, r, 1f))
            }
            Tool.EARTHQUAKE -> earthquake(world, tileX, tileY, r, fx)

            Tool.HEAL -> heal(world, tileX, tileY, r)
            Tool.SMITE -> {
                damageArea(world, tileX, tileY, r * 0.4f, 900f)
                crater(world, tileX, tileY, r * 0.35f, Biome.ASH)
                fx?.explosion(tileX, tileY, r, 0xFFFFF6C0.toInt(), 0xFFFFC24A.toInt(), 40)
            }
            Tool.ERASE -> {
                for (u in world.units) if (MathX.dist(u.x, u.y, tileX, tileY) < r) u.alive = false
            }
            Tool.ARMAGEDDON -> armageddon(world, fx)
        }
        world.syncToPlanet()
    }

    // ------------------------------------------------------------- primitives

    private fun clampIdx(world: PlanetWorld, x: Float, y: Float): Int {
        val ix = MathX.clamp(x.toInt(), 0, world.width - 1)
        val iy = MathX.clamp(y.toInt(), 0, world.height - 1)
        return world.idx(ix, iy)
    }

    private inline fun forArea(world: PlanetWorld, cx: Float, cy: Float, r: Float, body: (Int, Int, Int, Float) -> kotlin.Unit) {
        val x0 = max(0, (cx - r).toInt())
        val x1 = min(world.width - 1, (cx + r).toInt())
        val y0 = max(0, (cy - r).toInt())
        val y1 = min(world.height - 1, (cy + r).toInt())
        for (y in y0..y1) for (x in x0..x1) {
            val d = MathX.dist(cx, cy, x + 0.5f, y + 0.5f)
            if (d > r) continue
            body(x, y, world.idx(x, y), 1f - d / r)
        }
    }

    private fun paintHeight(world: PlanetWorld, cx: Float, cy: Float, r: Float, delta: Float) {
        forArea(world, cx, cy, r) { x, y, i, falloff ->
            world.heightMap[i] = MathX.clamp(world.heightMap[i] + delta * falloff, 0f, 1f)
            world.biome[i] = reclassify(world, i).ordinal.toByte()
        }
        world.dirty = true
    }

    private fun paintBiome(
        world: PlanetWorld,
        cx: Float,
        cy: Float,
        r: Float,
        b: Biome,
        landOnly: Boolean,
        tempDelta: Float = 0f
    ) {
        forArea(world, cx, cy, r) { x, y, i, falloff ->
            val cur = Biome.of(world.biome[i].toInt())
            if (landOnly && cur.liquid && falloff < 0.55f) return@forArea
            if (landOnly && cur.liquid) {
                world.heightMap[i] = world.seaLevel + 0.02f
            }
            world.biome[i] = b.ordinal.toByte()
            world.temperature[i] += tempDelta * falloff
            world.fire[i] = 0f
        }
        world.dirty = true
    }

    private fun reclassify(world: PlanetWorld, i: Int): Biome {
        val h = world.heightMap[i]
        val t = world.temperature[i]
        val m = world.moisture[i]
        return when {
            h < world.seaLevel - 0.16f -> Biome.DEEP_OCEAN
            h < world.seaLevel - 0.05f -> Biome.OCEAN
            h < world.seaLevel -> Biome.SHALLOW
            h < world.seaLevel + 0.025f -> Biome.BEACH
            h > 0.84f -> Biome.PEAK
            h > 0.74f -> Biome.MOUNTAIN
            t < -22f -> Biome.SNOW
            t < -4f -> Biome.TUNDRA
            t > 40f -> if (m > 0.5f) Biome.SAVANNA else Biome.DESERT
            m > 0.62f -> Biome.FOREST
            m > 0.4f -> Biome.GRASS
            else -> Biome.PLAINS
        }
    }

    private fun spawn(world: PlanetWorld, cx: Float, cy: Float, r: Float, species: Species, count: Int) {
        var placed = 0
        var attempts = 0
        while (placed < count && attempts < count * 40) {
            attempts++
            val a = rng.range(0f, MathX.TAU)
            val d = rng.range(0f, r)
            val x = cx + cos(a) * d
            val y = cy + sin(a) * d
            if (!world.walkable(x.toInt(), y.toInt())) continue
            world.spawnUnit(x, y, species, -1) ?: break
            placed++
        }
        if (placed > 0 && species.civilised) world.log("${species.label} appear on the surface.")
    }

    fun damageArea(world: PlanetWorld, cx: Float, cy: Float, r: Float, dmg: Float) {
        for (u in world.units) {
            val d = MathX.dist(u.x, u.y, cx, cy)
            if (d < r) {
                u.hp -= dmg * (1f - d / r)
                u.flash = 1f
                if (u.hp <= 0f) u.alive = false
            }
        }
        forArea(world, cx, cy, r) { _, _, i, falloff ->
            if (falloff > 0.35f && world.structure[i].toInt() != STRUCT_NONE) {
                world.structure[i] = STRUCT_RUIN.toByte()
            }
        }
    }

    private fun crater(world: PlanetWorld, cx: Float, cy: Float, r: Float, floor: Biome) {
        forArea(world, cx, cy, r) { _, _, i, falloff ->
            world.heightMap[i] = MathX.clamp(world.heightMap[i] - 0.12f * falloff, 0f, 1f)
            if (falloff > 0.25f) world.biome[i] = floor.ordinal.toByte()
            world.structure[i] = if (falloff > 0.3f) STRUCT_NONE.toByte() else world.structure[i]
        }
        world.dirty = true
    }

    fun meteor(world: PlanetWorld, cx: Float, cy: Float, r: Float, fx: ParticleSystem?) {
        crater(world, cx, cy, r, Biome.CRATER)
        damageArea(world, cx, cy, r * 1.6f, 500f)
        forArea(world, cx, cy, r * 2.2f) { _, _, i, falloff ->
            if (rng.chance(falloff * 0.55f) && !Biome.of(world.biome[i].toInt()).liquid) world.fire[i] = 1f
        }
        world.shake = max(world.shake, 1f)
        fx?.explosion(cx, cy, r * 1.4f, 0xFFFFD08A.toInt(), 0xFFFF5A22.toInt(), 70)
        fx?.smokePlume(cx, cy, 24, 6f, r * 0.4f)
        world.log("A meteor slams into the surface.")
        world.dirty = true
    }

    fun nuke(world: PlanetWorld, cx: Float, cy: Float, r: Float, fx: ParticleSystem?) {
        forArea(world, cx, cy, r) { _, _, i, falloff ->
            world.radiation[i] = min(1f, world.radiation[i] + falloff * 1.4f)
            if (falloff > 0.2f) {
                world.biome[i] = Biome.ASH.ordinal.toByte()
                world.structure[i] = STRUCT_NONE.toByte()
            } else if (rng.chance(0.5f)) world.fire[i] = 1f
        }
        damageArea(world, cx, cy, r * 1.5f, 2000f)
        world.shake = 1.4f
        fx?.explosion(cx, cy, r * 2f, 0xFFFFF3C0.toInt(), 0xFFFF7A2A.toInt(), 110)
        fx?.smokePlume(cx, cy - r * 0.2f, 50, 9f, r * 0.5f)
        world.log("A nuclear device detonates. The land is poisoned.")
        world.dirty = true
    }

    fun earthquake(world: PlanetWorld, cx: Float, cy: Float, r: Float, fx: ParticleSystem?) {
        world.disasters.add(ActiveDisaster(ActiveDisaster.Kind.QUAKE, cx, cy, 6f, r, 1f))
        world.shake = 1.6f
        forArea(world, cx, cy, r) { _, _, i, falloff ->
            if (rng.chance(falloff * 0.5f)) {
                if (world.structure[i].toInt() != STRUCT_NONE) world.structure[i] = STRUCT_RUIN.toByte()
                world.heightMap[i] = MathX.clamp(world.heightMap[i] + rng.range(-0.05f, 0.05f), 0f, 1f)
            }
            if (rng.chance(falloff * 0.06f)) world.biome[i] = Biome.LAVA.ordinal.toByte()
        }
        damageArea(world, cx, cy, r * 0.8f, 120f)
        fx?.spark(cx, cy, 30, 10f, 0xFF8D6E63.toInt(), 1.2f, 0.6f)
        world.log("The ground splits open.")
        world.dirty = true
    }

    fun heal(world: PlanetWorld, cx: Float, cy: Float, r: Float) {
        forArea(world, cx, cy, r) { _, _, i, falloff ->
            world.fire[i] = 0f
            world.radiation[i] = max(0f, world.radiation[i] - falloff * 0.9f)
            val b = Biome.of(world.biome[i].toInt())
            if (b == Biome.ASH || b == Biome.CRATER || b == Biome.LAVA || b == Biome.TOXIC) {
                world.biome[i] = (if (world.temperature[i] < -6f) Biome.SNOW else Biome.GRASS).ordinal.toByte()
            }
            if (world.structure[i].toInt() == STRUCT_RUIN) world.structure[i] = STRUCT_NONE.toByte()
        }
        for (u in world.units) {
            if (MathX.dist(u.x, u.y, cx, cy) < r) u.hp = u.maxHp
        }
        world.dirty = true
    }

    fun armageddon(world: PlanetWorld, fx: ParticleSystem?) {
        repeat(26) {
            val x = rng.range(0f, world.width.toFloat())
            val y = rng.range(0f, world.height.toFloat())
            world.disasters.add(
                ActiveDisaster(ActiveDisaster.Kind.METEOR_FALL, x, y, rng.range(0.2f, 8f), rng.range(4f, 9f), 1f)
            )
        }
        repeat(4) {
            val x = rng.range(0f, world.width.toFloat())
            val y = rng.range(0f, world.height.toFloat())
            val d = ActiveDisaster(ActiveDisaster.Kind.TORNADO, x, y, 40f, 4f, 1f)
            d.vx = rng.range(-7f, 7f); d.vy = rng.range(-4f, 4f)
            world.disasters.add(d)
        }
        world.disasters.add(
            ActiveDisaster(ActiveDisaster.Kind.VOLCANO, rng.range(0f, world.width.toFloat()),
                rng.range(0f, world.height.toFloat()), 40f, 6f, 1.6f)
        )
        world.shake = 2f
        world.log("ARMAGEDDON. May something survive.")
    }

    // -------------------------------------------------------------- disasters

    fun updateDisasters(world: PlanetWorld, dt: Float, fx: ParticleSystem?) {
        world.shake = max(0f, world.shake - dt * 0.8f)
        val it = world.disasters.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.timer -= dt
            d.phase += dt
            if (d.timer <= 0f) { it.remove(); continue }
            when (d.kind) {
                ActiveDisaster.Kind.TORNADO -> tornadoStep(world, d, dt, fx)
                ActiveDisaster.Kind.VOLCANO -> volcanoStep(world, d, dt, fx)
                ActiveDisaster.Kind.TSUNAMI -> tsunamiStep(world, d, dt, fx)
                ActiveDisaster.Kind.ACID_RAIN -> acidStep(world, d, dt, fx)
                ActiveDisaster.Kind.ICE_AGE -> iceStep(world, d, dt)
                ActiveDisaster.Kind.PLAGUE -> plagueStep(world, d, dt)
                ActiveDisaster.Kind.QUAKE -> { world.shake = max(world.shake, 0.8f) }
                ActiveDisaster.Kind.METEOR_FALL -> {
                    if (d.timer <= 0.25f) {
                        meteor(world, d.x, d.y, d.radius, fx)
                        d.timer = 0f
                    }
                }
            }
        }
    }

    private fun tornadoStep(world: PlanetWorld, d: ActiveDisaster, dt: Float, fx: ParticleSystem?) {
        d.vx += rng.range(-6f, 6f) * dt
        d.vy += rng.range(-4f, 4f) * dt
        d.vx = MathX.clamp(d.vx, -9f, 9f)
        d.vy = MathX.clamp(d.vy, -6f, 6f)
        d.x = MathX.clamp(d.x + d.vx * dt, 0f, world.width - 1f)
        d.y = MathX.clamp(d.y + d.vy * dt, 0f, world.height - 1f)
        damageArea(world, d.x, d.y, d.radius, 90f * dt)
        forArea(world, d.x, d.y, d.radius) { _, _, i, falloff ->
            if (rng.chance(falloff * 0.35f * dt)) {
                world.structure[i] = if (world.structure[i].toInt() == STRUCT_NONE)
                    STRUCT_NONE.toByte() else STRUCT_RUIN.toByte()
                val b = Biome.of(world.biome[i].toInt())
                if (b == Biome.FOREST || b == Biome.JUNGLE) {
                    world.biome[i] = Biome.PLAINS.ordinal.toByte()
                    world.dirty = true
                }
            }
        }
        fx?.spark(d.x, d.y, 2, 4f, 0xFFCFD8DC.toInt(), 0.5f, 0.4f)
        // Suck creatures in.
        for (u in world.units) {
            val dist = MathX.dist(u.x, u.y, d.x, d.y)
            if (dist < d.radius * 3f) {
                val pull = (1f - dist / (d.radius * 3f)) * 6f * dt
                val ang = kotlin.math.atan2(d.y - u.y, d.x - u.x) + 1.1f
                u.x += cos(ang) * pull
                u.y += sin(ang) * pull
            }
        }
    }

    private fun volcanoStep(world: PlanetWorld, d: ActiveDisaster, dt: Float, fx: ParticleSystem?) {
        if (rng.chance(6f * dt)) {
            val a = rng.range(0f, MathX.TAU)
            val dist = rng.range(0f, d.radius * (1f + d.phase * 0.09f))
            val x = d.x + cos(a) * dist
            val y = d.y + sin(a) * dist
            if (world.inBounds(x.toInt(), y.toInt())) {
                val i = world.idx(x.toInt(), y.toInt())
                world.biome[i] = Biome.LAVA.ordinal.toByte()
                world.temperature[i] += 40f
                world.structure[i] = STRUCT_NONE.toByte()
                world.dirty = true
            }
            damageArea(world, x, y, 2f, 400f)
        }
        fx?.spark(d.x, d.y, 3, 22f, 0xFFFF7A2A.toInt(), 1.1f, 0.5f)
        fx?.smokePlume(d.x, d.y, 1, 5f, d.radius * 0.3f)
        world.shake = max(world.shake, 0.25f)
    }

    private fun tsunamiStep(world: PlanetWorld, d: ActiveDisaster, dt: Float, fx: ParticleSystem?) {
        val prevX = d.x
        d.x += d.vx * dt
        val x0 = max(0, prevX.toInt())
        val x1 = min(world.width - 1, d.x.toInt())
        if (x1 < x0) return
        for (x in x0..x1) {
            val y0 = max(0, (d.y - d.radius).toInt())
            val y1 = min(world.height - 1, (d.y + d.radius).toInt())
            for (y in y0..y1) {
                val i = world.idx(x, y)
                val b = Biome.of(world.biome[i].toInt())
                if (!b.liquid && world.heightMap[i] < world.seaLevel + 0.06f) {
                    world.biome[i] = Biome.SHALLOW.ordinal.toByte()
                    world.structure[i] = STRUCT_NONE.toByte()
                    world.fire[i] = 0f
                }
            }
            damageArea(world, x.toFloat(), d.y, d.radius, 300f * dt)
        }
        fx?.spark(d.x, d.y, 4, 10f, 0xFF7FD8FF.toInt(), 0.6f, 0.6f)
        world.dirty = true
        if (d.x > world.width) d.timer = 0f
    }

    private fun acidStep(world: PlanetWorld, d: ActiveDisaster, dt: Float, fx: ParticleSystem?) {
        forArea(world, d.x, d.y, d.radius) { x, y, i, falloff ->
            if (rng.chance(falloff * 0.8f * dt)) {
                val b = Biome.of(world.biome[i].toInt())
                if (b == Biome.FOREST || b == Biome.JUNGLE || b == Biome.GRASS) {
                    world.biome[i] = Biome.TOXIC.ordinal.toByte()
                    world.dirty = true
                }
            }
        }
        damageArea(world, d.x, d.y, d.radius, 14f * dt)
        repeat(3) {
            fx?.weather(
                d.x + rng.range(-d.radius, d.radius), d.y + rng.range(-d.radius, d.radius),
                ParticleKind.RAIN, 0f, 26f, 0.5f, 0.5f, 0xFF9CCC65.toInt()
            )
        }
    }

    private fun iceStep(world: PlanetWorld, d: ActiveDisaster, dt: Float) {
        forArea(world, d.x, d.y, d.radius) { _, _, i, falloff ->
            world.temperature[i] -= 26f * falloff * dt
            if (world.temperature[i] < -20f) {
                val b = Biome.of(world.biome[i].toInt())
                if (b.liquid && b != Biome.LAVA) world.biome[i] = Biome.ICE.ordinal.toByte()
                else if (b != Biome.ICE) world.biome[i] = Biome.SNOW.ordinal.toByte()
                world.dirty = true
            }
        }
    }

    private fun plagueStep(world: PlanetWorld, d: ActiveDisaster, dt: Float) {
        d.radius += 4f * dt
        for (u in world.units) {
            if (MathX.dist(u.x, u.y, d.x, d.y) < d.radius && rng.chance(1.2f * dt)) {
                u.hp -= 60f * dt
                u.flash = 0.6f
                if (u.hp <= 0f) u.alive = false
            }
        }
    }
}
