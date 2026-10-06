package io.grabbit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grabbit.app.DlItem
import io.grabbit.app.DlState
import io.grabbit.app.DownloadQueue
import io.grabbit.app.Engine
import io.grabbit.app.GrabbitApp
import io.grabbit.app.PlayerHolder
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ downloads
@Composable
fun DownloadsScreen() {
    val items by DownloadQueue.items.collectAsState()
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val active = items.filter { it.state == DlState.QUEUED || it.state == DlState.RUNNING || it.state == DlState.ERROR }
    val done = items.filter { it.state == DlState.DONE }
    Column(Modifier.fillMaxSize().background(G.Bg)) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("הורדות", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = G.Text, modifier = Modifier.weight(1f))
            if (active.any { it.state != DlState.ERROR }) OutlinedButton(onClick = { DownloadQueue.cancelAll() }) { Text("בטל הכול", color = G.Text2) }
            else if (items.isNotEmpty()) OutlinedButton(onClick = { DownloadQueue.clearFinished() }) { Text("נקה רשימה", color = G.Text2) }
        }
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(G.Field).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("בתהליך (${active.size})", "הושלמו (${done.size})").forEachIndexed { i, t ->
                Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (tab == i) G.Line else Color.Transparent)
                    .clickable { tab = i }, contentAlignment = Alignment.Center) {
                    Text(t, fontSize = 14.sp, color = if (tab == i) G.Text else G.Muted, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        val list = if (tab == 0) active else done
        if (list.isEmpty()) Text(if (tab == 0) "אין הורדות פעילות." else "עוד לא הורדתם כלום.", color = G.Muted, modifier = Modifier.padding(24.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { d -> DownloadCard(d, onRetry = { DownloadQueue.retry(ctx, d.id) }) }
        }
    }
}

@Composable
private fun DownloadCard(d: DlItem, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(G.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(d.thumbnail, 56.dp, 56.dp, radius = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = G.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    val video = d.kind == "video"
                    Text(d.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (video) G.VideoBadgeText else G.Accent,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (video) G.VideoBadge else G.AccentSoft).padding(horizontal = 7.dp, vertical = 2.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(d.status, fontSize = 12.sp, color = if (d.state == DlState.ERROR) G.Error else G.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            when (d.state) {
                DlState.QUEUED, DlState.RUNNING -> IconButton(onClick = { DownloadQueue.cancel(d.id) }) { Icon(Icons.Rounded.Close, "בטל", tint = G.Muted) }
                DlState.ERROR, DlState.CANCELLED -> IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, "נסה שוב", tint = G.Accent) }
                DlState.DONE -> if (d.files.isNotEmpty()) IconButton(onClick = { PlayerHolder.playFile(d.files.first(), d.title) },
                    modifier = Modifier.clip(CircleShape).background(G.Accent)) { Icon(Icons.Rounded.PlayArrow, "נגן", tint = G.OnAccent) }
            }
        }
        if (d.state == DlState.RUNNING || d.state == DlState.QUEUED) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LinearProgressIndicator(progress = { d.percent / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = G.Accent, trackColor = G.Line)
            }
        }
    }
}

