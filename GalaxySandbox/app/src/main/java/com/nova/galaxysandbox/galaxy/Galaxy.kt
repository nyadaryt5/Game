package com.nova.galaxysandbox.galaxy

import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.core.Names
import com.nova.galaxysandbox.core.Rng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class StarType(val label: String, val core: Int, val glow: Int, val radiusMul: Float) {
    RED_DWARF("Red Dwarf", 0xFFFF8A5C.toInt(), 0xFFFF3B1F.toInt(), 0.75f),
    YELLOW("G-Type Star", 0xFFFFF3B0.toInt(), 0xFFFFB020.toInt(), 1.0f),
    BLUE_GIANT("Blue Giant", 0xFFDDF3FF.toInt(), 0xFF3FA9FF.toInt(), 1.5f),
    WHITE_DWARF("White Dwarf", 0xFFFFFFFF.toInt(), 0xFFBFD8FF.toInt(), 0.5f),
    NEUTRON("Neutron Star", 0xFFEAF6FF.toInt(), 0xFF7B5CFF.toInt(), 0.4f),
    BLACK_HOLE("Black Hole", 0xFF000000.toInt(), 0xFFFF9A3C.toInt(), 0.9f)
}

enum class PlanetType(
    val label: String,
    val baseColor: Int,
    val altColor: Int,
    val habitability: Float,
    val gas: Boolean = false
) {
    TERRAN("Terran", 0xFF2E7BC4.toInt(), 0xFF4FBF6A.toInt(), 1.0f),
    OCEAN("Ocean", 0xFF1B5FB0.toInt(), 0xFF33A8D6.toInt(), 0.75f),
    JUNGLE("Jungle", 0xFF1F7A4A.toInt(), 0xFF8FD44A.toInt(), 0.85f),
    DESERT("Desert", 0xFFC98B3E.toInt(), 0xFFE9C874.toInt(), 0.45f),
    SAVANNA("Savanna", 0xFFB98A45.toInt(), 0xFF89B85A.toInt(), 0.6f),
    ICE("Ice World", 0xFFBFE4F5.toInt(), 0xFF7FA9D0.toInt(), 0.3f),
    TUNDRA("Tundra", 0xFF8FA8A6.toInt(), 0xFFD8E8E6.toInt(), 0.4f),
    LAVA("Lava World", 0xFF6B1B12.toInt(), 0xFFFF6B22.toInt(), 0.05f),
    BARREN("Barren Rock", 0xFF6E6A66.toInt(), 0xFF9A948C.toInt(), 0.1f),
    TOXIC("Toxic", 0xFF6E7A2A.toInt(), 0xFFB9D046.toInt(), 0.15f),
    GAS_GIANT("Gas Giant", 0xFFC98F5C.toInt(), 0xFFF0D3A8.toInt(), 0f, gas = true),
    ICE_GIANT("Ice Giant", 0xFF4E86C4.toInt(), 0xFFAFD8F0.toInt(), 0f, gas = true)
}

enum class PlanetState { INTACT, CRACKED, MOLTEN, SHATTERED, DESTROYED }

class Moon(
    val radius: Float,
    var orbitRadius: Float,
    var orbitAngle: Float,
    val orbitSpeed: Float,
    val color: Int
) {
    var x = 0f
    var y = 0f
    var alive = true
}

