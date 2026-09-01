package com.nova.galaxysandbox.action

import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.core.Rng
import com.nova.galaxysandbox.fx.ParticleSystem
import com.nova.galaxysandbox.galaxy.Galaxy
import com.nova.galaxysandbox.galaxy.GalaxySim
import com.nova.galaxysandbox.galaxy.Planet
import com.nova.galaxysandbox.galaxy.PlanetState
import com.nova.galaxysandbox.galaxy.StarSystem
import com.nova.galaxysandbox.world.PlanetWorld
import com.nova.galaxysandbox.world.WorldTools
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Projectile(
    val weapon: Weapon,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val targetX: Float,
    val targetY: Float,
    val targetPlanetId: Int
) {
    var alive = true
    var age = 0f
    var size = 6f
    var spin = 0f
    var rot = 0f
    val trail = ArrayDeque<Float>()
}

class Beam(
    var x1: Float, var y1: Float,
    var x2: Float, var y2: Float,
    val color: Int,
    var width: Float
) {
    var life = 0.12f
    var alive = true
}

class Singularity(var x: Float, var y: Float, var strength: Float) {
    var life = 26f
    var radius = 26f
    var spin = 0f
    var alive = true
    var swallowed = 0
}

class Mothership(var x: Float, var y: Float, val targetPlanetId: Int) {
    var alive = true
    var state = 0        // 0 approach, 1 harvest, 2 leave
    var timer = 0f
    var beamPhase = 0f
}

/**
 * Orbital weapon platform: spawns projectiles/beams, moves them, resolves impacts
 * and pushes the consequences into the galaxy simulation.
 */
