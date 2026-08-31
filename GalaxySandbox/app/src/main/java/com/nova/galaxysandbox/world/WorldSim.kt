package com.nova.galaxysandbox.world

import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.core.Rng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Drives everything that happens on a planet surface: creatures walking around,
 * villages growing into cities, kingdoms claiming land and going to war, fire
 * spreading, radiation decaying, terrain recovering.
 */
class WorldSim(private val world: PlanetWorld, private val fx: com.nova.galaxysandbox.fx.ParticleSystem? = null) {

    private val rng = Rng(world.planet.seed * 7919 + 13)
    private var accum = 0f
    private var slowAccum = 0f
    var speed = 1f
    var lifeEnabled = true
    var warEnabled = true

    /** Ambient particles the renderer draws (rain drops, embers, snow). */
    val ambient = ArrayList<FloatArray>()

    fun update(dt: Float) {
        val d = dt * speed
        world.tick++
        updateUnits(d)
        WorldTools.updateDisasters(world, d, fx)
        accum += d
        while (accum >= 0.1f) {
            accum -= 0.1f
            fireTick(0.1f)
            growthTick(0.1f)
        }
        slowAccum += d
        while (slowAccum >= 1f) {
            slowAccum -= 1f
            societyTick()
        }
    }

    // ------------------------------------------------------------------- units

    private fun updateUnits(dt: Float) {
        val units = world.units
        var i = 0
        while (i < units.size) {
            val u = units[i]
            if (!u.alive) { units.removeAt(i); continue }
            u.age += dt
            u.bob += dt * (6f + u.species.speed * 3f)
            u.flash = max(0f, u.flash - dt * 3f)
            if (u.cooldown > 0f) u.cooldown -= dt
            if (u.breedCooldown > 0f) u.breedCooldown -= dt

            val tx = u.x.toInt()
            val ty = u.y.toInt()
            val ti = if (world.inBounds(tx, ty)) world.idx(tx, ty) else -1
            if (ti >= 0) {
                // Environmental damage.
                val b = Biome.of(world.biome[ti].toInt())
                if (b == Biome.LAVA) u.hp -= 60f * dt
                if (world.fire[ti] > 0.2f) u.hp -= 34f * dt
                if (world.radiation[ti] > 0.15f) u.hp -= world.radiation[ti] * 16f * dt
                if (b.liquid && b != Biome.LAVA && u.species != Species.XENO) u.hp -= 12f * dt
                val temp = world.temperature[ti]
                if (temp < -35f && u.species != Species.FROSTKIN) u.hp -= 5f * dt
                if (temp > 60f) u.hp -= 6f * dt
            }
            if (u.age > u.lifespan) u.hp -= 12f * dt
            if (u.hp <= 0f) { u.alive = false; units.removeAt(i); continue }

            when (u.state) {
                Creature.State.FIGHT -> fightBehaviour(u, dt)
                Creature.State.FLEE -> {
                    moveTowards(u, u.tx, u.ty, dt, 1.35f)
                    if (MathX.dist(u.x, u.y, u.tx, u.ty) < 1.2f) u.state = Creature.State.WANDER
                }
                else -> wanderBehaviour(u, dt)
            }
            i++
        }
    }

    private fun wanderBehaviour(u: Creature, dt: Float) {
        if (MathX.dist(u.x, u.y, u.tx, u.ty) < 0.8f || !world.walkable(u.tx.toInt(), u.ty.toInt())) {
            pickDestination(u)
        }
        moveTowards(u, u.tx, u.ty, dt, 1f)

        // Look for enemies nearby.
        if (u.cooldown <= 0f && rng.chance(0.35f)) {
            val enemy = findEnemyNear(u, 6f)
            if (enemy != null) {
                u.targetUnit = enemy
                u.state = Creature.State.FIGHT
                return
            }
        }
        // Breeding.
        if (u.breedCooldown <= 0f && u.age > 25f) {
            val here = fertility(u.x.toInt(), u.y.toInt())
            val cap = if (u.species.civilised) 420 else 160
            val own = world.populationOfSpecies(u.species)
            if (here > 0.2f && own < cap && world.units.size < PlanetWorld.MAX_UNITS &&
                rng.chance(0.30f * here)
            ) {
                world.spawnUnit(u.x + rng.range(-1f, 1f), u.y + rng.range(-1f, 1f), u.species, u.kingdom)
                u.breedCooldown = rng.range(12f, 30f)
            } else {
                u.breedCooldown = rng.range(6f, 14f)
            }
        }
    }

