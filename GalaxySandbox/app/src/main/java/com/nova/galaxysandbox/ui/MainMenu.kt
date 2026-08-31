package com.nova.galaxysandbox.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Animated starfield + title screen shown before the simulation starts. */
@Composable
fun MainMenu(
    onNewGalaxy: (Int) -> Unit,
    onContinue: (() -> Unit)?,
    onHelp: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "menu")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "t"
    )
    val stars = remember {
        List(180) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) }
    }
    var size by remember { mutableStateOf(48) }
    var showSize by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF17123A), Color(0xFF070718), Color(0xFF02020A))
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for ((i, s) in stars.withIndex()) {
                val (sx, sy, sr) = s
                val twinkle = 0.45f + 0.55f * sin(t * 6.28f * (1f + sr) + i).let { kotlin.math.abs(it) }
                drawCircle(
                    color = Color(0xFFCFE6FF).copy(alpha = 0.15f + twinkle * 0.7f),
                    radius = 0.7f + sr * 2.4f,
                    center = Offset(sx * this.size.width, sy * this.size.height)
                )
            }
            // Slow orbiting planet motif behind the title.
            val cx = this.size.width * 0.78f
            val cy = this.size.height * 0.34f
            val r = this.size.minDimension * 0.16f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF6FC8FF), Color(0xFF1B4E96), Color(0xFF0A1A3A)),
                    center = Offset(cx - r * 0.35f, cy - r * 0.4f),
                    radius = r * 1.6f
                ),
                radius = r, center = Offset(cx, cy)
            )
            val a = t * 6.28f
            drawCircle(
                color = Color(0xFFBBBBC8),
                radius = r * 0.14f,
                center = Offset(cx + cos(a) * r * 1.9f, cy + sin(a) * r * 0.7f)
            )
        }

        Column(
            Modifier
                .fillMaxHeight()
                .padding(start = 46.dp, top = 28.dp, bottom = 28.dp)
                .width(360.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "GALAXY",
                fontSize = 54.sp,
                fontWeight = FontWeight.Black,
                color = Palette.TextHi,
                letterSpacing = 6.sp
            )
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .width(300.dp)
                    .height(4.dp)
                    .background(Palette.HeaderBrush)
            )
            Text(
                "SANDBOX",
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                color = Palette.Cyan,
                letterSpacing = 16.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "Build worlds. Watch civilisations rise.\nThen decide whether they get to keep it.",
                color = Palette.TextMid,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 22.dp)
            )

            NeonButton(
                label = "NEW GALAXY",
                subtitle = "$size star systems, freshly generated",
                glyph = "✦",
                accent = Palette.Cyan,
                selected = true,
                modifier = Modifier.fillMaxWidth()
            ) { onNewGalaxy(size) }

            Spacer(Modifier.height(10.dp))

            if (onContinue != null) {
                NeonButton(
                    label = "CONTINUE",
                    subtitle = "Return to your galaxy",
                    glyph = "▶",
                    accent = Palette.Green,
                    modifier = Modifier.fillMaxWidth()
                ) { onContinue() }
                Spacer(Modifier.height(10.dp))
            }

            NeonButton(
                label = "GALAXY SIZE",
                subtitle = "$size systems",
                glyph = "◎",
                accent = Palette.Violet,
                modifier = Modifier.fillMaxWidth()
            ) { showSize = !showSize }

            if (showSize) {
                Slider(
                    value = size.toFloat(),
                    onValueChange = { size = it.toInt() },
                    valueRange = 16f..140f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))
            NeonButton(
                label = "HOW TO PLAY",
                glyph = "?",
                accent = Palette.Amber,
                modifier = Modifier.fillMaxWidth()
            ) { onHelp() }
        }
    }
}

@Composable
fun HelpSheet(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC03030A)),
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.86f)) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("HOW TO PLAY", color = Palette.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                HelpBlock(
                    "GALAXY VIEW",
                    listOf(
                        "Drag to pan, pinch to zoom across the spiral arms.",
                        "Pick a weapon from the bottom rail, then tap a planet to fire.",
                        "Tap the crosshair button to disarm and just tap planets to inspect them.",
                        "Double-tap any planet to descend to its surface.",
                        "Stars can be destroyed with the Sun Crusher — the whole system dies with them."
                    )
                )
                HelpBlock(
                    "PLANET VIEW",
                    listOf(
                        "Terrain brushes raise land, flood oceans, plant forests or freeze the poles.",
                        "Spawn humans, sylvan, orcs, dwarves, frostkin, synthetics — or wolves and dragons.",
                        "Civilisations claim land, build villages, grow into cities and declare wars on their own.",
                        "Disasters: meteors, volcanoes, nukes, tsunamis, tornadoes, plague, ice age, armageddon.",
                        "Use Restore to undo the damage and reseed a dead world."
                    )
                )
                HelpBlock(
                    "SIMULATION",
                    listOf(
                        "Everything keeps running while you watch: population, tech level, colony fleets, wars.",
                        "Advanced civilisations build planetary shields — break them before your shots land.",
                        "Wreck enough of a faction's worlds and it will turn hostile.",
                        "Speed buttons run the galaxy at 0.5x up to 4x, or pause it entirely."
                    )
                )
                Spacer(Modifier.height(14.dp))
                NeonButton("CLOSE", glyph = "✕", accent = Palette.Danger) { onClose() }
            }
        }
    }
}

@Composable
private fun HelpBlock(title: String, lines: List<String>) {
    Column(Modifier.padding(bottom = 16.dp)) {
        SectionTitle(title)
        Spacer(Modifier.height(6.dp))
        for (l in lines) {
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("▸ ", color = Palette.Violet, fontSize = 12.sp)
                Text(l, color = Palette.TextMid, fontSize = 12.sp, textAlign = TextAlign.Start)
            }
        }
    }
}
