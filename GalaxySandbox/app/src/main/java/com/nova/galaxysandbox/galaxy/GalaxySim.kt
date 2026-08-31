package com.nova.galaxysandbox.galaxy

import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.core.Rng
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Living-galaxy simulation: civilizations grow, terraform, colonize other worlds,
 * fight each other, and eventually notice the player smashing their planets.
 */
class GalaxySim(private val galaxy: Galaxy) {

    private val rng = Rng(galaxy.seed xor 0x5DEECE66DL)
    private var slowAccum = 0f
    var eventLog = ArrayDeque<String>()
    var lastEventId = 0

    var speed = 1f
    var lifeEnabled = true
    var warEnabled = true
    var retaliationEnabled = true

    fun log(msg: String) {
        lastEventId++
        eventLog.addLast(msg)
        while (eventLog.size > 60) eventLog.removeFirst()
    }

    fun update(dt: Float) {
        val d = dt * speed
        galaxy.time += d.toDouble()
        galaxy.updateOrbits(d)
        updateFleets(d)
        updateStars(d)
        updateDebris(d)

        slowAccum += d
        // Civilization tick runs at 2 Hz to keep the frame budget for rendering.
        while (slowAccum >= 0.5f) {
            slowAccum -= 0.5f
            civTick(0.5f * speed)
        }
    }

    private fun civTick(dt: Float) {
        if (!lifeEnabled) return
        for (system in galaxy.systems) {
            for (p in system.planets) {
                if (p.destroyed) continue
                p.lastHitAge += dt
                if (p.burning > 0f) {
                    p.burning = max(0f, p.burning - dt * 0.02f)
                    p.temperature = MathX.approach(p.temperature, 20f, dt * 4f)
                }
                if (p.radiation > 0f) p.radiation = max(0f, p.radiation - dt * 0.004f)
                if (p.quakeTimer > 0f) p.quakeTimer -= dt

                val hab = p.habitabilityScore()
                p.maxPopulation = (hab * hab * 12000.0 * (p.radius / 22.0)).coerceAtLeast(0.0)

                if (p.population > 0.0) {
                    val f = galaxy.factions.getOrNull(p.factionId)
                    val growth = 0.035 * dt * (1.0 - p.population / max(1.0, p.maxPopulation))
                    p.population = max(0.0, p.population * (1.0 + growth))
                    if (p.population < 0.4) {
                        // The last colonists die out.
                        if (p.civilised) log("${p.name}: the last colony has gone silent.")
                        p.population = 0.0
                        p.civilised = false
                        p.factionId = -1
                    } else {
                        p.civilised = true
                        val techRate = 0.0045f * dt * (1f + (f?.techFocus ?: 0.5f))
                        p.tech = min(10f, p.tech + techRate * (p.population / 2000.0).toFloat().coerceAtMost(2f))
                        p.shieldMax = MathX.clamp((p.tech - 3f) / 9f, 0f, 0.95f)
                        p.shield = MathX.approach(p.shield, p.shieldMax, dt * 0.02f)
                        // High tech civs terraform: nudge the world toward comfort.
                        if (p.tech > 6f && !p.type.gas) {
                            p.temperature = MathX.approach(p.temperature, 18f, dt * 0.6f)
                            p.atmosphere = MathX.approach(p.atmosphere, 1f, dt * 0.01f)
                        }
                    }
                } else if (hab > 0.55f && rng.chance(0.0006f * dt)) {
                    // Abiogenesis: brand new life appears on a good world.
                    p.population = rng.range(0.6f, 3f).toDouble()
                    p.tech = rng.range(0f, 0.4f)
                    p.factionId = -1
                    log("Primitive life stirs on ${p.name}.")
                }
            }
        }

        galaxy.recomputeFactionStats()
        expansionTick(dt)
        if (warEnabled) warTick(dt)
        if (retaliationEnabled) retaliationTick(dt)
    }

