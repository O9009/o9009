package io.grabbit.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.grabbit.app.ui.*
import kotlinx.coroutines.flow.MutableStateFlow

/** True while the app is shown as a small floating window. */
val PipMode = MutableStateFlow(false)

class MainActivity : ComponentActivity() {
    companion object { var instance: MainActivity? = null }

    /** Shrink to a floating window that keeps playing. */
    fun enterPip(): Boolean {
        if (PlayerHolder.now.value == null) return false
        return try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (_: Throwable) { false }
    }

    // Pressing Home while something plays → floating window automatically.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PlayerHolder.player.isPlaying && PlayerHolder.player.videoSize.width > 0) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        PipMode.value = isInPip
    }

    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    /** A link shared from the YouTube app waits here until the UI picks it up. */
    private val shared = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        handle(intent)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            GrabbitTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppRoot(shared, onOpenBrowser = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
                    })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            Regex("""https?://\S+""").find(text)?.value?.let { shared.value = it }
        }
    }
}

@Composable
private fun AppRoot(shared: MutableStateFlow<String?>, onOpenBrowser: () -> Unit) {
    val vm: SearchVM = viewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var fullPlayer by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf(!Cookies.present) }
    var sheet by remember { mutableStateOf<List<Result>?>(null) }
    val sharedUrl by shared.collectAsState()
    val active by DownloadQueue.items.collectAsState()
    val activeCount = active.count { it.state == DlState.QUEUED || it.state == DlState.RUNNING }

    // Shared from YouTube → open the download sheet right away.
    LaunchedEffect(sharedUrl) {
        val u = sharedUrl ?: return@LaunchedEffect
        shared.value = null
        val kind = Engine.kindOf(u)
        sheet = listOf(Result(kind, Engine.videoId(u) ?: u, u, u, "", if (kind == "channel") u else null,
            null, null, null, null, Engine.videoId(u)?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }, false))
    }

    // Picking a video opens the full player at once (playback starts by itself).
    val openFull by PlayerHolder.openFull.collectAsState()
    LaunchedEffect(openFull) { if (openFull > 0) fullPlayer = true }

    val pip by PipMode.collectAsState()
    if (pip) {
        // Floating window: only the video.
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = PlayerHolder.player; useController = false } },
            modifier = Modifier.fillMaxSize().background(Color.Black))
        return
    }

    BackHandler(enabled = login || fullPlayer || vm.browse != null || tab != 0) {
        when {
            login -> login = false
            fullPlayer -> fullPlayer = false
            vm.browse != null -> vm.back()
            else -> tab = 0
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = G.Bg,
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    MiniPlayer(onExpand = { fullPlayer = true })
                    NavigationBar(containerColor = G.Bg, tonalElevation = 0.dp) {
                        val items = listOf(
                            Triple("חיפוש", Icons.Rounded.Search, 0),
                            Triple("ספרייה", Icons.Rounded.VideoLibrary, 1),
                            Triple("הורדות", Icons.Rounded.Download, 2),
                            Triple("הגדרות", Icons.Rounded.Settings, 3),
                        )
                        items.forEach { (label, icon, i) ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = {
                                    if (i == 2 && activeCount > 0) BadgedBox(badge = { Badge(containerColor = G.Accent, contentColor = G.OnAccent) { Text("$activeCount") } }) { Icon(icon, label) }
                                    else Icon(icon, label)
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = G.Accent, selectedTextColor = G.Text, indicatorColor = G.AccentSoft,
                                    unselectedIconColor = G.Muted, unselectedTextColor = G.Muted),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).statusBarsPadding()) {
                when (tab) {
                    0 -> SearchScreen(vm, onDownload = { sheet = it }, onOpenViewer = onOpenBrowser)
                    1 -> LibraryScreen()
                    2 -> DownloadsScreen()
                    else -> SettingsScreen(onLogin = { login = true })
                }
            }
        }

        AnimatedVisibility(visible = fullPlayer, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                FullPlayer(onClose = { fullPlayer = false }, onDownload = { sheet = it })
            }
        }
    }

    if (login) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            LoginScreen(onDone = { ok -> login = false; if (ok) vm.error = null })
        }
    }

    sheet?.let { items ->
        DownloadSheet(items, onDismiss = { sheet = null }, onQueued = { sheet = null; vm.checked = emptySet(); tab = 2; fullPlayer = false })
    }
}

