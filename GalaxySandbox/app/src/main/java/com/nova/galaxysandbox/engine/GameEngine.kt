package com.nova.galaxysandbox.engine

import com.nova.galaxysandbox.action.Tool
import com.nova.galaxysandbox.action.Weapon
import com.nova.galaxysandbox.action.WeaponSystem
import com.nova.galaxysandbox.core.MathX
import com.nova.galaxysandbox.fx.ParticleSystem
import com.nova.galaxysandbox.galaxy.Galaxy
import com.nova.galaxysandbox.galaxy.GalaxySim
import com.nova.galaxysandbox.galaxy.Planet
import com.nova.galaxysandbox.world.PlanetWorld
import com.nova.galaxysandbox.world.Species
import com.nova.galaxysandbox.world.WorldSim
import com.nova.galaxysandbox.world.WorldTools
import kotlin.math.max
import kotlin.math.min

enum class GameMode { GALAXY, PLANET }

/** Immutable-ish snapshot the Compose UI reads; refreshed a few times per second. */
data class HudSnapshot(
    val mode: GameMode = GameMode.GALAXY,
    val fps: Int = 0,
    val population: String = "0",
    val planetsDestroyed: Int = 0,
    val starsDestroyed: Int = 0,
    val livesLost: String = "0",
    val factionCount: Int = 0,
    val selectedName: String = "",
    val selectedInfo: List<Pair<String, String>> = emptyList(),
    val log: List<String> = emptyList(),
    val worldName: String = "",
    val worldPopulation: Int = 0,
    val kingdoms: Int = 0,
    val timeScale: Float = 1f,
    val paused: Boolean = false
)

/**
 * Owns the whole game: galaxy simulation, surface simulation, weapons, camera,
 * input and the transition between the two scales.
 */
class GameEngine {

    var galaxy = Galaxy(System.currentTimeMillis())
        private set
    var sim = GalaxySim(galaxy)
        private set
    val fx = ParticleSystem(2600)
    val worldFx = ParticleSystem(1400)
    var weapons = WeaponSystem(galaxy, sim, fx)
        private set

    val galaxyCam = Camera()
    val planetCam = Camera()

    var mode = GameMode.GALAXY
        private set
    var world: PlanetWorld? = null
        private set
    var worldSim: WorldSim? = null
        private set

    var selectedPlanet: Planet? = null
    var selectedWeapon: Weapon = Weapon.LASER
    var selectedTool: Tool = Tool.INSPECT
    var brushScale = 1f
    var weaponArmed = true
    var paused = false
    var timeScale = 1f
    var showOrbits = true
    var showBorders = true
    var showNames = true

    var transition = 0f            // 1 -> fully zoomed into a planet
    private var transitionDir = 0  // +1 entering, -1 leaving
    var transitionPlanet: Planet? = null

    var hud = HudSnapshot()
        private set
    private var hudTimer = 0f
    private var fpsAccum = 0f
    private var fpsFrames = 0
    private var fpsValue = 0

    var onEvent: ((String) -> Unit)? = null

    init { resetCameras() }

    // ------------------------------------------------------------------ setup

    fun newGalaxy(seed: Long = System.currentTimeMillis(), systemCount: Int = 48) {
        galaxy = Galaxy(seed, systemCount)
        sim = GalaxySim(galaxy)
        weapons = WeaponSystem(galaxy, sim, fx)
        fx.clear(); worldFx.clear()
        world = null; worldSim = null
        selectedPlanet = null
        mode = GameMode.GALAXY
        transition = 0f
        transitionDir = 0
        resetCameras()
        sim.log("A new galaxy spins up: ${galaxy.systems.size} systems.")
    }

    private fun resetCameras() {
        galaxyCam.minZoom = 0.045f
        galaxyCam.maxZoom = 3.5f
        galaxyCam.snapTo(0f, 0f, 0.12f)
        planetCam.minZoom = 1f
        planetCam.maxZoom = 42f
    }

    fun setViewport(w: Int, h: Int) {
        galaxyCam.viewW = w.toFloat(); galaxyCam.viewH = h.toFloat()
        planetCam.viewW = w.toFloat(); planetCam.viewH = h.toFloat()
        world?.let { fitPlanetCamera(it) }
    }

    private fun fitPlanetCamera(w: PlanetWorld) {
        val zx = planetCam.viewW / w.width
        val zy = planetCam.viewH / w.height
        val z = min(zx, zy)
        planetCam.minZoom = z * 0.9f
        planetCam.maxZoom = z * 14f
        planetCam.snapTo(w.width / 2f, w.height / 2f, z)
    }

    // ----------------------------------------------------------------- update