class Planet(
    val id: Int,
    val systemId: Int,
    var name: String,
    val type: PlanetType,
    val radius: Float,
    var orbitRadius: Float,
    var orbitAngle: Float,
    val orbitSpeed: Float,
    val seed: Long,
    val tilt: Float,
    val hasRings: Boolean,
    val ringColor: Int,
    val moons: MutableList<Moon>
) {
    // World-space position, recomputed every tick.
    var x = 0f
    var y = 0f

    var integrity = 1f            // 1 = pristine, 0 = destroyed
    var state = PlanetState.INTACT
    var population = 0.0          // in millions
    var maxPopulation = 0.0
    var tech = 0f                 // 0..10 tech level
    var factionId = -1
    var shield = 0f               // 0..1 planetary shield strength
    var shieldMax = 0f
    var temperature = 15f         // celsius-ish, drives biome shifts
    var atmosphere = 1f
    var radiation = 0f
    var burning = 0f              // molten glow amount
    var spin = 0f
    var visited = false           // has the player zoomed into it
    var civilised = false
    var quakeTimer = 0f
    var lastHitAge = 999f
    var worldDirty = false        // surface world was edited, re-render icon

    val destroyed: Boolean get() = state == PlanetState.DESTROYED

    fun habitabilityScore(): Float {
        var h = type.habitability
        h *= MathX.clamp(1f - radiation, 0f, 1f)
        h *= MathX.clamp(atmosphere, 0f, 1.2f)
        val tempPenalty = MathX.clamp(1f - kotlin.math.abs(temperature - 18f) / 90f, 0f, 1f)
        h *= tempPenalty
        return MathX.clamp(h * integrity, 0f, 1f)
    }
}

class StarSystem(
    val id: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val starType: StarType,
    val starRadius: Float,
    val planets: MutableList<Planet>
) {
    var starAlive = true
    var starPulse = 0f
    var novaTimer = -1f          // > 0 while the star is going supernova
    var collapsed = false        // turned into a black hole
    var discovered = false
}

class Faction(
    val id: Int,
    val name: String,
    val color: Int,
    var aggression: Float,
    var techFocus: Float
) {
    var planets = 0
    var population = 0.0
    var hostileToPlayer = false
    var anger = 0f
    var fleetCooldown = 0f
    var alive = true
}

class Fleet(
    var x: Float,
    var y: Float,
    val targetPlanetId: Int,
    val factionId: Int,
    val kind: Kind,
    var speed: Float
) {
    enum class Kind { COLONY, WAR, DEFENSE, EVAC }

    var alive = true
    var age = 0f
    val trail = ArrayDeque<Float>()   // flattened x,y pairs
}

class DebrisField(val x: Float, val y: Float, val radius: Float, val color: Int) {
    var age = 0f
    val chunks = ArrayList<FloatArray>() // x, y, vx, vy, size, rot, spin
}

/**
 * The whole galaxy: systems laid out on spiral arms, factions, fleets, debris.
 * Pure data + simulation, no Android dependencies, so it stays testable.
 */
class Galaxy(val seed: Long, val systemCount: Int = 48) {
    val rng = Rng(seed)
    val systems = ArrayList<StarSystem>()
    val factions = ArrayList<Faction>()
    val fleets = ArrayList<Fleet>()
    val debris = ArrayList<DebrisField>()
    val planetsById = HashMap<Int, Planet>()

    var radius = 5200f
    var time = 0.0
    var totalPlanetsDestroyed = 0
    var totalStarsDestroyed = 0
    var totalLivesLost = 0.0

    init { generate() }