// -------------------------------------------------------------------- library
@Composable
fun LibraryScreen() {
    val items by DownloadQueue.items.collectAsState()
    val done = items.filter { it.state == DlState.DONE }
    Column(Modifier.fillMaxSize().background(G.Bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ספרייה", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = G.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("וידאו", done.count { it.kind == "video" }, Icons.Rounded.Videocam, G.VideoBadgeText, Modifier.weight(1f))
            StatTile("שמע", done.count { it.kind == "audio" }, Icons.Rounded.MusicNote, G.Accent, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(G.Surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ערוצים שמורים", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = G.Text)
            Text("מעקב אחרי ערוצים ו\"הורד חדשים\" יגיעו בשלב 2. בינתיים, כל הקבצים נשמרים בתיקייה Download/Grabbit בטלפון.",
                fontSize = 13.sp, color = G.Muted, lineHeight = 19.sp)
        }
        Text("הורדו לאחרונה", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = G.Text)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(done, key = { it.id }) { d ->
                Row(Modifier.fillMaxWidth().clickable { d.files.firstOrNull()?.let { PlayerHolder.playFile(it, d.title) } }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Thumb(d.thumbnail, 48.dp, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, fontSize = 14.sp, color = G.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(d.label, fontSize = 12.sp, color = G.Muted)
                    }
                    Icon(Icons.Rounded.PlayArrow, "נגן", tint = G.Text2)
                }
            }
        }
    }
}

@Composable
private fun StatTile(title: String, n: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
    Column(modifier.height(84.dp).clip(RoundedCornerShape(18.dp)).background(G.Surface).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Icon(icon, null, tint = tint)
        Text("$title · $n קבצים", fontSize = 14.sp, color = G.Text)
    }
}

// ------------------------------------------------------------------- settings
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onLogin: () -> Unit = {}) {
    val p = GrabbitApp.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var vq by remember { mutableStateOf(p.vq) }
    var aq by remember { mutableStateOf(p.aq) }
    var wantV by remember { mutableStateOf(p.wantVideo) }
    var wantA by remember { mutableStateOf(p.wantAudio) }
    var parallel by remember { mutableIntStateOf(p.parallel) }
    var playQ by remember { mutableIntStateOf(p.playQuality) }
    var autoplay by remember { mutableStateOf(p.autoplay) }
    var cover by remember { mutableStateOf(p.cover) }
    var version by remember { mutableStateOf("…") }
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { version = if (Engine.ready) Engine.version(ctx) else (Engine.initError ?: "טוען…") }

    Column(Modifier.fillMaxSize().background(G.Bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("הגדרות", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = G.Text)

        Section("חשבון יוטיוב") {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (io.grabbit.app.Cookies.present) "מחובר ✓" else "לא מחובר", fontSize = 15.sp,
                        color = if (io.grabbit.app.Cookies.present) G.Accent else G.Text)
                    Text("נדרש כשיוטיוב שואל \"האם אתה בוט\". הפרטים נשמרים רק בטלפון.", fontSize = 12.sp, color = G.Muted)
                }
                Button(onClick = onLogin, colors = ButtonDefaults.buttonColors(containerColor = G.Accent, contentColor = G.OnAccent)) {
                    Text(if (io.grabbit.app.Cookies.present) "התחבר מחדש" else "התחבר ליוטיוב")
                }
            }
        }
        Section("הורדה") {
            SwitchRow("וידאו כברירת מחדל", wantV) { wantV = it; p.wantVideo = it }
            Chips(VIDEO_Q, vq) { vq = it; p.vq = it }
            HorizontalDivider(color = G.Line)
            SwitchRow("שמע כברירת מחדל", wantA) { wantA = it; p.wantAudio = it }
            Chips(AUDIO_Q, aq) { aq = it; p.aq = it }
            HorizontalDivider(color = G.Line)
            SwitchRow("תמונת עטיפה בקובצי שמע", cover) { cover = it; p.cover = it }
            HorizontalDivider(color = G.Line)
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("הורדות במקביל", fontSize = 15.sp, color = G.Text, modifier = Modifier.weight(1f))
                (1..4).forEach { n -> Spacer(Modifier.width(4.dp)); Pill("$n", parallel == n) { parallel = n; p.parallel = n } }
            }
        }
        Section("נגן") {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("איכות ניגון", fontSize = 15.sp, color = G.Text, modifier = Modifier.weight(1f))
                listOf(480, 720, 1080).forEach { q -> Spacer(Modifier.width(4.dp)); Pill("${q}p", playQ == q) { playQ = q; p.playQuality = q } }
            }
            HorizontalDivider(color = G.Line)
            SwitchRow("המשך אוטומטי לסרטון הבא", autoplay) { autoplay = it; p.autoplay = it }
        }
        Section("מנוע ותחזוקה") {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("מנוע ההורדה (yt-dlp)", fontSize = 15.sp, color = G.Text)
                    Text(updateMsg ?: "גרסה $version · עדכנו אם הורדות נכשלות", fontSize = 12.sp, color = G.Muted)
                }
                Button(onClick = {
                    updating = true
                    scope.launch { updateMsg = Engine.update(ctx); version = Engine.version(ctx); updating = false }
                }, enabled = !updating, colors = ButtonDefaults.buttonColors(containerColor = G.AccentSoft, contentColor = G.Accent)) {
                    Text(if (updating) "מעדכן…" else "עדכן")
                }
            }
        }
        Text("Grabbit 1.0 · הקבצים נשמרים ב-Download/Grabbit", fontSize = 12.sp, color = G.Muted)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = G.Accent, modifier = Modifier.padding(horizontal = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(G.Surface), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    FlowRow(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, t) -> Pill(t, value == k) { onPick(k) } }
    }
}
