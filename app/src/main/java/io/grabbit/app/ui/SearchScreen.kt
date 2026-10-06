package io.grabbit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.grabbit.app.Engine
import io.grabbit.app.GrabbitApp
import io.grabbit.app.PlayerHolder
import io.grabbit.app.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A channel / playlist being browsed instead of search results. */
data class Browse(val url: String, val title: String, val subscribers: Long?, val kind: String)

class SearchVM : ViewModel() {
    private val p = GrabbitApp.prefs
    var query by mutableStateOf("")
    var kind by mutableStateOf(p.sKind)
    var count by mutableIntStateOf(p.sCount)
    var sort by mutableStateOf(p.sSort)
    var date by mutableStateOf(p.sDate)
    var duration by mutableStateOf(p.sDuration)
    var features by mutableStateOf(p.sFeatures)
    var noShorts by mutableStateOf(p.noShorts)

    var results by mutableStateOf<List<Result>>(emptyList())
    var checked by mutableStateOf<Set<String>>(emptySet())       // urls
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var browse by mutableStateOf<Browse?>(null)
    private var saved: Pair<List<Result>, String>? = null          // results + query before browsing
    private var job: Job? = null

    val visible: List<Result>
        get() = results.filter { r -> !(noShorts && r.kind == "video" && (r.isShort || (r.duration ?: 999) <= 60)) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        if (Engine.isYoutube(q)) {
            if (Engine.kindOf(q) != "video") open(q, q) else PlayerHolder.play(Result("video", Engine.videoId(q) ?: q, q, q, "", null, null, null, null, null, null, false))
            return
        }
        p.sKind = kind; p.sCount = count; p.sSort = sort; p.sDate = date; p.sDuration = duration
        p.sFeatures = features; p.noShorts = noShorts; p.addHistory(q)
        browse = null; saved = null
        run { Engine.search(q, kind, count, sort, date, duration, features) }
    }

    fun open(url: String, title: String, tab: String? = null, inChannel: String? = null) {
        if (browse == null) saved = results to query
        val target = when {
            inChannel != null -> Engine.channelTab(url, "search", inChannel)
            tab != null -> Engine.channelTab(url, tab)
            else -> url
        }
        browse = Browse(target, title, browse?.subscribers, Engine.kindOf(url))
        run {
            val (t, subs, list) = Engine.browse(target, 60)
            browse = Browse(target, t.ifBlank { title }, subs, Engine.kindOf(url))
            list
        }
    }

    fun back() {
        browse = null
        saved?.let { results = it.first }
        saved = null
        checked = emptySet()
    }

    private fun run(block: suspend () -> List<Result>) {
        job?.cancel()
        loading = true; error = null; checked = emptySet()
        job = viewModelScope.launch {
            try { results = block() } catch (e: Throwable) { error = e.message ?: "שגיאה"; results = emptyList() }
            loading = false
        }
    }

    fun toggle(r: Result) { checked = if (r.url in checked) checked - r.url else checked + r.url }
    fun selectAll() { checked = if (checked.size == visible.size) emptySet() else visible.map { it.url }.toSet() }
    fun selected() = results.filter { it.url in checked }
}

