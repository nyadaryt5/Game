package com.nova.galaxysandbox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.galaxysandbox.action.Tool
import com.nova.galaxysandbox.action.Weapon
import com.nova.galaxysandbox.engine.GameEngine
import com.nova.galaxysandbox.engine.GameMode
import com.nova.galaxysandbox.engine.HudSnapshot

/**
 * The whole in-game interface: stat bar, side controls, weapon/tool rails,
 * inspector panel, event feed and the settings sheet.
 */
@Composable
fun GameHud(
    engine: GameEngine,
    hud: HudSnapshot,
    onExit: () -> Unit,
    onMenu: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(true) }
    var showInspector by remember { mutableStateOf(true) }
    var toolCategory by remember { mutableStateOf(Tool.Category.TERRAIN) }
    var refresh by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {

        TopStatBar(hud, engine, onMenu = onMenu, onSettings = { showSettings = !showSettings })

        // Left column: view + time controls.
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp, top = 70.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconAction(if (hud.paused) "▶" else "❚❚", active = hud.paused, accent = Palette.Amber) {
                engine.togglePause(); refresh++
            }
            IconAction("»", accent = Palette.Cyan) { engine.cycleTimeScale(); refresh++ }
            Text(
                "${formatSpeed(hud.timeScale)}x",
                color = Palette.TextMid,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 12.dp)
            )
            IconAction("◎", accent = Palette.Violet) {
                if (engine.mode == GameMode.GALAXY) engine.zoomToGalaxy() else engine.exitPlanet()
                refresh++
            }
            IconAction("⌖", accent = Palette.Green) { engine.focusRandomInhabited(); refresh++ }
            IconAction("ℹ", active = showInspector) { showInspector = !showInspector }
            IconAction("☰", active = showLog) { showLog = !showLog }
        }

        // Right inspector panel.
        AnimatedVisibility(
            visible = showInspector && (hud.selectedName.isNotEmpty() || hud.mode == GameMode.PLANET),
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 62.dp, end = 10.dp)
        ) {
            InspectorPanel(engine, hud)
        }

        // Event feed, bottom-left above the rail.
        AnimatedVisibility(
            visible = showLog,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 116.dp)
        ) {
            EventFeed(hud, engine)
        }

        // Bottom rail: weapons in galaxy view, tools on a surface.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        ) {
            if (hud.mode == GameMode.GALAXY) {
                WeaponRail(engine) { refresh++ }
            } else {
                ToolRail(engine, toolCategory, onCategory = { toolCategory = it }) { refresh++ }
            }
        }

        // Planet-mode exit button.
        AnimatedVisibility(
            visible = hud.mode == GameMode.PLANET,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 58.dp)
        ) {
            NeonButton("LEAVE ORBIT", glyph = "↥", accent = Palette.Danger) { onExit() }
        }

        if (showSettings) {
            SettingsSheet(engine, onClose = { showSettings = false })
        }
    }
    // Touch the counter so the composable recomposes after engine mutations.
    if (refresh < 0) Text("")
}

private fun formatSpeed(s: Float): String = if (s < 1f) "0.5" else s.toInt().toString()

@Composable
private fun TopStatBar(hud: HudSnapshot, engine: GameEngine, onMenu: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassPanel(corner = 14) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconAction("≡", accent = Palette.Violet) { onMenu() }
                StatChip("Population", hud.population, Palette.Green)
                StatChip("Planets Lost", hud.planetsDestroyed.toString(), Palette.Danger)
                StatChip("Stars Killed", hud.starsDestroyed.toString(), Palette.Amber)
                StatChip("Lives Lost", hud.livesLost, Palette.Danger)
                StatChip("Factions", hud.factionCount.toString(), Palette.Cyan)
                if (hud.mode == GameMode.PLANET) {
                    StatChip("Creatures", hud.worldPopulation.toString(), Palette.Green)
                    StatChip("Kingdoms", hud.kingdoms.toString(), Palette.Violet)
                }
                StatChip("FPS", hud.fps.toString(), Palette.TextLow)
                IconAction("⚙", accent = Palette.Cyan) { onSettings() }
            }
        }
    }
}