    private fun fightBehaviour(u: Creature, dt: Float) {
        val target = u.targetUnit
        if (target == null || !target.alive) { u.state = Creature.State.WANDER; u.targetUnit = null; return }
        val d = MathX.dist(u.x, u.y, target.x, target.y)
        if (d > 9f) { u.state = Creature.State.WANDER; u.targetUnit = null; return }
        if (d > 0.9f) {
            moveTowards(u, target.x, target.y, dt, 1.25f)
        } else if (u.cooldown <= 0f) {
            val dmg = u.species.strength * rng.range(9f, 17f)
            target.hp -= dmg
            target.flash = 1f
            u.cooldown = 0.6f
            if (target.hp <= 0f) {
                target.alive = false
                u.state = Creature.State.WANDER
                u.targetUnit = null
                if (rng.chance(0.02f) && u.kingdom >= 0) {
                    world.kingdoms.getOrNull(u.kingdom)?.let { it.wealth += 1f }
                }
            } else if (target.state != Creature.State.FIGHT) {
                if (target.species.strength < u.species.strength * 0.7f) {
                    target.state = Creature.State.FLEE
                    target.tx = target.x + (target.x - u.x) * 6f
                    target.ty = target.y + (target.y - u.y) * 6f
                } else {
                    target.state = Creature.State.FIGHT
                    target.targetUnit = u
                }
            }
        }
    }

    private fun findEnemyNear(u: Creature, radius: Float): Creature? {
        var best: Creature? = null
        var bestD = radius * radius
        for (o in world.units) {
            if (o === u || !o.alive) continue
            val hostile = when {
                !o.species.civilised && !u.species.civilised -> false
                o.species == u.species && o.kingdom == u.kingdom -> false
                !u.species.civilised -> true               // beasts attack anything
                !o.species.civilised -> o.species.strength > 1f
                o.kingdom < 0 || u.kingdom < 0 -> o.species != u.species
                else -> {
                    val k = world.kingdoms.getOrNull(u.kingdom)
                    k != null && k.atWarWith.contains(o.kingdom)
                }
            }
            if (!hostile) continue
            val d = MathX.dist2(u.x, u.y, o.x, o.y)
            if (d < bestD) { bestD = d; best = o }
        }
        return best
    }

    private fun pickDestination(u: Creature) {
        var bestScore = -1f
        var bx = u.x
        var by = u.y
        repeat(6) {
            val nx = u.x + rng.range(-9f, 9f)
            val ny = u.y + rng.range(-9f, 9f)
            val ix = nx.toInt()
            val iy = ny.toInt()
            if (!world.walkable(ix, iy)) return@repeat
            var score = fertility(ix, iy)
            val b = world.biomeAt(ix, iy)
            if (u.species.prefers.contains(b)) score += 0.6f
            score -= world.radiation[world.idx(ix, iy)]
            score += rng.range(0f, 0.25f)
            if (score > bestScore) { bestScore = score; bx = nx; by = ny }
        }
        if (bestScore < 0f) {
            // Trapped: hop to any walkable neighbour.
            for (dy in -2..2) for (dx in -2..2) {
                if (world.walkable(u.x.toInt() + dx, u.y.toInt() + dy)) {
                    bx = u.x + dx; by = u.y + dy
                }
            }
        }
        u.tx = MathX.clamp(bx, 0.5f, world.width - 1.5f)
        u.ty = MathX.clamp(by, 0.5f, world.height - 1.5f)
    }