private val KINDS = listOf("video" to "סרטונים", "channel" to "ערוצים", "playlist" to "פלייליסטים")
private val SORTS = listOf("relevance" to "רלוונטיות", "date" to "הכי חדש", "views" to "הכי נצפה", "rating" to "דירוג")
private val DATES = listOf("any" to "כל הזמנים", "hour" to "בשעה האחרונה", "today" to "היום", "week" to "השבוע", "month" to "החודש", "year" to "השנה")
private val DURS = listOf("any" to "כל אורך", "short" to "עד 4 דק׳", "medium" to "4–20 דק׳", "long" to "20+ דק׳")
private val COUNTS = listOf(20, 40, 60, 80, 100, 150, 200)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: SearchVM, onDownload: (List<Result>) -> Unit, onOpenViewer: () -> Unit) {
    val p = GrabbitApp.prefs
    val doneV = remember(vm.results, vm.loading) { p.done("video") }
    val doneA = remember(vm.results, vm.loading) { p.done("audio") }

    Column(Modifier.fillMaxSize().background(G.Bg)) {
        // ---- header
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(G.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Download, null, tint = G.OnAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Grabbit", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = G.Text, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenViewer) { Icon(Icons.Rounded.Public, "יוטיוב בדפדפן", tint = G.Text2) }
            }
            var showHistory by remember { mutableStateOf(false) }
            Box {
                TextField(
                    value = vm.query, onValueChange = { vm.query = it },
                    placeholder = { Text("חיפוש ביוטיוב או הדבקת קישור", color = G.Muted) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = G.Muted) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Rounded.Close, "נקה", tint = G.Muted) }
                        else if (p.history.isNotEmpty()) IconButton(onClick = { showHistory = true }) { Icon(Icons.Rounded.History, "חיפושים אחרונים", tint = G.Muted) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = G.Field, unfocusedContainerColor = G.Field,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = G.Accent, focusedTextColor = G.Text, unfocusedTextColor = G.Text,
                    ),
                    modifier = Modifier.fillMaxWidth().border(1.dp, G.Line, RoundedCornerShape(24.dp)),
                )
                DropdownMenu(expanded = showHistory, onDismissRequest = { showHistory = false }) {
                    p.history.forEach { h -> DropdownMenuItem(text = { Text(h) }, onClick = { showHistory = false; vm.query = h; vm.search() }) }
                }
            }

            if (vm.browse == null) {
                // type tabs
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(G.Field).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KINDS.forEach { (k, t) ->
                        val sel = vm.kind == k
                        Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) G.Line else Color.Transparent)
                            .clickable { vm.kind = k; if (vm.query.isNotBlank()) vm.search() }, contentAlignment = Alignment.Center) {
                            Text(t, fontSize = 14.sp, color = if (sel) G.Text else G.Muted, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                // filter chips
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuChip(SORTS, vm.sort) { vm.sort = it; vm.search() }
                    if (vm.kind == "video") {
                        MenuChip(DATES, vm.date) { vm.date = it; vm.search() }
                        MenuChip(DURS, vm.duration) { vm.duration = it; vm.search() }
                    }
                    MenuChip(COUNTS.map { it.toString() to "$it תוצאות" }, vm.count.toString(), always = true) { vm.count = it.toInt(); vm.search() }
                    ToggleChip("בלי Shorts", vm.noShorts) { vm.noShorts = !vm.noShorts; p.noShorts = vm.noShorts }
                    if (vm.kind == "video") {
                        listOf("hd" to "HD", "4k" to "4K", "subtitles" to "עם כתוביות", "live" to "שידור חי").forEach { (k, t) ->
                            ToggleChip(t, k in vm.features) { vm.features = if (k in vm.features) vm.features - k else vm.features + k; vm.search() }
                        }
                    }
                }
            } else {
                ChannelBar(vm, onDownload)
            }
        }

        // ---- selection bar
        if (vm.visible.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(G.Surface)
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.selectAll() }) {
                    Icon(if (vm.checked.size == vm.visible.size) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = G.Text2)
                    Spacer(Modifier.width(6.dp)); Text("סמן הכול", color = G.Text2)
                }
                Text("${vm.checked.size} מסומנים · ${vm.visible.size} תוצאות", color = G.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Button(onClick = { onDownload(vm.selected()) }, enabled = vm.checked.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = G.Accent, contentColor = G.OnAccent),
                    contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("הורד (${vm.checked.size})", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ---- results
        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = G.Accent) }
            vm.error != null -> Text("שגיאה: ${vm.error}\n\nאם זה חוזר – הגדרות ← עדכן מנוע.", color = G.Error, modifier = Modifier.padding(24.dp))
            vm.results.isEmpty() -> Text("חפשו סרטון, ערוץ או פלייליסט – או שתפו קישור מאפליקציית יוטיוב ל-Grabbit.",
                color = G.Muted, modifier = Modifier.padding(24.dp))
            else -> {
                val playing by PlayerHolder.now.collectAsState()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
                    itemsIndexed(vm.visible, key = { _, r -> r.url }) { _, r ->
                        val have = when {
                            r.id in doneV && r.id in doneA -> "וידאו + שמע"
                            r.id in doneV -> "וידאו"; r.id in doneA -> "שמע"; else -> null
                        }
                        ResultRow(r, r.url in vm.checked, playing?.result?.url == r.url, have,
                            onCheck = { vm.toggle(r) },
                            onOpen = { if (r.kind == "video") PlayerHolder.play(r, vm.visible) else vm.open(r.url, r.title) },
                            onChannel = { r.channelUrl?.let { vm.open(it, r.uploader) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelBar(vm: SearchVM, onDownload: (List<Result>) -> Unit) {
    val b = vm.browse ?: return
    var inChannel by remember(b.url) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.Rounded.ArrowForward, "חזרה", tint = G.Text) }
            Column(Modifier.weight(1f)) {
                Text(b.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = G.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subs = fmtCount(b.subscribers)
                if (subs.isNotEmpty()) Text("$subs מנויים", fontSize = 12.sp, color = G.Muted)
            }
            Button(onClick = { onDownload(listOf(Result(b.kind, b.url, b.title, b.url, b.title, b.url, null, null, null, null, null, false))) },
                colors = ButtonDefaults.buttonColors(containerColor = G.Accent, contentColor = G.OnAccent)) {
                Text(if (b.kind == "channel") "הורד ערוץ" else "הורד הכול", fontWeight = FontWeight.Bold)
            }
        }
        if (b.kind == "channel") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("videos" to "סרטונים", "shorts" to "Shorts", "streams" to "שידורים", "playlists" to "פלייליסטים").forEach { (k, t) ->
                    Pill(t, b.url.contains("/$k")) { vm.open(b.url, b.title, tab = k) }
                }
            }
            OutlinedTextField(value = inChannel, onValueChange = { inChannel = it }, singleLine = true,
                placeholder = { Text("חיפוש בתוך הערוץ", color = G.Muted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = G.Muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (inChannel.isNotBlank()) vm.open(b.url, b.title, inChannel = inChannel) }),
                shape = RoundedCornerShape(21.dp), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MenuChip(options: List<Pair<String, String>>, value: String, always: Boolean = false, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: options.first().second
    val active = always || value != options.first().first
    Box {
        Row(Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) G.AccentSoft else Color.Transparent)
            .border(1.dp, if (active) G.Accent else G.Line, RoundedCornerShape(16.dp))
            .clickable { open = true }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = if (active) G.Accent else G.Text2)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = if (active) G.Accent else G.Muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (k, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { open = false; onPick(k) }) }
        }
    }
}

