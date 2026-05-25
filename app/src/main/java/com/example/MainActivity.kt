package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Hardware
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WineCellarViewModel by viewModels {
        val app = application as WineCellarApplication
        WineCellarViewModelFactory(app.repository, app.syncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainContentScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainContentScreen(viewModel: WineCellarViewModel) {
    val userEmail by viewModel.syncManager.userEmail.collectAsState()
    val userSkippedLogin by viewModel.userSkippedLogin.collectAsState()

    if (userEmail.isBlank() && !userSkippedLogin) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = { email ->
                viewModel.syncManager.setUserEmail(email)
            },
            onSkip = {
                viewModel.skipLogin()
            }
        )
    } else {
        val activeTab by viewModel.activeTab.collectAsState()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing, // Edge to edge padding support
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = NavigationBarDefaults.Elevation,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    // TAB 1: Cellar Database Inventory (the Wine library, acting as main dashboard)
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Inventory,
                                contentDescription = "Database"
                            )
                        },
                        label = { Text("Library") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("nav_inventory_tab")
                    )

                    // TAB 2: Controller connection management Link
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Hardware,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Hardware") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        ) { innerPadding ->
            Crossfade(
                targetState = activeTab,
                label = "tab_crossfade",
                modifier = Modifier.padding(innerPadding)
            ) { tab ->
                when (tab) {
                    0 -> InventoryScreen(viewModel = viewModel)
                    1 -> SettingsScreen(viewModel = viewModel)
                    else -> InventoryScreen(viewModel = viewModel)
                }
            }
        }
    }
}
