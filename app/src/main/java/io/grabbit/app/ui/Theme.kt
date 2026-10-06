package io.grabbit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

object G {
    val Bg = Color(0xFF0E0F12)
    val Surface = Color(0xFF17191E)
    val Raised = Color(0xFF20232A)
    val Field = Color(0xFF1A1C22)
    val Line = Color(0xFF2A2E36)
    val Text = Color(0xFFF2F3F5)
    val Text2 = Color(0xFFC7CBD2)
    val Muted = Color(0xFF9AA0AA)
    val Accent = Color(0xFF3DDC97)
    val OnAccent = Color(0xFF06281A)
    val AccentSoft = Color(0x1F3DDC97)
    val VideoBadge = Color(0xFF25324A)
    val VideoBadgeText = Color(0xFF8DB8FF)
    val Error = Color(0xFFFF8A80)
}

@Composable
fun GrabbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = G.Accent, onPrimary = G.OnAccent,
            background = G.Bg, onBackground = G.Text,
            surface = G.Bg, onSurface = G.Text,
            surfaceVariant = G.Surface, onSurfaceVariant = G.Muted,
            surfaceContainer = G.Surface, surfaceContainerHigh = G.Raised, surfaceContainerLow = G.Surface,
            outline = G.Line, outlineVariant = G.Line,
            secondaryContainer = G.AccentSoft, onSecondaryContainer = G.Accent,
            error = G.Error,
        ),
        content = content,
    )
}

/** Thumbnail with an optional duration badge. */
@Composable
fun Thumb(url: String?, w: Dp, h: Dp, badge: String? = null, radius: Dp = 10.dp, circle: Boolean = false) {
    val shape = if (circle) RoundedCornerShape(50) else RoundedCornerShape(radius)
    Box(Modifier.size(w, h).clip(shape).background(G.Raised)) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(w, h))
        if (!badge.isNullOrBlank()) {
            Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                    .background(Color(0xC7000000), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
        }
    }
}

/** Pill chip: filled with the accent when selected. */
@Composable
fun Pill(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (selected) G.Accent else Color.Transparent
    val fg = when { !enabled -> G.Muted.copy(alpha = 0.5f); selected -> G.OnAccent; else -> G.Text2 }
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, Color(0xFF3A3F48), RoundedCornerShape(18.dp)))
            .then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

fun fmtDuration(sec: Int?): String {
    if (sec == null || sec <= 0) return ""
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun fmtCount(n: Long?): String {
    if (n == null || n <= 0) return ""
    return when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
        n >= 1_000 -> "%.1fK".format(n / 1_000.0).replace(".0K", "K")
        else -> n.toString()
    }
}
