package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.data.AuthState
import com.example.data.UserCellarState
import com.example.ui.screens.CellarSetupScreen
import com.example.ui.screens.ClimateScreen
import com.example.ui.screens.ErrorScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.LoadingScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: WineCellarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppRoot(viewModel)
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector, val tag: String)

private val TABS = listOf(
    Tab("Cellar", Icons.Rounded.Inventory, "nav_cellar"),
    Tab("Climate", Icons.Rounded.Thermostat, "nav_climate"),
    Tab("Settings", Icons.Rounded.Settings, "nav_settings")
)

@Composable
fun AppRoot(viewModel: WineCellarViewModel) {
    val authState by viewModel.authState.collectAsState()
    val userCellar by viewModel.userCellar.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val ready = authState is AuthState.SignedIn && userCellar is UserCellarState.Ready

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (ready) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("bottom_nav_bar")) {
                    TABS.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = activeTab == index,
                            onClick = { viewModel.selectTab(index) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (authState) {
                is AuthState.Loading -> LoadingScreen("Starting…")
                is AuthState.SignedOut -> LoginScreen(viewModel)
                is AuthState.SignedIn -> when (val state = userCellar) {
                    is UserCellarState.Loading -> LoadingScreen(
                        "Loading your cellar…\nThe first sign-in on a phone needs an internet connection."
                    )
                    is UserCellarState.Error -> ErrorScreen(state.message, onSignOut = { viewModel.signOut() })
                    is UserCellarState.None -> CellarSetupScreen(viewModel)
                    is UserCellarState.Ready -> Crossfade(targetState = activeTab, label = "tabs") { tab ->
                        when (tab) {
                            0 -> InventoryScreen(viewModel)
                            1 -> ClimateScreen(viewModel)
                            else -> SettingsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    val result = importResult
    if (result != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportResult() },
            title = { Text("Import from the previous version") },
            text = { Text(result) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissImportResult() }) { Text("OK") }
            }
        )
    }
}