    private fun moveTowards(u: Creature, tx: Float, ty: Float, dt: Float, mul: Float) {
        val dx = tx - u.x
        val dy = ty - u.y
        val d = sqrt(dx * dx + dy * dy)
        if (d < 0.001f) return
        val sp = u.species.speed * 2.4f * mul * dt
        val nx = u.x + dx / d * sp
        val ny = u.y + dy / d * sp
        if (world.walkable(nx.toInt(), ny.toInt()) || u.species == Species.DRAGON) {
            u.x = MathX.clamp(nx, 0.2f, world.width - 1.2f)
            u.y = MathX.clamp(ny, 0.2f, world.height - 1.2f)
            u.facing = if (dx >= 0) 1f else -1f
        } else {
            u.tx = u.x + rng.range(-4f, 4f)
            u.ty = u.y + rng.range(-4f, 4f)
        }
    }

    private fun fertility(x: Int, y: Int): Float = world.fertilityAt(x, y)

    // -------------------------------------------------------------------- fire

    private fun fireTick(dt: Float) {
        val w = world
        var any = false
        val samples = 2600
        repeat(samples) {
            val x = rng.nextInt(w.width)
            val y = rng.nextInt(w.height)
            val i = w.idx(x, y)
            val f = w.fire[i]
            if (f <= 0f) return@repeat
            any = true
            val b = Biome.of(w.biome[i].toInt())
            val fuel = when (b) {
                Biome.FOREST, Biome.JUNGLE -> 1f
                Biome.GRASS, Biome.PLAINS, Biome.SAVANNA -> 0.65f
                else -> 0.15f
            }
            w.fire[i] = max(0f, f - dt * (0.35f - fuel * 0.22f))
            if (w.fire[i] <= 0.02f && fuel > 0.5f) {
                w.biome[i] = Biome.ASH.ordinal.toByte()
                w.dirty = true
            }
            if (fuel > 0.3f && rng.chance(0.32f * fuel)) {
                val dx = rng.range(-1, 2)
                val dy = rng.range(-1, 2)
                if (w.inBounds(x + dx, y + dy)) {
                    val j = w.idx(x + dx, y + dy)
                    val nb = Biome.of(w.biome[j].toInt())
                    if (!nb.liquid && nb != Biome.ASH && w.fire[j] < 0.2f) {
                        w.fire[j] = 1f
                        w.dirty = true
                    }
                }
            }
        }
        if (any) world.dirty = true

        // Radiation slowly decays.
        repeat(900) {
            val i = rng.nextInt(world.size)
            if (world.radiation[i] > 0f) {
                world.radiation[i] = max(0f, world.radiation[i] - dt * 0.02f)
                world.dirty = true
            }
        }
    }

    /** Vegetation regrows, ash turns back into grass, lava crusts over. */
    private fun growthTick(dt: Float) {
        val w = world
        repeat(700) {
            val i = rng.nextInt(w.size)
            val b = Biome.of(w.biome[i].toInt())
            val t = w.temperature[i]
            when (b) {
                Biome.ASH, Biome.CRATER -> if (rng.chance(0.006f) && t > -6f && w.radiation[i] < 0.1f) {
                    w.biome[i] = Biome.GRASS.ordinal.toByte(); w.dirty = true
                }
                Biome.GRASS -> if (rng.chance(0.0035f) && w.moisture[i] > 0.55f && t > 2f) {
                    w.biome[i] = Biome.FOREST.ordinal.toByte(); w.dirty = true
                }
                Biome.LAVA -> if (rng.chance(0.004f)) {
                    w.biome[i] = Biome.BADLANDS.ordinal.toByte(); w.dirty = true
                }
                else -> Unit
            }
        }
    }

    // ----------------------------------------------------------------- society

