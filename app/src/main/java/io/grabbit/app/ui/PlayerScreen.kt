package io.grabbit.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.grabbit.app.GrabbitApp
import io.grabbit.app.PlayerHolder
import io.grabbit.app.Result
import kotlinx.coroutines.delay

/** Polls the player position for sliders / progress lines. */
@Composable
fun rememberPlayback(): Triple<Long, Long, Boolean> {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val p = PlayerHolder.player
            pos = p.currentPosition; dur = p.duration.coerceAtLeast(0L); playing = p.isPlaying
            delay(500)
        }
    }
    return Triple(pos, dur, playing)
}

fun fmtMs(ms: Long): String = fmtDuration((ms / 1000).toInt()).ifEmpty { "0:00" }

@Composable
fun MiniPlayer(onExpand: () -> Unit) {
    val now by PlayerHolder.now.collectAsState()
    val n = now ?: return
    val (pos, dur, playing) = rememberPlayback()
    Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(G.Raised).clickable(onClick = onExpand)) {
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(n.thumbnail, 48.dp, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = G.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (n.loading) "טוען…" else n.error ?: n.uploader, fontSize = 12.sp, color = if (n.error != null) G.Error else G.Muted, maxLines = 1)
            }
            IconButton(onClick = { PlayerHolder.toggle() }, modifier = Modifier.size(44.dp).clip(CircleShape).background(G.Text)) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "השהה" else "נגן", tint = G.Bg)
            }
            IconButton(onClick = { PlayerHolder.stop() }) { Icon(Icons.Rounded.Close, "סגור נגן", tint = G.Muted) }
        }
        if (dur > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(pos.toFloat() / dur).height(3.dp).background(G.Accent))
    }
}

@OptIn(UnstableApi::class)
@Composable
fun FullPlayer(onClose: () -> Unit, onDownload: (List<Result>) -> Unit) {
    val now by PlayerHolder.now.collectAsState()
    val queue by PlayerHolder.queue.collectAsState()
    val n = now ?: run { onClose(); return }
    val (pos, dur, playing) = rememberPlayback()
    var seeking by remember { mutableStateOf<Float?>(null) }
    var autoplay by remember { mutableStateOf(GrabbitApp.prefs.autoplay) }

    Column(Modifier.fillMaxSize().background(G.Bg)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "מזער", tint = G.Text) }
            Text("מתנגן עכשיו", color = G.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        // video
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = PlayerHolder.player; useController = true; setShowNextButton(false); setShowPreviousButton(false) } },
                modifier = Modifier.fillMaxSize())
            if (n.loading) CircularProgressIndicator(color = G.Accent, modifier = Modifier.align(Alignment.Center))
            n.error?.let { Text("לא הצלחתי לנגן:\n$it", color = G.Error, modifier = Modifier.align(Alignment.Center).padding(16.dp)) }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(n.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = G.Text, maxLines = 3, lineHeight = 25.sp)
            Text(n.uploader, fontSize = 13.sp, color = G.Muted)
        }
        // seek bar (always left-to-right, like every player)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Slider(value = seeking ?: if (dur > 0) pos.toFloat() / dur else 0f,
                    onValueChange = { seeking = it },
                    onValueChangeFinished = { seeking?.let { PlayerHolder.player.seekTo((it * dur).toLong()) }; seeking = null },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = G.Accent, inactiveTrackColor = G.Line))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text(fmtMs(pos), color = G.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(fmtMs(dur), color = G.Muted, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { PlayerHolder.step(-1) }) { Icon(Icons.Rounded.SkipPrevious, "הקודם", tint = G.Text, modifier = Modifier.size(30.dp)) }
                    IconButton(onClick = { PlayerHolder.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, "10 שניות אחורה", tint = G.Text, modifier = Modifier.size(30.dp)) }
                    IconButton(onClick = { PlayerHolder.toggle() }, modifier = Modifier.size(72.dp).clip(CircleShape).background(G.Accent)) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "השהה" else "נגן", tint = G.OnAccent, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { PlayerHolder.seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, "10 שניות קדימה", tint = G.Text, modifier = Modifier.size(30.dp)) }
                    IconButton(onClick = { PlayerHolder.step(1) }) { Icon(Icons.Rounded.SkipNext, "הבא", tint = G.Text, modifier = Modifier.size(30.dp)) }
                }
            }
        }
        // actions
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("הורד", Icons.Rounded.Download, accent = true, enabled = n.result != null, modifier = Modifier.weight(1f)) {
                n.result?.let { onDownload(listOf(it)) }
            }
            ActionTile("מסך בתוך מסך", Icons.Rounded.PictureInPictureAlt, modifier = Modifier.weight(1f)) {
                io.grabbit.app.MainActivity.instance?.enterPip()
            }
            ActionTile(if (autoplay) "הבא אוטומטי ✓" else "הבא אוטומטי", Icons.Rounded.PlaylistPlay, modifier = Modifier.weight(1f)) {
                autoplay = !autoplay; GrabbitApp.prefs.autoplay = autoplay
            }
        }
        // up next
        if (queue.size > 1) {
            Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(G.Surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("הבא בתור", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = G.Text)
                val i = queue.indexOfFirst { it.url == n.result?.url }
                LazyColumn {
                    items(queue.drop(i + 1).take(30), key = { it.url }) { r ->
                        Row(Modifier.fillMaxWidth().clickable { PlayerHolder.play(r) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Thumb(r.thumbnail, 64.dp, 40.dp, radius = 8.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontSize = 13.sp, color = G.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${r.uploader} · ${fmtDuration(r.duration)}", fontSize = 12.sp, color = G.Muted, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier,
                       accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Column(modifier.height(64.dp).clip(RoundedCornerShape(14.dp)).background(if (accent) G.AccentSoft else G.Surface)
        .clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = if (accent) G.Accent else G.Text2)
        Text(text, fontSize = 12.sp, color = if (accent) G.Accent else G.Text2, fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal)
    }
}
