package com.nova.galaxysandbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Space = Color(0xFF05050E)
    val Panel = Color(0xE60B0E1C)
    val PanelSoft = Color(0xCC121732)
    val Stroke = Color(0x3369E8FF)
    val Cyan = Color(0xFF48E8FF)
    val Violet = Color(0xFFB07BFF)
    val Amber = Color(0xFFFFC24A)
    val Danger = Color(0xFFFF5470)
    val Green = Color(0xFF5CE08A)
    val TextHi = Color(0xFFE9F4FF)
    val TextMid = Color(0xFFA9BCD6)
    val TextLow = Color(0xFF6C7F99)

    val HeaderBrush = Brush.horizontalGradient(listOf(Cyan, Violet))
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Int = 18,
    borderColor: Color = Palette.Stroke,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Palette.Panel, Palette.PanelSoft)
                )
            )
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(corner.dp))
    ) { content() }
}

@Composable
fun NeonButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Cyan,
    selected: Boolean = false,
    enabled: Boolean = true,
    glyph: String? = null,
    subtitle: String? = null,
    progress: Float = 0f,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = tween(160), label = "scale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.12f))
                ) else Brush.verticalGradient(
                    listOf(Color(0xE6101528), Color(0xCC0A0D1C))
                )
            )
            .border(
                BorderStroke(if (selected) 1.6.dp else 1.dp, if (selected) accent else Palette.Stroke),
                RoundedCornerShape(14.dp)
            )
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (progress > 0.001f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(progress)
                    .height(34.dp)
                    .background(accent.copy(alpha = 0.16f))
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                Text(
                    glyph,
                    color = accent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column {
                Text(
                    label,
                    color = if (selected) Palette.TextHi else Palette.TextMid,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(subtitle, color = Palette.TextLow, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun IconAction(
    glyph: String,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Cyan,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (active) accent.copy(alpha = 0.25f) else Color(0xCC0B0F1E))
            .border(BorderStroke(1.dp, if (active) accent else Palette.Stroke), RoundedCornerShape(13.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = if (active) accent else Palette.TextMid, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatChip(label: String, value: String, accent: Color = Palette.Cyan) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label.uppercase(), color = Palette.TextLow, fontSize = 8.5.sp, letterSpacing = 1.sp)
        Text(value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Palette.TextLow,
        fontSize = 9.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Start,
        modifier = modifier
    )
}

@Composable
fun KeyValueRow(key: String, value: String, accent: Color = Palette.TextHi) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, color = Palette.TextLow, fontSize = 11.sp)
        Text(value, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