    private fun societyTick() {
        if (!lifeEnabled) return
        // 1. Wild civilised units found kingdoms — or join a neighbouring one.
        for (u in world.units) {
            if (!u.alive || !u.species.civilised || u.kingdom >= 0) continue
            val x = u.x.toInt()
            val y = u.y.toInt()
            if (!world.walkable(x, y)) continue

            // Join an existing nearby nation of the same species first.
            val neighbour = nearestKingdomWithin(x, y, 14)
            if (neighbour != null) {
                if (neighbour.species == u.species) { u.kingdom = neighbour.id; continue }
                if (!rng.chance(0.02f)) continue   // rival species rarely settle next door
            }
            if (!rng.chance(0.035f)) continue
            if (world.fertilityAt(x, y) < 0.3f) continue
            val k = world.createKingdom(u.species) ?: continue
            u.kingdom = k.id
            k.capitalIndex = world.idx(x, y)
            world.structure[k.capitalIndex] = STRUCT_HUT.toByte()
            claimRadius(x, y, 3, k.id)
            // Anyone of the same species standing nearby joins the new nation.
            for (o in world.units) {
                if (o.alive && o.kingdom < 0 && o.species == u.species &&
                    MathX.dist(o.x, o.y, u.x, u.y) < 16f
                ) o.kingdom = k.id
            }
            world.log("${k.name} founded by the ${u.species.label}.")
            world.dirty = true
        }

        // 2. Kingdoms grow: claim land, raise buildings, build roads.
        for (k in world.kingdoms) {
            if (!k.alive) continue
            k.age += 1f
            var members = 0
            for (u in world.units) if (u.alive && u.kingdom == k.id) members++
            k.population = members
            if (members == 0) {
                if (k.tiles > 0 && rng.chance(0.25f)) collapseKingdom(k)
                continue
            }
            k.wealth += members * 0.02f

            val expandChance = MathX.clamp(0.25f + members / 80f, 0f, 0.9f)
            if (rng.chance(expandChance)) expandKingdom(k)
            var builds = 0
            while (k.wealth > 4f && builds < 3) {
                buildStructure(k)
                k.wealth -= 4f
                builds++
            }
        }

        // 3. Diplomacy: declare and end wars.
        if (warEnabled && world.kingdoms.size > 1 && rng.chance(0.25f)) {
            val a = world.kingdoms.filter { it.alive && it.population > 0 }.randomOrNullX(rng)
            val b = world.kingdoms.filter { it.alive && it.population > 0 && it !== a }.randomOrNullX(rng)
            if (a != null && b != null) {
                if (a.atWarWith.contains(b.id)) {
                    if (rng.chance(0.18f)) {
                        a.atWarWith.remove(b.id); b.atWarWith.remove(a.id)
                        world.log("${a.name} and ${b.name} sign a peace treaty.")
                    }
                } else if (rng.chance(0.20f * (a.aggression + b.aggression))) {
                    a.atWarWith.add(b.id); b.atWarWith.add(a.id)
                    world.log("${a.name} declares war on ${b.name}!")
                }
            }
        }

        // 4. Wars burn down border structures.
        if (warEnabled) {
            for (k in world.kingdoms) {
                if (!k.alive || k.atWarWith.isEmpty()) continue
                if (!rng.chance(0.3f)) continue
                val enemyId = k.atWarWith.random()
                raidKingdom(k, enemyId)
            }
        }
        world.syncToPlanet()
    }

    private fun nearestKingdomWithin(cx: Int, cy: Int, radius: Int): Kingdom? {
        var best: Kingdom? = null
        var bestD = Float.MAX_VALUE
        val step = 2
        var y = cy - radius
        while (y <= cy + radius) {
            var x = cx - radius
            while (x <= cx + radius) {
                if (world.inBounds(x, y)) {
                    val c = world.claim[world.idx(x, y)].toInt()
                    if (c > 0) {
                        val k = world.kingdoms.getOrNull(c - 1)
                        if (k != null && k.alive) {
                            val d = MathX.dist(cx.toFloat(), cy.toFloat(), x.toFloat(), y.toFloat())
                            if (d < bestD) { bestD = d; best = k }
                        }
                    }
                }
                x += step
            }
            y += step
        }
        return best
    }

