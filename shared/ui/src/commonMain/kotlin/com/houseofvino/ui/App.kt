package com.houseofvino.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.houseofvino.ui.app.AppGraph
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.HovIcons
import com.houseofvino.ui.components.LoadingRows
import com.houseofvino.ui.components.MessageState
import com.houseofvino.ui.feature.cellar.CellarScreen
import com.houseofvino.ui.feature.place.EditPlaceScreen
import com.houseofvino.ui.feature.place.PlaceScreen
import com.houseofvino.ui.feature.placeholder.TabNotReadyScreen
import com.houseofvino.ui.feature.search.SearchScreen
import com.houseofvino.ui.feature.settings.SettingsScreen
import com.houseofvino.ui.feature.wine.AddWineScreen
import com.houseofvino.ui.feature.wine.EditWineScreen
import com.houseofvino.ui.feature.wine.HistoryScreen
import com.houseofvino.ui.feature.wine.VintageScreen
import com.houseofvino.ui.nav.Entry
import com.houseofvino.ui.nav.LocalNavigator
import com.houseofvino.ui.nav.Navigator
import com.houseofvino.ui.nav.Route
import com.houseofvino.ui.nav.Tab
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.HouseOfVinoTheme
import com.houseofvino.ui.theme.ThemeMode

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No snackbar host") }

/** Root of the app on every platform. [navigator] can be pre-filled (tests, deep links). */
@Composable
fun HouseOfVinoApp(graph: AppGraph, themeMode: ThemeMode = ThemeMode.SYSTEM, navigator: Navigator = remember { Navigator() }) {
    // Hosts normally provide a ViewModelStoreOwner; fall back to our own so screens always work.
    val fallbackOwner = remember { RootViewModelStoreOwner() }
    val owner = LocalViewModelStoreOwner.current ?: fallbackOwner
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { AppRoot(graph, themeMode, navigator) }
}

private class RootViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

@Composable
private fun AppRoot(graph: AppGraph, themeMode: ThemeMode, navigator: Navigator) {
    HouseOfVinoTheme(themeMode) {
        val snackbar = remember { SnackbarHostState() }
        var started by remember { mutableStateOf(graph.session.value != null) }
        var startError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(graph) {
            runCatching { graph.start() }
                .onSuccess { started = true }
                .onFailure { startError = it.message ?: it::class.simpleName }
        }
        CompositionLocalProvider(LocalGraph provides graph, LocalNavigator provides navigator, LocalSnackbar provides snackbar) {
            val route = navigator.current
            val isForm = route is Route.AddWine || route is Route.EditWine || route is Route.EditPlace
            Scaffold(
                containerColor = Hov.colors.canvas,
                contentWindowInsets = WindowInsets(0),
                snackbarHost = {
                    SnackbarHost(snackbar) { data ->
                        Snackbar(data, containerColor = Hov.colors.surfaceRaised, contentColor = Hov.colors.ink, actionColor = Hov.colors.burgundyBright)
                    }
                },
                bottomBar = { if (!isForm && started) BottomBar(navigator) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).background(Hov.colors.canvas)) {
                    when {
                        startError != null -> MessageState(
                            title = "The cellar couldn't be opened",
                            body = "$startError\n\nYour data has not been changed. Restart the app; if this keeps happening, export a backup from Settings once it opens and contact support.",
                            icon = HovIcons.Alert,
                        )
                        !started -> LoadingRows(8)
                        else -> AppNavDisplay(navigator)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavDisplay(navigator: Navigator) {
    NavDisplay(
        backStack = navigator.stack,
        onBack = { navigator.pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { entry: Entry ->
            NavEntry(entry, contentKey = entry.uid) { e -> Destination(e.route) }
        },
    )
}

@Composable
private fun Destination(route: Route) {
    when (route) {
        Route.Cellar -> CellarScreen()
        Route.Search -> SearchScreen()
        Route.Journal -> TabNotReadyScreen("Journal", "Tasting notes arrive in the next milestone. Notes you write will be kept with each wine and survive when bottles are opened or sold.")
        Route.Wishlist -> TabNotReadyScreen("Wishlist", "The wishlist arrives in the next milestone.")
        Route.Insights -> TabNotReadyScreen("Insights", "Collection insights arrive in the next milestone.")
        is Route.Vintage -> VintageScreen(route.id)
        is Route.AddWine -> AddWineScreen(route.prefill)
        is Route.EditWine -> EditWineScreen(route.vintageId)
        is Route.History -> HistoryScreen(route.vintageId)
        is Route.Place -> PlaceScreen(route.id)
        is Route.EditPlace -> EditPlaceScreen(route.id, route.parentId)
        Route.Settings -> SettingsScreen()
    }
}

@Composable
private fun BottomBar(navigator: Navigator) {
    val c = Hov.colors
    Column(Modifier.fillMaxWidth().background(c.canvas)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val selected = navigator.tab == tab
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Tab) { navigator.select(tab) }
                        .semantics { this.selected = selected }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (selected) c.burgundyBright else c.inkMuted, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(tab.label, style = Hov.type.caption, color = if (selected) c.ink else c.inkMuted)
                }
            }
        }
    }
}
