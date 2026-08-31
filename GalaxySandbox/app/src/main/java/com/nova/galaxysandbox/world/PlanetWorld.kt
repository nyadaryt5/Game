package com.nova.galaxysandbox.world

import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.core.Names
import com.nova.galaxysandbox.core.Rng
import com.nova.galaxysandbox.core.ValueNoise
import com.nova.galaxysandbox.galaxy.Planet
import com.nova.galaxysandbox.galaxy.PlanetType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val MAX_KINGDOMS = 24

class Kingdom(
    val id: Int,
    var name: String,
    val species: Species,
    val color: Int
) {
    var tiles = 0
    val ownedTiles = ArrayList<Int>()
    var population = 0
    var capitalIndex = -1
    var alive = true
    var atWarWith = HashSet<Int>()
    var aggression = 0.4f
    var wealth = 0f
    var age = 0f
}

class Creature(
    var x: Float,
    var y: Float,
    val species: Species,
    var kingdom: Int
) {
    var hp = 100f
    var maxHp = 100f
    var age = 0f
    var lifespan = 220f
    var tx = x
    var ty = y
    var cooldown = 0f
    var breedCooldown = 20f
    var state = State.WANDER
    var targetUnit: Creature? = null
    var alive = true
    var flash = 0f
    var facing = 1f
    var bob = 0f

    enum class State { WANDER, MIGRATE, FIGHT, FLEE, BUILD }
}

const val STRUCT_NONE = 0
const val STRUCT_HUT = 1
const val STRUCT_HOUSE = 2
const val STRUCT_FARM = 3
const val STRUCT_TOWER = 4
const val STRUCT_CITY = 5
const val STRUCT_ROAD = 6
const val STRUCT_RUIN = 7
const val STRUCT_WALL = 8

/**
 * A single planet's surface: a tile grid with terrain, life, kingdoms and disasters.
 * Generated deterministically from the planet's seed so it is stable between visits.
 */
class PlanetWorld(val planet: Planet, val width: Int = 200, val height: Int = 120) {

    val size = width * height
    val biome = ByteArray(size)
    val heightMap = FloatArray(size)
    val temperature = FloatArray(size)
    val moisture = FloatArray(size)
    val fire = FloatArray(size)
    val radiation = FloatArray(size)
    val claim = ByteArray(size)        // kingdom id + 1, 0 = unclaimed
    val structure = ByteArray(size)
    val vegetation = FloatArray(size)

    val kingdoms = ArrayList<Kingdom>()
    val units = ArrayList<Creature>()
    val disasters = ArrayList<ActiveDisaster>()
    var shake = 0f
    var globalRainTimer = 0f
    val rng = Rng(planet.seed xor 0x1234ABCDL)

    var seaLevel = 0.46f
    var globalTemp = 0f
    var dirty = true                   // tells the renderer to rebuild the tile bitmap
    var tick = 0L
    var civilizationScore = 0f
    var eventLog = ArrayDeque<String>()

    fun idx(x: Int, y: Int): Int = y * width + x
    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height
    fun biomeAt(x: Int, y: Int): Biome = Biome.of(biome[idx(x, y)].toInt())
    fun setBiome(x: Int, y: Int, b: Biome) { biome[idx(x, y)] = b.ordinal.toByte(); dirty = true }

    fun log(m: String) {
        eventLog.addLast(m)
        while (eventLog.size > 40) eventLog.removeFirst()
    }

    init { generate() }

    // ------------------------------------------------------------------ terrain