    private fun claimRadius(cx: Int, cy: Int, r: Int, kingdomId: Int) {
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            if (!world.inBounds(x, y)) continue
            if (MathX.dist(cx.toFloat(), cy.toFloat(), x.toFloat(), y.toFloat()) > r) continue
            val i = world.idx(x, y)
            if (!Biome.of(world.biome[i].toInt()).walkable) continue
            if (world.claim[i].toInt() == 0) claimTile(i, kingdomId)
        }
        world.dirty = true
    }

    /** Claim a tile for a kingdom and remember it, so later growth is O(1) to sample. */
    private fun claimTile(i: Int, kingdomId: Int) {
        val k = world.kingdoms[kingdomId]
        world.claim[i] = (kingdomId + 1).toByte()
        k.tiles++
        k.ownedTiles.add(i)
    }

    /** Random tile actually owned by this kingdom right now (prunes stale entries). */
    private fun randomOwnedTile(k: Kingdom): Int {
        var tries = 0
        while (tries < 12 && k.ownedTiles.isNotEmpty()) {
            tries++
            val at = rng.nextInt(k.ownedTiles.size)
            val i = k.ownedTiles[at]
            if (world.claim[i].toInt() == k.id + 1) return i
            k.ownedTiles.removeAt(at)
        }
        return -1
    }

    private fun expandKingdom(k: Kingdom) {
        // Grow outward from a random owned border tile.
        var attempts = 0
        while (attempts < 14) {
            attempts++
            val i = randomOwnedTile(k)
            if (i < 0) return
            val x = i % world.width
            val y = i / world.width
            val dx = rng.range(-1, 2)
            val dy = rng.range(-1, 2)
            if (!world.inBounds(x + dx, y + dy)) continue
            val j = world.idx(x + dx, y + dy)
            val b = Biome.of(world.biome[j].toInt())
            if (!b.walkable || b.fertility < 0.03f) continue
            if (world.claim[j].toInt() == 0) {
                claimTile(j, k.id)
                world.dirty = true
                return
            }
        }
    }

    private fun buildStructure(k: Kingdom) {
        var attempts = 0
        while (attempts < 20) {
            attempts++
            val i = randomOwnedTile(k)
            if (i < 0) return
            if (world.structure[i].toInt() != STRUCT_NONE) continue
            val x = i % world.width
            val y = i / world.width
            if (!world.walkable(x, y)) continue
            var neighbours = 0
            for (dy in -2..2) for (dx in -2..2) {
                if (!world.inBounds(x + dx, y + dy)) continue
                if (world.structure[world.idx(x + dx, y + dy)].toInt() != STRUCT_NONE) neighbours++
            }
            val type = when {
                neighbours >= 6 && k.population > 30 -> STRUCT_CITY
                neighbours >= 3 -> STRUCT_HOUSE
                world.fertilityAt(x, y) > 0.6f && rng.chance(0.4f) -> STRUCT_FARM
                k.atWarWith.isNotEmpty() && rng.chance(0.35f) -> STRUCT_TOWER
                else -> STRUCT_HUT
            }
            world.structure[i] = type.toByte()
            world.dirty = true
            if (type == STRUCT_CITY) world.log("${k.name} raises a great city.")
            claimRadius(x, y, 2, k.id)
            return
        }
    }

    private fun raidKingdom(attacker: Kingdom, defenderId: Int) {
        val defender = world.kingdoms.getOrNull(defenderId) ?: return
        var attempts = 0
        while (attempts < 12) {
            attempts++
            val i = randomOwnedTile(defender)
            if (i < 0) return
            val defended = world.structure[i].toInt() == STRUCT_TOWER
            val power = attacker.population * (1f + attacker.aggression)
            val resist = defender.population * (if (defended) 1.8f else 1f)
            if (power > resist * rng.range(0.6f, 1.6f)) {
                if (world.structure[i].toInt() != STRUCT_NONE) {
                    world.structure[i] = STRUCT_RUIN.toByte()
                    if (rng.chance(0.4f)) world.fire[i] = 1f
                }
                defender.tiles = max(0, defender.tiles - 1)
                claimTile(i, attacker.id)
                world.dirty = true
            }
            return
        }
    }

    private fun collapseKingdom(k: Kingdom) {
        for (i in 0 until world.size) {
            if (world.claim[i].toInt() == k.id + 1) {
                world.claim[i] = 0
                if (world.structure[i].toInt() != STRUCT_NONE) world.structure[i] = STRUCT_RUIN.toByte()
            }
        }
        k.alive = false
        k.tiles = 0
        k.ownedTiles.clear()
        world.log("${k.name} has fallen into ruin.")
        world.dirty = true
    }
}

private fun <T> List<T>.randomOrNullX(rng: Rng): T? = if (isEmpty()) null else this[rng.nextInt(size)]