@Composable
private fun InspectorPanel(engine: GameEngine, hud: HudSnapshot) {
    GlassPanel(Modifier.width(232.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (hud.mode == GameMode.PLANET) hud.worldName else hud.selectedName,
                color = Palette.TextHi, fontSize = 17.sp, fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(2.dp))
            SectionTitle(if (hud.mode == GameMode.PLANET) "Surface Report" else "Orbital Scan")
            Spacer(Modifier.height(8.dp))
            for ((k, v) in hud.selectedInfo) {
                KeyValueRow(k, v, accentFor(k, v))
            }
            if (hud.mode == GameMode.PLANET) {
                val world = engine.world
                if (world != null) {
                    KeyValueRow("Creatures", hud.worldPopulation.toString(), Palette.Green)
                    KeyValueRow("Kingdoms", hud.kingdoms.toString(), Palette.Violet)
                    Spacer(Modifier.height(6.dp))
                    SectionTitle("Nations")
                    for (k in world.kingdoms.filter { it.alive }.take(6)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(Color(k.color))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(k.name, color = Palette.TextMid, fontSize = 11.sp)
                            }
                            Text(
                                "${k.population} · ${k.tiles}",
                                color = Palette.TextLow, fontSize = 10.sp
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                val planet = engine.selectedPlanet
                if (planet != null && !planet.destroyed) {
                    NeonButton(
                        "DESCEND TO SURFACE",
                        glyph = "↧",
                        accent = Palette.Green,
                        modifier = Modifier.fillMaxWidth()
                    ) { engine.enterPlanet(planet) }
                }
            }
        }
    }
}

private fun accentFor(key: String, value: String): Color = when (key) {
    "Integrity" -> if ((value.removeSuffix("%").toIntOrNull() ?: 100) < 50) Palette.Danger else Palette.Green
    "Population" -> Palette.Green
    "Shield" -> Palette.Cyan
    "Radiation" -> Palette.Amber
    "Owner" -> Palette.Violet
    else -> Palette.TextHi
}

@Composable
private fun EventFeed(hud: HudSnapshot, engine: GameEngine) {
    val lines = if (hud.mode == GameMode.PLANET)
        (engine.world?.eventLog?.toList()?.takeLast(6)?.reversed() ?: emptyList())
    else hud.log
    GlassPanel(Modifier.width(330.dp)) {
        Column(Modifier.padding(12.dp)) {
            SectionTitle("Event Feed")
            Spacer(Modifier.height(6.dp))
            if (lines.isEmpty()) {
                Text("The galaxy is quiet… for now.", color = Palette.TextLow, fontSize = 11.sp)
            }
            for ((i, line) in lines.withIndex()) {
                Text(
                    line,
                    color = if (i == 0) Palette.TextHi else Palette.TextMid.copy(alpha = 1f - i * 0.13f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun WeaponRail(engine: GameEngine, onChange: () -> Unit) {
    val scroll = rememberScrollState()
    GlassPanel {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(
                "⊕",
                active = engine.weaponArmed,
                accent = if (engine.weaponArmed) Palette.Danger else Palette.TextLow
            ) { engine.weaponArmed = !engine.weaponArmed; onChange() }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .widthCapped()
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (w in Weapon.values()) {
                    val selected = engine.selectedWeapon == w
                    NeonButton(
                        label = w.label,
                        glyph = w.glyph,
                        subtitle = if (selected) w.description.take(34) + "…" else null,
                        accent = Color(w.tint),
                        selected = selected,
                        progress = engine.weapons.cooldownFraction(w)
                    ) {
                        engine.selectedWeapon = w
                        engine.weaponArmed = true
                        onChange()
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRail(
    engine: GameEngine,
    category: Tool.Category,
    onCategory: (Tool.Category) -> Unit,
    onChange: () -> Unit
) {
    val scroll = rememberScrollState()
    GlassPanel {
        Column(Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in Tool.Category.values()) {
                    NeonButton(
                        label = c.label,
                        accent = Palette.Violet,
                        selected = c == category
                    ) { onCategory(c) }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "BRUSH",
                    color = Palette.TextLow,
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Slider(
                    value = engine.brushScale,
                    onValueChange = { engine.brushScale = it; onChange() },
                    valueRange = 0.4f..5f,
                    modifier = Modifier.width(150.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .widthCapped()
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (t in Tool.values()) {
                    if (t.category != category && t != Tool.INSPECT) continue
                    if (t == Tool.INSPECT && category != Tool.Category.SELECT) continue
                    val selected = engine.selectedTool == t
                    NeonButton(
                        label = t.label,
                        glyph = t.glyph,
                        subtitle = if (selected) t.description.take(30) + "…" else null,
                        accent = Color(t.tint),
                        selected = selected
                    ) { engine.selectedTool = t; onChange() }
                }
            }
        }
    }
}

/** Keeps the rails from stretching past the screen on tablets. */
@Composable
private fun Modifier.widthCapped(): Modifier = this.width(760.dp)

@Composable
private fun SettingsSheet(engine: GameEngine, onClose: () -> Unit) {
    var orbits by remember { mutableStateOf(engine.showOrbits) }
    var borders by remember { mutableStateOf(engine.showBorders) }
    var names by remember { mutableStateOf(engine.showNames) }
    var life by remember { mutableStateOf(engine.sim.lifeEnabled) }
    var wars by remember { mutableStateOf(engine.sim.warEnabled) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xAA02030A)),
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(Modifier.width(400.dp)) {
            Column(
                Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("SETTINGS", color = Palette.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                SectionTitle("Display")
                ToggleRow("Orbit rings", orbits) { orbits = it; engine.showOrbits = it }
                ToggleRow("Faction borders", borders) { borders = it; engine.showBorders = it }
                ToggleRow("Names & labels", names) { names = it; engine.showNames = it }
                Spacer(Modifier.height(10.dp))
                SectionTitle("Simulation")
                ToggleRow("Life & growth", life) {
                    life = it; engine.sim.lifeEnabled = it; engine.worldSim?.lifeEnabled = it
                }
                ToggleRow("Wars & conquest", wars) {
                    wars = it; engine.sim.warEnabled = it; engine.worldSim?.warEnabled = it
                }
                Spacer(Modifier.height(16.dp))
                NeonButton("CLOSE", glyph = "✕", accent = Palette.Danger, modifier = Modifier.fillMaxWidth()) {
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Palette.TextMid, fontSize = 13.sp)
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Cyan,
                checkedTrackColor = Palette.Cyan.copy(alpha = 0.3f)
            )
        )
    }
}
