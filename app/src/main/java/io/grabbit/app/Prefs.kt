package io.grabbit.app

import android.content.Context
import android.content.SharedPreferences

/** All remembered choices. Every setter saves immediately. */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("grabbit", Context.MODE_PRIVATE)

    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun put(f: SharedPreferences.Editor.() -> Unit) = sp.edit().apply(f).apply()

    var wantVideo: Boolean get() = b("want_video", true); set(v) = put { putBoolean("want_video", v) }
    var wantAudio: Boolean get() = b("want_audio", false); set(v) = put { putBoolean("want_audio", v) }
    var vq: String get() = s("vq", "1080"); set(v) = put { putString("vq", v) }
    var aq: String get() = s("aq", "mp3-192"); set(v) = put { putString("aq", v) }
    var subs: Boolean get() = b("subs", false); set(v) = put { putBoolean("subs", v) }
    var cover: Boolean get() = b("cover", true); set(v) = put { putBoolean("cover", v) }
    var sponsor: Boolean get() = b("sponsor", false); set(v) = put { putBoolean("sponsor", v) }
    var parallel: Int get() = i("parallel", 2); set(v) = put { putInt("parallel", v) }
    var limit: String get() = s("limit", "25"); set(v) = put { putString("limit", v) }
    var skipDone: Boolean get() = b("skip_done", true); set(v) = put { putBoolean("skip_done", v) }

    var playQuality: Int get() = i("play_q", 720); set(v) = put { putInt("play_q", v) }
    var autoplay: Boolean get() = b("autoplay", true); set(v) = put { putBoolean("autoplay", v) }

    var sKind: String get() = s("s_kind", "video"); set(v) = put { putString("s_kind", v) }
    var sCount: Int get() = i("s_count", 40); set(v) = put { putInt("s_count", v) }
    var sSort: String get() = s("s_sort", "relevance"); set(v) = put { putString("s_sort", v) }
    var sDate: String get() = s("s_date", "any"); set(v) = put { putString("s_date", v) }
    var sDuration: String get() = s("s_dur", "any"); set(v) = put { putString("s_dur", v) }
    var sFeatures: Set<String> get() = sp.getStringSet("s_feat", emptySet()) ?: emptySet(); set(v) = put { putStringSet("s_feat", v) }
    var noShorts: Boolean get() = b("no_shorts", false); set(v) = put { putBoolean("no_shorts", v) }

    var history: List<String>
        get() = s("history", "").split('\n').filter { it.isNotBlank() }
        set(v) = put { putString("history", v.take(20).joinToString("\n")) }

    fun addHistory(q: String) { history = listOf(q) + history.filter { it != q } }

    /** Ids already downloaded, per kind ("video"/"audio") – for the ✓ marks. */
    fun done(kind: String): Set<String> = sp.getStringSet("done_$kind", emptySet()) ?: emptySet()
    fun markDone(kind: String, id: String) = put { putStringSet("done_$kind", done(kind) + id) }

    val limitInt: Int? get() = limit.toIntOrNull()
}