class WeaponSystem(
    private val galaxy: Galaxy,
    private val sim: GalaxySim,
    private val fx: ParticleSystem
) {
    val projectiles = ArrayList<Projectile>()
    val beams = ArrayList<Beam>()
    val singularities = ArrayList<Singularity>()
    val motherships = ArrayList<Mothership>()
    private val rng = Rng(424242L)

    val cooldowns = HashMap<Weapon, Float>()
    var screenShake = 0f
    var flashAmount = 0f
    var lastImpactPlanet: Planet? = null

    /** Optional live surface world so orbital strikes leave craters on the ground. */
    var activeWorld: PlanetWorld? = null

    fun ready(w: Weapon): Boolean = (cooldowns[w] ?: 0f) <= 0f

    fun cooldownFraction(w: Weapon): Float {
        val c = cooldowns[w] ?: 0f
        return if (w.cooldown <= 0f) 0f else MathX.clamp(c / w.cooldown, 0f, 1f)
    }

    fun fire(weapon: Weapon, wx: Float, wy: Float, fromX: Float, fromY: Float): Boolean {
        if (!ready(weapon)) return false
        cooldowns[weapon] = weapon.cooldown
        val target = galaxy.nearestPlanet(wx, wy, 260f)

        when (weapon) {
            Weapon.LASER, Weapon.ICE_BEAM -> {
                val b = Beam(fromX, fromY, wx, wy, weapon.tint, if (weapon == Weapon.LASER) 7f else 11f)
                beams.add(b)
                fx.beamImpact(wx, wy, weapon.tint)
                target?.let {
                    if (weapon == Weapon.LASER) damagePlanet(it, 0.010f, heat = 26f, wx, wy)
                    else damagePlanet(it, 0.002f, heat = -34f, wx, wy)
                }
            }
            Weapon.RAILGUN -> spawnProjectile(weapon, wx, wy, fromX, fromY, 2400f, target, 5f)
            Weapon.MISSILE -> {
                repeat(6) {
                    spawnProjectile(
                        weapon,
                        wx + rng.range(-26f, 26f), wy + rng.range(-26f, 26f),
                        fromX + rng.range(-160f, 160f), fromY + rng.range(-90f, 90f),
                        620f, target, 6f
                    )
                }
            }
            Weapon.NUKE -> spawnProjectile(weapon, wx, wy, fromX, fromY, 700f, target, 10f)
            Weapon.ASTEROID -> spawnProjectile(weapon, wx, wy, fromX, fromY, 460f, target, 26f)
            Weapon.METEOR_STORM -> {
                repeat(18) {
                    spawnProjectile(
                        weapon,
                        wx + rng.range(-220f, 220f), wy + rng.range(-220f, 220f),
                        fromX + rng.range(-700f, 700f), fromY + rng.range(-500f, -100f),
                        rng.range(520f, 900f), target, rng.range(6f, 16f)
                    )
                }
            }
            Weapon.ANNIHILATOR -> {
                val b = Beam(fromX, fromY, wx, wy, weapon.tint, 34f)
                b.life = 0.9f
                beams.add(b)
                screenShake = 1.6f
                flashAmount = 1f
                target?.let { annihilate(it) }
            }
            Weapon.BLACK_HOLE -> {
                singularities.add(Singularity(wx, wy, 1f))
                fx.explosion(wx, wy, 60f, 0xFF9C7BFF.toInt(), 0xFF2A1B4A.toInt(), 40)
                screenShake = 0.8f
            }
            Weapon.SUN_CRUSHER -> {
                val system = galaxy.nearestSystem(wx, wy)
                if (system != null && system.starAlive &&
                    MathX.dist(wx, wy, system.x, system.y) < system.starRadius * 6f
                ) {
                    sim.killStar(system)
                    screenShake = 2f
                    flashAmount = 1f
                    fx.explosion(system.x, system.y, system.starRadius * 2f, 0xFFFFF4C0.toInt(), 0xFFFF8A2A.toInt(), 120)
                } else return false
            }
            Weapon.GRAVITY_SLAM -> {
                target?.let {
                    damagePlanet(it, 0.35f, heat = 12f, wx, wy)
                    it.quakeTimer = 2.4f
                    fx.explosion(it.x, it.y, it.radius * 1.6f, 0xFF8AF0D4.toInt(), 0xFF1F6E5E.toInt(), 50)
                    activeWorld?.let { w -> if (w.planet === it) WorldTools.earthquake(w, w.width / 2f, w.height / 2f, 40f, null) }
                    screenShake = 1f
                } ?: return false
            }
            Weapon.PLAGUE -> {
                target?.let {
                    val killed = it.population * rng.range(0.55f, 0.95f)
                    it.population -= killed
                    galaxy.totalLivesLost += killed
                    sim.log("${it.name}: a bio-plague kills ${GalaxySim.formatPop(killed)}.")
                    sim.angerFaction(it.factionId, 0.3f)
                    fx.explosion(it.x, it.y, it.radius * 1.2f, 0xFFB6FF6B.toInt(), 0xFF2C5E1B.toInt(), 40)
                    activeWorld?.let { w ->
                        if (w.planet === it) WorldTools.apply(w, Tool.PLAGUE_TOOL, w.width / 2f, w.height / 2f, 3f, fx)
                    }
                } ?: return false
            }
            Weapon.EMP -> {
                target?.let {
                    it.shield = 0f
                    it.tech = max(0f, it.tech - 3.5f)
                    fx.explosion(it.x, it.y, it.radius * 2.2f, 0xFF9AD6FF.toInt(), 0xFF1B3A6E.toInt(), 30)
                    sim.log("${it.name}: EMP burst knocks technology back centuries.")
                    sim.angerFaction(it.factionId, 0.12f)
                } ?: return false
            }
            Weapon.UFO -> {
                target?.let {
                    motherships.add(Mothership(it.x - 700f, it.y - 500f, it.id))
                    sim.log("An unidentified mothership approaches ${it.name}.")
                } ?: return false
            }
            Weapon.TERRAFORM -> {
                target?.let { p ->
                    if (p.destroyed) return false
                    p.integrity = min(1f, p.integrity + 0.35f)
                    p.radiation = max(0f, p.radiation - 0.6f)
                    p.burning = max(0f, p.burning - 0.7f)
                    p.atmosphere = min(1.1f, p.atmosphere + 0.35f)
                    p.temperature = MathX.lerp(p.temperature, 18f, 0.6f)
                    if (p.integrity > 0.7f) {
                        p.state = PlanetState.INTACT
                        if (p.population <= 0.0 && p.habitabilityScore() > 0.4f) {
                            p.population = 5.0
                            p.tech = 0.5f
                            sim.log("${p.name} has been reseeded with life.")
                        }
                    }
                    fx.explosion(p.x, p.y, p.radius * 1.5f, 0xFF8AF5A0.toInt(), 0xFF1E7A4A.toInt(), 40)
                    activeWorld?.let { w ->
                        if (w.planet === p) WorldTools.heal(w, w.width / 2f, w.height / 2f, w.width.toFloat())
                    }
                } ?: return false
            }
        }
        return true
    }

    private fun spawnProjectile(
        weapon: Weapon,
        tx: Float, ty: Float,
        fromX: Float, fromY: Float,
        speed: Float,
        target: Planet?,
        size: Float
    ) {
        val dx = tx - fromX
        val dy = ty - fromY
        val d = max(1f, sqrt(dx * dx + dy * dy))
        val p = Projectile(weapon, fromX, fromY, dx / d * speed, dy / d * speed, tx, ty, target?.id ?: -1)
        p.size = size
        p.spin = rng.range(-5f, 5f)
        projectiles.add(p)
    }

    // ------------------------------------------------------------------ update

    fun update(dt: Float) {
        for (w in Weapon.values()) {
            val c = cooldowns[w] ?: 0f
            if (c > 0f) cooldowns[w] = max(0f, c - dt)
        }
        screenShake = max(0f, screenShake - dt * 1.6f)
        flashAmount = max(0f, flashAmount - dt * 2.2f)

        updateProjectiles(dt)
        updateBeams(dt)
        updateSingularities(dt)
        updateMotherships(dt)
    }

    private fun updateProjectiles(dt: Float) {
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            p.rot += p.spin * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.trail.addLast(p.x); p.trail.addLast(p.y)
            while (p.trail.size > 20) { p.trail.removeFirst(); p.trail.removeFirst() }

            // Gravity from singularities bends projectiles — looks great, costs nothing.
            for (s in singularities) {
                val dx = s.x - p.x
                val dy = s.y - p.y
                val d2 = max(400f, dx * dx + dy * dy)
                val f = 260000f * s.strength / d2
                p.vx += dx / sqrt(d2) * f * dt
                p.vy += dy / sqrt(d2) * f * dt
            }

            when (p.weapon) {
                Weapon.MISSILE, Weapon.NUKE -> {
                    fx.plasma(p.x, p.y, rng.range(-20f, 20f), rng.range(-20f, 20f), 0xFFFFB25C.toInt(), p.size * 0.6f, 0.4f)
                }
                Weapon.ASTEROID, Weapon.METEOR_STORM -> {
                    fx.plasma(p.x, p.y, rng.range(-14f, 14f), rng.range(-14f, 14f), 0xFFFF7A3C.toInt(), p.size * 0.5f, 0.5f)
                }
                else -> Unit
            }

            // Impact test against every planet in nearby systems.
            var hit: Planet? = null
            for (system in galaxy.systems) {
                if (MathX.dist2(p.x, p.y, system.x, system.y) > 4_000_000f) continue
                if (system.starAlive && MathX.dist(p.x, p.y, system.x, system.y) < system.starRadius) {
                    // Burned up in the star.
                    fx.explosion(p.x, p.y, 30f, 0xFFFFE9A0.toInt(), 0xFFFF7A2A.toInt(), 14)
                    it.remove()
                    hit = null
                    break
                }
                for (pl in system.planets) {
                    if (pl.destroyed) continue
                    if (MathX.dist(p.x, p.y, pl.x, pl.y) < pl.radius + p.size * 0.4f) { hit = pl; break }
                }
                if (hit != null) break
            }
            if (hit != null) {
                impact(p, hit)
                it.remove()
                continue
            }
            if (p.age > 14f) it.remove()
        }
    }

    private fun impact(p: Projectile, planet: Planet) {
        lastImpactPlanet = planet
        planet.lastHitAge = 0f
        val shielded = planet.shield > 0.05f
        if (shielded) {
            planet.shield = max(0f, planet.shield - 0.22f)
            fx.explosion(p.x, p.y, planet.radius * 0.7f, 0xFF7FD0FF.toInt(), 0xFF2B62A8.toInt(), 24)
            sim.angerFaction(planet.factionId, 0.05f)
            return
        }
        when (p.weapon) {
            Weapon.RAILGUN -> {
                damagePlanet(planet, 0.14f, 8f, p.x, p.y)
                fx.explosion(p.x, p.y, planet.radius * 0.5f, 0xFFCFF2FF.toInt(), 0xFF3E8CC8.toInt(), 26)
            }
            Weapon.MISSILE -> {
                damagePlanet(planet, 0.07f, 22f, p.x, p.y)
                fx.explosion(p.x, p.y, planet.radius * 0.55f, 0xFFFFD08A.toInt(), 0xFFFF5A22.toInt(), 30)
            }
            Weapon.NUKE -> {
                damagePlanet(planet, 0.22f, 55f, p.x, p.y)
                planet.radiation = min(1f, planet.radiation + 0.4f)
                fx.explosion(p.x, p.y, planet.radius * 1.3f, 0xFFFFF6C8.toInt(), 0xFFFF7A2A.toInt(), 70)
                screenShake = max(screenShake, 0.7f)
                activeWorld?.let { w -> if (w.planet === planet) WorldTools.nuke(w, rng.range(0f, w.width.toFloat()), rng.range(0f, w.height.toFloat()), 12f, null) }
            }
            Weapon.ASTEROID -> {
                damagePlanet(planet, 0.30f, 40f, p.x, p.y)
                fx.explosion(p.x, p.y, planet.radius * 1.1f, 0xFFFFC98A.toInt(), 0xFF8A4A22.toInt(), 60)
                fx.debris(p.x, p.y, 18, 90f, 0xFF9A8A72.toInt())
                screenShake = max(screenShake, 0.9f)
                activeWorld?.let { w -> if (w.planet === planet) WorldTools.meteor(w, rng.range(0f, w.width.toFloat()), rng.range(0f, w.height.toFloat()), 9f, null) }
            }
            Weapon.METEOR_STORM -> {
                damagePlanet(planet, 0.045f, 18f, p.x, p.y)
                fx.explosion(p.x, p.y, planet.radius * 0.4f, 0xFFFFB27A.toInt(), 0xFF7A3A1A.toInt(), 18)
                activeWorld?.let { w -> if (w.planet === planet) WorldTools.meteor(w, rng.range(0f, w.width.toFloat()), rng.range(0f, w.height.toFloat()), 4f, null) }
            }
            else -> damagePlanet(planet, 0.05f, 10f, p.x, p.y)
        }
    }

    /** Core damage model shared by every weapon. */
    fun damagePlanet(planet: Planet, amount: Float, heat: Float, hx: Float, hy: Float) {
        if (planet.destroyed) return
        if (planet.shield > 0.05f && amount > 0.004f) {
            planet.shield = max(0f, planet.shield - amount * 1.4f)
            return
        }
        planet.lastHitAge = 0f
        planet.integrity = max(0f, planet.integrity - amount)
        planet.temperature += heat * (0.6f + amount * 3f)
        if (heat > 0) planet.burning = min(1f, planet.burning + amount * 2.2f)
        planet.atmosphere = max(0f, planet.atmosphere - amount * 0.5f)

        val casualties = planet.population * MathX.clamp(amount * 2.4f, 0f, 1f)
        if (casualties > 0.01) {
            planet.population = max(0.0, planet.population - casualties)
            galaxy.totalLivesLost += casualties
        }
        sim.angerFaction(planet.factionId, amount * 0.6f)

        planet.state = when {
            planet.integrity < 0.25f -> PlanetState.SHATTERED
            planet.integrity < 0.5f -> PlanetState.MOLTEN
            planet.integrity < 0.8f -> PlanetState.CRACKED
            else -> PlanetState.INTACT
        }
        if (planet.integrity <= 0f) {
            sim.destroyPlanet(planet, "torn apart by orbital bombardment")
            fx.explosion(planet.x, planet.y, planet.radius * 2.6f, 0xFFFFF0C0.toInt(), 0xFFFF6A2A.toInt(), 140)
            fx.debris(planet.x, planet.y, 36, 160f, planet.type.baseColor)
            screenShake = max(screenShake, 1.4f)
            flashAmount = max(flashAmount, 0.8f)
        }
    }

    private fun annihilate(planet: Planet) {
        if (planet.destroyed) return
        planet.shield = 0f
        planet.integrity = 0f
        sim.destroyPlanet(planet, "split in half by an annihilator beam")
        fx.explosion(planet.x, planet.y, planet.radius * 3.4f, 0xFFF0D8FF.toInt(), 0xFF7B3BFF.toInt(), 180)
        fx.debris(planet.x, planet.y, 48, 220f, planet.type.baseColor)
    }

    private fun updateBeams(dt: Float) {
        val it = beams.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.life -= dt
            if (b.life <= 0f) it.remove()
        }
    }

    private fun updateSingularities(dt: Float) {
        val it = singularities.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.life -= dt
            s.spin += dt * 2.4f
            s.radius = 18f + s.strength * 22f + s.swallowed * 3.5f
            if (s.life <= 0f) {
                fx.explosion(s.x, s.y, s.radius * 3f, 0xFFD0B8FF.toInt(), 0xFF3A1B6E.toInt(), 60)
                it.remove()
                continue
            }
            // Pull in and consume nearby planets.
            val reach = 520f + s.swallowed * 40f
            for (system in galaxy.systems) {
                if (MathX.dist2(s.x, s.y, system.x, system.y) > (reach + 3000f) * (reach + 3000f)) continue
                for (p in system.planets) {
                    if (p.destroyed) continue
                    val d = MathX.dist(s.x, s.y, p.x, p.y)
                    if (d > reach) continue
                    val pull = (1f - d / reach) * 220f * dt
                    val ang = atan2(s.y - p.y, s.x - p.x)
                    p.orbitRadius = max(6f, p.orbitRadius - pull * 0.55f)
                    p.x += cos(ang) * pull
                    p.y += sin(ang) * pull
                    if (d < s.radius * 1.5f) {
                        sim.destroyPlanet(p, "swallowed by a singularity")
                        s.swallowed++
                        s.strength += 0.2f
                        s.life += 3f
                        fx.explosion(p.x, p.y, p.radius * 1.6f, 0xFFB89AFF.toInt(), 0xFF2A1050.toInt(), 40)
                    }
                }
                if (system.starAlive && MathX.dist(s.x, s.y, system.x, system.y) < s.radius * 2.2f) {
                    sim.killStar(system)
                    s.strength += 0.6f
                }
            }
            repeat(2) {
                val a = rng.range(0f, MathX.TAU)
                val d = s.radius * rng.range(2f, 7f)
                fx.plasma(
                    s.x + cos(a) * d, s.y + sin(a) * d,
                    -cos(a) * 120f, -sin(a) * 120f,
                    0xFFB08AFF.toInt(), 2.6f, 0.8f
                )
            }
        }
    }

    private fun updateMotherships(dt: Float) {
        val it = motherships.iterator()
        while (it.hasNext()) {
            val m = it.next()
            val target = galaxy.planet(m.targetPlanetId)
            if (target == null || target.destroyed) { it.remove(); continue }
            m.beamPhase += dt * 3f
            when (m.state) {
                0 -> {
                    val dx = target.x - m.x
                    val dy = (target.y - target.radius * 3.2f) - m.y
                    val d = max(1f, sqrt(dx * dx + dy * dy))
                    m.x += dx / d * 420f * dt
                    m.y += dy / d * 420f * dt
                    if (d < 12f) { m.state = 1; m.timer = 9f }
                }
                1 -> {
                    m.timer -= dt
                    m.x = target.x
                    m.y = target.y - target.radius * 3.2f
                    val harvested = target.population * 0.12 * dt
                    target.population = max(0.0, target.population - harvested)
                    galaxy.totalLivesLost += harvested
                    fx.plasma(
                        target.x + rng.range(-target.radius, target.radius),
                        target.y - rng.range(0f, target.radius * 3f),
                        0f, -140f, 0xFF32FF9A.toInt(), 2.4f, 0.5f
                    )
                    if (m.timer <= 0f) {
                        m.state = 2
                        sim.log("The mothership leaves ${target.name} half-empty.")
                        sim.angerFaction(target.factionId, 0.2f)
                    }
                }
                else -> {
                    m.y -= 520f * dt
                    m.x += 240f * dt
                    if (MathX.dist(m.x, m.y, target.x, target.y) > 4000f) it.remove()
                }
            }
        }
    }

    fun clear() {
        projectiles.clear(); beams.clear(); singularities.clear(); motherships.clear()
    }
}
