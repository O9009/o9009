package io.grabbit.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.grabbit.app.Cookies

/** Sign in to YouTube inside the app; the cookies are then passed to yt-dlp. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().background(G.Bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDone(false) }) { Icon(Icons.Rounded.Close, "סגור", tint = G.Text) }
            Column(Modifier.weight(1f)) {
                Text("התחברות ליוטיוב", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = G.Text)
                Text("התחברו, וכשרואים את דף הבית של יוטיוב – לחצו \"סיימתי\"", fontSize = 12.sp, color = G.Muted)
            }
            Button(onClick = { onDone(Cookies.save()) },
                colors = ButtonDefaults.buttonColors(containerColor = G.Accent, contentColor = G.OnAccent)) {
                Text("סיימתי", fontWeight = FontWeight.Bold)
            }
        }
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            CookieManager.getInstance().setAcceptCookie(true)
            WebView(ctx).apply {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Look like regular mobile Chrome (Google refuses sign-in from identified WebViews).
                settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("Version/\\S+ "), "")
                webViewClient = WebViewClient()
                loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F")
            }
        })
    }
}