    private fun generate() {
        var planetId = 0
        val arms = 4
        for (i in 0 until systemCount) {
            val arm = i % arms
            val t = (i / arms.toFloat()) / (systemCount / arms.toFloat())
            val angle = arm * (MathX.TAU / arms) + t * 2.4f + rng.range(-0.18f, 0.18f)
            val dist = 620f + t.pow(0.85f) * radius * 0.9f + rng.range(-260f, 260f)
            val sx = cos(angle) * dist
            val sy = sin(angle) * dist * 0.86f

            val starType = when {
                rng.chance(0.05f) -> StarType.BLACK_HOLE
                rng.chance(0.08f) -> StarType.NEUTRON
                rng.chance(0.12f) -> StarType.BLUE_GIANT
                rng.chance(0.16f) -> StarType.WHITE_DWARF
                rng.chance(0.38f) -> StarType.RED_DWARF
                else -> StarType.YELLOW
            }
            val starRadius = rng.range(46f, 74f) * starType.radiusMul
            val system = StarSystem(i, Names.star(rng), sx, sy, starType, starRadius, ArrayList())

            val planetCount = if (starType == StarType.BLACK_HOLE) rng.range(0, 3) else rng.range(2, 8)
            var orbit = starRadius + rng.range(120f, 200f)
            for (p in 0 until planetCount) {
                val far = p.toFloat() / kotlin.math.max(1, planetCount - 1)
                val type = pickPlanetType(far, starType)
                val pr = if (type.gas) rng.range(30f, 52f) else rng.range(14f, 30f)
                val moons = ArrayList<Moon>()
                val moonCount = if (type.gas) rng.range(0, 5) else rng.range(0, 3)
                repeat(moonCount) {
                    moons.add(
                        Moon(
                            radius = rng.range(3f, 7f),
                            orbitRadius = pr + rng.range(16f, 42f),
                            orbitAngle = rng.range(0f, MathX.TAU),
                            orbitSpeed = rng.range(0.6f, 1.8f) * (if (rng.chance(0.2f)) -1f else 1f),
                            color = 0xFF9A968E.toInt()
                        )
                    )
                }
                val planet = Planet(
                    id = planetId,
                    systemId = i,
                    name = Names.planet(rng),
                    type = type,
                    radius = pr,
                    orbitRadius = orbit,
                    orbitAngle = rng.range(0f, MathX.TAU),
                    orbitSpeed = (0.16f / sqrt(orbit / 260f)) * rng.range(0.7f, 1.3f),
                    seed = rng.nextLong(),
                    tilt = rng.range(-0.5f, 0.5f),
                    hasRings = type.gas && rng.chance(0.55f) || rng.chance(0.08f),
                    ringColor = 0xFFD8C7A8.toInt(),
                    moons = moons
                )
                planet.temperature = temperatureFor(type)
                planet.atmosphere = if (type.gas) 2f else rng.range(0.4f, 1.15f)
                planet.spin = rng.range(0.1f, 0.5f)
                system.planets.add(planet)
                planetsById[planetId] = planet
                planetId++
                orbit += rng.range(120f, 250f) + pr * 2f
            }
            systems.add(system)
        }

        // Factions seeded on the most habitable worlds.
        val factionCount = 7
        val candidates = planetsById.values
            .filter { it.habitabilityScore() > 0.4f }
            .sortedByDescending { it.habitabilityScore() }
        for (f in 0 until factionCount) {
            val faction = Faction(
                id = f,
                name = Names.faction(rng),
                color = FACTION_COLORS[f % FACTION_COLORS.size],
                aggression = rng.range(0.15f, 0.9f),
                techFocus = rng.range(0.2f, 1f)
            )
            factions.add(faction)
            val home = candidates.getOrNull(f * 2)
            if (home != null && home.factionId < 0) {
                home.factionId = f
                home.population = rng.range(900f, 4200f).toDouble()
                home.tech = rng.range(2.5f, 5.5f)
                home.civilised = true
                home.shieldMax = 0.2f
                home.shield = 0.2f
                systems[home.systemId].discovered = true
            }
        }
        recomputeFactionStats()
        updateOrbits(0f)
    }

    private fun pickPlanetType(far: Float, starType: StarType): PlanetType {
        val hot = starType == StarType.BLUE_GIANT
        val cold = starType == StarType.RED_DWARF || starType == StarType.WHITE_DWARF ||
            starType == StarType.NEUTRON || starType == StarType.BLACK_HOLE
        val zone = MathX.clamp(far + (if (hot) -0.2f else 0f) + (if (cold) 0.22f else 0f), 0f, 1f)
        return when {
            zone < 0.16f -> if (rng.chance(0.6f)) PlanetType.LAVA else PlanetType.BARREN
            zone < 0.34f -> if (rng.chance(0.45f)) PlanetType.DESERT else
                if (rng.chance(0.4f)) PlanetType.SAVANNA else PlanetType.BARREN
            zone < 0.56f -> when (rng.nextInt(5)) {
                0 -> PlanetType.TERRAN
                1 -> PlanetType.OCEAN
                2 -> PlanetType.JUNGLE
                3 -> PlanetType.TERRAN
                else -> PlanetType.TOXIC
            }
            zone < 0.74f -> if (rng.chance(0.5f)) PlanetType.TUNDRA else
                if (rng.chance(0.5f)) PlanetType.GAS_GIANT else PlanetType.TERRAN
            else -> if (rng.chance(0.45f)) PlanetType.ICE else
                if (rng.chance(0.6f)) PlanetType.ICE_GIANT else PlanetType.GAS_GIANT
        }
    }

