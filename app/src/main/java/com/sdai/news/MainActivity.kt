package com.sdai.news

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.sdai.news.ui.nav.NavGraph
import com.sdai.news.ui.theme.Sdai
import com.sdai.news.ui.theme.SdaiTheme
import com.sdai.news.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    // Runtime permission for foreground notifications (Android 13+).
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ignore result — worker double-checks before posting */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge with system-bar styling that adapts to the theme
        // mode the user picked. We default to a dark scrim because the
        // AMOLED palette is the launch default.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = 0),
            navigationBarStyle = SystemBarStyle.dark(scrim = 0),
        )
        super.onCreate(savedInstanceState)

        maybeRequestNotificationPermission()

        val prefs = SDAINewsApp.get().prefs

        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.AMOLED)
            SdaiTheme(mode = themeMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Sdai.background)
                ) {
                    val nav = rememberNavController()
                    NavGraph(navController = nav)
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