    private fun expansionTick(dt: Float) {
        for (f in galaxy.factions) {
            if (!f.alive) continue
            f.fleetCooldown -= dt
            if (f.fleetCooldown > 0f) continue
            val source = galaxy.planetsById.values.firstOrNull {
                it.factionId == f.id && !it.destroyed && it.population > 500 && it.tech > 3.5f
            } ?: continue
            val target = galaxy.planetsById.values
                .filter { it.factionId < 0 && !it.destroyed && it.habitabilityScore() > 0.32f }
                .minByOrNull { MathX.dist2(source.x, source.y, it.x, it.y) } ?: continue
            val dist = MathX.dist(source.x, source.y, target.x, target.y)
            if (dist > 4200f) continue
            f.fleetCooldown = rng.range(12f, 32f)
            galaxy.fleets.add(
                Fleet(source.x, source.y, target.id, f.id, Fleet.Kind.COLONY, rng.range(190f, 300f))
            )
        }
    }

    private fun warTick(dt: Float) {
        for (f in galaxy.factions) {
            if (!f.alive || f.aggression < 0.35f) continue
            if (!rng.chance(0.02f * f.aggression * dt)) continue
            val source = galaxy.planetsById.values.firstOrNull {
                it.factionId == f.id && !it.destroyed && it.tech > 4.5f && it.population > 800
            } ?: continue
            val enemy = galaxy.planetsById.values
                .filter { it.factionId >= 0 && it.factionId != f.id && !it.destroyed }
                .minByOrNull { MathX.dist2(source.x, source.y, it.x, it.y) } ?: continue
            if (MathX.dist(source.x, source.y, enemy.x, enemy.y) > 5000f) continue
            galaxy.fleets.add(
                Fleet(source.x, source.y, enemy.id, f.id, Fleet.Kind.WAR, rng.range(240f, 380f))
            )
            if (rng.chance(0.35f)) log("${f.name} launches an assault on ${enemy.name}.")
        }
    }

    /** Advanced civilizations get angry when the player wrecks their neighbours. */
    private fun retaliationTick(dt: Float) {
        for (f in galaxy.factions) {
            if (!f.alive) continue
            if (f.anger > 0f) f.anger = max(0f, f.anger - dt * 0.01f)
            if (f.anger > 0.6f && !f.hostileToPlayer) {
                f.hostileToPlayer = true
                log("${f.name} has declared war on the unknown aggressor.")
            }
        }
    }

    fun angerFaction(factionId: Int, amount: Float) {
        val f = galaxy.factions.getOrNull(factionId) ?: return
        f.anger = MathX.clamp(f.anger + amount, 0f, 2f)
    }

    private fun updateFleets(dt: Float) {
        val it = galaxy.fleets.iterator()
        while (it.hasNext()) {
            val fleet = it.next()
            fleet.age += dt
            val target = galaxy.planet(fleet.targetPlanetId)
            if (target == null || target.destroyed || fleet.age > 240f) { it.remove(); continue }
            val dx = target.x - fleet.x
            val dy = target.y - fleet.y
            val d = sqrt(dx * dx + dy * dy)
            if (d < target.radius + 8f) {
                arrive(fleet, target)
                it.remove()
                continue
            }
            val step = fleet.speed * dt
            fleet.x += dx / d * step
            fleet.y += dy / d * step
            fleet.trail.addLast(fleet.x)
            fleet.trail.addLast(fleet.y)
            while (fleet.trail.size > 24) { fleet.trail.removeFirst(); fleet.trail.removeFirst() }
        }
    }