    private fun temperatureFor(type: PlanetType): Float = when (type) {
        PlanetType.LAVA -> 780f
        PlanetType.DESERT -> 48f
        PlanetType.SAVANNA -> 32f
        PlanetType.JUNGLE -> 29f
        PlanetType.TERRAN -> 16f
        PlanetType.OCEAN -> 14f
        PlanetType.TOXIC -> 62f
        PlanetType.TUNDRA -> -12f
        PlanetType.ICE -> -58f
        PlanetType.BARREN -> -20f
        PlanetType.GAS_GIANT -> -110f
        PlanetType.ICE_GIANT -> -180f
    }

    fun system(id: Int): StarSystem = systems[id]

    fun planet(id: Int): Planet? = planetsById[id]

    fun updateOrbits(dt: Float) {
        for (s in systems) {
            for (p in s.planets) {
                if (p.destroyed) continue
                p.orbitAngle += p.orbitSpeed * dt * 0.12f
                if (p.orbitAngle > MathX.TAU) p.orbitAngle -= MathX.TAU
                p.x = s.x + cos(p.orbitAngle) * p.orbitRadius
                p.y = s.y + sin(p.orbitAngle) * p.orbitRadius * 0.9f
                for (m in p.moons) {
                    if (!m.alive) continue
                    m.orbitAngle += m.orbitSpeed * dt * 0.6f
                    m.x = p.x + cos(m.orbitAngle) * m.orbitRadius
                    m.y = p.y + sin(m.orbitAngle) * m.orbitRadius * 0.75f
                }
            }
        }
    }

    fun recomputeFactionStats() {
        for (f in factions) { f.planets = 0; f.population = 0.0 }
        for (p in planetsById.values) {
            val f = factions.getOrNull(p.factionId) ?: continue
            if (p.destroyed) continue
            f.planets++
            f.population += p.population
        }
        for (f in factions) if (f.planets == 0) f.alive = false
    }

    fun livingPopulation(): Double {
        var sum = 0.0
        for (p in planetsById.values) if (!p.destroyed) sum += p.population
        return sum
    }

    fun nearestPlanet(wx: Float, wy: Float, maxDist: Float): Planet? {
        var best: Planet? = null
        var bestD = maxDist * maxDist
        for (s in systems) {
            if (MathX.dist2(wx, wy, s.x, s.y) > (3000f + maxDist) * (3000f + maxDist)) continue
            for (p in s.planets) {
                if (p.destroyed) continue
                val d = MathX.dist2(wx, wy, p.x, p.y)
                if (d < bestD) { bestD = d; best = p }
            }
        }
        return best
    }

    fun nearestSystem(wx: Float, wy: Float): StarSystem? =
        systems.minByOrNull { MathX.dist2(wx, wy, it.x, it.y) }

    companion object {
        val FACTION_COLORS = intArrayOf(
            0xFF4FC3F7.toInt(), 0xFFFF7043.toInt(), 0xFF9CCC65.toInt(), 0xFFBA68C8.toInt(),
            0xFFFFD54F.toInt(), 0xFF4DB6AC.toInt(), 0xFFF06292.toInt(), 0xFF7986CB.toInt()
        )
    }
}

/** Deterministic angle helper shared by renderers. */
fun angleOf(dx: Float, dy: Float): Float {
    var a = kotlin.math.atan2(dy, dx)
    if (a < 0) a += (PI * 2).toFloat()
    return a
}