    private fun generate() {
        val noise = ValueNoise(planet.seed)
        val warp = ValueNoise(planet.seed * 31 + 7)
        val type = planet.type
        globalTemp = planet.temperature

        seaLevel = when (type) {
            PlanetType.OCEAN -> 0.62f
            PlanetType.TERRAN -> 0.48f
            PlanetType.JUNGLE -> 0.45f
            PlanetType.SAVANNA -> 0.40f
            PlanetType.DESERT -> 0.30f
            PlanetType.ICE -> 0.44f
            PlanetType.TUNDRA -> 0.42f
            PlanetType.LAVA -> 0.36f
            PlanetType.TOXIC -> 0.38f
            PlanetType.BARREN -> 0.18f
            PlanetType.GAS_GIANT, PlanetType.ICE_GIANT -> 0.55f
        }

        val scale = 5.5f
        for (y in 0 until height) {
            val v = y / (height - 1f)
            // Latitude band: -1 (south pole) .. 1 (north pole)
            val lat = abs(v * 2f - 1f)
            for (x in 0 until width) {
                val u = x / (width - 1f)
                val i = idx(x, y)

                // Domain warp for less grid-like continents.
                val wx = warp.fbm(u * 3f, v * 3f, 3) - 0.5f
                val wy = warp.fbm(u * 3f + 11f, v * 3f + 7f, 3) - 0.5f
                val nx = u * scale + wx * 0.9f
                val ny = v * scale + wy * 0.9f

                var h = noise.fbm(nx, ny, 6)
                // Ridged mountains layered on top.
                h = h * 0.78f + noise.ridge(nx * 1.7f + 3f, ny * 1.7f, 4) * 0.32f
                // Pull the poles / edges down a little so continents feel bounded.
                h -= MathX.smoothstep(MathX.invLerp(0.78f, 1f, lat)) * 0.10f
                h = MathX.clamp(h, 0f, 1f)

                val moist = noise.fbm(nx * 1.4f + 21f, ny * 1.4f + 13f, 4)
                var t = globalTemp - lat * 42f + (0.5f - h) * 30f + (moist - 0.5f) * 6f
                if (type == PlanetType.GAS_GIANT || type == PlanetType.ICE_GIANT) {
                    // Banded cloud layers instead of terrain.
                    h = 0.5f + kotlin.math.sin(v * 26f + noise.fbm(nx, ny, 3) * 3f) * 0.12f
                    t = globalTemp
                }

                heightMap[i] = h
                moisture[i] = moist
                temperature[i] = t
                vegetation[i] = 0f
                biome[i] = classify(h, t, moist, type).ordinal.toByte()
            }
        }

        // Rivers on habitable worlds.
        if (!type.gas && type != PlanetType.LAVA && type != PlanetType.BARREN) {
            repeat(10 + rng.nextInt(10)) { carveRiver() }
        }
        smoothCoasts()
        dirty = true
    }

    private fun classify(h: Float, t: Float, moist: Float, type: PlanetType): Biome {
        if (type == PlanetType.GAS_GIANT || type == PlanetType.ICE_GIANT) {
            return when {
                h < 0.44f -> if (type == PlanetType.ICE_GIANT) Biome.ICE else Biome.TOXIC
                h < 0.52f -> if (type == PlanetType.ICE_GIANT) Biome.SNOW else Biome.DESERT
                else -> if (type == PlanetType.ICE_GIANT) Biome.TUNDRA else Biome.BADLANDS
            }
        }
        if (h < seaLevel - 0.16f) return if (type == PlanetType.LAVA) Biome.LAVA else
            if (t < -8f) Biome.ICE else Biome.DEEP_OCEAN
        if (h < seaLevel - 0.05f) return if (type == PlanetType.LAVA) Biome.LAVA else
            if (t < -8f) Biome.ICE else Biome.OCEAN
        if (h < seaLevel) return when {
            type == PlanetType.LAVA -> Biome.LAVA
            type == PlanetType.TOXIC -> Biome.TOXIC
            t < -8f -> Biome.ICE
            else -> Biome.SHALLOW
        }
        if (h < seaLevel + 0.025f && t > -4f) return Biome.BEACH
        if (h > 0.84f) return Biome.PEAK
        if (h > 0.74f) return Biome.MOUNTAIN

        if (type == PlanetType.LAVA) return if (h > 0.6f) Biome.BADLANDS else Biome.ASH
        if (type == PlanetType.BARREN) return if (h > 0.6f) Biome.BADLANDS else Biome.CRATER
        if (type == PlanetType.TOXIC) return if (moist > 0.55f) Biome.TOXIC else Biome.BADLANDS

        return when {
            t < -22f -> Biome.SNOW
            t < -4f -> if (moist > 0.55f) Biome.SNOW else Biome.TUNDRA
            t < 6f -> if (moist > 0.5f) Biome.FOREST else Biome.TUNDRA
            t < 20f -> when {
                moist > 0.64f -> Biome.FOREST
                moist > 0.42f -> Biome.GRASS
                else -> Biome.PLAINS
            }
            t < 32f -> when {
                moist > 0.68f -> Biome.JUNGLE
                moist > 0.46f -> Biome.GRASS
                moist > 0.30f -> Biome.SAVANNA
                else -> Biome.DESERT
            }
            else -> if (moist > 0.62f) Biome.JUNGLE else if (moist > 0.34f) Biome.SAVANNA else Biome.DESERT
        }
    }