    private fun arrive(fleet: Fleet, target: Planet) {
        val faction = galaxy.factions.getOrNull(fleet.factionId) ?: return
        when (fleet.kind) {
            Fleet.Kind.COLONY -> {
                if (target.factionId < 0 && !target.destroyed) {
                    target.factionId = faction.id
                    target.population = max(target.population, 12.0)
                    target.tech = max(target.tech, 3f)
                    target.civilised = true
                    galaxy.systems[target.systemId].discovered = true
                    log("${faction.name} colonises ${target.name}.")
                }
            }
            Fleet.Kind.WAR -> {
                val defenderPower = target.tech * 12f + (target.population / 100.0).toFloat() + target.shield * 60f
                val attackerPower = faction.aggression * 120f + rng.range(0f, 90f)
                if (attackerPower > defenderPower) {
                    val killed = target.population * rng.range(0.18f, 0.55f)
                    target.population -= killed
                    galaxy.totalLivesLost += killed
                    target.shield = max(0f, target.shield - 0.3f)
                    if (target.population < 20 || rng.chance(0.35f)) {
                        log("${faction.name} conquers ${target.name}.")
                        target.factionId = faction.id
                    } else {
                        log("Orbital bombardment scars ${target.name}.")
                    }
                } else {
                    log("${target.name} repels the ${faction.name} fleet.")
                }
            }
            Fleet.Kind.DEFENSE, Fleet.Kind.EVAC -> Unit
        }
    }

    private fun updateStars(dt: Float) {
        for (s in galaxy.systems) {
            s.starPulse += dt
            if (s.novaTimer > 0f) {
                s.novaTimer -= dt
                if (s.novaTimer <= 0f) {
                    s.starAlive = false
                    s.collapsed = true
                    for (p in s.planets) if (!p.destroyed) destroyPlanet(p, "consumed by the supernova")
                }
            }
        }
    }

    private fun updateDebris(dt: Float) {
        val it = galaxy.debris.iterator()
        while (it.hasNext()) {
            val field = it.next()
            field.age += dt
            for (c in field.chunks) {
                c[0] += c[2] * dt
                c[1] += c[3] * dt
                c[2] *= 0.999f
                c[3] *= 0.999f
                c[5] += c[6] * dt
            }
            if (field.age > 600f) it.remove()
        }
    }

    fun destroyPlanet(p: Planet, reason: String) {
        if (p.destroyed) return
        val lost = p.population
        galaxy.totalLivesLost += lost
        p.population = 0.0
        p.state = PlanetState.DESTROYED
        p.integrity = 0f
        p.shield = 0f
        galaxy.totalPlanetsDestroyed++
        val faction = galaxy.factions.getOrNull(p.factionId)
        if (faction != null) angerFaction(faction.id, 0.35f + (lost / 8000.0).toFloat())
        val field = DebrisField(p.x, p.y, p.radius, p.type.baseColor)
        val chunkCount = 22 + rng.nextInt(20)
        repeat(chunkCount) {
            val a = rng.range(0f, MathX.TAU)
            val sp = rng.range(4f, 46f)
            field.chunks.add(
                floatArrayOf(
                    p.x + kotlin.math.cos(a) * rng.range(0f, p.radius),
                    p.y + kotlin.math.sin(a) * rng.range(0f, p.radius),
                    kotlin.math.cos(a) * sp,
                    kotlin.math.sin(a) * sp,
                    rng.range(1.5f, p.radius * 0.32f),
                    rng.range(0f, MathX.TAU),
                    rng.range(-1.6f, 1.6f)
                )
            )
        }
        galaxy.debris.add(field)
        for (m in p.moons) m.alive = false
        galaxy.recomputeFactionStats()
        log("${p.name} was ${reason}. ${formatPop(lost)} lives lost.")
    }

    fun killStar(s: StarSystem) {
        if (!s.starAlive || s.novaTimer > 0f) return
        s.novaTimer = 2.2f
        galaxy.totalStarsDestroyed++
        log("${s.name} is going supernova!")
    }

    companion object {
        fun formatPop(millions: Double): String = when {
            millions >= 1_000_000 -> String.format("%.2f T", millions / 1_000_000.0)
            millions >= 1_000 -> String.format("%.2f B", millions / 1_000.0)
            millions >= 1 -> String.format("%.1f M", millions)
            millions > 0 -> String.format("%.0f K", millions * 1000)
            else -> "0"
        }
    }
}
