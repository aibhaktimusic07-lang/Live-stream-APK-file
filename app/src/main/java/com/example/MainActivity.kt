package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.PlatformsScreen
import com.example.ui.screens.PlaylistScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.LiveStudioTheme
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.StudioDarkSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

enum class AppNavDestination {
    DASHBOARD,
    PLAYLIST,
    PLATFORMS,
    SETTINGS,
    LOGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsState()

            // Dynamic Locale switching between English and Hindi
            val currentLocale = if (settings.language == "hi") Locale("hi") else Locale("en")
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(currentLocale)
                setLayoutDirection(currentLocale)
            }

            CompositionLocalProvider(LocalConfiguration provides configuration) {
                LiveStudioTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = StudioDarkBg
                    ) {
                        MainAppContent(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppNavDestination.DASHBOARD) }

    // Request notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        containerColor = StudioDarkBg,
        bottomBar = {
            NavigationBar(
                containerColor = StudioDarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppNavDestination.DASHBOARD,
                    onClick = { currentScreen = AppNavDestination.DASHBOARD },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = stringResource(R.string.nav_dashboard)) },
                    label = { Text(stringResource(R.string.nav_dashboard), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonBlue,
                        selectedTextColor = NeonBlue,
                        indicatorColor = NeonBlue.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == AppNavDestination.PLAYLIST,
                    onClick = { currentScreen = AppNavDestination.PLAYLIST },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = stringResource(R.string.nav_playlist)) },
                    label = { Text(stringResource(R.string.nav_playlist), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonPurple,
                        selectedTextColor = NeonPurple,
                        indicatorColor = NeonPurple.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == AppNavDestination.PLATFORMS,
                    onClick = { currentScreen = AppNavDestination.PLATFORMS },
                    icon = { Icon(Icons.Default.CastConnected, contentDescription = stringResource(R.string.nav_platforms)) },
                    label = { Text(stringResource(R.string.nav_platforms), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonBlue,
                        selectedTextColor = NeonBlue,
                        indicatorColor = NeonBlue.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == AppNavDestination.SETTINGS,
                    onClick = { currentScreen = AppNavDestination.SETTINGS },
                    icon = { Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.nav_settings)) },
                    label = { Text(stringResource(R.string.nav_settings), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonPurple,
                        selectedTextColor = NeonPurple,
                        indicatorColor = NeonPurple.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    )
                )

                NavigationBarItem(
                    selected = currentScreen == AppNavDestination.LOGS,
                    onClick = { currentScreen = AppNavDestination.LOGS },
                    icon = { Icon(Icons.Default.History, contentDescription = stringResource(R.string.nav_logs)) },
                    label = { Text(stringResource(R.string.nav_logs), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonBlue,
                        selectedTextColor = NeonBlue,
                        indicatorColor = NeonBlue.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppNavDestination.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToPlaylist = { currentScreen = AppNavDestination.PLAYLIST },
                    onNavigateToPlatforms = { currentScreen = AppNavDestination.PLATFORMS }
                )
                AppNavDestination.PLAYLIST -> PlaylistScreen(
                    viewModel = viewModel
                )
                AppNavDestination.PLATFORMS -> PlatformsScreen(
                    viewModel = viewModel
                )
                AppNavDestination.SETTINGS -> SettingsScreen(
                    viewModel = viewModel
                )
                AppNavDestination.LOGS -> LogsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