@Composable
private fun ToggleChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) G.AccentSoft else Color.Transparent)
        .border(1.dp, if (on) G.Accent else G.Line, RoundedCornerShape(16.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, fontSize = 13.sp, color = if (on) G.Accent else G.Text2)
    }
}

@Composable
fun ResultRow(r: Result, checked: Boolean, playing: Boolean, have: String?, onCheck: () -> Unit, onOpen: () -> Unit, onChannel: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (playing) G.Surface else Color.Transparent)
        .clickable(onClick = onOpen).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onCheck() },
            colors = CheckboxDefaults.colors(checkedColor = G.Accent, checkmarkColor = G.OnAccent, uncheckedColor = Color(0xFF5A606B)))
        when (r.kind) {
            "channel" -> Thumb(r.thumbnail, 68.dp, 68.dp, circle = true)
            else -> Thumb(r.thumbnail, 120.dp, 68.dp,
                badge = if (r.kind == "playlist") r.count?.let { "$it סרטונים" } else fmtDuration(r.duration))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val prefix = when (r.kind) { "channel" -> "ערוץ · "; "playlist" -> "פלייליסט · "; else -> "" }
            Text(prefix + r.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (playing) G.Accent else G.Text, lineHeight = 19.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.kind == "video" && r.uploader.isNotBlank()) {
                    Text("${r.uploader} ›", fontSize = 12.sp, color = G.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clickable(onClick = onChannel))
                }
                val meta = when (r.kind) {
                    "video" -> fmtCount(r.views).let { if (it.isEmpty()) "" else " · $it צפיות" }
                    "channel" -> fmtCount(r.subscribers).let { if (it.isEmpty()) "" else "$it מנויים" }
                    else -> r.uploader
                }
                Text(meta, fontSize = 12.sp, color = G.Muted, maxLines = 1)
            }
            if (have != null) Text("✓ $have", fontSize = 11.sp, color = G.Accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(G.AccentSoft).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}
