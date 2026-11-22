package com.pocketdev.dropway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketdev.dropway.data.local.LocalHouseDatabase
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.remote.NeonClient
import com.pocketdev.dropway.data.repository.LocalHouseRepository
import com.pocketdev.dropway.data.sharing.SharingManager
import com.pocketdev.dropway.ui.screens.HousesScreen
import com.pocketdev.dropway.ui.screens.NavigationScreen
import com.pocketdev.dropway.ui.screens.ScanScreen
import com.pocketdev.dropway.ui.screens.SettingsScreen
import com.pocketdev.dropway.ui.theme.*

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var neonClient: NeonClient
    private lateinit var localDb: LocalHouseDatabase
    private lateinit var houseRepo: LocalHouseRepository
    private lateinit var sharingManager: SharingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(applicationContext)
        neonClient = NeonClient { prefs.databaseUrl }
        localDb = LocalHouseDatabase.getInstance(applicationContext)
        houseRepo = LocalHouseRepository(applicationContext, localDb, neonClient, prefs)
        sharingManager = SharingManager(applicationContext, prefs, localDb)

        setContent {
            DropwayTheme {
                MainApp(
                    prefs = prefs,
                    neonClient = neonClient,
                    houseRepo = houseRepo,
                    sharingManager = sharingManager
                )
            }
        }
    }
}

enum class ScreenTab(val title: String) {
    HOUSES("คลังบ้าน"),
    SCAN("สแกน"),
    NAVIGATION("นำทาง"),
    SETTINGS("ตั้งค่า")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    prefs: PreferencesManager,
    neonClient: NeonClient,
    houseRepo: LocalHouseRepository,
    sharingManager: SharingManager
) {
    var currentTab by remember { mutableStateOf(ScreenTab.HOUSES) }
    var isDbConfigured by remember { mutableStateOf(prefs.isConfigured()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceCard,
                tonalElevation = 4.dp
            ) {
                // 1. คลังบ้าน
                NavigationBarItem(
                    selected = (currentTab == ScreenTab.HOUSES),
                    onClick = { currentTab = ScreenTab.HOUSES },
                    icon = { Icon(Icons.Default.Home, contentDescription = "คลังบ้าน") },
                    label = { Text("คลังบ้าน") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealDark,
                        selectedTextColor = TealDark,
                        indicatorColor = TealContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                // 2. สแกน
                NavigationBarItem(
                    selected = (currentTab == ScreenTab.SCAN),
                    onClick = { currentTab = ScreenTab.SCAN },
                    icon = { Icon(Icons.Default.Search, contentDescription = "สแกน") },
                    label = { Text("สแกน") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealDark,
                        selectedTextColor = TealDark,
                        indicatorColor = TealContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                // 3. นำทาง
                NavigationBarItem(
                    selected = (currentTab == ScreenTab.NAVIGATION),
                    onClick = { currentTab = ScreenTab.NAVIGATION },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "นำทาง") },
                    label = { Text("นำทาง") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealDark,
                        selectedTextColor = TealDark,
                        indicatorColor = TealContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                // 4. ตั้งค่า
                NavigationBarItem(
                    selected = (currentTab == ScreenTab.SETTINGS),
                    onClick = { currentTab = ScreenTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "ตั้งค่า") },
                    label = { Text("ตั้งค่า") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealDark,
                        selectedTextColor = TealDark,
                        indicatorColor = TealContainer,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Tab Content
            when (currentTab) {
                ScreenTab.HOUSES -> {
                    HousesScreen(
                        houseRepo = houseRepo,
                        sharingManager = sharingManager,
                        neonClient = neonClient,
                        prefs = prefs,
                        onNavigateToSettings = { currentTab = ScreenTab.SETTINGS },
                        onNavigateToNavigation = { currentTab = ScreenTab.NAVIGATION }
                    )
                }
                ScreenTab.SCAN -> {
                    ScanScreen(
                        neonClient = neonClient,
                        prefs = prefs,
                        onNavigateToQueue = { currentTab = ScreenTab.NAVIGATION },
                        onNavigateToSettings = { currentTab = ScreenTab.SETTINGS }
                    )
                }
                ScreenTab.NAVIGATION -> {
                    NavigationScreen(
                        neonClient = neonClient,
                        prefs = prefs,
                        onNavigateToSettings = { currentTab = ScreenTab.SETTINGS }
                    )
                }
                ScreenTab.SETTINGS -> {
                    SettingsScreen(
                        neonClient = neonClient,
                        prefs = prefs,
                        onConnectionUpdated = {
                            isDbConfigured = prefs.isConfigured()
                        }
                    )
                }
            }
        }
    }
}
