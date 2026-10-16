package com.houseofvino.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.ui.nav.Navigator
import com.houseofvino.ui.nav.Route
import com.houseofvino.ui.nav.Tab
import com.houseofvino.ui.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** The critical workflows, driven through the real UI and checked against the database. */
@OptIn(ExperimentalTestApi::class)
class FlowsTest {

    private fun SemanticsNodeInteractionsProvider.exists(text: String) =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun SemanticsNodeInteractionsProvider.existsDesc(d: String) =
        onAllNodes(hasContentDescription(d, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun addAWineFromAnEmptyCellar() = runComposeUiTest {
        val g = TestCellar.empty()
        setContent { Screenshots.Host { HouseOfVinoApp(g, ThemeMode.DARK) } }
        waitUntil(5_000) { exists("No wines yet") }
        onNodeWithContentDescription("Add wine").performClick()
        waitUntil(5_000) { existsDesc("Producer") }
        onNodeWithContentDescription("Producer").performTextInput("Neudorf")
        onNodeWithContentDescription("Wine name, optional").performTextInput("Moutere Chardonnay")
        onNodeWithContentDescription("Vintage").performTextInput("2021")
        onNodeWithText("Save").performClick()
        waitUntil(5_000) { exists("Open a bottle") }
        val page = g.search.searchCellar(g.requireSession(), SearchQuery())
        assertEquals(1, page.total)
        assertEquals("Neudorf", page.items.single().producer)
        assertEquals(1, page.bottles)
    }

    @Test
    fun openABottle() = runComposeUiTest {
        val seeded = TestCellar.seeded()
        val g = seeded.graph
        val felton = seeded.ids.getValue("felton")
        val before = g.inventory.holdings(felton).sumOf { it.quantity }
        val nav = Navigator().apply { push(Route.Vintage(felton)) }
        setContent { Screenshots.Host { HouseOfVinoApp(g, ThemeMode.DARK, nav) } }
        waitUntil(5_000) { exists("Open a bottle") }
        onNodeWithText("Open a bottle").performClick()
        waitUntil(5_000) { exists("Open 1 bottle") }
        onNodeWithText("Open 1 bottle").performClick()
        waitUntil(5_000) { g.inventory.holdings(felton).sumOf { it.quantity } == before - 1 }
        waitUntil(5_000) { exists("Opened 1 bottle") }
    }

    @Test
    fun searchForgivesTypos() = runComposeUiTest {
        val seeded = TestCellar.seeded()
        val nav = Navigator().apply { select(Tab.SEARCH) }
        setContent { Screenshots.Host { HouseOfVinoApp(seeded.graph, ThemeMode.DARK, nav) } }
        waitUntil(5_000) { existsDesc("Search") }
        onNodeWithContentDescription("Search").performTextInput("giacossa")
        waitUntil(5_000) { exists("Bruno Giacosa") }
        waitUntil(5_000) { exists("Including giacosa") }
    }
}
