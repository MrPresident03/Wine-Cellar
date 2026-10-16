package com.houseofvino.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import com.houseofvino.ui.components.HovIcons
import androidx.compose.ui.graphics.vector.ImageVector

/** Every destination. Arguments are ids, never whole records. */
sealed interface Route {
    data object Cellar : Route
    data object Search : Route
    data object Journal : Route
    data object Wishlist : Route
    data object Insights : Route
    data class Vintage(val id: String) : Route
    data class AddWine(val prefill: AddWinePrefill? = null) : Route
    data class EditWine(val vintageId: String) : Route
    data class History(val vintageId: String) : Route
    data class Place(val id: String) : Route
    data class EditPlace(val id: String? = null, val parentId: String? = null) : Route
    data object Settings : Route
}

/** Values to start the add form with (from a scan, a wishlist item, or "add another vintage"). */
data class AddWinePrefill(
    val wineId: String? = null,
    val producer: String? = null,
    val name: String? = null,
    val vintage: String? = null,
    val locationId: String? = null,
)

enum class Tab(val label: String, val icon: ImageVector, val root: Route) {
    CELLAR("Cellar", HovIcons.Bottle, Route.Cellar),
    SEARCH("Search", HovIcons.Search, Route.Search),
    JOURNAL("Journal", HovIcons.Glass, Route.Journal),
    WISHLIST("Wishlist", HovIcons.Bookmark, Route.Wishlist),
    INSIGHTS("Insights", HovIcons.Chart, Route.Insights),
}

/** A route on a back stack; [uid] keeps saved state apart when the same route is open in two tabs. */
data class Entry(val route: Route, val uid: Long)

/** One back stack per tab. The current tab's stack is what NavDisplay shows. */
@Stable
class Navigator {
    private var nextUid = 1L
    private val stacks: Map<Tab, SnapshotStateList<Entry>> = Tab.entries.associateWith { mutableStateListOf(Entry(it.root, nextUid++)) }

    var tab: Tab by mutableStateOf(Tab.CELLAR)
        private set

    val stack: SnapshotStateList<Entry> get() = stacks.getValue(tab)

    val current: Route get() = stack.last().route

    fun push(route: Route) {
        stack.add(Entry(route, nextUid++))
    }

    /** Replaces the top entry, e.g. after saving a new wine go to its detail instead of back to the form. */
    fun replace(route: Route) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        push(route)
    }

    /** Returns false at a tab root (so the caller can leave or switch to Cellar). */
    fun pop(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (tab != Tab.CELLAR) {
            tab = Tab.CELLAR
            return true
        }
        return false
    }

    fun select(t: Tab) {
        if (t == tab) {
            // Re-selecting a tab returns to its root.
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            tab = t
        }
    }

    fun openInTab(t: Tab, route: Route) {
        tab = t
        push(route)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