    private fun carveRiver() {
        var x = rng.nextInt(width)
        var y = rng.nextInt(height)
        var best = -1f
        // Start from a reasonably high point.
        repeat(24) {
            val cx = rng.nextInt(width)
            val cy = rng.nextInt(height)
            val h = heightMap[idx(cx, cy)]
            if (h > best) { best = h; x = cx; y = cy }
        }
        if (best < seaLevel + 0.12f) return
        var steps = 0
        while (steps < 400) {
            steps++
            val i = idx(x, y)
            val b = Biome.of(biome[i].toInt())
            if (b.liquid) break
            biome[i] = Biome.SHALLOW.ordinal.toByte()
            heightMap[i] = min(heightMap[i], seaLevel - 0.01f)
            // Flow downhill (with a bit of noise so rivers meander).
            var bestH = Float.MAX_VALUE
            var nx2 = x
            var ny2 = y
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val cx = x + dx
                val cy = y + dy
                if (!inBounds(cx, cy)) continue
                val hh = heightMap[idx(cx, cy)] + rng.range(-0.02f, 0.02f)
                if (hh < bestH) { bestH = hh; nx2 = cx; ny2 = cy }
            }
            if (nx2 == x && ny2 == y) break
            x = nx2; y = ny2
        }
    }

    private fun smoothCoasts() {
        val copy = biome.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = idx(x, y)
                val b = Biome.of(copy[i].toInt())
                if (!b.liquid) continue
                var land = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if (!Biome.of(copy[idx(x + dx, y + dy)].toInt()).liquid) land++
                }
                if (land >= 7) biome[i] = Biome.BEACH.ordinal.toByte()
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    fun walkable(x: Int, y: Int): Boolean {
        if (!inBounds(x, y)) return false
        val i = idx(x, y)
        return Biome.of(biome[i].toInt()).walkable && fire[i] < 0.4f
    }

    fun fertilityAt(x: Int, y: Int): Float {
        if (!inBounds(x, y)) return 0f
        val i = idx(x, y)
        val b = Biome.of(biome[i].toInt())
        return b.fertility * (1f - MathX.clamp(radiation[i], 0f, 1f)) * (1f - MathX.clamp(fire[i], 0f, 1f))
    }

    fun kingdomOf(x: Int, y: Int): Kingdom? {
        if (!inBounds(x, y)) return null
        val c = claim[idx(x, y)].toInt()
        if (c <= 0) return null
        return kingdoms.getOrNull(c - 1)
    }

    fun createKingdom(species: Species): Kingdom? {
        if (kingdoms.size >= MAX_KINGDOMS) return null
        val color = KINGDOM_COLORS[kingdoms.size % KINGDOM_COLORS.size]
        val k = Kingdom(kingdoms.size, Names.kingdom(rng), species, color)
        k.aggression = rng.range(0.15f, 0.85f)
        kingdoms.add(k)
        return k
    }

    fun spawnUnit(x: Float, y: Float, species: Species, kingdom: Int = -1): Creature? {
        if (units.size >= MAX_UNITS) return null
        val u = Creature(x, y, species, kingdom)
        u.maxHp = 60f + species.strength * 55f
        u.hp = u.maxHp
        u.lifespan = if (species.civilised) rng.range(180f, 340f) else rng.range(120f, 260f)
        u.age = rng.range(0f, 20f)
        u.tx = x; u.ty = y
        units.add(u)
        return u
    }

    fun populationOfSpecies(s: Species): Int = units.count { it.alive && it.species == s }

    fun totalCivPopulation(): Int = units.count { it.alive && it.species.civilised }

    /** Push the surface state back into the galaxy-level planet stats. */
    fun syncToPlanet() {
        var pop = 0
        var struct = 0
        for (u in units) if (u.alive && u.species.civilised) pop++
        for (s in structure) if (s.toInt() != STRUCT_NONE && s.toInt() != STRUCT_RUIN) struct++
        val estimate = pop * 4.0 + struct * 1.6
        planet.population = max(planet.population * 0.25, estimate)
        planet.civilised = pop > 0
        civilizationScore = MathX.clamp(struct / 240f, 0f, 1f)
        planet.tech = max(planet.tech, civilizationScore * 6f)
        var burned = 0
        for (i in 0 until size) if (fire[i] > 0.1f) burned++
        planet.burning = MathX.clamp(burned / (size * 0.25f), 0f, 1f)
        planet.worldDirty = true
    }

    companion object {
        const val MAX_UNITS = 900
        val KINGDOM_COLORS = intArrayOf(
            0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt(),
            0xFF8E24AA.toInt(), 0xFF00ACC1.toInt(), 0xFFF4511E.toInt(), 0xFF6D4C41.toInt(),
            0xFF3949AB.toInt(), 0xFF7CB342.toInt(), 0xFFD81B60.toInt(), 0xFF00897B.toInt()
        )
    }
}
