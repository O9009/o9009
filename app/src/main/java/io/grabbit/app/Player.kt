package io.grabbit.app

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What the player is showing now. */
data class NowPlaying(
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val result: Result?,         // null when playing a local file
    val loading: Boolean = false,
    val error: String? = null,
)

class GrabbitApp : Application() {
    companion object {
        lateinit var prefs: Prefs
            private set
        lateinit var app: GrabbitApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        prefs = Prefs(this)
        Thread {
            Engine.init(this)
            val sp = getSharedPreferences("grabbit", MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (Engine.ready && now - sp.getLong("last_update", 0) > 24 * 3600 * 1000L) {
                kotlinx.coroutines.runBlocking { Engine.update(this@GrabbitApp) }
                sp.edit().putLong("last_update", now).apply()
            }
        }.start()
        PlayerHolder.init(this)
    }
}

@OptIn(UnstableApi::class)
object PlayerHolder {
    lateinit var player: ExoPlayer
        private set
    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    val now = MutableStateFlow<NowPlaying?>(null)
    /** The list "next" / "previous" / autoplay walk through. */
    val queue = MutableStateFlow<List<Result>>(emptyList())

    fun init(a: Application) {
        app = a
        player = ExoPlayer.Builder(a)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)          // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)           // keep CPU + Wi-Fi awake with the screen off
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && GrabbitApp.prefs.autoplay) step(1)
            }
        })
    }

    /** Media item with title/artist so the notification & lock screen show them. */
    private fun item(uri: String, title: String, artist: String, art: String?) = MediaItem.Builder().setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .apply { art?.let { setArtworkUri(android.net.Uri.parse(it)) } }.build()).build()

    private fun startService() {
        try { app.startService(Intent(app, PlaybackService::class.java)) } catch (_: Throwable) {}
    }

    /** Bumped when the user picks something – the UI opens the full player right away. */
    val openFull = MutableStateFlow(0)

    fun play(r: Result, list: List<Result>? = null, user: Boolean = true) {
        if (user) openFull.value += 1
        if (list != null) queue.value = list.filter { it.kind == "video" }
        now.value = NowPlaying(r.title, r.uploader, r.thumbnail, r, loading = true)
        player.stop()
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val s = Engine.stream(r.url, GrabbitApp.prefs.playQuality)
                val http = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(s.headers)
                s.headers["User-Agent"]?.let { http.setUserAgent(it) }
                val source = when {
                    s.video.contains(".m3u8") -> HlsMediaSource.Factory(http).createMediaSource(item(s.video, r.title, r.uploader, r.thumbnail))
                    s.audio != null -> MergingMediaSource(
                        ProgressiveMediaSource.Factory(http).createMediaSource(item(s.video, r.title, r.uploader, r.thumbnail)),
                        ProgressiveMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(s.audio)),
                    )
                    else -> ProgressiveMediaSource.Factory(http).createMediaSource(item(s.video, r.title, r.uploader, r.thumbnail))
                }
                player.setMediaSource(source)
                player.prepare()
                player.play()
                startService()
                now.value = now.value?.copy(loading = false)
            } catch (e: Throwable) {
                now.value = now.value?.copy(loading = false, error = e.message ?: "שגיאה בניגון")
            }
        }
    }

    fun playFile(uri: Uri, title: String) {
        loadJob?.cancel()
        openFull.value += 1
        now.value = NowPlaying(title, "קובץ מהמכשיר", null, null)
        val src = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(app)).createMediaSource(item(uri.toString(), title, "Grabbit", null))
        player.setMediaSource(src)
        player.prepare()
        player.play()
        startService()
    }

    fun step(d: Int) {
        val cur = now.value?.result ?: return
        val list = queue.value
        val i = list.indexOfFirst { it.url == cur.url }
        val next = list.getOrNull(if (i < 0) 0 else i + d) ?: return
        play(next, user = false)
    }

    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    fun seekBy(ms: Long) { player.seekTo((player.currentPosition + ms).coerceAtLeast(0)) }
    fun stop() { player.stop(); now.value = null }
}