    fun update(dtRaw: Float) {
        val dt = min(dtRaw, 0.05f)
        fpsAccum += dtRaw
        fpsFrames++
        if (fpsAccum >= 0.5f) {
            fpsValue = (fpsFrames / fpsAccum).toInt()
            fpsAccum = 0f; fpsFrames = 0
        }

        val simDt = if (paused) 0f else dt * timeScale

        // Mode transition easing.
        if (transitionDir != 0) {
            transition = MathX.clamp(transition + transitionDir * dt * 1.6f, 0f, 1f)
            if (transitionDir > 0 && transition >= 1f) {
                transitionDir = 0
                mode = GameMode.PLANET
            } else if (transitionDir < 0 && transition <= 0f) {
                transitionDir = 0
                mode = GameMode.GALAXY
                world = null
                worldSim = null
                weapons.activeWorld = null
            }
        }

        sim.speed = timeScale
        if (simDt > 0f) sim.update(dt)
        weapons.update(dt)
        fx.update(dt)

        if (weapons.screenShake > 0f) galaxyCam.addShake(weapons.screenShake)
        galaxyCam.update(dt)

        world?.let { w ->
            worldSim?.let { ws ->
                ws.speed = timeScale
                if (simDt > 0f) ws.update(dt)
            }
            worldFx.update(dt)
            if (w.shake > 0f) planetCam.addShake(w.shake * 0.6f)
            planetCam.update(dt)
        }

        hudTimer += dt
        if (hudTimer > 0.2f) { hudTimer = 0f; refreshHud() }
    }

    private fun refreshHud() {
        val sel = selectedPlanet
        val info = ArrayList<Pair<String, String>>()
        if (sel != null) {
            info.add("Class" to sel.type.label)
            info.add("Status" to sel.state.name.lowercase().replaceFirstChar { it.uppercase() })
            info.add("Integrity" to "${(sel.integrity * 100).toInt()}%")
            info.add("Population" to GalaxySim.formatPop(sel.population))
            info.add("Tech" to String.format("%.1f / 10", sel.tech))
            info.add("Shield" to "${(sel.shield * 100).toInt()}%")
            info.add("Temp" to "${sel.temperature.toInt()}°C")
            info.add("Radiation" to "${(sel.radiation * 100).toInt()}%")
            info.add("Moons" to sel.moons.count { it.alive }.toString())
            val f = galaxy.factions.getOrNull(sel.factionId)
            info.add("Owner" to (f?.name ?: "Uninhabited"))
            info.add("System" to galaxy.systems[sel.systemId].name)
        }
        hud = HudSnapshot(
            mode = mode,
            fps = fpsValue,
            population = GalaxySim.formatPop(galaxy.livingPopulation()),
            planetsDestroyed = galaxy.totalPlanetsDestroyed,
            starsDestroyed = galaxy.totalStarsDestroyed,
            livesLost = GalaxySim.formatPop(galaxy.totalLivesLost),
            factionCount = galaxy.factions.count { it.alive },
            selectedName = sel?.name ?: "",
            selectedInfo = info,
            log = sim.eventLog.toList().takeLast(6).reversed(),
            worldName = world?.planet?.name ?: "",
            worldPopulation = world?.totalCivPopulation() ?: 0,
            kingdoms = world?.kingdoms?.count { it.alive } ?: 0,
            timeScale = timeScale,
            paused = paused
        )
    }

    // ------------------------------------------------------------ mode change

    fun enterPlanet(planet: Planet) {
        if (planet.destroyed) return
        if (mode == GameMode.PLANET) return
        val w = PlanetWorld(planet)
        // Seed the surface with whatever the galaxy thinks lives there.
        if (planet.population > 0.5) seedLife(w, planet)
        world = w
        worldSim = WorldSim(w, worldFx)
        worldFx.clear()
        weapons.activeWorld = w
        planet.visited = true
        transitionPlanet = planet
        transitionDir = 1
        fitPlanetCamera(w)
        selectedPlanet = planet
        onEvent?.invoke("Entering ${planet.name}")
    }

    private fun seedLife(w: PlanetWorld, planet: Planet) {
        val species = when {
            planet.temperature < -10f -> Species.FROSTKIN
            planet.tech > 7f -> Species.SYNTH
            planet.temperature > 34f -> Species.ORC
            planet.type.habitability > 0.8f -> Species.HUMAN
            else -> Species.ELF
        }
        val count = MathX.clamp((planet.population / 60.0).toInt(), 8, 220)
        var placed = 0
        var attempts = 0
        while (placed < count && attempts < count * 30) {
            attempts++
            val x = w.rng.range(0f, w.width.toFloat())
            val y = w.rng.range(0f, w.height.toFloat())
            if (!w.walkable(x.toInt(), y.toInt())) continue
            if (w.spawnUnit(x, y, species, -1) == null) break
            placed++
        }
        if (planet.tech > 2f) {
            repeat(6) {
                val x = w.rng.range(0f, w.width.toFloat())
                val y = w.rng.range(0f, w.height.toFloat())
                if (w.walkable(x.toInt(), y.toInt())) {
                    w.spawnUnit(x, y, Species.WOLF, -1)
                }
            }
        }
    }

