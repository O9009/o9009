package io.grabbit.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

enum class DlState { QUEUED, RUNNING, DONE, ERROR, CANCELLED }

data class DlItem(
    val id: String,
    val url: String,
    val title: String,
    val kind: String,            // video | audio
    val label: String,           // "וידאו 1080p", "MP3 192"…
    val source: String,          // video | channel | playlist
    val thumbnail: String?,
    val state: DlState = DlState.QUEUED,
    val percent: Float = 0f,
    val status: String = "בתור",
    val files: List<Uri> = emptyList(),
    val error: String? = null,
)

/** The download queue. Lives as long as the app process; DownloadService keeps the process alive. */
object DownloadQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _items = MutableStateFlow<List<DlItem>>(emptyList())
    val items: StateFlow<List<DlItem>> = _items
    private val running = AtomicInteger(0)
    private val jobs = HashMap<String, Job>()

    private fun update(id: String, f: (DlItem) -> DlItem) {
        synchronized(this) { _items.value = _items.value.map { if (it.id == id) f(it) else it } }
    }

    fun active() = _items.value.count { it.state == DlState.QUEUED || it.state == DlState.RUNNING }

    fun enqueue(ctx: Context, url: String, title: String, thumbnail: String?, kinds: List<String>) {
        val p = GrabbitApp.prefs
        for (kind in kinds) {
            val label = if (kind == "audio") when (p.aq) {
                "m4a" -> "M4A"; "opus" -> "OPUS"; else -> "MP3 " + p.aq.removePrefix("mp3-")
            } else "וידאו " + (if (p.vq == "best") "מקס׳" else p.vq + "p")
            val source = Engine.kindOf(url)
            val item = DlItem(UUID.randomUUID().toString(), url, title, kind,
                (if (source == "channel") "ערוץ · " else if (source == "playlist") "פלייליסט · " else "") + label,
                source, thumbnail)
            synchronized(this) { _items.value = listOf(item) + _items.value }
            jobs[item.id] = scope.launch { runItem(ctx.applicationContext, item) }
        }
        DownloadService.ensureRunning(ctx)
    }

    private suspend fun runItem(ctx: Context, item: DlItem) {
        while (true) {
            val cur = _items.value.firstOrNull { it.id == item.id } ?: return
            if (cur.state == DlState.CANCELLED) return
            val max = GrabbitApp.prefs.parallel
            val n = running.get()
            if (n < max && running.compareAndSet(n, n + 1)) break
            delay(500)
        }
        val dir = File(ctx.cacheDir, "dl/${item.id}").apply { mkdirs() }
        try {
            update(item.id) { it.copy(state = DlState.RUNNING, status = "מתחיל…") }
            val p = GrabbitApp.prefs
            Engine.download(item.url, dir.absolutePath, item.kind, p,
                if (item.source == "video") null else p.limitInt, item.id) { pct, line ->
                val status = when {
                    line.contains("ExtractAudio") || line.contains("Merger") || line.contains("EmbedThumbnail") -> "ממיר / ממזג…"
                    line.startsWith("ניסיון") -> line
                    line.contains("Downloading item") -> line.substringAfter("Downloading item").trim().let { "פריט $it" }
                    else -> "${pct.toInt()}%"
                }
                update(item.id) { it.copy(percent = pct.coerceIn(0f, 100f), status = status) }
            }
            val files = dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }.toList()
            if (files.isEmpty()) throw RuntimeException("לא נוצר קובץ (אולי כבר הורד, או שהסרטון חסום)")
            val uris = files.map { saveToDownloads(ctx, it, item.kind) }
            Engine.videoId(item.url)?.let { GrabbitApp.prefs.markDone(item.kind, it) }
            update(item.id) { it.copy(state = DlState.DONE, percent = 100f, files = uris,
                status = if (uris.size > 1) "הושלם · ${uris.size} קבצים" else "הושלם") }
        } catch (e: Throwable) {
            val cancelled = _items.value.firstOrNull { it.id == item.id }?.state == DlState.CANCELLED
            if (!cancelled) update(item.id) { it.copy(state = DlState.ERROR, error = e.message, status = "נכשל: ${e.message?.take(120)}") }
        } finally {
            running.decrementAndGet()
            dir.deleteRecursively()
        }
    }

    fun cancel(id: String) {
        update(id) { it.copy(state = DlState.CANCELLED, status = "בוטל") }
        Engine.cancel(id)
        jobs.remove(id)?.cancel()
    }

    fun cancelAll() = _items.value.filter { it.state == DlState.QUEUED || it.state == DlState.RUNNING }.forEach { cancel(it.id) }

    fun retry(ctx: Context, id: String) {
        val it = _items.value.firstOrNull { x -> x.id == id } ?: return
        synchronized(this) { _items.value = _items.value.filter { x -> x.id != id } }
        enqueue(ctx, it.url, it.title, it.thumbnail, listOf(it.kind))
    }

    fun clearFinished() {
        synchronized(this) { _items.value = _items.value.filter { it.state == DlState.QUEUED || it.state == DlState.RUNNING } }
    }

    /** Copy a finished file into the public Download/Grabbit folder. */
    private fun saveToDownloads(ctx: Context, f: File, kind: String): Uri {
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (kind == "audio") "audio/mpeg" else "video/mp4"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Grabbit")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw RuntimeException("לא ניתן לשמור בתיקיית ההורדות")
        resolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}

/** Foreground service: keeps downloads running when the app is in the background. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CH = "downloads"
        private const val ID = 7
        fun ensureRunning(ctx: Context) {
            val i = Intent(ctx, DownloadService::class.java)
            ctx.startForegroundService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "הורדות", NotificationManager.IMPORTANCE_LOW))
        val n = build("מוריד…", 0)
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, n)
        scope.launch {
            while (true) {
                delay(1000)
                val items = DownloadQueue.items.value
                val active = items.filter { it.state == DlState.RUNNING || it.state == DlState.QUEUED }
                if (active.isEmpty()) { stopForeground(STOP_FOREGROUND_DETACH); stopSelf(); break }
                val cur = active.firstOrNull { it.state == DlState.RUNNING } ?: active.first()
                nm.notify(ID, build("${active.size} הורדות · ${cur.title}", cur.percent.toInt()))
            }
        }
        return START_NOT_STICKY
    }

    private fun build(text: String, pct: Int): Notification =
        Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Grabbit")
            .setContentText(text)
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .build()

    override fun onDestroy() { scope.coroutineContext[Job]?.cancel(); super.onDestroy() }
}
