package io.grabbit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.grabbit.app.DownloadQueue
import io.grabbit.app.GrabbitApp
import io.grabbit.app.Result

val VIDEO_Q = listOf("best" to "הכי טוב", "2160" to "4K", "1440" to "1440p", "1080" to "1080p", "720" to "720p", "480" to "480p", "360" to "360p")
val AUDIO_Q = listOf("mp3-320" to "MP3 320", "mp3-256" to "MP3 256", "mp3-192" to "MP3 192", "mp3-128" to "MP3 128", "m4a" to "M4A מקורי", "opus" to "OPUS")
val LIMITS = listOf("10" to "10 האחרונים", "25" to "25 האחרונים", "50" to "50 האחרונים", "100" to "100 האחרונים", "all" to "הכול")

/** Bottom sheet: choose video / audio / both + qualities, then queue the downloads. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadSheet(items: List<Result>, onDismiss: () -> Unit, onQueued: () -> Unit) {
    val p = GrabbitApp.prefs
    val ctx = LocalContext.current
    var video by remember { mutableStateOf(p.wantVideo) }
    var audio by remember { mutableStateOf(p.wantAudio) }
    var vq by remember { mutableStateOf(p.vq) }
    var aq by remember { mutableStateOf(p.aq) }
    var subs by remember { mutableStateOf(p.subs) }
    var sponsor by remember { mutableStateOf(p.sponsor) }
    var limit by remember { mutableStateOf(p.limit) }
    val collections = items.count { it.kind != "video" }
    val files = items.size * ((if (video) 1 else 0) + (if (audio) 1 else 0))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = G.Surface, dragHandle = { BottomSheetDefaults.DragHandle(color = G.Line) }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("הורדה", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = G.Text)
                Text(if (items.size == 1) items[0].title else "${items.size} פריטים מסומנים", fontSize = 13.sp, color = G.Muted, maxLines = 2)
            }

            FormatCard("וידאו MP4", "כולל קול · עד האיכות שנבחרה", Icons.Rounded.Videocam, video, { video = it }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VIDEO_Q.forEach { (k, t) -> Pill(t, vq == k, enabled = video) { vq = k } }
                }
            }
            FormatCard("שמע בלבד", "עם תמונת עטיפה ופרטים", Icons.Rounded.MusicNote, audio, { audio = it }) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AUDIO_Q.forEach { (k, t) -> Pill(t, aq == k, enabled = audio) { aq = k } }
                }
            }

            if (collections > 0) {
                Column(Modifier.clip(RoundedCornerShape(18.dp)).background(G.Raised).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("מכל ערוץ / פלייליסט להוריד:", fontSize = 14.sp, color = G.Text)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LIMITS.forEach { (k, t) -> Pill(t, limit == k) { limit = k } }
                    }
                }
            }

            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(G.Raised)) {
                SwitchRow("כתוביות עברית / אנגלית", subs) { subs = it }
                HorizontalDivider(color = G.Line)
                SwitchRow("דילוג על קטעי חסות", sponsor) { sponsor = it }
            }
            Text("נשמר ב: Download/Grabbit · הבחירה נזכרת לפעם הבאה", fontSize = 12.sp, color = G.Muted)

            Button(
                onClick = {
                    p.wantVideo = video; p.wantAudio = audio; p.vq = vq; p.aq = aq; p.subs = subs; p.sponsor = sponsor; p.limit = limit
                    val kinds = buildList { if (video) add("video"); if (audio) add("audio") }
                    items.forEach { DownloadQueue.enqueue(ctx, it.url, it.title, it.thumbnail, kinds) }
                    onQueued()
                },
                enabled = files > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = G.Accent, contentColor = G.OnAccent),
            ) {
                Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp))
                Text(if (files == 0) "בחרו וידאו או שמע" else if (collections > 0) "הוסף להורדות" else "הורד $files קבצים",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FormatCard(title: String, sub: String, icon: ImageVector, on: Boolean, onToggle: (Boolean) -> Unit, chips: @Composable () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(G.Raised)
        .border(1.5.dp, if (on) G.Accent else G.Raised, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(G.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = G.Accent)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = G.Text)
                Text(sub, fontSize = 12.sp, color = G.Muted)
            }
            Switch(checked = on, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = G.Accent, checkedThumbColor = G.OnAccent))
        }
        chips()
    }
}

@Composable
fun SwitchRow(text: String, on: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 15.sp, color = G.Text)
            if (sub != null) Text(sub, fontSize = 12.sp, color = G.Muted)
        }
        Switch(checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = G.Accent, checkedThumbColor = G.OnAccent))
    }
}
