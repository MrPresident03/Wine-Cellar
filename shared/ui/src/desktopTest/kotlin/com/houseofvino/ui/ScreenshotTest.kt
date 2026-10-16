package com.houseofvino.ui

import com.houseofvino.ui.nav.AddWinePrefill
import com.houseofvino.ui.nav.Navigator
import com.houseofvino.ui.nav.Route
import com.houseofvino.ui.nav.Tab
import com.houseofvino.ui.theme.ThemeMode
import kotlin.test.Test

/** Not assertions: these images are reviewed by people (and published by CI) to judge the design. */
class ScreenshotTest {
    @Test
    fun keyScreens() {
        val seeded = TestCellar.seeded()
        val g = seeded.graph
        Screenshots.capture("01-cellar-dark", g, Navigator())
        Screenshots.capture("02-cellar-light", g, Navigator(), ThemeMode.LIGHT)
        Screenshots.capture("03-wine-detail-dark", g, Navigator().apply { push(Route.Vintage(seeded.ids.getValue("giacosa"))) })
        Screenshots.capture("04-wine-detail-light", g, Navigator().apply { push(Route.Vintage(seeded.ids.getValue("felton"))) }, ThemeMode.LIGHT)
        Screenshots.capture("05-add-wine", g, Navigator().apply { push(Route.AddWine()) })
        Screenshots.capture("06-place-rack", g, Navigator().apply { push(Route.Place(seeded.ids.getValue("rack"))) })
        Screenshots.capture("07-search", g, Navigator().apply { select(Tab.SEARCH) })
        Screenshots.capture("08-add-another-vintage", g, Navigator().apply { push(Route.AddWine(AddWinePrefill(producer = "Felton Road", name = "Block 5 Pinot Noir"))) }, ThemeMode.LIGHT)
        Screenshots.capture("09-empty-cellar", TestCellar.empty(), Navigator())
    }
}