    fun exitPlanet() {
        if (mode != GameMode.PLANET) return
        world?.syncToPlanet()
        transitionDir = -1
    }

    // ------------------------------------------------------------------ input

    fun pan(dxScreen: Float, dyScreen: Float) {
        activeCamera().panBy(dxScreen, dyScreen)
    }

    fun pinch(scaleFactor: Float, focusX: Float, focusY: Float) {
        activeCamera().zoomBy(scaleFactor, focusX, focusY)
    }

    fun activeCamera(): Camera = if (mode == GameMode.PLANET) planetCam else galaxyCam

    /** Single tap: fire the armed weapon, select a planet, or use the surface tool. */
    fun onTap(sx: Float, sy: Float) {
        if (mode == GameMode.PLANET) {
            useToolAt(sx, sy)
            return
        }
        val p = galaxyCam.screenToWorld(sx, sy)
        val wx = p[0]
        val wy = p[1]
        if (weaponArmed) {
            fireWeapon(wx, wy, sx, sy)
        } else {
            val hit = galaxy.nearestPlanet(wx, wy, 120f / galaxyCam.zoom)
            selectedPlanet = hit
            if (hit != null) galaxyCam.moveTo(hit.x, hit.y)
        }
        refreshHud()
    }

    fun onDoubleTap(sx: Float, sy: Float) {
        if (mode == GameMode.PLANET) return
        val p = galaxyCam.screenToWorld(sx, sy)
        val hit = galaxy.nearestPlanet(p[0], p[1], 160f / galaxyCam.zoom)
        if (hit != null) enterPlanet(hit) else galaxyCam.zoomBy(1.8f, sx, sy)
    }

    fun onDrag(sx: Float, sy: Float) {
        if (mode == GameMode.PLANET) {
            useToolAt(sx, sy, continuous = true)
        } else if (weaponArmed && selectedWeapon.targeting == Weapon.Targeting.HOLD) {
            val p = galaxyCam.screenToWorld(sx, sy)
            fireWeapon(p[0], p[1], sx, sy)
        }
    }

    fun selectPlanetAt(sx: Float, sy: Float): Planet? {
        val p = galaxyCam.screenToWorld(sx, sy)
        val hit = galaxy.nearestPlanet(p[0], p[1], 140f / galaxyCam.zoom)
        if (hit != null) selectedPlanet = hit
        return hit
    }

    private fun fireWeapon(wx: Float, wy: Float, sx: Float, sy: Float) {
        // Shots come in from beyond the top-left of the viewport for a nice angle.
        val originScreenX = sx - galaxyCam.viewW * 0.9f
        val originScreenY = sy - galaxyCam.viewH * 1.1f
        val o = galaxyCam.screenToWorld(originScreenX, originScreenY)
        val ox = o[0]
        val oy = o[1]
        val ok = weapons.fire(selectedWeapon, wx, wy, ox, oy)
        if (ok) {
            val hit = galaxy.nearestPlanet(wx, wy, 200f)
            if (hit != null) selectedPlanet = hit
        }
    }

    private fun useToolAt(sx: Float, sy: Float, continuous: Boolean = false) {
        val w = world ?: return
        val p = planetCam.screenToWorld(sx, sy)
        val tx = p[0]
        val ty = p[1]
        if (tx < 0 || ty < 0 || tx >= w.width || ty >= w.height) return
        if (selectedTool == Tool.INSPECT) return
        // Heavy one-shot tools should not repeat while dragging.
        if (continuous && selectedTool.category == Tool.Category.DISASTER &&
            selectedTool != Tool.LIGHTNING
        ) return
        if (continuous && selectedTool == Tool.ARMAGEDDON) return
        WorldTools.apply(w, selectedTool, tx, ty, brushScale, worldFx)
    }

    fun cycleTimeScale() {
        timeScale = when {
            timeScale < 0.9f -> 1f
            timeScale < 1.9f -> 2f
            timeScale < 3.9f -> 4f
            else -> 0.5f
        }
    }

    fun togglePause() { paused = !paused }

    fun focusRandomInhabited() {
        val candidates = galaxy.planetsById.values.filter { !it.destroyed && it.population > 10 }
        val p = candidates.randomOrNull() ?: galaxy.planetsById.values.firstOrNull { !it.destroyed }
        if (p != null) {
            selectedPlanet = p
            galaxyCam.moveTo(p.x, p.y, max(galaxyCam.targetZoom, 0.55f))
        }
    }

    fun zoomToGalaxy() {
        galaxyCam.moveTo(0f, 0f, 0.075f)
    }
}
