package io.grabbit.app

import android.webkit.CookieManager
import java.io.File

/** Exports the in-app YouTube sign-in as a Netscape cookies.txt for yt-dlp. */
object Cookies {
    val file: File get() = File(GrabbitApp.app.filesDir, "cookies.txt")
    val present: Boolean get() = file.exists() && file.length() > 100

    fun save(): Boolean {
        val cm = CookieManager.getInstance()
        cm.flush()
        val expiry = System.currentTimeMillis() / 1000 + 180L * 24 * 3600
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        var n = 0
        for ((host, domain) in listOf("https://www.youtube.com" to ".youtube.com", "https://accounts.google.com" to ".google.com")) {
            val raw = cm.getCookie(host) ?: continue
            for (part in raw.split(";")) {
                val i = part.indexOf('=')
                if (i <= 0) continue
                val name = part.substring(0, i).trim()
                val value = part.substring(i + 1).trim()
                sb.append("$domain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value\n")
                n++
            }
        }
        val signedIn = sb.contains("SAPISID") || sb.contains("__Secure-3PSID") || sb.contains("LOGIN_INFO")
        if (n > 0 && signedIn) { file.writeText(sb.toString()); return true }
        return false
    }

    fun clear() {
        file.delete()
        CookieManager.getInstance().removeAllCookies(null)
    }
}
