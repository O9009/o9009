package io.grabbit.app

import android.content.Context
import android.util.Base64
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

/** One search / channel result. */
data class Result(
    val kind: String,              // video | channel | playlist
    val id: String,
    val title: String,
    val url: String,
    val uploader: String,
    val channelUrl: String?,
    val duration: Int?,
    val views: Long?,
    val subscribers: Long?,
    val count: Int?,
    val thumbnail: String?,
    val isShort: Boolean,
)

data class Stream(val video: String, val audio: String?, val headers: Map<String, String>, val title: String)

/** Everything that talks to yt-dlp. */
object Engine {
    @Volatile var ready = false
        private set
    var initError: String? = null
        private set

    private val clientFallbacks = listOf(null, "youtube:player_client=tv,web_safari", "youtube:player_client=android_vr,mweb")

    fun init(ctx: Context) {
        try {
            YoutubeDL.getInstance().init(ctx)
            FFmpeg.getInstance().init(ctx)
            ready = true
        } catch (e: Throwable) {
            initError = e.message ?: e.toString()
        }
    }

    fun version(ctx: Context): String = try { YoutubeDL.getInstance().version(ctx) ?: "?" } catch (e: Throwable) { "?" }

    suspend fun update(ctx: Context): String = withContext(Dispatchers.IO) {
        try {
            val st = YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel._STABLE)
            when (st) {
                YoutubeDL.UpdateStatus.DONE -> "עודכן לגרסה ${version(ctx)}"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "כבר מעודכן (${version(ctx)})"
                else -> "העדכון הסתיים"
            }
        } catch (e: Throwable) {
            "העדכון נכשל: ${e.message}"
        }
    }

    // ---------------------------------------------------------------- URLs
    private val channelRe = Regex("""youtube\.com/(@[^/?#]+|channel/[^/?#]+|c/[^/?#]+|user/[^/?#]+)(/[^?#]*)?""")
    private val videoIdRe = Regex("""(?:v=|youtu\.be/|/shorts/|/live/)([A-Za-z0-9_-]{11})""")

    fun isYoutube(s: String) = Regex("""^https?://([a-z0-9-]+\.)?(youtube\.com|youtu\.be)/""", RegexOption.IGNORE_CASE).containsMatchIn(s.trim())
    fun kindOf(url: String) = when {
        channelRe.containsMatchIn(url) -> "channel"
        url.contains("/playlist") && url.contains("list=") -> "playlist"
        else -> "video"
    }
    fun videoId(url: String) = videoIdRe.find(url)?.groupValues?.get(1)

    fun channelTab(url: String, tab: String = "videos", query: String? = null): String {
        val m = channelRe.find(url) ?: return url
        val base = url.substring(0, m.groups[1]!!.range.last + 1)
        return if (tab == "search" && !query.isNullOrBlank())
            "$base/search?query=" + URLEncoder.encode(query, "UTF-8")
        else "$base/$tab"
    }

    fun normalize(url: String): String {
        val m = channelRe.find(url) ?: return url
        val tail = (m.groups[2]?.value ?: "").trim('/').substringBefore('/').lowercase()
        val tabs = setOf("videos", "shorts", "streams", "live", "playlists", "search")
        return if (tail in tabs) url else channelTab(url)
    }

    // ----------------------------------------------------- search filter (sp)
    private fun varint(n0: Int): ByteArray {
        var n = n0
        val out = ByteArrayOutputStream()
        while (true) {
            val b = n and 0x7F
            n = n ushr 7
            out.write(if (n != 0) b or 0x80 else b)
            if (n == 0) break
        }
        return out.toByteArray()
    }

    fun searchParam(kind: String, sort: String, date: String, duration: String, features: Set<String>): String {
        val f = ByteArrayOutputStream()
        val video = kind == "video"
        val dateN = mapOf("hour" to 1, "today" to 2, "week" to 3, "month" to 4, "year" to 5)[date]
        if (video && dateN != null) { f.write(0x08); f.write(varint(dateN)) }
        f.write(0x10); f.write(varint(mapOf("video" to 1, "channel" to 2, "playlist" to 3)[kind] ?: 1))
        val durN = mapOf("short" to 1, "long" to 2, "medium" to 3)[duration]
        if (video && durN != null) { f.write(0x18); f.write(varint(durN)) }
        if (video) {
            val feat = mapOf("hd" to 4, "subtitles" to 5, "cc" to 6, "live" to 8, "4k" to 14)
            for (name in features) feat[name]?.let { f.write(varint(it shl 3)); f.write(1) }
        }
        val msg = ByteArrayOutputStream()
        val sortN = mapOf("rating" to 1, "date" to 2, "views" to 3)[sort]
        if (sortN != null) { msg.write(0x08); msg.write(varint(sortN)) }
        val fb = f.toByteArray()
        msg.write(0x12); msg.write(varint(fb.size)); msg.write(fb)
        return Base64.encodeToString(msg.toByteArray(), Base64.NO_WRAP)
    }

    // --------------------------------------------------------------- calls
    private fun run(url: String, extra: List<Pair<String, String?>>, clients: String? = null): String {
        val req = YoutubeDLRequest(url)
        for ((k, v) in extra) if (v == null) req.addOption(k) else req.addOption(k, v)
        if (clients != null) req.addOption("--extractor-args", clients)
        addCookies(req)
        return YoutubeDL.getInstance().execute(req, null, null).out
    }

    private fun runWithFallback(url: String, extra: List<Pair<String, String?>>): String {
        var last: Throwable? = null
        for (c in clientFallbacks) {
            try { return run(url, extra, c) } catch (e: Throwable) { last = e }
        }
        throw RuntimeException(cleanError(last))
    }

    private fun addCookies(req: YoutubeDLRequest) {
        if (Cookies.present) req.addOption("--cookies", Cookies.file.absolutePath)
    }

    fun cleanError(e: Throwable?): String {
        val raw = e?.message ?: ""
        if (raw.contains("not a bot") || raw.contains("Sign in to confirm"))
            return if (Cookies.present) "יוטיוב חוסם זמנית – נסו שוב בעוד כמה דקות, או התחברו מחדש בהגדרות."
                   else "יוטיוב מבקש התחברות. הגדרות ← \"התחבר ליוטיוב\" (פעם אחת)."
        return cleanError0(e)
    }

    private fun cleanError0(e: Throwable?): String {
        val m = (e?.message ?: "שגיאה").lines().lastOrNull { it.contains("ERROR") } ?: (e?.message ?: "שגיאה")
        return m.replace(Regex("^.*ERROR:\\s*"), "").take(300)
    }

    suspend fun search(
        query: String, kind: String, count: Int, sort: String, date: String, duration: String, features: Set<String>,
    ): List<Result> = withContext(Dispatchers.IO) {
        val sp = searchParam(kind, sort, date, duration, features)
        val url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8") +
            "&sp=" + URLEncoder.encode(sp, "UTF-8")
        val out = runWithFallback(url, listOf("-J" to null, "--flat-playlist" to null, "--playlist-end" to count.toString()))
        parseEntries(JSONObject(out)).filter { it.kind == kind }
    }

    /** Videos of a channel tab / playlist / channel search. Returns (title, subscribers, results). */
    suspend fun browse(url: String, count: Int): Triple<String, Long?, List<Result>> = withContext(Dispatchers.IO) {
        val out = runWithFallback(normalize(url), listOf("-J" to null, "--flat-playlist" to null, "--playlist-end" to count.toString()))
        val o = JSONObject(out)
        val title = o.optString("channel").ifBlank { o.optString("uploader") }.ifBlank { o.optString("title") }
        val subs = if (o.has("channel_follower_count") && !o.isNull("channel_follower_count")) o.optLong("channel_follower_count") else null
        Triple(title, subs, parseEntries(o))
    }

    private fun parseEntries(o: JSONObject): List<Result> {
        val arr = o.optJSONArray("entries") ?: return emptyList()
        val list = ArrayList<Result>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            var url = e.optString("url").ifBlank { e.optString("webpage_url") }
            if (url.isNotBlank() && !url.startsWith("http")) {
                url = when {
                    url.startsWith("UC") -> "https://www.youtube.com/channel/$url"
                    url.take(2) in setOf("PL", "OL", "UU", "RD") -> "https://www.youtube.com/playlist?list=$url"
                    else -> "https://www.youtube.com/watch?v=$url"
                }
            }
            if (url.isBlank()) continue
            val kind = kindOf(url)
            var ch = e.optString("channel_url").ifBlank { e.optString("uploader_url") }.ifBlank { null }
            if (ch == null && e.optString("channel_id").isNotBlank()) ch = "https://www.youtube.com/channel/" + e.optString("channel_id")
            if (kind == "channel") ch = url
            val thumbs = e.optJSONArray("thumbnails")
            var thumb: String? = null
            if (thumbs != null && thumbs.length() > 0) {
                thumb = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                if (thumb != null && thumb.startsWith("//")) thumb = "https:$thumb"
            }
            val vid = if (kind == "video") videoId(url) else null
            if (thumb.isNullOrBlank() && vid != null) thumb = "https://i.ytimg.com/vi/$vid/mqdefault.jpg"
            fun optLong(k: String) = if (e.has(k) && !e.isNull(k)) e.optLong(k) else null
            list += Result(
                kind = kind,
                id = vid ?: e.optString("id", url),
                title = e.optString("title").ifBlank { e.optString("channel", url) },
                url = url,
                uploader = e.optString("channel").ifBlank { e.optString("uploader") },
                channelUrl = ch,
                duration = if (e.has("duration") && !e.isNull("duration")) e.optDouble("duration").toInt() else null,
                views = optLong("view_count"),
                subscribers = optLong("channel_follower_count"),
                count = if (e.has("playlist_count") && !e.isNull("playlist_count")) e.optInt("playlist_count") else null,
                thumbnail = thumb,
                isShort = url.contains("/shorts/"),
            )
        }
        return list
    }

    /** Direct media URLs for the player. */
    suspend fun stream(url: String, height: Int): Stream = withContext(Dispatchers.IO) {
        val fmt = "bv*[height<=$height][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$height]+ba/b[height<=$height]/b"
        val o = JSONObject(runWithFallback(url, listOf("-J" to null, "--no-playlist" to null, "-f" to fmt)))
        val headers = HashMap<String, String>()
        val req = o.optJSONArray("requested_formats")
        val (v, a) = if (req != null && req.length() >= 2) {
            req.getJSONObject(0).optJSONObject("http_headers")?.let { h -> h.keys().forEach { headers[it] = h.optString(it) } }
            req.getJSONObject(0).optString("url") to req.getJSONObject(1).optString("url")
        } else {
            o.optJSONObject("http_headers")?.let { h -> h.keys().forEach { headers[it] = h.optString(it) } }
            o.optString("url").ifBlank { o.optString("manifest_url") } to null
        }
        Stream(v, a, headers, o.optString("title"))
    }

    /**
     * Download into [dir]. Returns when done. Throws on failure.
     * Progress: (percent 0..100, status line).
     */
    fun download(
        url: String, dir: String, kind: String, p: Prefs, limit: Int?, processId: String,
        progress: (Float, String) -> Unit,
    ) {
        val collection = kindOf(url) != "video"
        var last: Throwable? = null
        for ((n, clients) in clientFallbacks.withIndex()) {
            if (collection && n > 0) break
            val req = YoutubeDLRequest(normalize(url))
            req.addOption("-o", if (collection) "$dir/%(playlist_index)03d - %(title)s [%(id)s].%(ext)s" else "$dir/%(title)s [%(id)s].%(ext)s")
            req.addOption("--no-mtime")
            req.addOption("--trim-filenames", "150")
            if (collection) {
                req.addOption("--yes-playlist"); req.addOption("--ignore-errors")
                if (limit != null) req.addOption("--playlist-end", limit.toString())
            } else req.addOption("--no-playlist")
            if (clients != null) req.addOption("--extractor-args", clients)
            addCookies(req)
            if (p.sponsor) { req.addOption("--sponsorblock-remove", "sponsor,selfpromo,interaction") }
            if (kind == "audio") {
                req.addOption("-x")
                when (p.aq) {
                    "m4a" -> { req.addOption("-f", "ba[ext=m4a]/ba/b"); req.addOption("--audio-format", "m4a") }
                    "opus" -> { req.addOption("-f", "ba[acodec=opus]/ba/b"); req.addOption("--audio-format", "opus") }
                    else -> { req.addOption("-f", "ba/b"); req.addOption("--audio-format", "mp3"); req.addOption("--audio-quality", p.aq.removePrefix("mp3-") + "K") }
                }
                if (p.cover) { req.addOption("--embed-thumbnail"); req.addOption("--convert-thumbnails", "jpg") }
                req.addOption("--embed-metadata")
            } else {
                val h = p.vq
                req.addOption("-f", if (h == "best") "bv*+ba/b" else "bv*[height<=$h]+ba/b[height<=$h]/bv*+ba/b")
                req.addOption("-S", "res,ext:mp4:m4a,vcodec:h264")
                req.addOption("--merge-output-format", "mp4")
                if (p.subs) { req.addOption("--write-subs"); req.addOption("--sub-langs", "he,iw,en"); req.addOption("--embed-subs") }
                req.addOption("--embed-metadata")
            }
            try {
                YoutubeDL.getInstance().execute(req, processId) { pct, _, line -> progress(pct, line) }
                return
            } catch (e: Throwable) {
                last = e
                val msg = e.message ?: ""
                if (!(msg.contains("403") || msg.contains("Forbidden"))) break
                progress(0f, "ניסיון ${n + 2} – יוטיוב החזיר 403, מנסה בדרך אחרת…")
            }
        }
        throw RuntimeException(cleanError(last))
    }

    fun cancel(processId: String) {
        try { YoutubeDL.getInstance().destroyProcessById(processId) } catch (_: Throwable) {}
    }
}
